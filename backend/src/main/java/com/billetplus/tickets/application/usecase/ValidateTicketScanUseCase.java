package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.QrVerificationResult;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.exception.BilletNonValideException;
import com.billetplus.tickets.domain.exception.TicketAlreadyScannedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — contrôle d'un billet à l'entrée
 * (scan du QR code par un agent).
 *
 * Dernier rempart contre le double passage : la PWA scanner vérifie la
 * signature hors ligne (ADR 0004), mais ne peut pas garantir l'unicité. La
 * règle « un billet n'entre qu'une fois » vit dans le domaine
 * ({@link Ticket#marquerCommeScanne()}) ; ce cas d'usage vérifie le QR, le
 * relie au billet en base, et traduit les refus du domaine en {@link ResultatScan}.
 *
 * {@code @Transactional} est indispensable : la lecture du billet et son
 * enregistrement doivent partager la transaction, pour que le @Version des
 * billets rejette deux scans simultanés (le second lève alors
 * OptimisticLockingFailureException à la validation, traduite en 409 : à lire
 * comme « déjà scanné »).
 *
 * <b>Contrat de l'appelant</b> (respecté par {@link ScanTicketUseCase}, appelé par la route de scan) :
 * <ul>
 *   <li>{@code eventIdDuControle} n'est accepté que si l'utilisateur identifié par
 *       le jeton y est affecté comme agent (lu en base), ou en est l'organisateur
 *       (vérifié par le module events). Un agent affecté à plusieurs événements
 *       indique lequel il contrôle ; une valeur pour laquelle il n'est pas affecté
 *       est refusée en 403, AVANT tout appel à ce cas d'usage. Sans ce contrôle,
 *       n'importe quel agent scannerait pour n'importe quel événement ;</li>
 *   <li>un conflit de version (OptimisticLockingFailureException, 409 : deux
 *       scans simultanés du même billet) n'est jamais présenté comme un « problème
 *       technique, réessayez » : la route rejoue l'appel une fois (sans risque, il
 *       renvoie alors DEJA_SCANNE). L'écran du scanner traite toute issue autre
 *       qu'ACCEPTE comme un refus d'entrée ;</li>
 *   <li>la réponse au client est le {@link ResultatScan}, jamais les messages des
 *       exceptions du domaine, et n'y ajoute aucune donnée de la commande ni de
 *       l'acheteur ;</li>
 *   <li>le DTO d'entrée borne le QR ({@code @Size(max = 1024)}, comme
 *       {@link #TAILLE_MAX_QR}) et la route limite le débit par compte agent.</li>
 * </ul>
 *
 * Limites connues (voir docs/HANDOFF.md) : le QR est un jeton porteur. Une copie
 * présentée avant le titulaire est acceptée. Le statut de l'événement
 * (annulé) n'est pas vérifié ici, seul celui du billet l'est.
 */
@Service
public class ValidateTicketScanUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateTicketScanUseCase.class);

    /**
     * Un QR fait environ 230 caractères (JSON compact + signature hexadécimale de
     * 128 caractères). Au-delà de cette borne, le contenu est refusé sans même
     * vérifier la signature, pour ne pas faire travailler la cryptographie sur
     * des données arbitrairement longues.
     */
    static final int TAILLE_MAX_QR = 1024;

    private final QRSigner qrSigner;
    private final TicketRepository ticketRepository;

    public ValidateTicketScanUseCase(QRSigner qrSigner, TicketRepository ticketRepository) {
        this.qrSigner = qrSigner;
        this.ticketRepository = ticketRepository;
    }

    /**
     * @param qrContent         contenu brut lu dans le QR code (peut être null ou quelconque)
     * @param eventIdDuControle événement dont l'agent contrôle l'entrée
     * @throws IllegalArgumentException si {@code eventIdDuControle} est absent
     */
    @Transactional
    public ResultatScan execute(String qrContent, UUID eventIdDuControle) {
        if (eventIdDuControle == null) {
            throw new IllegalArgumentException("L'événement contrôlé est obligatoire");
        }
        if (qrContent == null || qrContent.isBlank() || qrContent.length() > TAILLE_MAX_QR) {
            return ResultatScan.invalide();
        }

        QrVerificationResult qr = qrSigner.verify(qrContent);
        if (!qr.valide()) {
            return ResultatScan.invalide();
        }

        Optional<Ticket> trouve = ticketRepository.findById(qr.ticketId());
        if (trouve.isEmpty()) {
            LOG.warn("QR à signature valide pour un billet inconnu : {}", qr.ticketId());
            return ResultatScan.invalide();
        }
        Ticket ticket = trouve.get();

        // Le QR signe emisLe à la MILLISECONDE (champ iat), la base le garde à la
        // microseconde : comparer après troncature, sinon tout billet légitime
        // serait refusé.
        boolean coherent = ticket.getEventId().equals(qr.eventId())
            && ticket.getEmisLe().truncatedTo(ChronoUnit.MILLIS).equals(qr.issuedAt());
        if (!coherent) {
            // Une signature authentique sur des données qui ne correspondent pas à
            // la base : clé privée compromise ou défaut d'émission. À investiguer.
            LOG.error("QR à signature valide mais incohérent avec la base pour le billet {}", ticket.getId());
            return ResultatScan.invalide();
        }

        if (!ticket.getEventId().equals(eventIdDuControle)) {
            return ResultatScan.autreEvenement(ticket.getId());
        }

        try {
            ticket.marquerCommeScanne();
        } catch (TicketAlreadyScannedException e) {
            return ResultatScan.dejaScanne(ticket.getId(), e.getPremierScanLe());
        } catch (BilletNonValideException e) {
            return ResultatScan.nonValide(ticket.getId(), e.getStatus());
        }
        ticketRepository.save(ticket);
        return ResultatScan.accepte(ticket.getId());
    }
}

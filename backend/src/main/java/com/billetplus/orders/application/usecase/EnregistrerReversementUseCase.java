package com.billetplus.orders.application.usecase;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Reversement;
import com.billetplus.shared.evenements.ReversementEnregistre;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.ControleEntrees;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Enregistre un virement fait à la main par l'administrateur vers l'organisateur d'un
 * événement (ADR 0009). Billet+ ne déclenche aucun virement : il garde seulement la trace.
 * L'administrateur vient du jeton, jamais du corps de la requête.
 */
@Service
public class EnregistrerReversementUseCase {

    private final OrderRepository orderRepository;
    private final ReversementRepository reversementRepository;
    private final StatutEvenements statutEvenements;
    private final Comptes comptes;
    private final ControleEntrees controleEntrees;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public EnregistrerReversementUseCase(OrderRepository orderRepository, ReversementRepository reversementRepository,
                                         StatutEvenements statutEvenements, Comptes comptes,
                                         ControleEntrees controleEntrees, ApplicationEventPublisher publisher,
                                         Clock clock) {
        this.orderRepository = orderRepository;
        this.reversementRepository = reversementRepository;
        this.statutEvenements = statutEvenements;
        this.comptes = comptes;
        this.controleEntrees = controleEntrees;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Premier jour où un virement positif est permis : le lendemain de la plus tardive de ces
     * dates (heure de Dakar = UTC) : la fin de l'événement, et sa dernière vente payée (un
     * événement daté dans le passé et vendu ensuite ne contourne pas le délai).
     */
    public static LocalDate reversablesLe(Instant finEvenement, Optional<Instant> derniereVente) {
        Instant reference = derniereVente.filter(v -> v.isAfter(finEvenement)).orElse(finEvenement);
        return LocalDate.ofInstant(reference, ZoneOffset.UTC).plusDays(1);
    }

    /**
     * @throws NoSuchElementException   (404) événement sans aucune commande payée ni reversement
     * @throws IllegalArgumentException (400) montant, référence, motif ou date invalides
     * @throws BusinessConflictException (409) virement déjà enregistré, ou virement positif vers
     *                                   l'organisateur d'un événement annulé (ses acheteurs sont
     *                                   remboursés : il n'a plus rien à recevoir)
     */
    @Transactional
    public Reversement execute(UUID eventId, long montantFcfa, String reference, String motif, LocalDate reverseLe,
                               UUID administrateurId) {
        return execute(eventId, montantFcfa, reference, motif, reverseLe, administrateurId, null);
    }

    /**
     * Un virement positif exige aussi (ADR 0012) : organisateur vérifié ; lendemain de
     * l'événement atteint ; au moins un billet scanné, sauf dérogation motivée. Une correction
     * (montant négatif) reste toujours possible.
     *
     * @param derogationScans motif d'un virement sans billet scanné (entrée sans contrôle), ou null
     */
    @Transactional
    public Reversement execute(UUID eventId, long montantFcfa, String reference, String motif, LocalDate reverseLe,
                               UUID administrateurId, String derogationScans) {
        boolean connu = orderRepository.existeCommandePayee(eventId) || reversementRepository.existePour(eventId);
        if (!connu) {
            throw new NoSuchElementException("Aucune vente pour cet événement : " + eventId);
        }
        if (montantFcfa > 0 && statutEvenements.estAnnule(eventId)) {
            throw new BusinessConflictException("Événement annulé : aucun virement à l'organisateur, seule une correction est possible");
        }
        Instant maintenant = Instant.now(clock);
        UUID organisateur = statutEvenements.organisateurDe(eventId)
            .orElseThrow(() -> new NoSuchElementException("Événement introuvable : " + eventId));
        if (montantFcfa > 0) {
            if (!comptes.estOrganisateurVerifie(organisateur)) {
                throw new BusinessConflictException(
                    "Organisateur non vérifié (ou suspendu) : aucun virement vers lui n'est permis");
            }
            LocalDate permisLe = reversablesLe(statutEvenements.finDe(eventId).orElseThrow(),
                orderRepository.dernierePayee(eventId));
            if (LocalDate.ofInstant(maintenant, ZoneOffset.UTC).isBefore(permisLe)) {
                throw new BusinessConflictException("Reversement possible à partir du " + permisLe
                    + " (lendemain de l'événement)");
            }
            if (controleEntrees.billetsScannes(eventId) == 0 && (derogationScans == null || derogationScans.isBlank())) {
                throw new BusinessConflictException("Aucun billet scanné à l'entrée : virement refusé, "
                    + "sauf dérogation motivée (entrée sans contrôle)");
            }
        }
        Reversement reversement = Reversement.enregistrer(eventId, montantFcfa, reference, motif, reverseLe,
            administrateurId, maintenant, montantFcfa > 0 ? derogationScans : null)
            .versLeNumero(montantFcfa > 0 ? comptes.numeroReversementVerifie(organisateur).orElse(null) : null);
        Reversement enregistre = reversementRepository.save(reversement);
        // Conservation des pièces de l'organisateur : 5 ans après son dernier VIREMENT (module auth) ;
        // une correction négative ne la prolonge pas.
        if (montantFcfa > 0) {
            publisher.publishEvent(new ReversementEnregistre(organisateur, enregistre.getEnregistreLe()));
        }
        return enregistre;
    }
}

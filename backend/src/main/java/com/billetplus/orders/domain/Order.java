package com.billetplus.orders.domain;

import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — une commande de billets, passée sans
 * compte (ADR 0006).
 *
 * L'acheteur est identifié par son numéro de téléphone, et il accède à sa
 * commande par un jeton secret dont seule l'empreinte est conservée ici. Les
 * références aux autres modules (événement, catégories) sont des UUID bruts.
 *
 * Les dates viennent de l'appelant (l'horloge injectée du cas d'usage), jamais
 * de Instant.now() : le domaine reste déterministe en test. Elles sont tronquées
 * à la microseconde, la précision de TIMESTAMPTZ (sinon une commande relue de la
 * base ne serait plus égale à celle écrite).
 */
public class Order {

    /**
     * Même plafond que tickets.domain.QuantiteParCommande.MAX (20), recopié ici :
     * le domaine d'orders ne peut pas dépendre de celui de tickets (ADR 0001).
     * Les deux valeurs doivent rester égales, sinon une commande acceptée ici
     * serait refusée à l'émission, après paiement.
     */
    public static final int QUANTITE_MAX_PAR_CATEGORIE = 20;
    /** Borne la durée de la transaction d'émission et la taille d'une commande. */
    public static final int LIGNES_MAX = 10;

    private final UUID id;
    private final UUID eventId;
    private final NumeroTelephone telephone;
    private final List<OrderLine> lignes;
    private final String empreinteJeton;
    private final Instant creeLe;
    private final Instant expireLe;
    /** Taux de commission figé à la création (ADR 0009) : un changement de taux ne touche pas les ventes passées. */
    private final TauxCommission tauxCommission;
    private OrderStatus status;
    private Instant payeeLe;
    private MotifSansBillet motifSansBillet;
    private Paiement paiement;

    /**
     * Le résultat d'une création : la commande, et son jeton d'accès EN CLAIR, à
     * remettre une seule fois à l'acheteur (ADR 0006). La commande, elle, ne garde
     * que l'empreinte.
     */
    public record CommandeCreee(Order commande, JetonAcces jeton) {
    }

    /**
     * @param dureeValidite  délai de paiement ; doit être plus court que la
     *                       réservation des places (contrat de orders, HANDOFF section 7)
     * @param tauxCommission taux courant (configuration), recopié dans la commande (ADR 0009) ;
     *                       obligatoire, sans valeur par défaut, pour qu'aucun appelant ne
     *                       contourne la configuration
     * @throws IllegalArgumentException si un invariant de création n'est pas respecté
     */
    public static CommandeCreee creer(UUID eventId, NumeroTelephone telephone, List<OrderLine> lignes,
                                      Instant maintenant, Duration dureeValidite, TauxCommission tauxCommission) {
        if (tauxCommission == null) {
            throw new IllegalArgumentException("Le taux de commission est obligatoire");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId est obligatoire");
        }
        if (telephone == null) {
            throw new IllegalArgumentException("Le numéro de téléphone est obligatoire");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new IllegalArgumentException("Une commande contient au moins une catégorie");
        }
        if (lignes.size() > LIGNES_MAX) {
            throw new IllegalArgumentException("Une commande contient au plus %d catégories".formatted(LIGNES_MAX));
        }
        Set<UUID> categories = new HashSet<>();
        for (OrderLine ligne : lignes) {
            if (ligne == null) {
                throw new IllegalArgumentException("Ligne de commande absente");
            }
            if (!categories.add(ligne.categoryId())) {
                // Une catégorie en double contournerait le plafond par catégorie,
                // et l'émission (une par couple commande, catégorie) la refuserait.
                throw new IllegalArgumentException("Une catégorie apparaît plusieurs fois dans la commande");
            }
        }
        if (maintenant == null) {
            throw new IllegalArgumentException("La date de création est obligatoire");
        }
        if (dureeValidite == null || dureeValidite.isNegative() || dureeValidite.isZero()) {
            throw new IllegalArgumentException("La durée de validité doit être strictement positive");
        }
        Instant creeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
        JetonAcces jeton = JetonAcces.generer();
        Order commande = new Order(UUID.randomUUID(), eventId, telephone, List.copyOf(lignes), jeton.empreinte(),
            creeLe, creeLe.plus(dureeValidite).truncatedTo(ChronoUnit.MICROS), tauxCommission,
            OrderStatus.EN_ATTENTE_PAIEMENT, null, null, null);
        return new CommandeCreee(commande, jeton);
    }

    /**
     * Reconstruction depuis la persistance — ne revalide pas les invariants
     * de création, l'état est supposé déjà cohérent en base.
     */
    /** Reconstruction depuis la persistance, avec le taux de commission enregistré (V16). */
    public static Order reconstituer(UUID id, UUID eventId, NumeroTelephone telephone, List<OrderLine> lignes,
                                     String empreinteJeton, Instant creeLe, Instant expireLe,
                                     TauxCommission tauxCommission, OrderStatus status, Instant payeeLe,
                                     MotifSansBillet motifSansBillet, Paiement paiement) {
        return new Order(id, eventId, telephone, List.copyOf(lignes), empreinteJeton, creeLe, expireLe,
            tauxCommission, status, payeeLe, motifSansBillet, paiement);
    }

    private Order(UUID id, UUID eventId, NumeroTelephone telephone, List<OrderLine> lignes, String empreinteJeton,
                  Instant creeLe, Instant expireLe, TauxCommission tauxCommission, OrderStatus status,
                  Instant payeeLe, MotifSansBillet motifSansBillet, Paiement paiement) {
        this.id = id;
        this.eventId = eventId;
        this.telephone = telephone;
        this.lignes = lignes;
        this.empreinteJeton = empreinteJeton;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
        this.tauxCommission = tauxCommission;
        this.status = status;
        this.payeeLe = payeeLe;
        this.motifSansBillet = motifSansBillet;
        this.paiement = paiement;
    }

    /** Somme des lignes, recalculée à chaque appel : aucun total stocké ne peut diverger des lignes. */
    public Money montantTotal() {
        return lignes.stream().map(OrderLine::sousTotal).reduce(Money.ofFcfa(0), Money::plus);
    }

    /** Commission de Billet+ sur cette commande, au taux figé, arrondie au franc inférieur (ADR 0009). */
    public Money commission() {
        return tauxCommission.commissionSur(montantTotal());
    }

    /** Part de l'organisateur : montant payé moins la commission. */
    public Money montantNet() {
        return montantTotal().moins(commission());
    }

    /** Vrai si le délai de paiement est écoulé : la commande ne peut plus être payée à temps. */
    public boolean delaiDepasse(Instant maintenant) {
        return !maintenant.isBefore(expireLe);
    }

    /**
     * Enregistre le paiement ouvert chez la passerelle (ADR 0007). Une seule fois :
     * deux paiements ouverts pour une commande pourraient tous deux aboutir, et le
     * second serait encaissé sans billet.
     *
     * Il doit rester au moins {@code margeMinimale} avant l'expiration : un paiement
     * ouvert dans les dernières secondes aboutirait presque sûrement après
     * l'expiration des places (payé sans billet). L'appelant vérifie cette marge
     * AVANT d'appeler la passerelle ({@link #verifierPeutOuvrirUnPaiement}), et le
     * domaine la revérifie ici, à l'heure de l'enregistrement.
     *
     * @throws TransitionCommandeInterditeException si la commande n'attend plus de paiement, si un
     *                                              paiement est déjà ouvert, ou si le délai restant est trop court
     */
    public void ouvrirPaiement(Paiement nouveau, Duration margeMinimale) {
        if (nouveau == null) {
            throw new IllegalArgumentException("Le paiement est obligatoire");
        }
        verifierPeutOuvrirUnPaiement(nouveau.ouvertLe(), margeMinimale);
        paiement = nouveau;
    }

    /**
     * Un paiement peut-il être ouvert à {@code maintenant} ? Commande en attente,
     * sans paiement, et au moins {@code margeMinimale} avant l'expiration.
     *
     * @throws TransitionCommandeInterditeException sinon, avec la raison
     */
    public void verifierPeutOuvrirUnPaiement(Instant maintenant, Duration margeMinimale) {
        if (paiement != null) {
            throw new TransitionCommandeInterditeException("Un paiement est déjà ouvert pour cette commande");
        }
        exigerStatut(OrderStatus.EN_ATTENTE_PAIEMENT, "Cette commande n'attend plus de paiement");
        if (delaiDepasse(maintenant.plus(margeMinimale))) {
            throw new TransitionCommandeInterditeException(
                "Le délai de paiement de cette commande est presque écoulé : passez une nouvelle commande");
        }
    }

    /**
     * Paiement confirmé dans le délai : les billets peuvent être émis.
     *
     * Un paiement arrivé après le délai ne passe PAS par ici : l'appelant doit
     * utiliser {@link #marquerPayeeSansBillet} avec {@link MotifSansBillet#RESERVATION_EXPIREE},
     * car les places ne sont plus garanties.
     *
     * @throws TransitionCommandeInterditeException si la commande n'attend plus de paiement,
     *                                              ou si le délai est dépassé
     */
    public void marquerPayee(Instant maintenant) {
        exigerStatut(OrderStatus.EN_ATTENTE_PAIEMENT, "Cette commande n'attend plus de paiement");
        if (delaiDepasse(maintenant)) {
            throw new TransitionCommandeInterditeException("Le délai de paiement de cette commande est dépassé");
        }
        status = OrderStatus.PAYEE;
        payeeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Paiement reçu, mais billets impossibles à émettre : la commande est à
     * rembourser à la main (décision B, ADR 0006). Possible depuis une commande en
     * attente, expirée (paiement tardif) ou annulée (webhooks reçus dans le désordre) :
     * un paiement reçu ne doit jamais être perdu.
     *
     * @throws TransitionCommandeInterditeException depuis tout autre statut
     */
    public void marquerPayeeSansBillet(MotifSansBillet motif, Instant maintenant) {
        if (motif == null) {
            throw new IllegalArgumentException("Le motif est obligatoire");
        }
        if (status != OrderStatus.EN_ATTENTE_PAIEMENT && status != OrderStatus.EXPIREE
            && status != OrderStatus.ANNULEE) {
            throw new TransitionCommandeInterditeException("Cette commande n'attend plus de paiement");
        }
        status = OrderStatus.PAYEE_SANS_BILLET;
        payeeLe = maintenant.truncatedTo(ChronoUnit.MICROS);
        motifSansBillet = motif;
    }

    /**
     * Délai de paiement écoulé sans paiement : les places réservées peuvent être libérées.
     *
     * @throws TransitionCommandeInterditeException si la commande n'est plus en attente,
     *                                              ou si son délai n'est pas encore écoulé
     */
    public void expirer(Instant maintenant) {
        exigerStatut(OrderStatus.EN_ATTENTE_PAIEMENT, "Seule une commande en attente de paiement peut expirer");
        if (!delaiDepasse(maintenant)) {
            throw new TransitionCommandeInterditeException("Le délai de paiement de cette commande n'est pas écoulé");
        }
        status = OrderStatus.EXPIREE;
    }

    /**
     * Abandon avant paiement (par l'acheteur, ou échec définitif confirmé par Wave).
     *
     * @throws TransitionCommandeInterditeException si la commande n'est plus en attente
     */
    public void annuler() {
        exigerStatut(OrderStatus.EN_ATTENTE_PAIEMENT, "Seule une commande en attente de paiement peut être annulée");
        status = OrderStatus.ANNULEE;
    }

    /**
     * Vrai si {@code jeton} est le secret d'accès de cette commande (comparaison
     * en temps constant). Faux pour un jeton absent ou erroné.
     */
    public boolean accepteLeJeton(String jeton) {
        return JetonAcces.correspond(jeton, empreinteJeton);
    }

    private void exigerStatut(OrderStatus attendu, String message) {
        if (status != attendu) {
            throw new TransitionCommandeInterditeException(message);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public NumeroTelephone getTelephone() {
        return telephone;
    }

    /** Non modifiable. */
    public List<OrderLine> getLignes() {
        return lignes;
    }

    public String getEmpreinteJeton() {
        return empreinteJeton;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public TauxCommission getTauxCommission() {
        return tauxCommission;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getPayeeLe() {
        return payeeLe;
    }

    public MotifSansBillet getMotifSansBillet() {
        return motifSansBillet;
    }

    /** Le paiement ouvert chez la passerelle, ou null s'il n'y en a pas encore. */
    public Paiement getPaiement() {
        return paiement;
    }
}

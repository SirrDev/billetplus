package com.billetplus.orders.domain;

import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00.123456789Z");
    private static final Duration DELAI = Duration.ofMinutes(4);
    private static final NumeroTelephone TELEPHONE = NumeroTelephone.depuis("77 123 45 67");

    private static OrderLine ligne(int quantite, long prix) {
        return new OrderLine(UUID.randomUUID(), quantite, Money.ofFcfa(prix));
    }

    private static Order.CommandeCreee creer(List<OrderLine> lignes) {
        return Order.creer(UUID.randomUUID(), TELEPHONE, lignes, MAINTENANT, DELAI, TauxCommission.CINQ_POUR_CENT);
    }

    private static Order enAttente() {
        return creer(List.of(ligne(2, 5000))).commande();
    }

    @Test
    void une_commande_creee_attend_son_paiement_et_calcule_son_total() {
        Order.CommandeCreee creee = creer(List.of(ligne(2, 5000), ligne(3, 10000)));
        Order commande = creee.commande();

        assertThat(commande.getId()).isNotNull();
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
        assertThat(commande.montantTotal()).isEqualTo(Money.ofFcfa(40000));
        assertThat(commande.getTelephone().valeur()).isEqualTo("+221771234567");
        assertThat(commande.getPayeeLe()).isNull();
        assertThat(commande.getMotifSansBillet()).isNull();
        // Tronquée à la microseconde, précision de TIMESTAMPTZ.
        assertThat(commande.getCreeLe()).isEqualTo(Instant.parse("2026-11-01T10:00:00.123456Z"));
        assertThat(commande.getExpireLe()).isEqualTo(commande.getCreeLe().plus(DELAI));
    }

    @Test
    void la_commande_ne_garde_que_l_empreinte_du_jeton_remis_a_l_acheteur() {
        Order.CommandeCreee creee = creer(List.of(ligne(1, 5000)));
        String jeton = creee.jeton().enClair();

        assertThat(creee.commande().getEmpreinteJeton())
            .isEqualTo(JetonAcces.empreinteDe(jeton))
            .isNotEqualTo(jeton);
        assertThat(creee.commande().accepteLeJeton(jeton)).isTrue();
        assertThat(creee.commande().accepteLeJeton(jeton + "x")).isFalse();
        assertThat(creee.commande().accepteLeJeton(null)).isFalse();
    }

    @Test
    void deux_commandes_ont_des_jetons_differents() {
        assertThat(creer(List.of(ligne(1, 5000))).jeton().enClair())
            .isNotEqualTo(creer(List.of(ligne(1, 5000))).jeton().enClair());
    }

    @Test
    void la_liste_des_lignes_est_copiee_et_non_modifiable() {
        List<OrderLine> lignes = new ArrayList<>(List.of(ligne(1, 5000)));
        Order commande = creer(lignes).commande();
        lignes.add(ligne(1, 5000));

        assertThat(commande.getLignes()).hasSize(1);
        assertThatThrownBy(() -> commande.getLignes().add(ligne(1, 1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void la_creation_refuse_une_commande_vide_trop_grande_ou_avec_une_categorie_en_double() {
        assertThatThrownBy(() -> creer(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> creer(null)).isInstanceOf(IllegalArgumentException.class);

        List<OrderLine> tropDeLignes = IntStream.range(0, Order.LIGNES_MAX + 1).mapToObj(i -> ligne(1, 5000)).toList();
        assertThatThrownBy(() -> creer(tropDeLignes)).isInstanceOf(IllegalArgumentException.class);
        List<OrderLine> auMaximum = IntStream.range(0, Order.LIGNES_MAX).mapToObj(i -> ligne(1, 5000)).toList();
        assertThat(creer(auMaximum).commande().getLignes()).hasSize(Order.LIGNES_MAX);

        UUID categorie = UUID.randomUUID();
        List<OrderLine> doublon = List.of(new OrderLine(categorie, 1, Money.ofFcfa(5000)),
            new OrderLine(categorie, 1, Money.ofFcfa(5000)));
        assertThatThrownBy(() -> creer(doublon))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("plusieurs fois");
    }

    @Test
    void la_creation_exige_evenement_telephone_date_et_delai_positif() {
        List<OrderLine> lignes = List.of(ligne(1, 5000));
        assertThatThrownBy(() -> Order.creer(null, TELEPHONE, lignes, MAINTENANT, DELAI, TauxCommission.CINQ_POUR_CENT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.creer(UUID.randomUUID(), null, lignes, MAINTENANT, DELAI, TauxCommission.CINQ_POUR_CENT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.creer(UUID.randomUUID(), TELEPHONE, lignes, null, DELAI, TauxCommission.CINQ_POUR_CENT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.creer(UUID.randomUUID(), TELEPHONE, lignes, MAINTENANT, Duration.ZERO, TauxCommission.CINQ_POUR_CENT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.creer(UUID.randomUUID(), TELEPHONE, lignes, MAINTENANT, Duration.ofSeconds(-1), TauxCommission.CINQ_POUR_CENT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_ligne_refuse_une_quantite_hors_bornes_et_un_prix_absent() {
        assertThatThrownBy(() -> ligne(0, 5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ligne(Order.QUANTITE_MAX_PAR_CATEGORIE + 1, 5000))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(ligne(Order.QUANTITE_MAX_PAR_CATEGORIE, 5000).sousTotal()).isEqualTo(Money.ofFcfa(100000));
        assertThatThrownBy(() -> new OrderLine(UUID.randomUUID(), 1, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(null, 1, Money.ofFcfa(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_paiement_dans_le_delai_rend_la_commande_payee() {
        Order commande = enAttente();
        Instant paiement = MAINTENANT.plus(Duration.ofMinutes(3));

        commande.marquerPayee(paiement);

        assertThat(commande.getStatus()).isEqualTo(OrderStatus.PAYEE);
        assertThat(commande.getPayeeLe()).isEqualTo(paiement.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void un_paiement_apres_le_delai_est_refuse_et_la_commande_reste_en_attente() {
        Order commande = enAttente();

        assertThatThrownBy(() -> commande.marquerPayee(commande.getExpireLe()))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }

    @Test
    void une_commande_ne_peut_etre_payee_deux_fois() {
        Order commande = enAttente();
        commande.marquerPayee(MAINTENANT);

        assertThatThrownBy(() -> commande.marquerPayee(MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThatThrownBy(() -> commande.marquerPayeeSansBillet(MotifSansBillet.VENTE_FERMEE, MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
    }

    @Test
    void un_paiement_recu_sans_billet_possible_est_conserve_avec_son_motif() {
        Order enAttente = enAttente();
        enAttente.marquerPayeeSansBillet(MotifSansBillet.VENTE_FERMEE, MAINTENANT);
        assertThat(enAttente.getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThat(enAttente.getMotifSansBillet()).isEqualTo(MotifSansBillet.VENTE_FERMEE);
        assertThat(enAttente.getPayeeLe()).isNotNull();

        // Paiement tardif sur une commande déjà expirée : il ne doit pas être perdu.
        Order expiree = enAttente();
        expiree.expirer(expiree.getExpireLe());
        expiree.marquerPayeeSansBillet(MotifSansBillet.RESERVATION_EXPIREE, expiree.getExpireLe().plusSeconds(60));
        assertThat(expiree.getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThat(expiree.getMotifSansBillet()).isEqualTo(MotifSansBillet.RESERVATION_EXPIREE);

        assertThatThrownBy(() -> enAttente().marquerPayeeSansBillet(null, MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_commande_annulee_ne_peut_plus_etre_payee_mais_un_paiement_tardif_y_est_conserve() {
        Order commande = enAttente();
        commande.annuler();

        assertThat(commande.getStatus()).isEqualTo(OrderStatus.ANNULEE);
        assertThatThrownBy(() -> commande.marquerPayee(MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThatThrownBy(commande::annuler).isInstanceOf(TransitionCommandeInterditeException.class);

        // Webhook « payé » arrivé après « expiré » : l'argent reçu doit rester visible.
        commande.marquerPayeeSansBillet(MotifSansBillet.PAIEMENT_APRES_ANNULATION, MAINTENANT);
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThatThrownBy(() -> commande.marquerPayeeSansBillet(MotifSansBillet.VENTE_FERMEE, MAINTENANT))
            .isInstanceOf(TransitionCommandeInterditeException.class);
    }

    @Test
    void l_expiration_exige_un_delai_ecoule_et_une_commande_en_attente() {
        Order commande = enAttente();
        Instant justeAvant = commande.getExpireLe().minusNanos(1000);

        assertThat(commande.delaiDepasse(justeAvant)).isFalse();
        assertThatThrownBy(() -> commande.expirer(justeAvant))
            .isInstanceOf(TransitionCommandeInterditeException.class);

        commande.expirer(commande.getExpireLe());
        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EXPIREE);
        assertThatThrownBy(() -> commande.expirer(commande.getExpireLe()))
            .isInstanceOf(TransitionCommandeInterditeException.class);

        Order payee = enAttente();
        payee.marquerPayee(MAINTENANT);
        assertThatThrownBy(() -> payee.expirer(payee.getExpireLe().plusSeconds(1)))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThatThrownBy(payee::annuler).isInstanceOf(TransitionCommandeInterditeException.class);
    }

    private static final Duration MARGE = Duration.ofSeconds(60);

    private static Paiement paiement(MoyenPaiement moyen, Instant ouvertLe) {
        return new Paiement(moyen, "wave_" + UUID.randomUUID(), 18, "https://pay.example/p/1", ouvertLe);
    }

    @Test
    void un_paiement_s_ouvre_une_seule_fois_sur_une_commande_en_attente() {
        Order commande = enAttente();
        Paiement premier = paiement(MoyenPaiement.WAVE, MAINTENANT.plusSeconds(10));

        commande.ouvrirPaiement(premier, MARGE);
        assertThat(commande.getPaiement()).isEqualTo(premier);

        assertThatThrownBy(() -> commande.ouvrirPaiement(paiement(MoyenPaiement.ORANGE_MONEY, MAINTENANT), MARGE))
            .isInstanceOf(TransitionCommandeInterditeException.class)
            .hasMessage("Un paiement est déjà ouvert pour cette commande");
        assertThat(commande.getPaiement()).isEqualTo(premier);
    }

    @Test
    void aucun_paiement_ne_s_ouvre_a_moins_de_la_marge_avant_l_expiration() {
        Order commande = enAttente();
        Instant justeAvantLaMarge = commande.getExpireLe().minus(MARGE).minusMillis(1);
        Instant dansLaMarge = commande.getExpireLe().minus(MARGE);

        commande.verifierPeutOuvrirUnPaiement(justeAvantLaMarge, MARGE);
        assertThatThrownBy(() -> commande.verifierPeutOuvrirUnPaiement(dansLaMarge, MARGE))
            .isInstanceOf(TransitionCommandeInterditeException.class)
            .hasMessageContaining("presque écoulé");
        assertThatThrownBy(() -> commande.ouvrirPaiement(paiement(MoyenPaiement.WAVE, dansLaMarge), MARGE))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        assertThat(commande.getPaiement()).isNull();
    }

    @Test
    void une_commande_qui_n_attend_plus_de_paiement_n_en_ouvre_pas() {
        Order annulee = enAttente();
        annulee.annuler();
        assertThatThrownBy(() -> annulee.ouvrirPaiement(paiement(MoyenPaiement.WAVE, MAINTENANT), MARGE))
            .isInstanceOf(TransitionCommandeInterditeException.class)
            .hasMessage("Cette commande n'attend plus de paiement");
    }

    @Test
    void un_paiement_exige_une_reference_un_lien_https_et_une_date() {
        assertThatThrownBy(() -> new Paiement(MoyenPaiement.WAVE, " ", 1, "https://p", MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paiement(MoyenPaiement.WAVE, "r".repeat(101), 1, "https://p", MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paiement(MoyenPaiement.WAVE, "ref", 1, "http://p", MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paiement(null, "ref", 1, "https://p", MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paiement(MoyenPaiement.WAVE, "ref", 1, "https://p", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituer_restaure_l_etat_sans_revalider() {
        Order origine = enAttente();
        origine.ouvrirPaiement(paiement(MoyenPaiement.WAVE, MAINTENANT), MARGE);
        origine.marquerPayeeSansBillet(MotifSansBillet.PLACES_INDISPONIBLES, MAINTENANT);

        Order copie = Order.reconstituer(origine.getId(), origine.getEventId(), origine.getTelephone(),
            origine.getLignes(), origine.getEmpreinteJeton(), origine.getCreeLe(), origine.getExpireLe(), TauxCommission.CINQ_POUR_CENT,
            origine.getStatus(), origine.getPayeeLe(), origine.getMotifSansBillet(), origine.getPaiement());

        assertThat(copie).usingRecursiveComparison().isEqualTo(origine);
    }
}

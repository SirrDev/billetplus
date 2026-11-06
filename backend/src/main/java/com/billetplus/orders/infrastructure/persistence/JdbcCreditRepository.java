package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.domain.MouvementCredit;
import com.billetplus.shared.Money;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur du port CreditRepository (ADR 0010), en SQL direct : le débit doit être UNE
 * requête conditionnelle (solde ≥ montant), que JPA ne sait pas exprimer sans lire puis
 * écrire (deux commandes simultanées dépenseraient alors le même franc). Aucune entité
 * JPA pour ces tables : {@code ddl-auto: validate} ne les contrôle donc pas, les tests
 * d'intégration le font.
 */
@Component
class JdbcCreditRepository implements CreditRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCreditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean debiterEntreeLibre(UUID organisateurId, Money montant, UUID orderId, Instant maintenant) {
        long fcfa = positif(montant);
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("organisateur", organisateurId)
            .addValue("montant", fcfa)
            .addValue("maintenant", Timestamp.from(maintenant));
        int modifiees = jdbc.update("UPDATE credits_organisateurs SET solde_fcfa = solde_fcfa - :montant, "
            + "mis_a_jour_le = :maintenant WHERE organisateur_id = :organisateur AND solde_fcfa >= :montant", p);
        if (modifiees == 0) {
            return false;
        }
        journaliser(organisateurId, -fcfa, MouvementCredit.Type.ENTREE_LIBRE, null, orderId, maintenant);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void crediterRecharge(UUID organisateurId, Money montant, UUID rechargeId, Instant maintenant) {
        long fcfa = positif(montant);
        // Journal d'abord : l'index unique uq_mouvements_recharge refuse un second crédit
        // de la même recharge avant que le solde ne soit touché.
        journaliser(organisateurId, fcfa, MouvementCredit.Type.RECHARGE, rechargeId, null, maintenant);
        jdbc.update("INSERT INTO credits_organisateurs (organisateur_id, solde_fcfa, mis_a_jour_le) "
                + "VALUES (:organisateur, :montant, :maintenant) ON CONFLICT (organisateur_id) DO UPDATE "
                + "SET solde_fcfa = credits_organisateurs.solde_fcfa + EXCLUDED.solde_fcfa, "
                + "mis_a_jour_le = EXCLUDED.mis_a_jour_le",
            new MapSqlParameterSource()
                .addValue("organisateur", organisateurId)
                .addValue("montant", fcfa)
                .addValue("maintenant", Timestamp.from(maintenant)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void verrouillerEntreesLibres(String telephone, UUID eventId) {
        // Verrou consultatif de transaction : libéré au commit ou à l'annulation. Clé 64 bits
        // dérivée du couple (collision possible, sans autre effet qu'une attente inutile).
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(:cle, 0))",
            new MapSqlParameterSource("cle", "entree-libre|" + telephone + "|" + eventId),
            (PreparedStatementCallback<Boolean>) PreparedStatement::execute);
    }

    @Override
    public Money solde(UUID organisateurId) {
        List<Long> soldes = jdbc.queryForList(
            "SELECT solde_fcfa FROM credits_organisateurs WHERE organisateur_id = :organisateur",
            new MapSqlParameterSource("organisateur", organisateurId), Long.class);
        return Money.ofFcfa(soldes.isEmpty() ? 0 : soldes.get(0));
    }

    @Override
    public List<MouvementCredit> derniersMouvements(UUID organisateurId, int limite) {
        return jdbc.query("SELECT id, organisateur_id, montant_fcfa, type, recharge_id, order_id, cree_le "
                + "FROM mouvements_credit WHERE organisateur_id = :organisateur "
                + "ORDER BY cree_le DESC, id LIMIT :limite",
            new MapSqlParameterSource().addValue("organisateur", organisateurId).addValue("limite", limite),
            (rs, i) -> new MouvementCredit(
                rs.getObject("id", UUID.class),
                rs.getObject("organisateur_id", UUID.class),
                rs.getLong("montant_fcfa"),
                MouvementCredit.Type.valueOf(rs.getString("type")),
                rs.getObject("recharge_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getTimestamp("cree_le").toInstant()));
    }

    private void journaliser(UUID organisateurId, long montantFcfa, MouvementCredit.Type type, UUID rechargeId,
                             UUID orderId, Instant maintenant) {
        jdbc.update("INSERT INTO mouvements_credit (id, organisateur_id, montant_fcfa, type, recharge_id, order_id, "
                + "cree_le) VALUES (:id, :organisateur, :montant, :type, :recharge, :commande, :maintenant)",
            new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("organisateur", organisateurId)
                .addValue("montant", montantFcfa)
                .addValue("type", type.name())
                .addValue("recharge", rechargeId)
                .addValue("commande", orderId)
                .addValue("maintenant", Timestamp.from(maintenant)));
    }

    private static long positif(Money montant) {
        long fcfa = montant.valeur().longValueExact();
        if (fcfa <= 0) {
            throw new IllegalArgumentException("Le montant d'un mouvement de crédit doit être positif");
        }
        return fcfa;
    }
}

package com.billetplus.shared.evenements;

import java.util.UUID;

/**
 * Événement applicatif : un organisateur vient de retirer un de ses agents. Publié par le
 * module auth, écouté par le module events, qui supprime ses affectations (ADR 0005).
 *
 * Simple donnée, sans logique (discipline de shared, ADR 0001). Les écouteurs s'exécutent
 * dans la transaction du retrait : s'ils échouent, le retrait est annulé avec eux.
 */
public record AgentRetire(UUID agentId) {
}

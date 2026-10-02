package com.billetplus.auth.domain;

/**
 * Rôles RBAC simples — voir cahier des charges, section 5.7.
 * Volontairement plat (3 rôles) au MVP ; le RBAC fin par permission
 * est classé WON'T (V1) dans la priorisation MoSCoW.
 */
public enum Role {
    ORGANISATEUR,
    AGENT,
    ADMIN
}

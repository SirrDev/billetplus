package com.billetplus.auth.domain;

/** Pièces demandées à un organisateur (ADR 0012). */
public enum TypePiece {
    /** CNI ou passeport du responsable : toujours obligatoire. */
    IDENTITE,
    /** Capture du compte Wave ou Orange Money montrant le titulaire : toujours obligatoire. */
    JUSTIFICATIF_REVERSEMENT,
    /** NINEA ou registre du commerce : obligatoire pour une structure. */
    NINEA_RCCM
}

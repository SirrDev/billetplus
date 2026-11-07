package com.billetplus.auth.api.dto;

import jakarta.validation.constraints.Size;

/** Motif d'un refus ou d'une suspension (ADR 0012) ; obligatoire pour eux, vérifié par le domaine. */
public record DecisionRequest(@Size(max = 500) String motif) {
}

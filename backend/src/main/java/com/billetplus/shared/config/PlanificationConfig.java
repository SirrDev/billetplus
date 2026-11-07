package com.billetplus.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (@Scheduled), comme la purge des pièces des organisateurs (ADR 0012). */
@Configuration
@EnableScheduling
class PlanificationConfig {
}

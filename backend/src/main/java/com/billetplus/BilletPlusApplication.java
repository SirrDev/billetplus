package com.billetplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du backend Billet+.
 *
 * Monolithe modulaire : un seul artefact déployé, modules isolés par package
 * ({@code com.billetplus.<module>}). Voir docs/architecture/adr/0001-monolithe-modulaire.md.
 */
@SpringBootApplication
public class BilletPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilletPlusApplication.class, args);
    }
}

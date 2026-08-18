package com.billetplus.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Règles d'architecture vérifiées automatiquement en CI (.github/workflows/architecture-check.yml).
 *
 * Encode les décisions actées dans :
 * - docs/architecture/adr/0001-monolithe-modulaire.md (isolation des modules)
 * - docs/architecture/adr/0002-hexagonale-par-module.md (isolation du domaine)
 *
 * Ces tests sont volontairement stricts : une violation doit casser la CI,
 * pas être laissée à la discipline individuelle en revue de code.
 */
@AnalyzeClasses(packages = "com.billetplus", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Le domaine ne doit jamais dépendre de l'infrastructure (JPA, HTTP, clients externes).
     * S'applique en priorité aux modules de niveau 3 (tickets, orders) — voir ADR 0002.
     */
    @ArchTest
    static final ArchRule domaine_ne_depend_jamais_de_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    /**
     * Le domaine ne doit jamais dépendre de la couche api (controllers, DTOs).
     */
    @ArchTest
    static final ArchRule domaine_ne_depend_jamais_de_api =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    /**
     * Aucun module ne doit dépendre directement d'un autre module.
     * La communication inter-module passe par UUID ou par événement applicatif
     * (ApplicationEventPublisher), jamais par accès direct aux classes internes
     * d'un autre module.
     *
     * Le package "shared" est exclu : c'est le seul point de partage légitime,
     * à condition qu'il ne contienne aucune logique métier (voir ADR 0001).
     */
    @ArchTest
    static final ArchRule modules_sont_independants =
        slices().matching("com.billetplus.(*)..")
            .ignoreDependency(
                dependency -> true,
                dependency -> dependency.getTargetClass().getPackageName().contains(".shared")
            )
            .should().notDependOnEachOther();

    /**
     * Les ports (interfaces définies par la couche application) ne doivent jamais
     * exposer de type Spring Data — sinon la fuite d'infrastructure remonte
     * jusqu'à l'application, pas seulement au domaine.
     */
    @ArchTest
    static final ArchRule ports_ne_dependent_pas_de_spring_data =
        noClasses().that().resideInAPackage("..application.port..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data..");
}

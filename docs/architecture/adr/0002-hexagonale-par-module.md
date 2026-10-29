# 0002 — Architecture hexagonale appliquée par module, avec rigueur variable

## Statut

Acceptée — 17 août 2026

## Contexte

Le backend Billet+ est structuré en monolithe modulaire Spring Boot (voir [ADR 0001](0001-monolithe-modulaire.md)). Cette décision règle la question du **déploiement et du macro-découpage** (un seul artefact, des frontières de package strictes entre modules), mais laisse ouverte une question distincte : comment structurer le code **à l'intérieur** de chaque module, en particulier la façon dont la logique métier se protège (ou non) des détails d'infrastructure (JPA, HTTP, client de paiement Wave).

Le domaine billetterie combine plusieurs sous-domaines aux profils très différents :

- Des invariants métier critiques et sensibles à la concurrence (quota de billets, anti-overselling, transitions d'état d'une commande payée/remboursée), où un bug se traduit directement par une perte d'argent ou un litige avec un organisateur.
- Des besoins essentiellement CRUD (gestion d'événements, de lieux, de comptes utilisateurs), où la logique métier est minimale et où la vitesse de livraison prime.

Appliquer un seul niveau de rigueur architecturale à l'ensemble du backend pose un problème dans les deux sens :

- Un modèle de couches classiques partout (`Service` dépendant directement de `JpaRepository`) rend le module `tickets` difficile à tester en isolation : la logique de quota ne peut être vérifiée sans démarrer un contexte Spring et une base de données, ce qui ralentit le développement et réduit la couverture de tests sur le code le plus sensible du projet.
- Une architecture hexagonale stricte partout (domaine 100 % pur, entité JPA séparée du modèle métier, mapper systématique) double le volume de code sur des modules comme `events` ou `venues`, sans bénéfice proportionné, ce qui compromet le délai de livraison du MVP (3 mois).

Il faut donc trancher : quel niveau d'architecture appliquer, et selon quel critère faire varier ce niveau d'un module à l'autre.

## Décision

**L'architecture hexagonale (Ports & Adapters) est appliquée à l'intérieur de chaque module du monolithe**, avec un niveau de rigueur qui varie explicitement selon le risque métier du module, plutôt qu'un standard unique imposé partout.

### Principe : deux axes orthogonaux

Le monolithe modulaire (ADR 0001) répond à *où s'arrêtent les frontières de déploiement et de code*. L'architecture hexagonale répond à une question différente, interne à chaque module : *comment le domaine se protège de l'infrastructure*. Les deux axes se combinent sans se contredire — chaque module applique l'hexagonale en son sein, indépendamment du fait que l'ensemble soit déployé comme un seul artefact.

### Structure type d'un module hexagonal

```
com.billetplus.<module>/
├── domain/            # entités, value objects, exceptions métier
├── application/
│   ├── port/           # interfaces définies par le module (repository, gateway, signer)
│   └── usecase/         # un fichier par cas d'usage, pas un service central unique
├── infrastructure/    # implémentations concrètes (JPA, HTTP, HMAC/Ed25519, Wave)
└── api/                # controllers REST, DTOs d'entrée/sortie
```

La règle de dépendance est stricte et à sens unique : `api` dépend de `application`, `application` dépend de `domain`, `infrastructure` implémente les interfaces (`port`) définies par `application`. Le domaine ne dépend jamais de l'infrastructure.

### Trois niveaux de rigueur, choisis par module

| Niveau | Le use case dépend de | Domaine porteur d'annotations JPA ? | Testable sans base de données ? | Coût d'implémentation |
|---|---|---|---|---|
| 1 — Couches strictes | `JpaRepository` directement | Oui (l'entité JPA est le modèle) | Non | Très faible |
| 2 — Port + JPA-as-domain | Interface définie par le module | Oui | Oui (mock de l'interface) | Faible |
| 3 — Domaine séparé | Interface + domaine POJO distinct de l'entité JPA | Non | Oui, totalement | Élevé |

### Répartition retenue

| Module | Niveau (écriture) | Niveau (lecture) | Justification |
|---|---|---|---|
| `tickets` | 3 | 1 | Quota, anti-overselling, forte concurrence à l'achat |
| `orders` / `payments` | 3 | 1 | Transitions d'état financières (paiement, remboursement) |
| `events`, `venues` | 2 | 1 | CRUD avec validation simple, pas d'invariant complexe |
| `auth`, `users` | 2 | 1 | Spring Security impose déjà ses propres contraintes techniques |
| `notifications`, `sponsors` (V2/V3) | 1 | 1 | Risque métier faible, priorité à la vitesse de livraison |

**Règle de décision pour tout nouveau module** : *ce module a-t-il un invariant métier qui peut coûter de l'argent ou créer une incohérence irréversible s'il est violé (double vente, remboursement en double) ?*
- Oui → niveau 3 en écriture.
- Non, mais la logique métier va évoluer et mérite d'être isolée pour être testée → niveau 2.
- Non, CRUD ou lecture pure → niveau 1.

Les lectures (dashboards, exports, projections) restent en niveau 1 même dans les modules critiques : ce sont des requêtes de restitution, pas de la logique métier à protéger — leur imposer des ports ajouterait de la complexité sans bénéfice.

### Organisation des cas d'usage

Dans les modules de niveau 3 en particulier, chaque cas d'usage est une classe dédiée (`IssueTicketUseCase`, `ValidateTicketScanUseCase`, `CancelTicketUseCase`, `TransferTicketUseCase`) plutôt que regroupé dans un `TicketService` unique. Cela évite qu'un service central devienne un point de contention en revue de code à mesure que les cas d'usage se multiplient — un bénéfice proche de ce qu'apporterait une architecture Vertical Slice, sans introduire un second paradigme architectural dans le projet.

### Garde-fou : préserver l'isolation malgré la base de données partagée

Tous les modules du monolithe partagent la même instance PostgreSQL. Rien n'empêche techniquement une relation JPA cross-module (`@ManyToOne` d'un module vers l'entité interne d'un autre), ce qui effacerait les frontières que l'architecture hexagonale est censée garantir. Deux règles, vérifiées automatiquement plutôt que laissées à la discipline individuelle :

1. Un module ne référence un autre module que par identifiant (`UUID`), jamais par relation JPA traversant un package d'un autre module.
2. La communication inter-module passe par un événement applicatif interne (`ApplicationEventPublisher`) ou l'appel direct à un port public du module, jamais par accès aux classes internes d'un autre module.

Ces règles sont encodées en tests **ArchUnit**, exécutés dans un job CI dédié (`architecture-check.yml`) qui casse la pull request en cas de violation :

```java
@AnalyzeClasses(packages = "com.billetplus")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domaineIsole =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule modulesIndependants =
        slices().matching("com.billetplus.(*)..")
            .should().notDependOnEachOther();
}
```

## Alternatives considérées

### Couches classiques partout (niveau 1 uniquement)

Rejetée comme standard unique. Rapide à écrire, mais rend `tickets` et `orders` difficiles à tester en isolation — précisément les modules où une régression non détectée a le coût le plus élevé.

### Architecture hexagonale stricte partout (niveau 3 uniquement)

Rejetée comme standard unique. Double le boilerplate sur les modules à faible risque (`events`, `venues`), ce qui n'est pas justifié dans le délai du MVP (3 mois) et retarde le retour d'expérience du premier événement pilote.

### Clean Architecture / Onion Architecture

Écartées, non pour des raisons de fond — ce sont des variantes très proches de l'hexagonale, avec les mêmes principes de dépendance vers le centre — mais parce qu'elles introduiraient un vocabulaire différent (cercles concentriques, DTOs de frontière systématiques) sans apporter de bénéfice supplémentaire par rapport à l'hexagonale déjà pratiquée par l'équipe.

### Vertical Slice Architecture généralisée

Écartée comme standard global. Pertinente en particulier pour organiser les cas d'usage du module `tickets` (voir « Organisation des cas d'usage » ci-dessus), mais l'imposer comme paradigme global introduirait un second modèle mental à maintenir en plus de l'hexagonale déjà connue de l'équipe, pour un bénéfice marginal sur une petite équipe.

### Critère de performance

Écarté comme critère de décision. Le mapping objet propre au niveau 3 (domaine ↔ entité JPA) représente une fraction négligeable (de l'ordre de la microseconde) du temps d'une requête dominée par la latence réseau et base de données (de l'ordre de la milliseconde à la centaine de millisecondes pour un appel Wave). La décision repose uniquement sur la testabilité et la maintenabilité de la logique métier dans le temps.

## Conséquences

**Positives**

- La logique métier la plus sensible (`tickets`, `orders`/`payments`) est testable unitairement en quelques millisecondes, sans base de données ni contexte Spring, ce qui encourage une couverture de tests réelle sur le code où elle compte le plus.
- Le délai de livraison du MVP n'est pas pénalisé par une rigueur excessive sur les modules à faible risque.
- Un module de niveau 3 peut être extrait en service séparé plus tard (si un besoin de scalabilité indépendante apparaît) avec un effort limité : le port reste identique, seul l'adaptateur change.
- Les frontières entre modules restent réelles et vérifiées en continu (ArchUnit), pas seulement déclarées dans la documentation.

**Négatives**

- Deux niveaux de rigueur coexistent dans le même projet, ce qui demande une documentation à jour (cette ADR) pour qu'un nouveau développeur sache quel niveau appliquer à un nouveau module, plutôt qu'une règle unique et immédiatement visible dans tout le code.
- Le passage d'un module du niveau 2 au niveau 3 (si sa complexité métier augmente en V2/V3) demande une refonte : séparation du domaine et de l'entité JPA, introduction d'un mapper. Ce coût est accepté comme un compromis délibéré plutôt qu'une architecture strict dès le départ sur tous les modules.
- Les modules de niveau 3 ont un coût d'entrée plus élevé pour un développeur qui découvre le projet (deux modèles à comprendre : domaine et entité JPA), à documenter dans `CONTRIBUTING.md`.

## Références

- [ADR 0001 — Monolithe modulaire](0001-monolithe-modulaire.md)
- [ADR 0003 — PostgreSQL vs NoSQL](0003-postgresql-vs-nosql.md)
- Cockburn, A. — *Hexagonal Architecture* (Ports & Adapters)
- Vernon, V. — *Implementing Domain-Driven Design* (modèle riche réservé aux sous-domaines à forte valeur métier)
- [ArchUnit](https://www.archunit.org/) — vérification automatisée des règles d'architecture

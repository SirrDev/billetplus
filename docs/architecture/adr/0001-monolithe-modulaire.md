# 0001 — Monolithe modulaire plutôt que microservices

## Statut

Acceptée — 17 août 2026

## Contexte

Billet+ doit livrer un MVP réel en 3 mois (voir cahier des charges, section priorisation MoSCoW), avec une équipe restreinte (1 à 2-3 développeurs), sur un premier événement pilote à Dakar. Le domaine se découpe naturellement en sous-domaines métier assez distincts : authentification, gestion d'événements et de lieux, émission et scan de billets, commandes et paiement.

Ce découpage métier pose la question classique du macro-découpage applicatif : faut-il déployer ces sous-domaines comme des **services indépendants (microservices)**, ou comme des **modules à l'intérieur d'un seul artefact déployé (monolithe modulaire)** ?

Les arguments habituellement avancés en faveur des microservices — scalabilité indépendante par domaine, déploiement isolé, équipes autonomes — supposent un contexte que Billet+ n'a pas encore :

- Le volume de données et de trafic attendu au MVP reste modeste (environ 20 événements/mois, 10 000 billets/mois la première année), très loin du seuil où la scalabilité indépendante d'un module devient nécessaire.
- L'équipe est trop petite pour bénéficier de l'autonomie d'équipes séparées par service — c'est souvent la même ou les mêmes personnes qui interviennent sur plusieurs sous-domaines.
- Les microservices ajoutent un coût opérationnel réel dès le premier jour : observabilité distribuée, gestion de la cohérence entre services (transactions distribuées ou cohérence éventuelle), latence réseau inter-services, complexité de déploiement (orchestration, service discovery).

À l'inverse, un monolithe sans discipline de découpage interne dégénère fréquemment en code fortement couplé (« monolithe spaghetti »), où les frontières entre sous-domaines s'effacent avec le temps faute de contrainte structurelle.

## Décision

**Le backend Billet+ est développé comme un monolithe modulaire Spring Boot : un seul artefact déployé, mais avec des frontières de code strictes entre modules métier**, organisées par package Java (`com.billetplus.<module>`), une base de données PostgreSQL partagée, et une discipline d'isolation vérifiée automatiquement plutôt que laissée à la convention.

### Ce que « modulaire » signifie concrètement

- Un module = un package Java autonome (`events`, `tickets`, `orders`, `auth`...), avec sa propre structure interne en couches (voir [ADR 0002](0002-hexagonale-par-module.md) pour le détail de cette structure interne).
- Un module ne référence un autre module que par identifiant (`UUID`), jamais par relation JPA (`@ManyToOne`/`@OneToMany`) traversant un package d'un autre module.
- La communication inter-module passe par un événement applicatif interne (`ApplicationEventPublisher`) ou l'appel direct à un port public exposé par le module, jamais par accès aux classes internes d'un autre module.
- Ces règles sont vérifiées par des tests **ArchUnit**, exécutés en CI, qui font échouer une pull request en cas de violation plutôt que de dépendre de la vigilance humaine en revue de code.

### Réversibilité recherchée

Le choix du monolithe modulaire n'est pas présenté comme définitif. Si un module donné devait un jour justifier une scalabilité ou un cycle de déploiement indépendant (par exemple `tickets` lors d'un pic de charge massif sur un événement à très forte demande), l'extraction en service séparé reste possible avec un effort limité, à condition que la discipline de frontières décrite ci-dessus ait été respectée : le port applicatif du module reste identique, seul l'adaptateur change (appel HTTP au lieu d'un appel Java direct), et le domaine métier ne bouge pas.

## Alternatives considérées

### Microservices dès le MVP

Rejetée pour ce stade du projet. Le coût opérationnel (observabilité distribuée, gestion de la cohérence entre services, complexité de déploiement) n'est justifié par aucun besoin réel de scalabilité indépendante à la volumétrie attendue en année 1 (voir [ADR 0003](0003-postgresql-vs-nosql.md), section volumétrie). Ce choix serait revisité si la traction post-pilote démontrait un besoin clair de scaling différencié par sous-domaine.

### Monolithe non modulaire (couches techniques globales)

Rejetée. Un découpage par couche technique globale (`com.billetplus.controllers.*`, `com.billetplus.services.*`, `com.billetplus.repositories.*` pour l'ensemble de l'application) rendrait les frontières entre sous-domaines invisibles dans le code, même si le projet reste un seul artefact déployé. Cette organisation rend impossible, à terme, de raisonner sur les dépendances réelles entre `tickets` et `orders`, et empêcherait toute extraction future en service séparé sans réécriture complète.

## Conséquences

**Positives**

- Un seul déploiement à opérer, une seule base de données à administrer, aucune latence réseau entre modules — cohérent avec une équipe restreinte et un délai de 3 mois.
- Les frontières entre sous-domaines existent réellement dans le code (packages, ports) et sont vérifiées automatiquement, pas seulement documentées.
- La réversibilité vers un découpage en services reste ouverte si la traction du produit le justifie, sans dette d'architecture cachée.

**Négatives**

- Toute l'application partage le même cycle de déploiement : une modification sur `events` redéploie l'ensemble du backend, y compris `tickets` et `orders`. Acceptable à la volumétrie et à la fréquence de déploiement attendues au MVP.
- La base de données partagée reste un point de vigilance permanent (voir garde-fou ArchUnit) : sans discipline active, elle peut redevenir un vecteur de couplage caché entre modules malgré l'isolation applicative.
- Un scaling différencié par module (par exemple `tickets` seul sous forte charge) n'est pas possible sans extraction préalable en service séparé — non nécessaire au MVP, mais à anticiper si la traction le justifie.

## Références

- [ADR 0002 — Architecture hexagonale appliquée par module](0002-hexagonale-par-module.md)
- [ADR 0003 — PostgreSQL vs NoSQL](0003-postgresql-vs-nosql.md)
- [ArchUnit](https://www.archunit.org/) — vérification automatisée des règles d'architecture
- [Spring Modulith](https://spring.io/projects/spring-modulith) — alternative outillée pour déclarer et vérifier les frontières de modules dans une application Spring Boot

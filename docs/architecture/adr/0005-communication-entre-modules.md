# 0005 — Communication entre modules : port public pour lire, événement applicatif pour réagir

## Statut

Acceptée — 29 octobre 2026

## Contexte

L'[ADR 0001](0001-monolithe-modulaire.md) pose que « la communication inter-module passe par un événement applicatif interne (`ApplicationEventPublisher`) ou l'appel direct à un port public exposé par le module, jamais par accès aux classes internes d'un autre module ». La règle ArchUnit `modules_sont_independants`, elle, interdit **toute** dépendance d'un module vers un autre, `shared` excepté. Le texte de l'ADR et le test qui doit le vérifier ne disent donc pas la même chose.

Tant que les modules étaient indépendants, ce désaccord n'avait pas de conséquence. Il en a désormais trois, toutes dans le module `tickets`, qui ne peut rien savoir de l'état d'un événement :

1. **Un événement annulé n'annule pas ses billets.** `Event.annuler()` (module `events`) passe l'événement à `ANNULE`, mais les billets vendus (module `tickets`) restent `EMIS`. Le soir prévu, `ValidateTicketScanUseCase` répond `ACCEPTE` à un billet d'un concert annulé.
2. **On peut réserver des places pour un événement non publié.** `ReserveSeatsUseCase` ne vérifie que l'existence de la catégorie. Un brouillon doit pourtant rester indiscernable d'un identifiant inconnu (règle de visibilité du module `events`).
3. **On peut émettre des billets pour un événement annulé** entre le paiement et sa confirmation.

Le module `orders`, à venir, aura les mêmes besoins.

Deux besoins de nature différente apparaissent :

- **Lire un état au moment d'agir** : « cet événement est-il ouvert à la vente ? ». La réponse doit être exacte à l'instant de la décision.
- **Réagir à un changement d'état** : « l'événement est annulé, donc ses billets aussi ». Le module qui change d'état ne doit pas connaître ceux qui en dépendent.

## Décision

**Les deux mécanismes de l'ADR 0001 sont retenus, chacun pour le besoin qu'il sert le mieux.**

### 1. Lire : un port public par module, dans un package `api.publique`

Un module qui veut laisser les autres lire une partie de son état expose une **interface** dans `com.billetplus.<module>.api.publique`, implémentée à l'intérieur du module. C'est le **seul** point d'entrée autorisé vers un autre module.

Premier port : `events.api.publique.StatutEvenements`, avec `boolean estOuvertALaVente(UUID eventId)` (vrai si l'événement existe et est `PUBLIE`).

La règle ArchUnit `modules_sont_independants` est ajustée pour ignorer, en plus de `shared`, les dépendances vers `..api.publique..`, et **seulement** celles-là : tout autre accès aux classes d'un autre module reste interdit.

### 2. Réagir : un événement applicatif traité dans la même transaction

Un module qui change d'état publie un **événement applicatif** (`ApplicationEventPublisher`). Son type est un *record* sans logique, placé dans `shared.evenements` pour être visible des deux côtés sans dépendance entre modules. Les modules intéressés l'écoutent avec `@EventListener`.

Premier événement : `shared.evenements.EvenementAnnule(UUID eventId)`, publié par `EventService.annuler`. Le module `tickets` y répond en annulant tous les billets encore `EMIS` de l'événement.

L'écouteur s'exécute **dans la transaction de l'émetteur** (`@EventListener` synchrone, et non `@TransactionalEventListener(AFTER_COMMIT)`). Si l'annulation des billets échoue, l'annulation de l'événement est annulée avec elle : l'état reste cohérent, sans mécanisme de reprise. C'est possible parce que le monolithe n'a qu'une base de données et qu'un seul gestionnaire de transactions (ADR 0001, ADR 0003).

### 3. Contenu de `shared.evenements`

Uniquement des *records* de données, sans logique métier, conformément à la discipline de `shared` (ADR 0001).

## Alternatives considérées

### Uniquement des ports publics

Rejetée comme mécanisme unique. Elle suffit pour lire (besoins 2 et 3), mais pas pour réagir : avec un port seul, les billets d'un événement annulé resteraient `EMIS` en base, et chaque lecteur (scan, remboursement, tableau de bord) devrait consulter `events` pour corriger l'état. Le module `events` devrait sinon connaître `tickets` pour l'appeler, ce qui inverserait la dépendance.

### Uniquement des événements applicatifs, avec une copie locale de l'état

Rejetée pour la lecture. `tickets` aurait gardé sa propre table du statut des événements, alimentée par `EvenementPublie` et `EvenementAnnule`. Cela supprime toute dépendance, mais crée une copie à initialiser (les événements existants), à maintenir et à tester, pour une information qui se lit directement. Le gain d'isolation ne justifie pas ce coût au stade du MVP.

### Écouteur après la validation (`AFTER_COMMIT`), avec une table d'*outbox*

Écartée pour le MVP. Elle raccourcit la transaction de l'émetteur, mais un écouteur qui échoue laisse l'état incohérent (événement annulé, billets valides) sans reprise automatique. Il faut alors une table d'*outbox* et un traitement de reprise. Ce mécanisme sera reconsidéré si un module est extrait en service séparé (ADR 0001, réversibilité), ou si une annulation doit traiter un volume de billets incompatible avec une transaction unique.

## Conséquences

**Positives**

- Le texte de l'ADR 0001 et la règle ArchUnit disent enfin la même chose, et la règle reste stricte : seul `api.publique` est ouvert.
- Un événement annulé annule réellement ses billets, de façon atomique. Le contrôle d'entrée les refuse (`NON_VALIDE`), et le futur remboursement les retrouvera par leur statut.
- La lecture « ouvert à la vente » est toujours exacte, sans copie à maintenir.
- `events` ne connaît aucun module qui l'écoute : ajouter un écouteur (`orders`, notifications) ne le modifie pas.

**Négatives**

- `tickets` dépend désormais d'`events`, par une seule interface. Une modification de `StatutEvenements` touche les deux modules.
- La transaction d'annulation d'un événement inclut l'annulation de tous ses billets : elle s'allonge avec leur nombre. Acceptable pour une action rare, faite par un organisateur, aux volumes du MVP. Limite à surveiller.
- Un écouteur qui lève une exception fait échouer l'action de l'émetteur. C'est voulu (cohérence), mais un écouteur fragile peut bloquer une annulation : chaque écouteur doit être testé dans la transaction réelle.

## Références

- [ADR 0001 — Monolithe modulaire](0001-monolithe-modulaire.md) (communication inter-module)
- [ADR 0002 — Hexagonale par module](0002-hexagonale-par-module.md)
- [ADR 0003 — PostgreSQL vs NoSQL](0003-postgresql-vs-nosql.md) (une seule base, transactions ACID)
- Spring Framework — `ApplicationEventPublisher`, `@EventListener`, `@TransactionalEventListener`

# Document de passation — Billet+

Dernière mise à jour : 30 octobre 2026, après la branche n° 4 de l'inventaire (`feature/backend-tests-http-routes-existantes` : tests HTTP des routes anciennes, **404 pour un non-propriétaire sur un brouillon**). Avant elle, trois merges locaux de Claude Code : **catégories de billets** (`232d58e`), **comptes `AGENT` et affectation** (`24dc753`, migrations V6 et V7), **route de scan** (`fa562bc`) ; CI verte sur `main` pour les deux premiers selon SirrDev. Avant eux, deux merges faits en local par Claude Code (sans PR, faute de `gh`) : `feature/backend-inter-module-event-status` (**ADR 0005** : un événement annulé annule ses billets et ferme la vente, une réservation sur un brouillon répond 404) et `feature/backend-tests-contexte-complet` (premier test du contexte complet). Avant eux, la PR #26 `feature/backend-tickets-validate-scan-usecase` (contrôle d'un billet à l'entrée : signature Ed25519, cinq issues, double scan simultané rejeté). Avant elle, la PR #24 `fix/infra-localhost-only` (PostgreSQL et Redis de dev publiés sur `127.0.0.1` seulement, bruit des repositories Redis coupé), et la PR #22 `feature/backend-tickets-redis-seat-lock` (réservation temporaire des places dans Redis pendant le paiement, ADR 0003), suivie d'un démarrage réel confirmé (base en V5). Plus tôt le même jour : PR #20 `feature/backend-tickets-quota-lock` (garde d'idempotence atomique des émissions, migration V5, 101 tests sur le runner, 0 ignoré), PR #18 `fix/ci-restore-backend-workflows` (**la CI backend et le job ArchUnit tournent de nouveau** sur les PR), PR #16 `feature/backend-tickets-jpa-mapper` (persistance JPA niveau 3 du module `tickets`, migration V4, `IssueTicketUseCase`), PR #17 (HANDOFF), PR #14 (alignement du dépôt) et PR #15 (configuration Claude Code)). Ce document complète le contexte du projet (ADR, dépôt GitHub synchronisé) pour reprendre le travail sans rien réexpliquer. En cas de doute entre ce document et le code, **le code du dépôt fait foi** — mais attention : la synchronisation GitHub ne voit que les fichiers **commités, poussés et mergés**, et elle n'est rafraîchie qu'après un clic sur « Sync now » dans le projet.

---

## 1. Objectif du projet

**Billet+** est une plateforme de billetterie événementielle mobile-first pour l'Afrique de l'Ouest (Sénégal/Dakar en premier). Elle digitalise un parcours aujourd'hui largement informel (vente cash, revente non régulée, fraude au billet papier) via :
- Achat 100 % digital (Wave en premier, Orange Money/Free Money/carte en V2)
- QR code individuel infalsifiable par billet, vérifiable **hors ligne**
- Tableau de bord organisateur (ventes, revenus, quotas)
- Scanner d'entrée avec détection de double passage

**Approche MVP-first stricte** : priorisation MoSCoW, un seul événement pilote à Dakar comme objectif de validation avant tout scaling. Les ambitions du cahier des charges original (IA, multi-pays, microservices, Kubernetes) sont délibérément repoussées en « WON'T (V1) ».

Développeur : **SirrDev** (Cheikh Bassirou Gueye), qui développe **seul tout le backend** (le découpage à deux devs est abandonné — ne plus y faire référence).

---

## 2. Décisions d'architecture déjà prises, et pourquoi

Actées, à ne **pas** remettre en question sans raison nouvelle et explicite. Chacune a son ADR dans `docs/architecture/adr/` :

| ADR | Décision | Pourquoi (résumé) |
|---|---|---|
| **0001** | Monolithe modulaire (pas microservices) | Équipe réduite, volumétrie MVP modeste (~120k billets/an). Frontières de module strictes, vérifiées par ArchUnit. |
| **0002** | Hexagonale, **rigueur variable par module** | `tickets`/`orders` : domaine 100 % pur (niveau 3). `auth`/`events` : JPA-as-domain pragmatique (niveau 2). Critère : *« un bug ici peut-il coûter de l'argent ou créer une incohérence irréversible ? »* |
| **0003** | PostgreSQL (pas NoSQL) | ACID fort pour l'anti-overselling. JSONB (plan de salle), PostGIS (activé, pas encore mappé en JPA), pg_trgm. |
| **0004** | PWA React (pas React Native) | Un seul codebase, pas de délai de store. Conséquence sécurité : signature **Ed25519** (pas HMAC) car le JS client est inspectable. |

### 3 niveaux de rigueur hexagonale

| Niveau | Quand | Exemple |
|---|---|---|
| 1 — Couches strictes | Lecture pure | Dashboards, exports |
| 2 — Port + JPA-as-domain | CRUD avec un peu de validation | `auth`, `events` |
| 3 — Domaine séparé (POJO pur) | Invariant critique | `tickets`, `orders` |

### Règle d'isolation inter-module (vérifiée par ArchUnit)

Jamais de relation JPA cross-module : UUID brut uniquement, sans `FOREIGN KEY` en base entre modules. Exemple : `Event.organizerId` est un `UUID` vers `auth.User`. Le package `shared` est le seul point de partage autorisé et **ne contient aucune logique métier** (types techniques seulement : `Money`, handler d'exceptions, classes d'exception de base comme `BusinessConflictException`).

---

## 3. État du dépôt

**Avancement estimé** (ordre de grandeur, pas une mesure) : **backend ≈ 45 %** du MVP. Les branches 1 à 11 du plan sont livrées, plus l'ADR 0005, le test du contexte complet, et les trois premières branches de l'inventaire (catégories, comptes `AGENT`, route de scan). **13 branches restent** (section 11.1), dont la moitié dépend de deux décisions de SirrDev. MVP complet ≈ 25 % (le frontend n'a aucune branche commencée).

### Phase 0 — Socle technique : ✅ mergée

Docker (PostGIS + Redis), squelette Spring Boot, squelette PWA, CI GitHub Actions (backend, frontend, ArchUnit), ESLint, Vitest.

### Backend — branches

| # | Branche | Statut | Contenu |
|---|---|---|---|
| 1 | `backend-auth-user-entity` | ✅ Mergée | `User`, `Role`, migration V2 |
| 2 | `backend-auth-tokens` | ✅ Mergée | JWT, refresh, login/register/me, BCrypt |
| 3 | `backend-tickets-domain-model` | ✅ Mergée | `TicketCategory`, `Ticket` (POJO purs) |
| 4 | `backend-tickets-ed25519-signer` | ✅ Mergée | Port `QRSigner`, impl Ed25519 |
| 5 | `backend-events-entity-migration` | ✅ Mergée le 05/10/2026 (PR #11, merge `8e1676a`) | `Event`, `Venue`, migration V3 |
| 6 | `backend-events-crud-api` | ✅ **Mergée le 19/10/2026** (4 commits : `428baad`, `a14d67e`, `66ba9ce`, `4731c90`) | API REST événements/lieux, sécurité par méthode, 409, visibilité des brouillons (section 4) |
| — | `fix/auth-register-role-restriction` | ✅ **Mergée le 20/10/2026** (hors plan initial) | Inscription publique limitée au rôle `ORGANISATEUR` (section 4.5) |
| 7 | `backend-tickets-jpa-mapper` | ✅ **Mergée le 29/10/2026** (PR #16, merge `22845ba` ; 10 commits de `9b75555` à `5c601ec`) | Migration V4, ports, entités JPA + mappers + adaptateurs, verrou `@Version`, `IssueTicketUseCase`, 5ᵉ règle ArchUnit, premiers tests d'intégration (section 4.6) |
| 8 | `tickets-issue-usecase` | 🟡 **Cœur livré dans la branche 7** (`c17e5e6`, `996c87e`) | Reste : vérifier que l'événement est `PUBLIE` avant d'émettre, **bloqué par une décision d'architecture** (section 7). Ne pas recréer la branche : à intégrer à `orders-confirm-payment-usecase` une fois la décision prise |
| 9 | `tickets-quota-lock` | ✅ **Mergée le 29/10/2026** (PR #20, merge `d84456c` ; commits `40e9bf3`, `54bc93a`, `77e5b39`) | Migration V5 `ticket_issuances`, garde d'idempotence atomique par `INSERT ... ON CONFLICT DO NOTHING`, tests à 8 threads (section 4.8) |
| — | `infra-claude-code-setup` | ✅ Mergée le 29/10/2026 (PR #15, merge `c3affef`) | `CLAUDE.md`, agents, skills, règles de `.claude/` |
| — | `fix/docs-handoff-pr16` | ✅ Mergée le 29/10/2026 (PR #17, merge `08c4a1a` ; commit `e77f602`) | Mise à jour du HANDOFF après la PR #16 |
| — | `fix/ci-restore-backend-workflows` | ✅ Mergée le 29/10/2026 (PR #18, merge `bcb82e7` ; commits `015e9a7`, `b331130`) | CI backend et job ArchUnit restaurés (section 4.7), `CONTRIBUTING.md` à 5 règles |
| — | `fix/docs-handoff-pr18` | ✅ Mergée le 29/10/2026 (PR #19, merge `33a163c` ; commit `ae2bac3`) | Mise à jour du HANDOFF après la PR #18 |
| — | `fix/docs-handoff-pr20` | ✅ Mergée le 29/10/2026 (PR #21, merge `632766f` ; commit `c2468de`) | Mise à jour du HANDOFF après la PR #20 |
| 10 | `tickets-redis-seat-lock` | ✅ **Mergée le 29/10/2026** (PR #22, merge `06ec5bd` ; commits `04b52d9`, `93a0ffa`, `77a555a`) | Réservation temporaire des places dans Redis, script Lua atomique, `ReserveSeatsUseCase` / `ReleaseSeatsUseCase`, 503 si Redis indisponible (section 4.9) |
| — | `fix/docs-handoff-pr22` | ✅ Mergée le 29/10/2026 (PR #23, merge `d07fbf2` ; commit `70ebb10`) | Mise à jour du HANDOFF après la PR #22 |
| — | `fix/infra-localhost-only` | ✅ Mergée le 29/10/2026 (PR #24, merge `4cc23a3` ; commits `ec0dd27`, `6449a50`) | PostgreSQL et Redis de dev sur `127.0.0.1` seulement (relecture sécurité de la PR #22) ; `spring.data.redis.repositories.enabled: false`. Branche créée sous le nom `fix/infra-redis-localhost-only`, renommée avant le premier push quand PostgreSQL a été ajouté au périmètre |
| — | `fix/docs-handoff-pr24` | ✅ Mergée le 29/10/2026 (PR #25, merge `42acb36` ; commit `6c00595`) | Mise à jour du HANDOFF après la PR #24 |
| 11 | `tickets-validate-scan-usecase` | ✅ **Mergée le 29/10/2026** (PR #26, merge `f637c4d` ; commits `d67445d`, `f136753`) | `ValidateTicketScanUseCase`, `ResultatScan`, `TicketRepository.findById`, refus de scan du domaine en 409 (section 4.10). **Aucune route** : elle attend les comptes `AGENT` |
| — | `fix/docs-handoff-pr26` | ✅ Mergée le 29/10/2026 (PR #27, merge `84ff531` ; commit `fcd179b`) | Mise à jour du HANDOFF après la PR #26 |
| — | `feature/backend-inter-module-event-status` | ✅ **Mergée le 29/10/2026, en local, sans PR** (merge `1c73296` ; commits `a3fd1a6`, `a635345`, `5672ab3`) | **ADR 0005**, port public `StatutEvenements`, événement `EvenementAnnule`, billets annulés avec l'événement, vente fermée (section 4.11). CI verte sur `main` selon SirrDev |
| — | `feature/backend-tests-contexte-complet` | ✅ Mergée le 29/10/2026, en local, sans PR (merge `2f126a9` ; commit `3d880e1`) | Premier `@SpringBootTest` sur PostGIS et Redis réels (section 4.11) |
| — | `fix/docs-handoff-adr-0005` | ✅ Mergée le 29/10/2026, en local (merge `7b0e7f9` ; commits `69c61c7`, `665e9e1`) | HANDOFF (inventaire des branches), passage à 7 règles ArchUnit dans `CLAUDE.md`, `CONTRIBUTING.md` et `.claude/` |
| — | `feature/backend-tickets-category-management` | ✅ Mergée le 29/10/2026, en local (merge `232d58e` ; commits `c5e8a17`, `ce0e9ac`, `7a7c14e`) | Création et liste des catégories de billets, premiers tests HTTP (section 4.12). CI verte selon SirrDev |
| — | `feature/backend-auth-agent-accounts` | ✅ Mergée le 30/10/2026, en local (merge `24dc753` ; commits `2c0b082`, `70944cd`, `9e10ff9`) | Comptes `AGENT`, affectation aux événements, premier `ADMIN` hors API, **V6** et **V7** (section 4.12). CI verte selon SirrDev |
| — | `feature/backend-tickets-scan-route` | ✅ Mergée le 30/10/2026, en local (merge `fa562bc` ; commits `2ca151f`, `b64368e`) | Route de contrôle d'entrée, rejeu d'un scan simultané (section 4.12). CI verte selon SirrDev |
| — | `feature/backend-tests-http-routes-existantes` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | 12 tests HTTP des routes `/api/auth`, `/api/events`, `/api/venues` avec de **vrais jetons JWT** ; base commune `ContexteCompletTestBase` (contexte partagé) ; non-propriétaire : 404 sur un brouillon, 403 sur un événement visible (section 4.13) |
| 12–17 | `tickets-pdf-export`, `orders-domain-model`, `orders-create-usecase`, `orders-wave-gateway`, `orders-confirm-payment-usecase`, `rate-limiting` | ⬜ | |

Branches volontairement supprimées (ne pas recréer) : `backend-auth-rbac-roles` (absorbée dans auth-tokens), `backend-events-categories-quotas` (redondante : `TicketCategory` vit dans `tickets/domain`).

### Branche d'alignement : `fix/infra-align-repo-with-handoff` — ✅ Mergée le 29/10/2026 (PR #14, merge `c688b50`)

Commits : `2ce6a53` (`infra/docker-compose.yml` : image `postgis/postgis:16-3.4`, propriété Redis de Spring Boot 3, port `5432:5432`), `55daf4a` (`.gitattributes` normalisé, `qodana.yaml` en `projectJDK: "21"`), `c1e48c4` (ADR 0001 à 0004 versionnés dans `docs/architecture/adr/`), `37e272c` (`CONTRIBUTING.md` PowerShell + ArchUnit), `8e17bd5` (cahier des charges aligné sur les ADR).

Le `docker-compose.yml` local n'est plus modifié hors Git : `git status` est propre sur `main`. `CONTRIBUTING.md` liste les règles ArchUnit (7 depuis l'ADR 0005). La règle « jamais de `git add .` » reste en vigueur.

### Frontend (PWA) : Phase 0 seulement

Squelette fonctionnel, aucune branche `feature/web-*` commencée. Le `docker-compose.yml` prévoit `VITE_API_URL: http://localhost:8080` : à revoir au démarrage du front, car le port 8080 est souvent occupé en local (section 8).

---

## 4. Dernières branches livrées

### 4.1 Routes exposées (branche `events-crud-api`)

| Route | Accès | Remarque |
|---|---|---|
| `POST /api/auth/register` | public | crée **toujours** un `ORGANISATEUR` (section 4.5) |
| `POST /api/venues` | `ORGANISATEUR` **ou** `ADMIN` | crée un lieu (global, sans propriétaire) |
| `GET /api/venues/{id}` | public | |
| `POST /api/events` | `ORGANISATEUR` | l'organisateur vient du jeton, jamais du corps de la requête |
| `GET /api/events/{id}` | public, **sensible à l'identité** | un brouillon n'est visible que par son organisateur, sinon **404** |
| `GET /api/events/mine` | `ORGANISATEUR` | |
| `POST /api/events/{id}/publier` | `ORGANISATEUR` **propriétaire** | 409 si l'événement n'est plus en brouillon |
| `POST /api/events/{id}/annuler` | `ORGANISATEUR` **propriétaire** | 409 si l'événement est `TERMINE` |

**Il n'existe aucune route `GET /api/events` (liste publique)** : elle répond 405. La découverte/recherche est classée MUST/SHOULD dans le cahier des charges, donc à traiter dans une branche ultérieure.

### 4.2 Décisions prises pendant la branche `events-crud-api` (à respecter)

- **Visibilité** : règle portée par le domaine (`Event.estVisiblePar(UUID viewerId)`), appliquée par `EventService.getVisible(id, viewerId)`. Brouillon non visible → `NoSuchElementException` avec le **même message** qu'un identifiant inconnu (404 indiscernable, pour ne pas révéler l'existence d'un brouillon). Un événement `ANNULE` ou `TERMINE` reste public (les acheteurs doivent voir l'annulation). `EventService.get(id)` reste une lecture **brute, interne** : toute lecture exposée à un client passe par `getVisible`.
- **Identité d'un visiteur anonyme** : `@AuthenticationPrincipal UUID viewerId` vaut `null` (le principal anonyme est la chaîne `"anonymousUser"`, non assignable à `UUID`). Vérifié par le scénario HTTP.
- **Conflits métier** : `shared/exception/BusinessConflictException extends IllegalStateException` → **409**. Elle étend `IllegalStateException` volontairement : les tests existants restent verts, et une `IllegalStateException` ordinaire (ex. panne de signature Ed25519) **reste un 500**. `Event.publier()` et `Event.annuler()` la lèvent.
- **`AccessDeniedException`** → **401** si le visiteur est anonyme (`null` ou `AnonymousAuthenticationToken`), **403** sinon. Un `@PreAuthorize` refusé lève `AuthorizationDeniedException`, sous-classe déjà couverte.
- **Chargement du lieu** : `Event.venue` est `LAZY` et `spring.jpa.open-in-view` vaut `false`. `SpringDataEventRepository` déclare `@EntityGraph(attributePaths = "venue")` sur `findById` et `findByOrganizerId`, sinon `EventDto.from` (appelé hors transaction) lit un proxy non initialisé. *(Le défaut avait été identifié à la relecture ; le scénario a été passé avec le correctif, jamais sans — on n'a pas observé l'exception réelle.)*
- **Validation des entrées** alignée sur le schéma V3 : `@Size(max = 255)` sur `titre` et `nom` (sinon PostgreSQL refuse et l'API répond 500 au lieu de 400), `@Size(max = 5000)` description, `@Size(max = 500)` adresse, latitude bornée −90/90, longitude −180/180 (null accepté).

### 4.3 Sécurité — `SecurityConfig` final

`@EnableMethodSecurity` ; point d'entrée `HttpStatusEntryPoint(401)` ; règles dans cet ordre : `/api/auth/login|refresh|register` public → `GET /api/events/mine` `authenticated()` (doit précéder la règle publique) → `GET /api/events/**` et `/api/venues/**` public → `/api-docs/**`, `/swagger-ui/**`, `/actuator/health` publics → **`/error` public** → `anyRequest().authenticated()`.

Pourquoi `/error` : sans cette règle, Spring Security 6 soumet à l'authentification la redirection interne de Tomcat vers `/error`, et tout 404/400/500 devient un **403** pour un client non connecté.

### 4.4 Tests

**247 tests, 0 échec, 0 ignoré, mesurés le 30/10/2026 (`./mvnw clean verify`, Docker démarré)**, dont 78 tests d'intégration Testcontainers : 29 PostGIS (`TicketPersistenceIntegrationTest`), 13 Redis (`RedisSeatReservationStoreIntegrationTest`), et **36 sur le contexte complet**, qui partagent un seul contexte Spring (`ContexteCompletIntegrationTest` 24, `RoutesExistantesHttpIntegrationTest` 12, dont 31 par HTTP). **7 règles ArchUnit.** *(Historique : 55 le 20/10 ; de 92 à 196 le 29/10 ; 222, 234 puis 247 le 30/10.)*

⚠️ Sans Docker, les 13 tests d'intégration sont **ignorés** (`disabledWithoutDocker`) et la suite reste verte : toujours lire la ligne `Skipped` avant d'annoncer un résultat.

### 4.5 Correctif `fix/auth-register-role-restriction` (mergé le 20/10/2026)

**Problème corrigé** : `POST /api/auth/register` (public) acceptait un champ `role` (`ORGANISATEUR`, `AGENT` ou `ADMIN`) : n'importe qui pouvait s'auto-créer ADMIN.

**Décisions (à respecter)** :
- `RegisterRequest` n'a **plus de champ `role`** : le client ne choisit jamais son rôle. Un champ `role` envoyé quand même est ignoré par Jackson (Spring Boot désactive `FAIL_ON_UNKNOWN_PROPERTIES`) et n'est jamais lu.
- `AuthService.register(..., Role)` est **supprimée**, remplacée par `AuthService.registerOrganisateur(email, motDePasse, nomComplet)`, qui fixe le rôle `ORGANISATEUR`. **Aucune méthode publique d'`AuthService` n'accepte un `Role`** : à conserver. Les comptes `AGENT` (créés par un organisateur) et `ADMIN` (créé hors API) auront chacun leur propre cas d'usage, avec son propre contrôle d'accès, quand le besoin se présentera.
- Validation alignée sur le schéma V2 (leçon 15) : `email` et `nomComplet` ≤ 255 caractères, mot de passe 8 à 72 caractères (limite de BCrypt, qui ignore tout au-delà de 72 octets).
- **Conséquence** : aucune route ne peut plus créer un `AGENT` ni un `ADMIN`. Aucune fonctionnalité actuelle n'en a besoin, mais c'est un chantier à prévoir (section 7).

**Vérifié en HTTP le 20/10/2026** (script de la section 6.2) : inscription avec `role = "ADMIN"` → 201, puis `GET /api/auth/me` → `"role":"ORGANISATEUR"` ; email de 300 caractères → 400 avec le message de validation.

### 4.6 Branche `feature/backend-tickets-jpa-mapper` (PR #16, mergée le 29/10/2026)

Aucune route HTTP ajoutée. **Décisions (à respecter)** :

- **Persistance niveau 3** : `tickets/infrastructure/persistence/` contient `TicketCategoryJpaEntity` et `TicketJpaEntity` (package-private, distinctes du domaine), `TicketCategoryMapper` et `TicketMapper` (trois opérations : `versDomaine`, `versNouvelleEntite`, `copierDans`), les deux interfaces Spring Data et les deux adaptateurs. Ports : `tickets/application/port/TicketCategoryRepository` (`findById`, `save`) et `TicketRepository` (`save` ; `existsByOrderIdAndCategoryId` a été **supprimé** par la PR #20, remplacé par `TicketIssuanceRepository`, section 4.8). `categoryId` est un `UUID` simple dans l'entité, pas un `@ManyToOne`.
- **Verrouillage optimiste** : colonne `version` sur `ticket_categories` **et** sur `tickets` (double scan concurrent). `@Version Long` (wrapper, `null` à la création ⇒ Spring Data fait un `persist`, pas un `merge`). Le domaine n'a **pas** de champ version.
- **`save()` recharge l'entité gérée puis y copie l'état du domaine** (jamais une entité neuve pour un objet existant), pour conserver la version lue. Les deux `save()` sont en **`@Transactional(propagation = MANDATORY)`** : tout cas d'usage qui lit puis modifie une catégorie ou un billet **doit** être `@Transactional`, sinon l'appel échoue exprès (`IllegalTransactionStateException`). Hors transaction englobante, le verrou ne protégerait rien.
- **Conflit de version → 409** : `GlobalExceptionHandler` traduit `OptimisticLockingFailureException` en 409 avec un message générique (ni entité, ni identifiant). Il n'y a ni nouvelle tentative automatique ni retry côté serveur.
- **`QuotaDepasseException` et `BilletsDejaEmisException` étendent `BusinessConflictException`** (409).
- **`IssueTicketUseCase.execute(categoryId, orderId, quantite)`** : `@Service @Transactional`. Il lit la catégorie (404 si inconnue), refuse une seconde émission pour le même couple (commande, catégorie) (409 ; mécanisme remplacé par la PR #20, section 4.8), réserve le quota dans le domaine, enregistre la catégorie, crée et enregistre N billets, puis signe chaque QR (`QRSigner.sign(ticketId, eventId, emisLe)`). Il renvoie des `IssuedTicket(ticket, qrContent)`. Une panne de signature annule toute l'émission (test d'intégration).
- **Plafond : 20 billets par émission**, sinon 400. *(Depuis la PR #22 : règle du domaine `QuantiteParCommande.MAX` ; la constante `IssueTicketUseCase.QUANTITE_MAX_PAR_EMISSION` est supprimée.)* La même limite est à appliquer à la création de commande (module `orders`).
- **Domaine** : `TicketCategory` refuse un `prix` nul (`Money.ofFcfa(0)` pour une catégorie gratuite) ; `Ticket` tronque `emisLe` et `scanneLe` à la **microseconde** (précision de `TIMESTAMPTZ`), pour qu'un billet relu en base soit strictement égal au billet émis. **Le QR, lui, signe `emisLe` à la milliseconde** (`Ed25519QRSigner`, champ `iat`).
- **Migration V4** (appliquée sur la base de dev de SirrDev) : `prix_fcfa BIGINT >= 0`, `quota_total > 0`, contrainte `0 <= quota_vendu <= quota_total` (dernier rempart contre la survente), `CHECK` des 4 statuts, `SCANNE ⇒ scanne_le NOT NULL`, FK intra-module `tickets.category_id`, aucune FK vers `events` ni `orders`. `nom VARCHAR(255)` : le futur DTO de création de catégorie devra porter `@Size(max = 255)`, `@NotBlank`, prix `>= 0`, quota `>= 1`.
- **5ᵉ règle ArchUnit** `domaines_niveau_3_sans_jpa_ni_spring` : interdit `jakarta.persistence`, `org.hibernate` et `org.springframework` dans `tickets.domain` et `orders.domain` (pas `auth` ni `events`, niveau 2). Elle a été testée à l'envers : une classe `@Entity` placée dans `tickets/domain` la fait échouer.
- **Testcontainers 1.21.4** (propriété `testcontainers.version` du `pom.xml`, qui aligne aussi la version gérée par le parent Spring Boot).

**Vérifié** : 92 tests verts, dont, sur PostGIS réel : survente concurrente rejetée (seule la réservation validée en premier subsiste), double scan concurrent rejeté, `save()` hors transaction refusé, émission puis rejeu refusé, annulation complète sur panne de signature. Démarrage réel sur le port 8081 le 29/10/2026 : Flyway applique V4 et `ddl-auto: validate` passe. Ce démarrage a été fait **avant** les correctifs `MANDATORY`, 409 et plafond, et n'a pas été relancé depuis. Relecture `archi-guardian`, `db-schema-reviewer` et `security-reviewer` faite, avec ses points traités ou reportés en section 7.

### 4.7 CI backend restaurée (`fix/ci-restore-backend-workflows`, PR #18, mergée le 29/10/2026)

Deux workflows, restaurés depuis `0e38483` (supprimés par erreur par `32b0ba4` le 19/08) :

- **`backend-ci.yml` (« Backend CI »)** : JDK 21 Temurin, `./mvnw clean verify` sur `ubuntu-latest`, rapports Surefire publiés en artefact `backend-surefire-reports` (conservés 7 jours, même en cas d'échec).
  - **Plus de service PostgreSQL ni Redis** : aucun test ne démarre le contexte complet, et les tests d'intégration lancent leur propre conteneur PostGIS (Testcontainers, Docker fourni par le runner). L'ancien service utilisait `postgres:16`, sans PostGIS.
  - **Étape « Refuser les tests ignorés »** : additionne `Tests run` et `Skipped` des rapports Surefire. Elle échoue si au moins un test est ignoré, ou si aucun test n'a tourné. C'est le garde-fou contre le piège 18 (tests Testcontainers ignorés, CI verte sans rien vérifier). Testée en local sur trois cas : vrais rapports, un rapport avec 13 tests ignorés, aucun rapport.
- **`architecture-check.yml` (« Architecture rules »)** : `./mvnw test -Dtest=ArchitectureTest`, pour un statut ArchUnit distinct sur la PR.
- **Déclenchement** des deux workflows : PR qui touche `backend/**` ou le fichier du workflow, et push sur `main` qui touche `backend/**`. Une PR de documentation pure ne déclenche donc **aucun** des deux. C'est voulu, mais un statut absent ne prouve rien.
- **Aucun secret requis** : `Ed25519QRSignerTest` génère sa propre paire de clés. Si un futur test démarre le contexte complet, il faudra fournir une clé de test, **jamais** la clé de dev, ni aucun secret commité.

**Vérifié** :
- en local : YAML valides, `./mvnw clean verify` réussi, 92 tests verts ;
- sur la PR #18 : run vert, selon SirrDev le 29/10/2026. Le vert de l'étape « Refuser les tests ignorés » implique au moins un test exécuté et 0 ignoré. *Nombre exact relevé au run suivant (PR #20) : 101 exécutés, 0 ignoré, identique au local.*

### 4.8 Branche `feature/backend-tickets-quota-lock` (PR #20, mergée le 29/10/2026)

Aucune route HTTP ajoutée. **Décisions (à respecter)** :

- **Registre d'idempotence** : migration **V5** `ticket_issuances(order_id, category_id, quantite, emise_le)`, **clé primaire `(order_id, category_id)`**, FK intra-module vers `ticket_categories`, aucune FK vers `orders`. Port `TicketIssuanceRepository.enregistrer(orderId, categoryId, quantite)`, qui lève `BilletsDejaEmisException` (409) si le couple existe déjà. Pas d'objet de domaine : le registre ne porte aucune règle métier.
- **Ordre dans `IssueTicketUseCase`** : vérification des paramètres (quantité **entre 1 et 20 avant toute lecture**, sinon la contrainte `CHECK` du registre donnerait un 500) → lecture de la catégorie (404) → **enregistrement de l'émission** → réservation du quota → enregistrement de la catégorie → création et signature des billets. Une émission concurrente pour la même commande attend la fin de la première, puis reçoit un 409.
- **`INSERT ... ON CONFLICT DO NOTHING` natif** dans `JpaTicketIssuanceRepositoryAdapter`, avec lecture du nombre de lignes insérées (0 = doublon). Ni `save()` de Spring Data (un `merge` sur clé composite fait un `UPDATE` silencieux), ni `persist` + `flush` (l'erreur 23505 interrompt la transaction PostgreSQL ; un doublon dans la même transaction lève `EntityExistsException`, donc un 500).
- **Adaptateur en `@Component`, pas `@Repository`** : la traduction d'exceptions des beans `@Repository` convertit toute `IllegalStateException`, donc toute `BusinessConflictException`, en `InvalidDataAccessApiUsageException`, c'est-à-dire un 500. `@DataJpaTest` ne charge pas cette traduction : le test d'intégration l'active (`@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)`), ce qui a démontré le défaut avant correction. Règle notée dans `CLAUDE.md`.
- **`existsByOrderIdAndCategoryId` supprimé** (vérification « lire puis écrire », non atomique). L'index V4 `idx_tickets_order_id_category_id` reste en place : il ne sert plus que par son préfixe `order_id`, pour une future lecture des billets d'une commande.
- **Décisions actées pendant la branche** :
  - pas de requête SQL atomique sur le quota ni de `SELECT ... FOR UPDATE` (ADR 0003 : `@Version` d'abord) ;
  - **pas de nouvelle tentative automatique dans `IssueTicketUseCase`** : appelé dans la transaction de confirmation de paiement, un conflit de version marque cette transaction comme à annuler, donc la relance appartient à la frontière extérieure ;
  - **l'exception `BilletsDejaEmisException` est gardée** (plutôt qu'un résultat « déjà émis ») ;
  - le contrôle « événement `PUBLIE` » est sorti de la branche (section 7).
- **Contrat de l'appelant** : écrit dans la Javadoc d'`IssueTicketUseCase.execute` et repris en section 7 (« Contrat de `orders` »).

**Vérifié** :
- 101 tests, 0 échec, 0 ignoré, en local et sur le runner GitHub ;
- 3 tests à **8 threads** simultanés, sur de vraies transactions, avec une sonde temporaire (retirée) qui a confirmé que les transactions se chevauchaient réellement :
  - même commande : 1 succès, 7 × 409 ;
  - commandes différentes et quota suffisant : aucune réservation perdue ;
  - quota insuffisant : jamais de survente ;
- un doublon dans la même transaction donne un 409, et la transaction PostgreSQL reste utilisable ;
- un quota insuffisant ou une panne de signature annulent aussi la ligne du registre ;
- relecture `archi-guardian`, `db-schema-reviewer` et `security-reviewer` faite, avec ses points traités ou reportés en section 7.

**Constat de la sonde** : avec 100 places libres et **8 acheteurs différents simultanés, 1 seul réussit et 7 reçoivent un conflit de version (409)**. C'est le comportement normal du `@Version` : aucune donnée fausse, mais un taux d'échec élevé lors d'une ouverture de billetterie (section 7).

### 4.9 Branche `feature/backend-tickets-redis-seat-lock` (PR #22, mergée le 29/10/2026)

Aucune route HTTP ajoutée. Objet (ADR 0003, ligne 48) : bloquer des places pendant le paiement Wave, pour que le quota soit garanti **avant** que l'acheteur paie. **Décisions (à respecter)** :

- **Domaine** :
  - `ReservationTemporaire(categoryId, orderId, quantite, expireLe)`, un POJO pur : `creer(..., maintenant, duree)` valide les invariants, `reconstituer(...)` ne revalide pas, `estExpireeA(instant)` considère la borne comme incluse. Expiration à la milliseconde.
  - **`QuantiteParCommande`** : le plafond de 20 places par commande et par catégorie devient une **règle du domaine** (`MAX`, `verifier(quantite)`). Elle est utilisée par `ReservationTemporaire`, `ReserveSeatsUseCase` et `IssueTicketUseCase`, et aucun cas d'usage ne dépend plus d'un autre.
- **Une réservation ne modifie jamais `ticket_categories`** : seule une vente payée consomme le quota. PostgreSQL reste la source de vérité de la vente, et le dernier rempart contre la survente.
- **Port `SeatReservationStore`** :
  - `reserver(reservation, limite, maintenant)` renvoie l'**expiration effectivement enregistrée** (`Optional<Instant>`), ou vide en cas de refus. Pas d'exception métier dans le port : c'est le cas d'usage qui lève le 409 ;
  - `liberer(categoryId, orderId)`, idempotent.
- **Adaptateur `RedisSeatReservationStore`** (`@Component`, `tickets/infrastructure/redis/`) :
  - trois clés par catégorie, avec le hash tag `{categoryId}` : un *sorted set* (`orderId` → expiration en ms), un *hash* (`orderId` → quantité) et un **total courant** (`:total`) ;
  - **script Lua atomique** : il retire les réservations expirées (**au plus 100 par appel**), lit le total, refuse si `autres + quantité > limite`, puis enregistre ;
  - le total évite de parcourir toutes les réservations (`HGETALL` en O(n), qui bloquerait Redis). Une réservation expirée pas encore retirée reste comptée : l'erreur est prudente (place refusée à tort), jamais une survente ;
  - les trois clés portent une expiration Redis égale à la réservation la plus lointaine plus 1 minute : une catégorie inactive ne laisse rien.
- **Limite = `TicketCategory.quotaRestant()`**, lue dans PostgreSQL juste avant la réservation. Le script ne contient pas de règle métier : il applique cette limite de façon atomique.
- **Une réservation par commande et par catégorie** (clé `orderId`) : réserver de nouveau remplace la quantité mais **garde l'expiration d'origine**, ce qui empêche de bloquer des places indéfiniment en renouvelant (relecture sécurité).
- **Durée : 5 minutes**, `billetplus.reservation.duree: 5m` (`application.yml`), lue par `@Value` dans `ReserveSeatsUseCase`. Elle est plus longue que le tunnel de paiement (1 à 2 min), parce qu'une durée trop courte ferait expirer des paiements en cours.
- **Heure fournie par l'application**, par un bean `Clock` (UTC) dans **`shared/config/HorlogeConfig`** (et non dans `tickets`, car `orders` en aura besoin), et non par l'heure du serveur Redis. Une seule source d'heure, et des tests sans attente.
- **Cas d'usage** :
  - `ReserveSeatsUseCase` : quantité de 1 à 20, catégorie (404), réservation, puis `PlacesIndisponiblesException` (409) si elle ne tient pas. Le message est **générique**, sans identifiant ni stock restant ;
  - `ReleaseSeatsUseCase` : idempotent, **sans contrôle de propriété** (contrat de l'appelant en section 7).
- **`IssueTicketUseCase` libère la réservation après la validation** de la transaction (`TransactionSynchronization.afterCommit`) : jamais avant, pour que l'acheteur garde ses places si l'émission est annulée. Une panne Redis à ce moment est journalisée en `WARN` et n'annule pas la vente : la réservation expirera d'elle-même.
- **Redis indisponible : 503**, et pas de repli sur PostgreSQL seul (qui rouvrirait la vente sans réservation). `GlobalExceptionHandler` traduit `DataAccessResourceFailureException` en 503 avec un message générique et un **`LOG.error` côté serveur**. Cela couvre aussi une base PostgreSQL injoignable, ce qui est le bon code (« réessayez »).
- **Risque résiduel documenté** : une vente validée entre la lecture de la limite et la réservation peut, sur une fenêtre de quelques microsecondes, laisser accepter une réservation de trop. PostgreSQL la refusera à l'émission, et le client, déjà débité, devra être remboursé. Le cas « paiement plus long que la réservation » est **beaucoup plus probable** : il fait l'objet du contrat de `orders` (section 7).

**Vérifié** :
- 134 tests, 0 échec, 0 ignoré, dont **13 sur un vrai Redis** (Testcontainers `redis:7-alpine`) :
  - réservation, refus, limite exacte ;
  - remplacement sans prolongation de l'expiration ;
  - expiration et retrait, testés sans attente ;
  - nettoyage borné, avec 150 réservations expirées : 100 retirées, puis 50 ;
  - durée de vie des clés ;
  - libération idempotente ;
  - **8 réservations simultanées de 3 places pour une limite de 10 : exactement 3 acceptées**, la concurrence étant confirmée par une sonde (les 8 appels partis avant la première réponse) ;
  - **total courant égal à la somme réelle**, vérifié après chaque test.
- La libération après validation est vérifiée sur de vraies transactions : une fois après une vente validée, jamais après un rejeu refusé ni après une émission annulée.
- Démarrage réel de l'application confirmé (section 8).
- Relecture `archi-guardian` et `security-reviewer` faite, avec les cinq corrections appliquées et le reste reporté en section 7.

**Non vérifié** :
- une vraie panne Redis de bout en bout (le 503 n'est testé que sur le handler) ;
- le scénario du paiement lent avec PostgreSQL et Redis ensemble ;
- le coût du script sous forte charge.

### 4.10 Branche `feature/backend-tickets-validate-scan-usecase` (PR #26, mergée le 29/10/2026)

Aucune route HTTP ajoutée : la route de scan attend les comptes `AGENT` (section 7). **Décisions (à respecter)** :

- **Les issues attendues d'un scan sont renvoyées, jamais levées.** `ValidateTicketScanUseCase.execute(qrContent, eventIdDuControle)` (`@Service @Transactional`) renvoie un `ResultatScan` (*record* et *enum* `Issue`) :

  | Issue | Quand | Données |
  |---|---|---|
  | `ACCEPTE` | signature valide, billet `EMIS`, bon événement : le billet passe à `SCANNE` | `ticketId` |
  | `DEJA_SCANNE` | billet `SCANNE` | `ticketId`, **heure du premier passage** |
  | `NON_VALIDE` | billet `ANNULE` ou `REMBOURSE` | `ticketId`, statut |
  | `AUTRE_EVENEMENT` | billet authentique d'un autre événement | `ticketId` |
  | `INVALIDE` | QR absent, vide, de plus de 1 024 caractères, mal formé ou falsifié, billet inconnu, données signées incohérentes avec la base | **aucune** : un objet unique, identique quelle qu'en soit la cause |

  Les quatre premières issues supposent une signature authentique, que seul le backend produit : les détailler n'aide pas à fabriquer un faux QR.
- **Ordre des contrôles** :
  1. taille du QR, **avant toute cryptographie** (`TAILLE_MAX_QR = 1024` ; un QR fait environ 230 caractères) ;
  2. vérification de la signature (`QRSigner.verify`) ;
  3. lecture du billet (`TicketRepository.findById`, ajouté au port) ;
  4. **cohérence QR ↔ base** : `eventId`, et date d'émission comparée **après `truncatedTo(MILLIS)`**, car le QR signe `iat` à la milliseconde et la base garde la microseconde ;
  5. événement contrôlé ;
  6. scan, par le domaine.

  La cohérence est testée **avant** l'événement, pour qu'un QR incohérent reste `INVALIDE` au lieu de devenir `AUTRE_EVENEMENT`. L'événement est testé **avant** le scan : un agent de l'événement B ne peut ni consommer un billet de A, ni lire son statut.
- **La règle du scan reste dans le domaine** (`Ticket.marquerCommeScanne()`). Le cas d'usage attrape ses deux refus, levés avant toute écriture (la transaction n'est donc pas abîmée), et les traduit en `DEJA_SCANNE` et `NON_VALIDE`.
- **Refus du domaine devenus conflits métier (409)** :
  - `TicketAlreadyScannedException` étend `BusinessConflictException`, avec les accesseurs `getTicketId()` et `getPremierScanLe()` ;
  - nouvelle **`BilletNonValideException`** (accesseur `getStatus()`) remplace l'`IllegalStateException` (500) pour un billet `ANNULE` ou `REMBOURSE` ;
  - **messages génériques** (« Billet déjà scanné », « Billet non valable pour l'entrée ») : le handler 409 renvoie le message au client, donc aucune donnée du billet n'y figure. Les données passent par les accesseurs.
- **Double scan simultané** : rejeté par le `@Version` des billets, c'est-à-dire un 409 (`OptimisticLockingFailureException`) à la validation. Il ne peut pas devenir `DEJA_SCANNE` dans la transaction. Le contrat de la route impose de rejouer une fois (section 7).
- **Journalisation** : `LOG.warn` pour un QR authentique d'un billet inconnu ; `LOG.error` pour un QR authentique **incohérent avec la base** (clé compromise ou défaut d'émission : à investiguer). Le contenu du QR, qui est le titre d'accès, n'est jamais journalisé : seul l'UUID du billet l'est.
- **`eventIdDuControle` est un paramètre obligatoire** : sa provenance est le contrat de la future route (section 7).

**Vérifié** :
- 156 tests, 0 échec, 0 ignoré ;
- **6 tests avec le vrai Ed25519** (paire de clés générée, sans Spring) :
  - un QR réellement signé à l'émission, avec `emisLe` à la microseconde, est accepté ;
  - une signature modifiée d'un seul caractère donne `INVALIDE` ;
  - l'identifiant d'un autre billet recopié dans le contenu signé donne `INVALIDE` ;
  - une autre clé donne `INVALIDE` ;
  - un contenu quelconque donne `INVALIDE` ;
  - le même QR présenté deux fois donne `ACCEPTE`, puis `DEJA_SCANNE` ;
- 11 tests unitaires, un par issue, dont le piège de la milliseconde (`.123456` en base, `.123` dans le QR) ;
- **8 scans simultanés du même billet** sur PostgreSQL réel : **un seul `ACCEPTE`**, les autres en `DEJA_SCANNE` ou en conflit de version, une seule écriture validée (version 1). Une sonde (retirée) a confirmé la concurrence réelle sur 3 passes (2, 7 et 3 conflits de version) ;
- relecture `archi-guardian` (conforme) et `security-reviewer` (aucune faille exploitable ; deux corrections de contrat appliquées).

**Non vérifié** : la route HTTP, le démarrage réel avec ce nouveau bean, l'intégration avec la PWA scanner hors ligne.

### 4.11 ADR 0005 et test du contexte complet (merges locaux `1c73296` et `2f126a9`, 29/10/2026)

**ADR 0005** (`docs/architecture/adr/0005-communication-entre-modules.md`) : **un port public pour lire, un événement applicatif dans la même transaction pour réagir.** Il met fin au désaccord entre le texte de l'ADR 0001 et la règle ArchUnit `modules_sont_independants`. **Décisions (à respecter)** :

- **Lire l'état d'un autre module** : uniquement par une **interface** placée dans `com.billetplus.<module>.api.publique`, implémentée à l'intérieur du module. Premier port : `events.api.publique.StatutEvenements.estOuvertALaVente(eventId)` (vrai seulement si l'événement existe et est `PUBLIE`), implémenté par `events.application.StatutEvenementsService`.
- **Réagir à un changement** : un *record* dans `shared.evenements`, publié par `ApplicationEventPublisher` et écouté par `@EventListener` **synchrone**, donc **dans la transaction de l'émetteur**. Premier événement : `EvenementAnnule(eventId)`, publié par `EventService.annuler` après l'enregistrement. Si un écouteur échoue, l'action de l'émetteur est annulée avec lui. Pas de `@TransactionalEventListener(AFTER_COMMIT)` ni d'*outbox* au MVP (raison et limite dans l'ADR).
- **ArchUnit, 7 règles** :
  - `modules_sont_independants` ignore désormais `..shared..` **et** `..api.publique..`, rien d'autre ;
  - nouvelle règle `ports_publics_ne_contiennent_que_des_interfaces` ;
  - nouvelle règle `evenements_applicatifs_sont_des_records` (`shared.evenements`).

  Les trois ont été testées à l'envers par des classes sondes : une classe concrète dans `api.publique`, une classe avec logique dans `shared.evenements`, et **un accès de `tickets` à `events.domain.Event`**. Chacune a bien échoué.
- **Module `tickets`** :
  - `CancelEventTicketsUseCase` passe à `ANNULE` tous les billets `EMIS` d'un événement. Il ne touche pas aux billets `SCANNE` (porteur déjà entré), ni aux `ANNULE` et `REMBOURSE`, ce qui le rend idempotent. Il est déclenché par `tickets.infrastructure.evenements.EvenementAnnuleListener` ;
  - nouveau `TicketRepository.findByEventIdAndStatus`, servi par l'index V4 `idx_tickets_event_id` ;
  - **`ReserveSeatsUseCase`** : un événement non ouvert à la vente (brouillon, annulé, terminé) donne **exactement** la même réponse qu'une catégorie inconnue (même `NoSuchElementException`, même message). Un brouillon reste indiscernable ;
  - **`IssueTicketUseCase`** : un événement fermé à la vente lève **`VenteFermeeException`** (409, message générique), avant toute écriture. Pour `orders`, c'est un cas « payé sans billet », donc à rembourser ;
  - au scan, un billet d'un événement annulé est désormais `ANNULE`, donc refusé avec l'issue `NON_VALIDE`.

**Premier test du contexte complet** : `com.billetplus.ContexteCompletIntegrationTest` (`@SpringBootTest`, PostGIS et Redis lancés par Testcontainers). Seul `QRSigner` est simulé (`@MockBean`), parce que la clé privée est absente en CI. Il vérifie :
- que tous les cas d'usage se câblent dans le contexte complet ;
- que le port public suit le statut d'un événement, du brouillon à l'annulation ;
- qu'une réservation sur un brouillon répond 404, sur un vrai Redis ;
- qu'annuler un événement annule ses 3 billets et ferme la vente ;
- **l'atomicité** : une panne simulée pendant l'annulation des billets (`@SpyBean` sur `CancelEventTicketsUseCase`) laisse l'événement `PUBLIE` et les billets `EMIS`.

Sa sortie a aussi confirmé la disparition des lignes `Could not safely identify store assignment` (effet de la PR #24).

**Vérifié** :
- `./mvnw clean verify` : 177 tests, 0 ignoré, et le contrôle « Refuser les tests ignorés » passe ;
- CI de `main` verte après le merge de l'ADR 0005 (selon SirrDev) ;
- relecture faite par Claude Code, sans agents relecteurs.

**Non vérifié** : la CI après le merge du test du contexte complet ; une annulation portant sur un très grand nombre de billets (durée de la transaction).

### 4.12 Catégories, comptes `AGENT`, route de scan (merges locaux `232d58e`, `24dc753`, `fa562bc`)

**Catégories de billets** (`feature/backend-tickets-category-management`). Sans elles, rien ne pouvait être vendu.
- `POST /api/events/{id}/categories` (`ORGANISATEUR`), corps `{nom, prixFcfa, quotaTotal}`. Bornes du DTO alignées sur V4 : `@Size(max = 255)`, prix de 0 à 1 000 000 000, quota de 1 à 1 000 000.
  - Un non-propriétaire et un événement inconnu reçoivent **le même 404** (« Événement introuvable »).
  - Un événement annulé ou terminé répond 409 (`VenteFermeeException`).
- `GET /api/events/{id}/categories` (public, sensible à l'identité). **Un acheteur voit le nom, le prix et `disponible`, jamais le stock** (`quotaTotal` et `quotaVendu` sont absents du JSON). L'organisateur les voit. Un brouillon n'est visible que de son organisateur, tous les autres reçoivent 404.
  - Limite : `disponible` ignore les réservations Redis en cours. Une catégorie peut s'afficher disponible puis refuser la réservation (409), jamais l'inverse.
- Cas d'usage `CreateTicketCategoryUseCase` et `ListTicketCategoriesUseCase`, contrôleur `tickets.api.TicketCategoryController` (premier package `api` du module `tickets`).
- Le port public `StatutEvenements` gagne `estOrganisateur(eventId, userId)` et `accepteDesCategories(eventId)` (brouillon ou publié). Que des booléens, puisque la règle 6 interdit tout type non-interface dans `api.publique`.

**Comptes `AGENT` et affectation** (`feature/backend-auth-agent-accounts`).
- **V6** : `users.cree_par` (FK vers `users`, même module), contrainte `chk_users_agent_a_un_createur` (un `AGENT` a toujours un créateur).
- **V7** : `event_agents(event_id, agent_id, affecte_le)`, avec une FK vers `events` (même module) et **aucune FK vers `users`** (module `auth`).
- `POST /api/agents` et `GET /api/agents` (`ORGANISATEUR`) :
  - service `AgentAccountService`, séparé d'`AuthService` : **aucune méthode publique d'`AuthService` n'accepte un `Role`**, règle conservée ;
  - le rôle est fixé par `User.agentCreePar(...)`, et un `"role"` envoyé dans le corps est ignoré (vérifié en base par un test HTTP) ;
  - le rôle d'organisateur est vérifié **en base**, en plus du `@PreAuthorize` ;
  - le hash du mot de passe n'est jamais renvoyé ;
  - l'agent se connecte ensuite par `/api/auth/login`.
- `PUT` et `DELETE /api/events/{id}/agents/{agentId}` (`ORGANISATEUR`), idempotents :
  - seulement **ses propres** agents : l'agent d'un autre organisateur, ou un identifiant inconnu, répond 404 ;
  - un non-propriétaire de l'événement reçoit 403, comme pour `publier` et `annuler` ;
  - un événement annulé ou terminé répond 409.
- Ports publics : `auth.api.publique.Comptes.estAgentCreePar(agentId, organisateurId)` (lu par `events`), et `StatutEvenements.estAgentAffecte(eventId, agentId)` (lu par la route de scan).
- **Premier `ADMIN`, hors API** (`auth.infrastructure.PremierAdminInitializer`, un `ApplicationRunner`) :
  - variables `BILLETPLUS_ADMIN_EMAIL` et `BILLETPLUS_ADMIN_MOT_DE_PASSE`, déclarées sans valeur dans `application.yml` ;
  - sans elles, rien n'est fait (dev, CI). Dès qu'un `ADMIN` existe, rien n'est fait non plus ;
  - configuration invalide (une seule variable, mot de passe hors 8–72, email déjà pris par un compte non `ADMIN`) : **le démarrage échoue** ;
  - le mot de passe n'est jamais journalisé.

**Route de scan** (`feature/backend-tickets-scan-route`).
- `POST /api/events/{eventId}/scans` (`AGENT` ou `ORGANISATEUR`), corps `{qrContent}` borné à 1 024 caractères (400 au-delà).
  - **Toutes les issues sont des 200** avec le `ResultatScan` (`issue`, et selon l'issue `ticketId`, `premierScanLe`, `statut`), sans aucune donnée de commande ni d'acheteur. `INVALIDE` ne porte que son nom.
- `ScanTicketUseCase`, volontairement **sans** `@Transactional` :
  - **autorisation d'abord** : l'utilisateur du jeton doit être affecté à l'événement de l'URL, ou en être l'organisateur. Sinon 403 **avant toute lecture du QR** : aucun billet consommé, aucune information. Le contrat de `ValidateTicketScanUseCase` est mis à jour en ce sens : l'événement de l'URL est permis (un agent peut contrôler plusieurs événements), mais seulement s'il y est affecté ;
  - **un conflit de version est rejoué une fois**, dans une nouvelle transaction. Le second essai lit le billet `SCANNE` et renvoie `DEJA_SCANNE`. L'agent ne voit jamais « erreur technique ». Il n'y a qu'un seul rejeu, donc jamais de boucle.

**Vérifié** :
- `./mvnw clean verify` : 234 tests, 0 ignoré ;
- tests HTTP : 401, 403 (rôle, non-propriétaire, agent non affecté), 400 (bornes), 404 (même message qu'un inconnu), 409 ;
- **8 scans simultanés du même billet par le vrai chemin** : 1 `ACCEPTE`, 7 `DEJA_SCANNE`, **aucune erreur**, une seule écriture. La sonde (retirée) a observé 7 puis 5 rejeux réels sur deux passes ;
- relecture faite par Claude Code, **sans agents relecteurs**.

**Non vérifié** :
- la CI après le merge de la route de scan ;
- un `spring-boot:run` sur la base de dev, qui passera en **V7** au prochain démarrage.

### 4.13 Tests HTTP des routes anciennes et visibilité pour un non-propriétaire (branche n° 4)

- **Décision : un non-propriétaire reçoit 404 sur le brouillon d'un autre** (même message qu'un identifiant inconnu), et **403 sur un événement visible** (publié, annulé, terminé), dont l'existence est publique. Avant, il recevait toujours 403, ce qui révélait l'existence d'un brouillon. La règle est unique : `EventService.verifierProprietaire` applique `Event.estVisiblePar`, et `EventService.getPourSonOrganisateur(eventId, userId)` la rend réutilisable. `publier`, `annuler` et l'affectation d'agents (`AffectationAgentService`) l'utilisent.
- **`ContexteCompletTestBase`** (classe de base des tests du contexte complet) :
  - **conteneurs singletons**, démarrés une fois par lancement dans un bloc statique, et non par `@Container`. Avec `@Container`, chaque classe arrêterait ses conteneurs alors que Spring réutilise le contexte mis en cache ;
  - les classes filles déclarent exactement les mêmes `@MockBean` (`QRSigner`) et `@SpyBean` (`CancelEventTicketsUseCase`), ceux de la base, et aucun autre : **un seul contexte Spring** pour toutes. Vérifié : un seul `Started … in 25 s`, et la seconde classe s'exécute en 4 s ;
  - **toute nouvelle classe de tests du contexte complet doit en hériter, sans ajouter de `@MockBean`**, sinon Spring démarre un second contexte.
- **`RoutesExistantesHttpIntegrationTest`** (12 tests, avec de **vrais jetons JWT** obtenus par `/api/auth/login`, donc `JwtAuthenticationFilter` est testé) :
  - inscription, connexion et `/me` ; le champ `role` ignoré à l'inscription ; bornes 400 (email, mot de passe 8–72) ; email déjà pris ; 401 (mauvais mot de passe, sans jeton, jeton inventé) ; rafraîchissement ;
  - visibilité d'un brouillon (404 pour l'anonyme et pour un autre organisateur, 200 pour son organisateur) ; 404 puis 403 pour un non-propriétaire ; 409 sur une double publication ; `/mine` ; création (401, 400, lieu inconnu 404) ;
  - lieux (création, lecture publique, latitude hors bornes) ; `/error` public (404 et non 403 pour un anonyme).

Les scénarios manuels de la section 6 restent valables, mais ils ne sont plus la seule vérification de ces règles.

---

## 5. Workflow de livraison d'une branche (procédure rodée)

Chaque bloc est **autonome** : chemins complets, **aucune variable définie dans un bloc précédent** (une variable `$main` définie plus haut n'existe pas dans une nouvelle session PowerShell et donne `Invalid path '/auth'`). Depuis `backend/` :

```powershell
# 1. Tests : tout doit être vert avant de commiter
.\mvnw test
.\mvnw test "-Dtest=ArchitectureTest"

# 2. Repartir d'un index propre (ne touche pas aux fichiers)
git restore --staged .

# 3. Un « git add » par chemin explicite — JAMAIS « git add . » (un fichier local étranger à la branche partirait avec)
git add src/main/java/com/billetplus/.../Fichier.java
git diff --cached --stat        # vérifier la liste exacte avant de commiter

# 4. Commit (Conventional Commits), puis contrôle
git commit -m "feat(contexte): description"
git log --oneline -3

# 5. Push, PR, attente des workflows verts, merge
git push -u origin HEAD
```

`git push -u origin HEAD` pousse la branche courante sans retaper son nom : le 29/10/2026, un nom de branche coupé au collage (`feature/backend-tickets-jpa-`) a donné `src refspec ... does not match any`. Depuis la PR #18, une PR qui touche `backend/` déclenche « Backend CI » et « Architecture rules » (section 4.7) : attendre ces deux statuts verts avant de merger.

Créer la branche depuis un `main` à jour : `git switch main`, `git pull`, puis `git switch -c <branche>`.

Après le merge sur GitHub :

```powershell
git switch main
git pull
git branch -d feature/<contexte>-<description>
```

Puis **« Sync now »** dans le projet Claude. Les fichiers ajoutés manuellement au projet (HANDOFF, ADR, `.docx`) se remplacent par suppression + réimport.

---

## 6. Scénarios de vérification HTTP

Les deux scénarios s'exécutent dans un **second terminal PowerShell**, l'application tournant dans le premier. **Prérequis** : Docker démarré (section 8) et application lancée sur le **port 8081** : `$env:SERVER_PORT = "8081"` puis `.\mvnw spring-boot:run`, car le port 8080 est souvent occupé en local par `footbooking-api` (section 8). Les access tokens expirent après **15 min** ; éviter les accents dans les corps JSON (PowerShell 5.1 les encode mal).

⚠️ **Toujours vérifier que c'est bien Billet+ qui répond** avant d'interpréter un résultat : un test lancé sur le mauvais serveur donne des 400/404 trompeurs (voir leçon 16, section 10). Le scénario 6.2 intègre ce contrôle.

⚠️ **PSReadLine peut planter au collage d'un long bloc** (« Oups, un problème s'est produit ») : enregistrer le script dans un fichier `.ps1` et l'exécuter, ou l'envelopper dans une fonction `function Test-Xxx { ... }` puis appeler `Test-Xxx` (voir leçon 17).

### 6.1 Visibilité, propriété, conflit, validation (validé le 19/10/2026 sur le port 8080 ; remplacer par 8081 si 8080 est pris)

```powershell
$base = "http://localhost:8081"

# Invoke-WebRequest lève une exception sur 4xx/5xx : on récupère le code HTTP dans le catch.
function Get-Status($method, $uri, $headers = @{}, $body = $null) {
  try {
    $r = Invoke-WebRequest -Method $method -Uri $uri -Headers $headers -Body $body `
         -ContentType "application/json" -UseBasicParsing
    return [int]$r.StatusCode
  } catch {
    return [int]$_.Exception.Response.StatusCode
  }
}

# Crée un compte puis renvoie son access token (valable 15 minutes).
# Depuis le correctif /register, tout compte créé ainsi est un ORGANISATEUR.
function New-Account($email, $nom) {
  $body = @{ email = $email; motDePasse = "MotDePasse123"; nomComplet = $nom } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -Body $body | Out-Null
  $login = @{ email = $email; motDePasse = "MotDePasse123" } | ConvertTo-Json
  (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $login).accessToken
}

# Suffixe aléatoire : évite le conflit d'email si le scénario est relancé.
$s = Get-Random
$hOrga  = @{ Authorization = "Bearer " + (New-Account "orga$s@test.local"  "Orga Un") }
$hOrga2 = @{ Authorization = "Bearer " + (New-Account "orga2$s@test.local" "Orga Deux") }

$venue = Invoke-RestMethod -Method Post -Uri "$base/api/venues" -Headers $hOrga -ContentType "application/json" `
  -Body (@{ nom = "Grand Theatre"; adresse = "Dakar"; latitude = 14.69; longitude = -17.44 } | ConvertTo-Json)
$ev = Invoke-RestMethod -Method Post -Uri "$base/api/events" -Headers $hOrga -ContentType "application/json" `
  -Body (@{ titre = "Concert test"; description = "Brouillon de test"; debuteLe = "2027-01-15T20:00:00Z"; termineLe = "2027-01-15T23:00:00Z"; venueId = $venue.id } | ConvertTo-Json)
$id = $ev.id

"--- Visibilité, propriété, conflit ---"
"statut initial           : " + $ev.status
"anonyme lit le brouillon : " + (Get-Status GET  "$base/api/events/$id")
"orga 2  lit le brouillon : " + (Get-Status GET  "$base/api/events/$id" $hOrga2)
"orga 1  lit le brouillon : " + (Get-Status GET  "$base/api/events/$id" $hOrga)
"orga 1  liste /mine      : " + (Get-Status GET  "$base/api/events/mine" $hOrga)
"orga 2  publie           : " + (Get-Status POST "$base/api/events/$id/publier" $hOrga2)
"orga 1  publie           : " + (Get-Status POST "$base/api/events/$id/publier" $hOrga)
"orga 1  republie         : " + (Get-Status POST "$base/api/events/$id/publier" $hOrga)
"anonyme lit le publié    : " + (Get-Status GET  "$base/api/events/$id")

"--- Validation des entrées ---"
$bodyLat = @{ nom = "Lieu test"; adresse = "Dakar"; latitude = 200; longitude = 0 } | ConvertTo-Json
"latitude hors bornes     : " + (Get-Status POST "$base/api/venues" $hOrga $bodyLat)
$bodyNom = @{ nom = ("x" * 300); adresse = "Dakar" } | ConvertTo-Json
"nom de 300 caracteres    : " + (Get-Status POST "$base/api/venues" $hOrga $bodyNom)
```

| Ligne | Résultat obtenu le 19/10 |
|---|---|
| anonyme lit le brouillon | **404** |
| orga 2 lit le brouillon | **404** |
| orga 1 lit le brouillon | 200 |
| orga 1 liste /mine | 200 |
| orga 2 publie | 403 |
| orga 1 publie | 200 |
| orga 1 republie | **409** |
| anonyme lit le publié | 200 |
| latitude hors bornes | **400** |
| nom de 300 caractères | **400** |

Autres vérifications faites le 12/10 (avant le correctif `/register`, avec des comptes `AGENT` créés par l'ancien `register`) : `GET /api/events` → 405 ; `GET /api/events/{id inconnu}` → 404 ; `GET /api/events/mine`, `POST /api/events`, `GET /api/auth/me` sans token → 401 ; `/api-docs` → 200 ; agent `POST .../publier` (id bidon) → 403 ; agent `GET /mine` → 403. **Ces contrôles « agent » ne sont plus reproductibles par HTTP** (plus de route pour créer un agent) ; pour les rejouer en local, passer un compte en `AGENT` directement en base.

### 6.2 Inscription publique (validé le 20/10/2026 sur le port 8081)

Le script contrôle d'abord l'identité du serveur (seul Billet+ répond « Lieu introuvable » sur cette route) et s'arrête sinon.

```powershell
function Test-Inscription {
  $base = "http://localhost:8081"

  # Renvoie le code HTTP ET le corps, même en cas d'erreur 4xx/5xx.
  function Send-Request($method, $uri, $body = $null, $headers = @{}) {
    try {
      $r = Invoke-WebRequest -Method $method -Uri $uri -Headers $headers -Body $body `
           -ContentType "application/json" -UseBasicParsing
      return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
      $resp = $_.Exception.Response
      if ($null -eq $resp) { return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message } }
      $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
      return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $reader.ReadToEnd() }
    }
  }

  # 0. Contrôle d'identité : seul Billet+ répond "Lieu introuvable" sur cette route.
  $ident = Send-Request GET "$base/api/venues/00000000-0000-0000-0000-000000000000"
  if ($ident.Status -ne 404 -or $ident.Body -notmatch "Lieu introuvable") {
    "STOP : ce serveur n'est pas Billet+ (statut " + $ident.Status + ")."
    "Demarre Billet+ sur 8081 dans l'autre terminal, puis relance Test-Inscription."
    return
  }
  "identite du serveur       : Billet+ OK"

  $s = Get-Random
  $email = "tentative$s@test.local"

  # 1. On tente de s'auto-créer ADMIN en envoyant un champ « role ».
  $body = @{ email = $email; motDePasse = "MotDePasse123"; nomComplet = "Tentative Admin"; role = "ADMIN" } | ConvertTo-Json
  $reg = Send-Request POST "$base/api/auth/register" $body
  "register avec role=ADMIN  : " + $reg.Status + "  " + $reg.Body

  # 2. On se connecte, puis on lit le rôle réellement attribué.
  $login = Send-Request POST "$base/api/auth/login" (@{ email = $email; motDePasse = "MotDePasse123" } | ConvertTo-Json)
  "login                     : " + $login.Status
  $token = ($login.Body | ConvertFrom-Json).accessToken
  $me = Send-Request GET "$base/api/auth/me" $null @{ Authorization = "Bearer $token" }
  "me                        : " + $me.Status + "  " + $me.Body

  # 3. Un email de 300 caractères doit être refusé en 400 avec un message de validation.
  $long = ("x" * 289) + "@test.local"
  $bodyLong = @{ email = $long; motDePasse = "MotDePasse123"; nomComplet = "Long" } | ConvertTo-Json
  $tooLong = Send-Request POST "$base/api/auth/register" $bodyLong
  "email de 300 caracteres   : " + $tooLong.Status + "  " + $tooLong.Body
}

Test-Inscription
```

| Ligne | Résultat obtenu le 20/10 |
|---|---|
| identité du serveur | Billet+ OK |
| register avec `role = "ADMIN"` | **201**, corps vide |
| login | 200 |
| me | 200, `"role":"ORGANISATEUR"` |
| email de 300 caractères | **400**, message de validation sur la taille de l'email |

---

## 7. Points ouverts et risques connus

- ~~Aucune route ne peut créer un `AGENT` ni un `ADMIN`~~ : **résolu** (merge `24dc753`). Les agents sont créés par un organisateur (`POST /api/agents`), et le premier `ADMIN` hors API par variables d'environnement. Restent :
  - **le mot de passe d'un agent est choisi par son organisateur**, et aucune route ne permet encore de le changer (acceptable pour du personnel au MVP ; à prévoir) ;
  - l'incohérence `ADMIN` (exclu des opérations sur les événements, accepté pour les lieux), toujours à trancher avec le back-office (WON'T V1).
- **Incohérence ADMIN** : `@PreAuthorize("hasRole('ORGANISATEUR')")` **exclut ADMIN** des opérations sur les événements, alors que `POST /api/venues` accepte `ORGANISATEUR` **ou** `ADMIN`. Devenue théorique (personne ne peut devenir ADMIN) ; à trancher avec le back-office (WON'T V1).
- **Pas de liste publique d'événements ni de recherche** (voir section 4.1).
- **Lieux** : tout organisateur peut créer des lieux globaux, sans propriétaire ni détection de doublon (acceptable au MVP). `VenueDto.seatingPlan` est une `String` sérialisée telle quelle (aucun endpoint ne la définit encore) : à revoir quand le plan de salle sera construit.
- **`EventDto.organizerId`** est exposé publiquement par `GET /api/events/{id}` : identifiant interne du module `auth`, non secret mais à retirer si le front n'en a pas besoin.
- **Énumération d'emails** : `POST /api/auth/register` répond 400 « Un compte existe déjà avec cet email », ce qui permet de tester l'existence d'un compte. Risque faible au MVP ; à revoir avec l'ouverture de l'inscription aux acheteurs (V2) et le rate limiting (branche 17).
- ~~Tests HTTP partiels~~ : **résolu** par la branche n° 4. 31 tests HTTP couvrent désormais toutes les routes (section 4.13). Les scénarios manuels de la section 6 restent un complément.
- ~~Un autre organisateur reçoit 403, et non 404, sur l'événement brouillon de quelqu'un d'autre~~ : **résolu** (branche n° 4). 404 sur un brouillon, 403 sur un événement visible (section 4.13).
- `EventTest.refuse_de_publier_un_evenement_deja_publie` attend `IllegalStateException` : il passe (sous-classe) mais gagnerait à attendre `BusinessConflictException`.
- `Event` n'a aucun moyen d'atteindre `TERMINE` (pas de transition automatique ; job planifié prévu en V2). `annuler()` sur un événement déjà `ANNULE` repasse silencieusement à `ANNULE` : à décider (no-op explicite ou 409).
- `EventService` (niveau 2) lève `AccessDeniedException` de Spring Security : acceptable (comme `AuthService`), mais pour les modules niveau 3 prévoir une exception de domaine.
- Warning au démarrage « Using generated security password » : bruit inoffensif (`UserDetailsServiceAutoConfiguration`) ; peut être exclu plus tard.
- Le refresh token n'est pas révocable (limite assumée au MVP, documentée dans `AuthService`).
- `QuotaDepasseException` étend `BusinessConflictException` (409) depuis `feature/backend-tickets-jpa-mapper`, et `TicketAlreadyScannedException` depuis la PR #26.
- ~~Un événement annulé n'annule pas ses billets~~ : **résolu par l'ADR 0005** (merge `1c73296`). L'annulation passe les billets `EMIS` à `ANNULE` dans la même transaction, et le contrôle d'entrée les refuse. Limite restante : la durée de cette transaction croît avec le nombre de billets (à surveiller pour un très grand événement).
- **Route de scan livrée** (merge `fa562bc`) : le contrat de `ValidateTicketScanUseCase` est respecté par `ScanTicketUseCase` (section 4.12). Reste du contrat d'origine, à faire : **limitation de débit par compte agent** (branche `rate-limiting`).
- **Le QR est un jeton porteur, statique et permanent** (limite de conception, PR #26) : une copie (photo, capture d'écran, e-mail transféré) présentée **avant** le titulaire est acceptée, et le vrai titulaire reçoit `DEJA_SCANNE`. Le « premier arrivé gagne » protège la jauge, pas l'acheteur. Pistes pour plus tard :
  - un code tournant de courte durée, ajouté au QR signé, dans l'application de l'acheteur ;
  - un contrôle d'identité nominatif pour les billets chers ;
  - ne jamais renvoyer le QR après le jour J.
- **En mode hors ligne** (ADR 0004), la PWA scanner vérifie la signature mais **ne détecte pas un double scan entre deux portes** : seul le serveur le fait. La synchronisation des scans faits hors ligne reste à concevoir.
- **Conflits de version massifs sous forte demande** (constat de la PR #20, section 4.8) : 8 achats simultanés sur une catégorie donnent 1 succès et 7 conflits. **À trancher avant l'ouverture de la billetterie de l'événement pilote**, avec deux leviers :
  - une nouvelle tentative bornée, avec délai aléatoire croissant, à la frontière du webhook (`orders`) ;
  - la requête SQL atomique prévue par l'ADR 0003 (ligne 101 : « à activer si le volume de conflits observé le justifie »). Le test est synthétique : idéalement, la décider sur une mesure de charge réaliste.
- ~~Contrôle « événement `PUBLIE` » bloqué par un désaccord entre l'ADR 0001 et ArchUnit~~ : **tranché par l'ADR 0005** (port public `api.publique` pour lire, événement applicatif pour réagir). Pour `orders`, la même voie est disponible : lire par `StatutEvenements`, et ajouter si besoin un port public au module `tickets`.
- **Précision des dates du QR** : le QR signe `emisLe` à la **milliseconde** (`Ed25519QRSigner`, champ `iat`), alors que `Ticket` le garde à la microseconde. Toute comparaison entre un QR vérifié et la base doit se faire après `truncatedTo(ChronoUnit.MILLIS)` (branche 11).
- **Contrat de `orders` envers `IssueTicketUseCase`** (relectures des PR #16 et #20, Javadoc d'`execute`) :
  - **Provenance** : catégorie, commande et quantité lues dans la commande en base, jamais dans la notification Wave. Signature et anti-rejeu Wave vérifiés avant l'appel. Commande payée, montant recalculé côté serveur. Aucune route publique ni organisateur ne mène à ce cas d'usage. Un appel avec une mauvaise quantité occupe le couple (commande, catégorie) et **bloque définitivement** la vraie émission.
  - **Idempotence à la frontière** : `BilletsDejaEmisException` (comme tout conflit de version) annule la transaction englobante. Un `catch` dans la transaction de paiement donnerait `UnexpectedRollbackException`, donc un 500. L'hypothèse n'a pas été testée, mais elle a été déduite par deux relecteurs. L'idempotence du webhook (« déjà traité » → 200) se vérifie donc **avant** d'ouvrir la transaction d'émission, par exemple avec une table des notifications Wave déjà traitées, ou à partir du statut de la commande.
  - **Réponses à Wave** : 200 (traité ou déjà traité) ou 5xx (conflit passager, à relancer), **jamais 409**. Beaucoup de prestataires ne relancent pas sur une réponse 4xx. Politique de relance de Wave à lire dans sa documentation avant d'écrire le webhook.
  - **Quota garanti avant le paiement** : une `QuotaDepasseException` après paiement donne un client débité sans billet. C'est l'objet de la branche 10 (`tickets-redis-seat-lock`, PR #22, section 4.9) : `orders` appelle `ReserveSeatsUseCase` avant d'ouvrir le paiement. Ne jamais écrire dans `ticket_categories` pour une commande non payée (sinon un flot de commandes non payées affame les émissions). Limiter le débit de création de commandes (par compte et par IP) et le nombre de commandes en attente par acheteur.
  - **Plusieurs catégories dans une commande** : les émettre dans l'ordre croissant de `categoryId` (sinon, risque d'interblocage entre deux confirmations simultanées).
  - **Délai d'attente** : poser un `lock_timeout` (ou `statement_timeout`) sur la transaction du webhook. Un `INSERT` concurrent attend sans limite tant que la première transaction est ouverte.
  - **Plafond** : 20 places par commande et par catégorie (`QuantiteParCommande.MAX`, règle du domaine), à appliquer aussi à la création de commande.
  - **Réservation temporaire** (PR #22, Javadoc de `ReserveSeatsUseCase` et `ReleaseSeatsUseCase`) :
    - **session de paiement Wave strictement plus courte que la réservation** (5 min), avec une marge pour la latence du webhook, et paiement refusé si la réservation a expiré. Sinon, un paiement lent laisse expirer la réservation, un autre acheteur prend les places, et le premier est débité sans billet. C'est bien plus probable que la fenêtre de quelques microsecondes du risque résiduel ;
    - `orderId` généré par le serveur, rattaché à l'acheteur du jeton et persisté avant la réservation, jamais lu dans la requête. Plafond de commandes en attente par compte et par événement, et limitation de débit par compte et par IP. Sans eux, des réservations non payées épuisent une catégorie ;
    - événement vérifié publié et en vente avant de réserver, sinon le même 404 qu'un identifiant inconnu (dépend de la décision ADR 0001 contre ArchUnit) ;
    - **`ReleaseSeatsUseCase` ne vérifie aucune propriété** : aucune route ne prend `orderId` ni `categoryId` du client pour libérer. Libération seulement sur un échec ou une annulation **définitifs** confirmés par Wave, ou sur une annulation par l'acheteur authentifié. Jamais sur un statut Wave ambigu ;
    - remboursement nécessaire pour le cas rare « payé sans billet » (risque résiduel, section 4.9).
- Messages 409/404 du module tickets : contiennent des UUID et le quota restant. À rendre génériques si un contrôleur acheteur les relaie.
- ~~`IssueTicketUseCase` ne vérifie pas que l'événement est `PUBLIE`~~ : **résolu** (`VenteFermeeException`, 409), comme `ReserveSeatsUseCase` (404 identique à une catégorie inconnue).
- Une seconde émission refusée avec une **quantité différente** de la première mériterait d'être journalisée comme anomalie : elle signale un appelant défaillant.
- **Coût d'une émission** : chaque `save()` d'un billet neuf fait un `SELECT` puis un `INSERT` (relecture de l'entité gérée), soit 40 requêtes pour 20 billets. Acceptable au MVP ; piste si besoin : une méthode de port dédiée à la création, ou un `saveAll`.
- **Redis à durcir en production** (relecture sécurité de la PR #22) : Redis porte désormais un état critique pour la vente (les réservations). Il faudra un mot de passe ou des ACL, un réseau privé, et **`maxmemory-policy noeviction`** : une politique LRU supprimerait des réservations sans prévenir, ce qui mènerait au « payé sans billet ». *(Le volet dev est fait : PR #24 publie Redis et PostgreSQL sur `127.0.0.1` seulement.)*
- **Mot de passe PostgreSQL de dev commité** (`billetplus_dev`, dans `infra/docker-compose.yml` et `application.yml`) : acceptable en dev depuis que le port n'est publié que sur `127.0.0.1` (PR #24). En production, il devra venir d'une variable d'environnement, comme `JWT_SECRET`.
- **Démarrage du contexte complet confirmé** le 29/10/2026 par `ContexteCompletIntegrationTest` : tous les beans se câblent (dont `ValidateTicketScanUseCase`, `CancelEventTicketsUseCase` et `StatutEvenements`), et la sortie ne contient plus aucune ligne `Could not safely identify store assignment` ni `Bootstrapping Spring Data Redis repositories`. Un `spring-boot:run` sur la base de dev n'a pas été relancé depuis la PR #22.
- Une `RedisSystemException` (erreur de script, mémoire pleine) n'est pas une `DataAccessResourceFailureException` : elle finit en 500, pas en 503.
- En multi-instances, les horloges doivent être synchronisées (NTP) : une instance en avance retire trop tôt des réservations encore valides.
- `TicketCategory.libererQuota` plafonne silencieusement `quotaVendu` à 0 au lieu de signaler une sur-libération : à décider avec le cas d'usage d'annulation.
- Plusieurs Javadoc du module `tickets` citent des noms de branches (`feature/backend-tickets-quota-lock`…), qui vieilliront : préférer un renvoi vers un ADR lors des prochaines retouches.

---

## 8. Environnement et commandes qui fonctionnent (Windows / PowerShell)

- JDK 21 (21.0.11), Maven wrapper, Docker Desktop, Node 20. Dépôt local : `C:\Users\bague\Desktop\billetplus`.
- **Démarrer l'infra depuis la racine du dépôt** : `docker compose -f infra/docker-compose.yml up -d`, puis attendre `healthy` : `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"` (juste après le démarrage, le statut est `health: starting`).
- **Conflit de port 8080** : le conteneur `footbooking-api` (autre projet de SirrDev) publie `0.0.0.0:8080->8080` et occupe donc le port 8080 de Windows (visible via `Get-NetTCPConnection -LocalPort 8080 -State Listen` : processus `com.docker.backend` et `wslrelay`, pas `java`). Ne pas l'arrêter : lancer Billet+ sur 8081 avec `$env:SERVER_PORT = "8081"` (variable valable pour le terminal courant, `application.yml` n'est pas modifié), puis `.\mvnw spring-boot:run`. `footbooking-db` utilise le port 5433 et ne gêne pas Billet+ (5432).
- Lancer le backend depuis `backend/` : `.\mvnw spring-boot:run`. Démarrage OK : PostgreSQL 16.4, Flyway applique V1–V3, `ddl-auto: validate` passe, Tomcat démarré. **Sans Docker démarré, l'application échoue avec `Connection to localhost:5432 refused`** (erreur sur `flywayInitializer`) — ce n'est pas un défaut du code ; les tests unitaires, eux, restent verts sans base.
- Arrêter l'infra sans perdre les données : `docker compose -f infra/docker-compose.yml stop`.
- Tests : `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"` (guillemets obligatoires). Résultat de référence au 30/10/2026 : 234 tests, 0 échec, 0 ignoré (Docker démarré ; sans Docker, les 66 tests d'intégration sont ignorés). Le test du contexte complet prend environ 40 s, dont 25 s de démarrage des conteneurs et du contexte. Les tests d'intégration prennent environ 25 s (PostGIS) et 7 s (Redis). **Démarrage réel confirmé le 29/10/2026 au soir** après la PR #22 : `Started BilletPlusApplication` sur 8081, Flyway « 5 migrations validées, schéma à jour », `ddl-auto: validate` sans erreur. Ce démarrage n'ouvre pas de connexion Redis : elle est ouverte au premier appel. Après la suppression d'une classe, utiliser `.\mvnw clean test` : ArchUnit lit les `.class` de `target/`, et un `.class` orphelin fausserait le résultat.
- Clé Ed25519 de dev : `src/main/resources/keys/ed25519_private_dev.pem` (gitignorée, déjà générée sur la machine de SirrDev) ; régénération : `bash ..\infra\scripts\generate-ed25519-keys.sh`.
- Surcharger l'URL de la base pour une session sans toucher à `application.yml` : `$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/billetplus"` — **attention : le port 5433 est celui de `footbooking-db`, pas de Billet+** ; à n'utiliser que si le port 5432 de Billet+ a été remappé. Activer les logs de sécurité : `$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY = "DEBUG"` (puis `Remove-Item Env:...`).
- Le `git status` doit être lu depuis `backend/` ; les chemins de `docker-compose.yml` apparaissent en `../infra/`.

---

## 9. Contraintes de collaboration (à respecter impérativement)

- **Langue** : toujours en français. **Qualité** : sorties complètes, production-ready, sans omission. **Pédagogie** : expliquer le *pourquoi* des commandes.
- **Windows / PowerShell uniquement** : jamais de `\` de continuation ni de `$(...)` bash. Backtick `` ` `` pour continuer une ligne. `curl` est un alias d'`Invoke-WebRequest` : utiliser `Invoke-RestMethod`.
- **Chaque bloc de commandes doit être autonome** : chemins complets, aucune variable définie dans un message précédent (SirrDev lance souvent les commandes dans une nouvelle session).
- **Indiquer précisément où copier chaque fichier** : tableau « fichier → dossier complet depuis la racine du dépôt → nouveau ou remplace l'existant », avec le chemin racine réel (`C:\Users\bague\Desktop\billetplus`) et la méthode glisser-déposer dans IntelliJ en alternative à `Copy-Item`.
- **Comment demander du code à SirrDev** : la méthode `... | Set-Clipboard` a prêté à confusion (le presse-papiers est écrasé par la commande suivante). **Préférer** : « ouvre le fichier dans IntelliJ, `Ctrl+A`, `Ctrl+C`, colle-le dans ton message », un fichier à la fois si besoin. Si un log est demandé, demander uniquement les lignes `Caused by:` / `APPLICATION FAILED`.
- **Tests HTTP** : donner des scripts qui **vérifient l'identité du serveur** avant de tester (port 8080 occupé par FootBooking) et qui affichent le corps des réponses d'erreur, pas seulement le code. Les envelopper dans une fonction pour qu'un `return` puisse les arrêter.
- Ne jamais demander de copier des secrets réels (JWT secret de prod, mots de passe).
- **Convention de branches** : `feature/<contexte>-<description>`, `fix/<contexte>-<description>`, créées juste avant usage. **Conventional Commits** pour les messages.
- **ArchUnit** : 7 règles actives (5ᵉ depuis `feature/backend-tickets-jpa-mapper` : domaines de niveau 3 sans JPA, Hibernate ni Spring ; 6ᵉ et 7ᵉ depuis l'ADR 0005 : `api.publique` ne contient que des interfaces, `shared.evenements` que des records), aucun `allowEmptyShould`. Après tout nouveau module/package, vérifier qu'elles s'appliquent à au moins une classe.
- `.gitattributes` : LF partout sauf `mvnw.cmd` ; les warnings `LF will be replaced by CRLF` sont normaux.
- Les fichiers de projet Claude sont en lecture seule : toute correction se produit en fichier de sortie que SirrDev copie dans son dépôt, commite, puis **resynchronise (« Sync now »)**. Les fichiers non encore commités/mergés ne sont **pas visibles** de Claude : les lui coller dans le message.

---

## 10. Problèmes déjà rencontrés et résolus

1. **JDK** : Java 21 strict (`pom.xml`, `qodana.yaml`).
2. **ArchUnit** : `ignoreDependency(...)` après `.should().notDependOnEachOther()` ; pas de `allowEmptyShould`.
3. **Image Docker** : `postgres:16` n'a pas PostGIS → `postgis/postgis:16-3.4` ; après changement d'image, `docker compose -f infra/docker-compose.yml down -v`.
4. **`docker compose` depuis la racine**, pas depuis `backend/`.
5. **Mappage de port Docker** : `"HÔTE:CONTENEUR"` — PostgreSQL écoute toujours sur 5432 *dans* le conteneur. `5433:5433` est faux ; si 5432 était pris : `5433:5432`. Depuis la PR #24, la forme est `"127.0.0.1:HÔTE:CONTENEUR"` (par exemple `127.0.0.1:5432:5432`), pour ne publier le port que sur le poste.
6. **Échec de démarrage `Connection refused` sur Flyway** = base non démarrée (Docker éteint) : rencontré les 12/10 et 19/10, même cause. Les tests unitaires restent verts quand même.
7. **IDs générés en mémoire** (`UUID.randomUUID()`), jamais `@GeneratedValue` (`User`, `Ticket`, `TicketCategory`, `Event`, `Venue`).
8. **Spring Security 6 masque les erreurs en 403** si `/error` n'est pas `permitAll` ; et un anonyme reçoit 403 (pas 401) sans `AuthenticationEntryPoint`. Corrigé dans `SecurityConfig`.
9. **Un `@PreAuthorize` s'évalue après la validation `@Valid`** du corps : pour tester l'autorisation, utiliser une route sans corps (`/publier` avec un id bidon) ; un corps invalide donnerait 400 avant 403.
10. **`.docx` du projet = Markdown renommé** (l'ancien) : la version régénérée est un vrai Word.
11. **Caractères `ÔÇö` dans les diffs copiés** : artefact d'encodage PowerShell 5.1 à l'affichage, pas un défaut des fichiers (UTF-8 dans IntelliJ).
12. **Extraction de zip sous Windows** : vérifier `pwd` et les extensions cachées.
13. **`fatal: Invalid path '/auth'`** : une variable PowerShell (`$main`) définie dans un autre bloc/session est vide ; donner des chemins complets (rencontré une seconde fois le 19/10 avec `$main` et `$test`).
14. **Chargement paresseux** : `LAZY` + `open-in-view: false` impose un `@EntityGraph` (ou équivalent) pour toute relation lue hors transaction (section 4.2).
15. **Dépassement de taille → 500** : une colonne `VARCHAR(255)` sans `@Size` côté DTO fait échouer l'insertion en base ; toujours aligner la validation sur le schéma (appliqué aux lieux, aux événements et à l'inscription).
16. **Port 8080 occupé par `footbooking-api`** : un test HTTP lancé sur 8080 interrogeait FootBooking et renvoyait des 400/404 incohérents (corps d'erreur avec `"error":"Bad Request"` et `"path"`, que le `GlobalExceptionHandler` de Billet+ ne produit jamais). Diagnostic : `Get-NetTCPConnection -LocalPort 8080 -State Listen` + `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"`. Solution : Billet+ sur 8081 (section 8) et contrôle d'identité dans les scripts.
17. **PSReadLine plante au collage de longs blocs** (`ArgumentOutOfRangeException ... SetCursorPosition`) : affichage de la console, sans effet sur l'exécution. Contournement : fonction enveloppante ou fichier `.ps1`.
18. **Testcontainers 1.20.1 incompatible avec Docker Engine 29** (29.8.1, API minimale 1.40) : `Could not find a valid Docker environment ... BadRequestException (Status 400)`, alors que Docker tourne. Avec `disabledWithoutDocker = true`, les tests sont alors **ignorés** et la suite reste verte. Solution : Testcontainers **1.21.4** (propriété `testcontainers.version`). Toujours vérifier `Skipped: 0`.
19. **Surefire ne lance pas les classes `*IT.java`** (il ne prend que `*Test`, `*Tests`, `*TestCase`, et Failsafe n'est pas configuré) : un test d'intégration nommé `...IT` ne s'exécute jamais. Nommer les tests d'intégration `...IntegrationTest`.
20. **`Instant.now()` à 100 ns sous Windows, `TIMESTAMPTZ` à la microseconde** : un billet relu en base différait du billet émis (`...177817400Z` contre `...177817Z`). Trouvé par le test d'intégration. Solution : `Ticket` tronque ses horodatages à la microseconde (domaine). Le QR, lui, signe à la milliseconde.
21. **`@Service` sans implémentation de ses ports** : `IssueTicketUseCase` a été écrit avant les adaptateurs. Les tests unitaires restaient verts, mais le contexte Spring ne pouvait pas démarrer faute de bean. Les commits ont été ordonnés pour que chaque commit démarre (adaptateurs avant cas d'usage).
22. **CI backend supprimée par un commit du frontend** : `32b0ba4` (19/08, « fix(web): ... ») a effacé `backend-ci.yml` et `architecture-check.yml` sans que personne le remarque. Diagnostic : `git log --diff-filter=D -- .github/workflows/`. Leçon : relire `git diff --cached --stat` aussi pour les suppressions. **Résolu** par la PR #18 du 29/10/2026 (section 4.7). Entre le 19/08 et le 29/10, les PR #6 à #16 ont été mergées sans CI backend (liste tirée de `git log --merges --since=2026-08-19`) : leurs tests n'ont été vérifiés qu'en local.
23. **Vérifier qu'une règle ArchUnit contrôle vraiment quelque chose** : la tester à l'envers avec une classe sonde qui la viole, puis supprimer la sonde et relancer en `clean` (voir section 8).
24. **`@DataJpaTest` cache la traduction d'exceptions de la production** : il ne charge pas `PersistenceExceptionTranslationAutoConfiguration`. Un adaptateur `@Repository` qui levait `BilletsDejaEmisException` passait au vert en test, alors qu'en production le 409 devenait `InvalidDataAccessApiUsageException` (500). Démontré en activant la traduction dans le test : 2 échecs. Solution : adaptateur en `@Component`, traduction gardée active dans le test (PR #20).
25. **`save()` de Spring Data sur une clé composite sans `@Version`** fait un `merge` : `SELECT`, puis `UPDATE` silencieux si la ligne existe. Un doublon d'idempotence passerait sans erreur. Solution : `INSERT ... ON CONFLICT DO NOTHING` natif, avec lecture du nombre de lignes insérées (PR #20).
26. **Un test de concurrence vert ne prouve pas qu'il y a eu concurrence** : les threads peuvent s'exécuter l'un après l'autre. Vérifier une fois, avec une sonde temporaire qui affiche le type de chaque résultat, que des conflits apparaissent bien, puis la retirer. Écrire les assertions sur l'**état final**, valable quel que soit l'ordre réel.
27. **Nom de branche coupé au collage**, une seconde fois (`backend-tickets-quota-loc`) : relire le nom avant `/new-branch`.
28. **Un test de concurrence Redis ne peut pas « prouver » l'atomicité par l'entrelacement** : Redis exécute les scripts un par un, par construction. Ce qu'il faut vérifier avec la sonde, c'est que les appels étaient **en même temps en route** (ici : les 8 partis avant la première réponse), sinon le test serait vert même avec une implémentation non atomique en plusieurs commandes (PR #22).
29. **Un invariant tenu par un script se vérifie après chaque test** : le total courant des réservations est comparé à la somme réelle dans un `@AfterEach`. Toute dérive du script (oubli d'un `DECRBY` à l'expiration, par exemple) ferait échouer tous les tests, pas seulement celui qui la provoque (PR #22).
30. **Passer l'heure en paramètre plutôt que la lire dans le serveur** (`Clock` injecté, `maintenant` transmis au script) : l'expiration se teste en simulant « 2 secondes plus tard », sans `Thread.sleep` ni test lent ou instable (PR #22).
31. **`registerSynchronization` exige une transaction active** : les tests unitaires d'un cas d'usage qui l'utilise activent la synchronisation à la main (`TransactionSynchronizationManager.initSynchronization()` dans `@BeforeEach`, `clearSynchronization()` dans `@AfterEach`) et déclenchent eux-mêmes `afterCommit`. Cela permet de vérifier que l'action n'a lieu **qu'après** la validation (PR #22).
32. **`ports are not available ... bind: Une seule utilisation de chaque adresse de socket` à la recréation d'un conteneur** : après une modification de `ports:` dans `docker-compose.yml`, `up -d` recrée le conteneur, mais l'ancien n'a pas encore libéré le port quand le nouveau démarre. Le nouveau conteneur reste à l'état `Created`, donc le service est arrêté. Diagnostic : `netstat -ano | findstr :6379` ne montre aucun `LISTENING`, seulement un `TIME_WAIT`. Solution : relancer `docker compose -f infra/docker-compose.yml up -d`. Rencontré deux fois le 29/10/2026 (Redis, puis PostgreSQL), sans perte de données, puisqu'il n'y a pas eu de `down -v` (PR #24).
33. **Élargir le périmètre d'une branche avant le premier push : la renommer** (`git branch -m ancien nouveau`), pour que le nom reflète le contenu. C'est sans risque tant que la branche n'existe pas sur le dépôt distant, ce qui se vérifie avec `git ls-remote --heads origin <ancien>`. Après un push, on ne renomme plus (PR #24).
34. **Une issue attendue n'est pas une exception** (PR #26) : un billet déjà scanné, annulé ou d'un autre événement est une situation normale à l'entrée. Le cas d'usage les renvoie dans un résultat (`ResultatScan`) au lieu de les lever. Il peut attraper les refus du domaine parce qu'ils sont levés **avant toute écriture** : la transaction n'est donc pas marquée à annuler. Un conflit détecté **à la validation** (`@Version`) ne peut pas être traduit ainsi : il reste une exception (409), que l'appelant doit traiter.
35. **Un message d'exception métier peut atteindre le client** : `GlobalExceptionHandler` renvoie `e.getMessage()` pour toute `BusinessConflictException`. Un message d'exception du domaine ne contient donc ni identifiant, ni heure, ni stock. Les données passent par des accesseurs, et un test le vérifie (`hasMessageNotContaining(ticket.getId().toString())`, PR #26).
36. **Une règle ArchUnit qui ouvre une exception doit être encadrée** (ADR 0005) : ignorer `..api.publique..` dans `modules_sont_independants` aurait laissé passer n'importe quelle classe déposée dans ce package. Deux règles l'encadrent : seulement des interfaces dans `api.publique`, seulement des records dans `shared.evenements`. Les tester à l'envers, **y compris** que l'accès interne (`tickets` vers `events.domain`) reste refusé.
37. **Un `@SpyBean` sur un cas d'usage permet de tester l'atomicité entre modules** : `doThrow(...).when(espion).execute(id)` simule une panne au milieu d'une transaction qui traverse deux modules. Spring réinitialise l'espion entre les tests (merge `2f126a9`).
38. **Un `@SpringBootTest` sans clé privée en CI** : la clé Ed25519 de dev est gitignorée. Il faut un `@MockBean QRSigner`, qui remplace la définition du bean, pour que `Ed25519Config` ne cherche pas à charger la clé.
39. **`assertThat(tx.execute(...))` peut être ambigu à la compilation** (`reference to assertThat is ambiguous`, entre `Predicate` et `IntPredicate`) : passer par une variable typée (`Integer resultat = tx.execute(...)`).
40. **Un script Python en ligne (`python - <<'EOF'`) qui contient du français échoue** quand l'outil Bash de Claude Code prépare la commande : `unexpected EOF while looking for matching `''`, à cause des apostrophes (`d'un`). Solution : écrire le script et les blocs de texte dans des fichiers du dossier temporaire, puis lancer `python -I script.py fichier`.
41. **Rejouer un conflit de version exige une nouvelle transaction** : le rejeu se fait dans un cas d'usage **sans** `@Transactional` (`ScanTicketUseCase`), qui appelle deux fois un cas d'usage `@Transactional`. Un rejeu à l'intérieur d'une transaction déjà marquée à annuler échouerait.

---

## 11. Prochaine étape précise

**Avant de commencer** : `git switch main` puis `git pull` (le `main` local doit être sur `2f126a9` ou plus récent), puis « Sync now » dans le projet. Docker démarré, sinon les 47 tests d'intégration sont ignorés en local (en CI, l'étape « Refuser les tests ignorés » ferait échouer le run).

**Mode de travail depuis le 29/10/2026 au soir** : SirrDev a autorisé Claude Code à coder, tester, relire, commiter, pousser **et merger** lui-même. Faute de `gh` (non installé) et parce que le dépôt est privé, les merges se font **en local** (`git merge --no-ff`, puis `git push origin main`), sans PR. Avant chaque merge, Claude Code lance la commande exacte de la CI (`./mvnw clean verify`), puis le contrôle des tests ignorés. La CI tourne sur le push de `main`, mais **seul SirrDev peut en voir le résultat**. Installer `gh` (`winget install GitHub.cli`, puis `gh auth login`) permettrait de revenir aux PR et de lire la CI.

### 11.1 Ce qui reste pour finir le backend MVP (inventaire du 29/10/2026)

Ordre proposé, du plus bloquant au moins bloquant. **(P)** = branche du plan initial ; **(H)** = hors plan, identifiée dans le code.

| # | Branche | Origine | Dépend de | Taille |
|---|---|---|---|---|
| 1 | ~~Création et gestion des **catégories de billets**~~ ✅ merge `232d58e` | H | ADR 0005 ✅ | courte |
| 2 | ~~**Comptes `AGENT`**, affectation, premier `ADMIN`~~ ✅ merge `24dc753` | H | — | moyenne |
| 3 | ~~**Route de scan**~~ ✅ merge `fa562bc` | H | 2 | courte |
| 4 | ~~**Tests HTTP** des routes anciennes, décision 403 ou 404~~ ✅ (section 4.13) | H | — | moyenne |
| 5 | **Liste publique et recherche** d'événements (`pg_trgm` déjà activé) | H | — | moyenne |
| 6 | **Identité de l'acheteur** (compte acheteur, ou achat sans compte avec téléphone) | H | **décision de SirrDev** | moyenne |
| 7 | `orders-domain-model` | P | 6 | moyenne |
| 8 | `orders-create-usecase` (réserve les places : `ReserveSeatsUseCase`) | P | 7 | moyenne |
| 9 | `orders-wave-gateway` | P | 7 | lourde |
| 10 | `orders-confirm-payment-usecase` (webhook, idempotence à la frontière, `IssueTicketUseCase`) | P | 8, 9 | lourde |
| 11 | **Remboursement** (« payé sans billet », événement annulé) | H | 10, **décision MVP ou V2** | moyenne |
| 12 | `tickets-pdf-export` (le QR se recalcule ; accès réservé à l'acheteur) | P | 6 | moyenne |
| 13 | **Tableau de bord organisateur** (ventes, revenus, quotas ; lecture niveau 1) | H | 10 | moyenne |
| 14 | `rate-limiting` (création de commande, scan, inscription) | P | 8, 3 | moyenne |
| 15 | **Préparation de la production** (`Dockerfile`, secrets en variables d'environnement, Redis avec mot de passe et `noeviction`, profil `prod`) | H | — | moyenne |
| 16 | Décision **conflits de version massifs** (relance côté `orders` ou requête atomique de l'ADR 0003), avant l'événement pilote | H | 10 | courte |

Soit **16 branches**, plus la mise à jour du HANDOFF après chaque lot. Selon la décision 6 et la taille réelle de `orders`, le total varie entre 16 et 19.

**Deux décisions de SirrDev débloquent la moitié de la liste** :
- **(A) Identité de l'acheteur** au MVP : un compte acheteur (nouveau rôle, inscription ouverte, ce que le HANDOFF classait en V2), ou un achat sans compte (numéro de téléphone Wave, avec billets envoyés par lien) ?
- **(B) Remboursement** : dans le MVP (au moins pour « payé sans billet » et l'annulation d'un événement), ou en V2 avec une procédure manuelle pour l'événement pilote ?

### 11.2 Prochaine branche : `feature/backend-events-public-listing` (n° 5)

**Objectif** : la liste publique et la recherche d'événements, classées MUST/SHOULD dans le cahier des charges. Aujourd'hui, `GET /api/events` répond 405 (section 4.1).

**Pistes** *(non actées)* :
- `GET /api/events?q=...&page=...` public : uniquement les événements **`PUBLIE`** et à venir, triés par date ;
- recherche par titre avec `pg_trgm` : l'index `idx_events_titre_trgm` existe déjà (V3) ;
- **pagination obligatoire**, avec une taille bornée (par exemple 50 au maximum), pour ne jamais renvoyer toute la table ;
- lecture de niveau 1 (ADR 0002) ;
- tests HTTP dans une classe fille de `ContexteCompletTestBase` : aucun brouillon ni événement annulé dans la liste, recherche insensible aux accents si possible, pagination.

Ensuite, la décision (A) sur l'identité de l'acheteur débloque `orders` (n° 6 à 13).

---

## 12. Premier message suggéré pour la nouvelle conversation

> Je reprends Billet+ (voir HANDOFF.md du projet). L'ADR 0005 et le test du contexte complet sont mergés et le projet est resynchronisé. Mes réponses aux décisions (A) identité de l'acheteur et (B) remboursement : [...]. Continue avec `feature/backend-tickets-category-management` (section 11.2) et propose un plan avant de coder.

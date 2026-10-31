# Document de passation — Billet+

Dernière mise à jour : 30 octobre 2026, tard le soir, après six merges poussés sur `main` (section 4.27) : **route `GET /api/events/affectes`** (l'agent voit ses événements), **billet PDF refait**, **icônes PNG de la PWA**, correctifs de tests sous Node 20 et deux retouches vues au **premier contrôle visuel du nouveau frontend** (32 écrans, Edge, vrai backend). **Tout est poussé** : `main` = `origin/main`. Avant eux, la **refonte complète du frontend** en trois branches mergées le même jour : `feature/web-refonte` (nouveau projet `web/`, nouvelle direction visuelle, parcours acheteur), `feature/web-espace-organisateur` (session JWT, espace organisateur) et `feature/web-controle-entree` (scanner avec vérification Ed25519 hors ligne). L'ancien frontend décrit en sections 4.24 et 4.25 a été **supprimé** (section 4.26) ; le billet PDF a été refait ensuite (section 4.27). Avant la refonte, `feature/web-billet-pdf-ordinateur` : **billets téléchargeables en PDF** (générés sur l'appareil, QR vectoriel identique au QR signé) et **adaptation complète à l'ordinateur** (en-tête de site, section 4.25). Avant elle, `fix/web-test-crypto-node20` (tests de signature hors jsdom, CI frontend verte sous Node 20) et `feature/web-parcours-achat` : **le frontend PWA complet** (acheteur, organisateur, contrôle d'entrée, adapté au mobile et à l'ordinateur) et la migration `V5_1` qui débloquait le démarrage du backend (section 4.24). Avant elle, la branche n° 16 (`feature/backend-charge-confirmations` : **mesure de charge, puis vente du quota par requête SQL atomique** ; l'inventaire du backend est terminé). Avant elle, la branche n° 13 (`feature/backend-dashboard-organisateur` : **tableau de bord de l'organisateur**, et remboursements des commandes payées d'un événement annulé). Avant elle, la branche n° 12 (`feature/backend-tickets-billets-acheteur` : **l'acheteur récupère ses billets et leurs QR**, `GET /api/orders/{id}/billets`). Avant elle, la branche n° 10 (`feature/backend-orders-confirm-payment` : **webhook UnitechPay signé, confirmation de la commande, émission des billets**, liste des commandes à rembourser). Avant elle, la branche n° 9 (`feature/backend-orders-payment-gateway` : **passerelle UnitechPay, Wave et Orange Money**, ADR 0007, `POST /api/orders/{id}/paiement`). Avant elle, la branche n° 15 (`feature/infra-production-readiness` : **`Dockerfile`, profil `prod`, garde-fous de démarrage, `docker-compose.prod.yml`**, image testée réellement). Avant elle, la branche n° 14 (`feature/backend-rate-limiting` : **limitation de débit** des routes sensibles, 429 avec `Retry-After`). Avant elle, la branche n° 8 (`feature/backend-orders-create-usecase` : **création d'une commande sans compte avec réservation des places**, `POST /api/orders` et `GET /api/orders/{id}`). Avant elle, les branches n° 6 et 7 (`feature/backend-orders-domain-model` : **ADR 0006 achat sans compte**, domaine `orders` niveau 3, migration V8). Avant elles, la branche n° 5 (`feature/backend-events-public-listing` : **liste publique et recherche d'événements**, `GET /api/events`) et les **décisions de SirrDev : (A) achat sans compte, (B) remboursement en V2** (section 11.1). Avant elle, la branche n° 4 (`feature/backend-tests-http-routes-existantes` : tests HTTP des routes anciennes, **404 pour un non-propriétaire sur un brouillon**). Avant elle, trois merges locaux de Claude Code : **catégories de billets** (`232d58e`), **comptes `AGENT` et affectation** (`24dc753`, migrations V6 et V7), **route de scan** (`fa562bc`) ; CI verte sur `main` pour les deux premiers selon SirrDev. Avant eux, deux merges faits en local par Claude Code (sans PR, faute de `gh`) : `feature/backend-inter-module-event-status` (**ADR 0005** : un événement annulé annule ses billets et ferme la vente, une réservation sur un brouillon répond 404) et `feature/backend-tests-contexte-complet` (premier test du contexte complet). Avant eux, la PR #26 `feature/backend-tickets-validate-scan-usecase` (contrôle d'un billet à l'entrée : signature Ed25519, cinq issues, double scan simultané rejeté). Avant elle, la PR #24 `fix/infra-localhost-only` (PostgreSQL et Redis de dev publiés sur `127.0.0.1` seulement, bruit des repositories Redis coupé), et la PR #22 `feature/backend-tickets-redis-seat-lock` (réservation temporaire des places dans Redis pendant le paiement, ADR 0003), suivie d'un démarrage réel confirmé (base en V5). Plus tôt le même jour : PR #20 `feature/backend-tickets-quota-lock` (garde d'idempotence atomique des émissions, migration V5, 101 tests sur le runner, 0 ignoré), PR #18 `fix/ci-restore-backend-workflows` (**la CI backend et le job ArchUnit tournent de nouveau** sur les PR), PR #16 `feature/backend-tickets-jpa-mapper` (persistance JPA niveau 3 du module `tickets`, migration V4, `IssueTicketUseCase`), PR #17 (HANDOFF), PR #14 (alignement du dépôt) et PR #15 (configuration Claude Code)). Ce document complète le contexte du projet (ADR, dépôt GitHub synchronisé) pour reprendre le travail sans rien réexpliquer. En cas de doute entre ce document et le code, **le code du dépôt fait foi** — mais attention : la synchronisation GitHub ne voit que les fichiers **commités, poussés et mergés**, et elle n'est rafraîchie qu'après un clic sur « Sync now » dans le projet.

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

**Avancement estimé** (ordre de grandeur, pas une mesure ; à confirmer par SirrDev) : **le code du MVP est complet** : backend (inventaire terminé, section 11.2) et frontend (trois espaces, billet PDF, icônes). Avant le pilote restent surtout des actions **hors code** ou des **décisions** : premier paiement réel UnitechPay, déploiement, essais sur de vrais téléphones, commande gratuite (section 11.2).

*Mesure du 30/10/2026, tard le soir* (`.\mvnw test`, Docker démarré) : **442 tests backend, 0 échec, 0 erreur, 0 ignoré** (67 classes), 7 règles ArchUnit vertes. Frontend : **148 tests Vitest** verts sous Node 24 **et** Node 20 (suite complète relancée plusieurs fois de suite sous Node 20, sans échec).

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
| — | `feature/backend-tests-http-routes-existantes` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | 12 tests HTTP des routes `/api/auth`, `/api/events`, `/api/venues` avec de **vrais jetons JWT** ; base commune `ContexteCompletTestBase` (contexte partagé) ; non-propriétaire : 404 sur un brouillon, 403 sur un événement visible (section 4.13). CI verte selon SirrDev |
| — | `feature/backend-events-public-listing` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | `GET /api/events?q=&page=&taille=` public : publiés, à venir ou en cours, recherche par titre (index trigramme), pagination bornée à 50 (section 4.14). CI verte selon SirrDev |
| — | `feature/backend-orders-domain-model` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | ADR 0006, domaine `orders` (commande, lignes, téléphone, jeton d'accès), migration V8, persistance JPA niveau 3 (section 4.15). Aucune route. CI verte selon SirrDev |
| — | `feature/backend-orders-create-usecase` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | Port public `tickets.api.publique.PlacesEnVente`, `CreateOrderUseCase` (commande et réservations dans une transaction, places rendues si elle échoue), `ConsulterCommandeUseCase`, routes publiques `/api/orders` (section 4.16). CI verte selon SirrDev |
| — | `feature/backend-rate-limiting` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | Limitation de débit par IP ou par compte (`shared.limitation`, compteur Redis à fenêtre fixe), règles dans `application.yml`, 429 + `Retry-After` (section 4.17). CI verte selon SirrDev |
| — | `feature/infra-production-readiness` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | `backend/Dockerfile`, profil `prod` (secrets obligatoires), `VerificationProduction`, contrôle de la paire Ed25519, actuator `health`, `infra/docker-compose.prod.yml` (section 4.18). CI verte selon SirrDev |
| — | `feature/backend-orders-payment-gateway` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | ADR 0007 (UnitechPay remplace Wave en direct), port `PasserellePaiement`, adaptateur `UnitechPayPasserelle`, migration V9, `OuvrirPaiementUseCase`, route de paiement (section 4.19). CI verte selon SirrDev |
| — | `feature/backend-orders-confirm-payment` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | Webhook signé `POST /api/paiements/unitechpay/webhook`, `ConfirmerPaiementUseCase` (transactions A et B), port public `tickets.api.publique.EmissionBillets`, V10, liste admin des commandes à rembourser (section 4.20). CI verte selon SirrDev |
| — | `feature/backend-tickets-billets-acheteur` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | Port public `tickets.api.publique.BilletsDeCommande` (interface `BilletVendu`), QR recalculé, `ConsulterBilletsUseCase`, route des billets de l'acheteur (section 4.21). CI verte selon SirrDev |
| — | `feature/backend-dashboard-organisateur` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | `TableauDeBordUseCase` et `GET /api/events/{id}/tableau-de-bord`, comptages par catégorie et statut, places en cours de paiement (Redis), `StatutEvenements.estAnnule`, liste admin complétée (section 4.22). CI verte selon SirrDev |
| — | `feature/backend-charge-confirmations` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | `ChargeConfirmationsIntegrationTest` (40 confirmations simultanées), `TicketCategoryRepository.consommerQuota` (requête atomique), mise à jour de l'ADR 0003 (section 4.23). CI verte selon SirrDev |
| — | `feature/web-parcours-achat` | ✅ Mergée le 30/10/2026, en local (voir `git log --merges`) | Migration `V5_1` ; frontend complet : design system, session JWT, parcours acheteur, espace organisateur, contrôle d'entrée hors ligne, adaptation ordinateur (section 4.24). CI verte selon SirrDev, après le correctif suivant |
| — | `fix/web-test-crypto-node20` | ✅ Mergée le 30/10/2026, en local (merge `9efeddd` ; commit `4a0490c`) | `verificationHorsLigne.test.ts` exécuté en environnement `node` : sous Node 20, jsdom fournit un `ArrayBuffer` d'un autre royaume que WebCrypto refuse (section 10, n° 42). CI verte selon SirrDev |
| — | `feature/web-billet-pdf-ordinateur` | ✅ Mergée le 30/10/2026, en local (merge `569507a`) | Billets en PDF (`billetPdf.ts`, jsPDF et qrcode chargés au clic), en-tête de site `NavigationSite` sur ordinateur, mises en page revues (section 4.25). *CI à confirmer*. **Code supprimé par la refonte suivante** |
| — | `feature/web-refonte` | ✅ Mergée le 30/10/2026, en local (merge `ce698af` ; commits `21c479b`, `c4786b9`, `499f524`, `06a36fb`, `90cce3a`, `1e53ce6`, `17673cc`), poussée | Suppression de l'ancien `web/`, nouveau projet, design « Dakar Night Market », composants `src/ui`, parcours acheteur (section 4.26). **CI verte selon SirrDev** |
| — | `feature/web-espace-organisateur` | ✅ Mergée le 30/10/2026, en local (merge `fb2a470` ; commits `a28ace4`, `ab3389b`, `d431e2a`, `8caff57`, `e309260`), poussée | Session JWT (renouvellement préventif), espace organisateur, cache hors ligne limité aux lectures publiques (section 4.26). *CI à confirmer* |
| — | `feature/web-controle-entree` | ✅ Mergée le 30/10/2026, en local (merge `38b293a` ; commits `368c41c`, `210e02a`, `0fe2abe`, `1200409`, `48c6bc5`), poussée avec le correctif suivant | Contrôle d'entrée : caméra jsQR, vérification Ed25519 hors ligne stricte, file de synchronisation, session conservée sans réseau (section 4.26). *CI à confirmer* |
| — | `fix/web-test-ed25519-node20` | ✅ Mergée le 30/10/2026, en local (merge `49312e2` ; commits `d6eee7e`, `642cf5c`), poussée | SHA-512 de noble transmis en `Uint8Array` dans le setup des tests (section 10, n° 45) ; HANDOFF de la refonte. *CI à confirmer* |
| — | `feature/backend-events-affectes` | ✅ Mergée le 30/10/2026, en local (merge `c9a1783` ; commits `6e696a1`, `77cf469`, `379b303`, `a63fda2`), poussée | `GET /api/events/affectes` (rôle `AGENT`), `EvenementsAControlerService`, limite de débit, scanner qui l'utilise avec dernière liste gardée hors ligne (section 4.27). *CI à confirmer* |
| — | `fix/web-test-delai-controle` | ✅ Mergée le 30/10/2026, en local (merge `5214942` ; commit `4f16f34`), poussée | Délai du test de verdict porté à 15 s (section 10, n° 53). *CI à confirmer* |
| — | `feature/web-billet-pdf` | ✅ Mergée le 30/10/2026, en local (merge `8a13e45` ; commits `2ccea8b`, `f45404b`, `cfc73ce`, `476c26b`), poussée | Billet PDF refait (section 4.27). *CI à confirmer* |
| — | `feature/web-icones-pwa` | ✅ Mergée le 30/10/2026, en local (merge `f6ac1e0` ; commit `f9acbb9`), poussée | Icônes PNG 192, 512, maskable et Apple, générateur `web/scripts/generer-icones.mjs` (section 4.27). *CI à confirmer* |
| — | `fix/web-retouches-visuelles` | ✅ Mergée le 30/10/2026, en local (merge `e662790` ; commits `60332b0`, `68d38c1`), poussée | Majuscule de date, échec de paiement masqué une fois la commande payée (section 4.27). *CI à confirmer* |
| 12–17 | `tickets-pdf-export`, `orders-domain-model`, `orders-create-usecase`, `orders-wave-gateway`, `orders-confirm-payment-usecase`, `rate-limiting` | ⬜ | |

Branches volontairement supprimées (ne pas recréer) : `backend-auth-rbac-roles` (absorbée dans auth-tokens), `backend-events-categories-quotas` (redondante : `TicketCategory` vit dans `tickets/domain`).

### Branche d'alignement : `fix/infra-align-repo-with-handoff` — ✅ Mergée le 29/10/2026 (PR #14, merge `c688b50`)

Commits : `2ce6a53` (`infra/docker-compose.yml` : image `postgis/postgis:16-3.4`, propriété Redis de Spring Boot 3, port `5432:5432`), `55daf4a` (`.gitattributes` normalisé, `qodana.yaml` en `projectJDK: "21"`), `c1e48c4` (ADR 0001 à 0004 versionnés dans `docs/architecture/adr/`), `37e272c` (`CONTRIBUTING.md` PowerShell + ArchUnit), `8e17bd5` (cahier des charges aligné sur les ADR).

Le `docker-compose.yml` local n'est plus modifié hors Git : `git status` est propre sur `main`. `CONTRIBUTING.md` liste les règles ArchUnit (7 depuis l'ADR 0005). La règle « jamais de `git add .` » reste en vigueur.

### Frontend (PWA) : refait le 30/10/2026 (section 4.26)

Trois espaces dans une seule PWA (ADR 0004) : acheteur sans compte, organisateur, contrôle d'entrée. Référence technique : `docs/frontend-design-system.md`. En développement, le proxy de Vite vise **Billet+ sur 8081** (`BILLETPLUS_API_URL` pour changer de cible). Lancement : `npm run dev` dans `web/`, puis http://localhost:5173/. Le billet PDF a été refait (section 4.27) : plus rien ne manque par rapport à l'ancien frontend.

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
| `GET /api/events?q=&page=&taille=` | public | liste publique paginée (section 4.14) |
| `POST /api/orders` | public | achat sans compte : crée la commande, réserve les places, renvoie **une seule fois** le jeton d'accès (section 4.16) |
| `GET /api/orders/{id}` | public, **jeton dans l'en-tête `X-Jeton-Commande`** | 404 identique pour une commande inconnue ou un jeton absent ou faux |
| `POST /api/orders/{id}/paiement` | public, même jeton | ouvre le paiement UnitechPay (`{"moyen":"WAVE"}` ou `ORANGE_MONEY`) et renvoie le lien ; même moyen redemandé → même lien (section 4.19) |
| `POST /api/paiements/unitechpay/webhook` | public, **authentifié par la signature HMAC** du corps | 200 traité ou sans effet, 401 signature, 400 corps illisible, 503 à relancer ; **jamais 409** (section 4.20) |
| `GET /api/admin/commandes-a-rembourser` | `ADMIN` | commandes `PAYEE_SANS_BILLET`, avec téléphone et référence du paiement (section 4.20) |
| `GET /api/orders/{id}/billets` | public, même jeton | billets d'une commande **`PAYEE`** avec leur QR (`EMIS` seulement) ; 409 avant le paiement, 404 sans le bon jeton (section 4.21) |
| `GET /api/events/{id}/tableau-de-bord` | `ORGANISATEUR` **de l'événement** | ventes, paiements en cours, disponibles, scans et chiffre d'affaires par catégorie ; 404 pour un autre organisateur (section 4.22) |
| `GET /api/events/mine` | `ORGANISATEUR` | |
| `POST /api/events/{id}/publier` | `ORGANISATEUR` **propriétaire** | 409 si l'événement n'est plus en brouillon |
| `POST /api/events/{id}/annuler` | `ORGANISATEUR` **propriétaire** | 409 si l'événement est `TERMINE` |

*(Jusqu'à la branche n° 5, `GET /api/events` répondait 405 : la liste publique existe désormais, section 4.14.)*

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

**436 tests, 0 échec, 0 ignoré, mesurés le 30/10/2026 (`./mvnw clean verify`, Docker démarré)**, dont 132 tests d'intégration Testcontainers : 29 PostGIS (`TicketPersistenceIntegrationTest`), 14 PostGIS (`OrderPersistenceIntegrationTest`), 14 Redis (`RedisSeatReservationStoreIntegrationTest`), 3 Redis (`RedisCompteurRequetesIntegrationTest`), et **72 sur le contexte complet**, qui partagent un seul contexte Spring (`ContexteCompletIntegrationTest` 24, `ChargeConfirmationsIntegrationTest` 1, `RoutesExistantesHttpIntegrationTest` 12, `ConfirmationPaiementHttpIntegrationTest` 7, `BilletsAcheteurHttpIntegrationTest` 3, `TableauDeBordHttpIntegrationTest` 3, `CommandesHttpIntegrationTest` 6, `ListePubliqueEvenementsHttpIntegrationTest` 5, `PaiementHttpIntegrationTest` 5, `LimitationDebitHttpIntegrationTest` 4, `SondeSanteHttpIntegrationTest` 2, dont 66 par HTTP). **7 règles ArchUnit.** *(Historique : 55 le 20/10 ; de 92 à 196 le 29/10 ; 222, 234, 247, 259, 311, 330, 347, 357, 386, 417, 427, 434 puis 436 le 30/10.)*

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

### 4.14 Liste publique et recherche d'événements (branche n° 5)

- **Route** : `GET /api/events?q=&page=&taille=`, publique (la règle `GET /api/events/**` de `SecurityConfig` couvre aussi `/api/events`, vérifié par un test anonyme). Réponse : `{contenu, page, taille, total, pages}`.
- **Contenu** : seulement les événements **`PUBLIE`**, **à venir ou en cours** (début dans le futur, ou fin dans le futur). Jamais un brouillon, un annulé, un terminé, ni un événement passé (sans date de fin, il disparaît dès son début). Tri : date de début croissante, puis identifiant (ordre stable d'une page à l'autre).
- **Recherche** `q` : sur le titre, **casse ignorée**, espaces autour retirés, 100 caractères au plus. HQL `ilike` (Hibernate 6) → `ILIKE` PostgreSQL ; **`%` et `_` sont échappés** (`JpaEventRepositoryAdapter.echapperJokers`) : « 100% » ne correspond pas à « 1000 ». `EXPLAIN` vérifié sur la base de dev : `ILIKE ... ESCAPE` passe par `idx_events_titre_trgm` (Bitmap Index Scan). **Les accents ne sont pas ignorés** (« Theatre » ne trouve pas « Théâtre ») : il faudrait l'extension `unaccent` et un index adapté (section 7).
- **Pagination obligatoire** : `page` à partir de 0, `taille` de 1 à 50, **20 par défaut**. Hors bornes → 400 (`IllegalArgumentException`) ; `page=abc` → 400 (Spring). Au-delà de la dernière page : liste vide, 200.
- **DTO public dédié** (`EvenementPublicDto`) : sans `organizerId`, `status` ni `creeLe` ; avec le nom et l'adresse du lieu, chargés par `@EntityGraph` dans la même requête (aucune pagination en mémoire : le journal ne contient aucun `HHH90003004`).
- **Architecture** : lecture de niveau 1 dans un service à part, `RechercheEvenementsService` (horloge `Clock` injectée), pour ne pas toucher `EventService`. Le port `EventRepository` reste indépendant de Spring Data : il renvoie un record `PageEvenements`, et l'adaptateur traduit la `Page` Spring.
- **Tests** : `RechercheEvenementsServiceTest` (5, bornes), `EchappementJokersTest` (2), `ListePubliqueEvenementsHttpIntegrationTest` (5, HTTP). Les données de test sont insérées en SQL (l'API refuse un événement passé), et **chaque test filtre sur un marqueur unique** dans ses titres, car la base est partagée par toutes les classes du contexte complet.

### 4.15 ADR 0006 et domaine `orders` (branches n° 6 et 7)

**ADR 0006** (`docs/architecture/adr/0006-achat-sans-compte.md`) : **achat sans compte** (décision A) et **remboursement en V2** (décision B). À respecter :
- pas de rôle acheteur ni d'inscription ouverte ; l'acheteur est identifié par son **numéro de mobile sénégalais**, normalisé en `+2217XXXXXXXX` et non vérifié au MVP ;
- l'accès à une commande passe par un **jeton secret propre à la commande** (256 bits, Base64 URL, 43 caractères), **remis une seule fois** à la création et jamais renvoyé ; seule son **empreinte SHA-256** est stockée ; comparaison en temps constant ; lien `/commandes/{id}#jeton=...` (dans le fragment, que le navigateur n'envoie ni au serveur ni dans le `Referer`) ;
- limites par **téléphone et IP**, et non par compte ;
- pas de remboursement automatique : le statut **`PAYEE_SANS_BILLET`** et son motif garantissent qu'aucun paiement reçu ne se perd.

**Domaine `orders`** (niveau 3, POJO pur, couvert par la 5ᵉ règle ArchUnit : vérifié par une sonde temporaire, qui a fait échouer la règle, puis a été retirée) :
- `Order.creer(eventId, telephone, lignes, maintenant, dureeValidite)` renvoie `CommandeCreee(commande, jeton)`. Invariants : 1 à 10 lignes (`LIGNES_MAX`), une catégorie au plus une fois, quantité de 1 à 20 (`QUANTITE_MAX_PAR_CATEGORIE`, **recopiée** de `tickets.domain.QuantiteParCommande.MAX` : les deux doivent rester égales), délai strictement positif. Dates fournies par l'appelant (horloge injectée), tronquées à la microseconde ;
- `OrderLine(categoryId, quantite, prixUnitaire)` : **prix figé à la commande**, lu côté serveur. `montantTotal()` est recalculé à partir des lignes (aucun total stocké qui pourrait diverger) ;
- transitions : `marquerPayee` (en attente **et** dans le délai), `marquerPayeeSansBillet(motif)` (depuis en attente **ou expirée**, pour qu'un paiement tardif ne se perde pas), `expirer` (délai écoulé), `annuler` (en attente). Toute autre transition lève `TransitionCommandeInterditeException` (409, message générique) ;
- `Money.fois(int)` ajoutée à `shared` (opération technique).

**Persistance** : migration **V8** (`orders`, `order_lines`), entité `OrderJpaEntity` + `OrderLineEmbeddable` (`@ElementCollection`, chargée par `@EntityGraph`), `OrderMapper`, `JpaOrderRepositoryAdapter` (`save()` en `MANDATORY`, `@Version`). Contraintes de base : téléphone normalisé, empreinte hexadécimale de 64 caractères, `payee_le` présent si et seulement si payée, motif présent si et seulement si `PAYEE_SANS_BILLET`, quantité 1–20, prix ≥ 0, `PRIMARY KEY (order_id, category_id)`. Aucune clé étrangère vers `events` ni `ticket_categories`.

**Tests** : `OrderTest` (14), `NumeroTelephoneTest` (21), `JetonAccesTest` (5), `MoneyTest` (3), `OrderPersistenceIntegrationTest` (9, dont deux transactions qui se chevauchent : la seconde échoue en `OptimisticLockingFailureException`).

### 4.16 Création et lecture d'une commande sans compte (branche n° 8)

- **Port public `tickets.api.publique.PlacesEnVente`** (ADR 0005), implémenté par `PlacesEnVenteService` (package-private, dans `tickets`) :
  - `prixSiEnVente(eventId, categoryId)` : vide si la catégorie est inconnue, appartient à un autre événement, ou si l'événement est fermé à la vente ;
  - `reserver` et `liberer` délèguent à `ReserveSeatsUseCase` et `ReleaseSeatsUseCase`.
  `orders` n'importe rien d'autre de `tickets`, et n'attrape aucune exception de son domaine.
- **`CreateOrderUseCase`** (`@Transactional`) : téléphone normalisé, au plus 10 lignes (vérifié **avant** toute lecture), prix lus côté serveur (404 identique pour une catégorie inconnue, d'un autre événement ou d'un événement fermé), limite par téléphone, puis `Order.creer`, `save`, et réservation de chaque catégorie **dans l'ordre croissant de `categoryId`**.
  - **Une seule transaction** : si une réservation échoue, l'exception annule la transaction, donc la commande n'existe pas en base. Les places déjà réservées sont rendues par un `afterCompletion` (statut autre que `COMMITTED`), qui couvre aussi un échec de la validation. Un échec de libération est journalisé (`WARN`) sans interrompre les autres, et la réservation expire d'elle-même.
  - Sonde de régression faite : sans le rendu des places, `CommandesHttpIntegrationTest.si_une_categorie_manque_de_places_rien_n_est_garde_de_la_commande` échoue (409 au lieu de 201).
- **Configuration** (`application.yml`) : `billetplus.commande.delai-paiement: 4m`, qui doit être **strictement inférieur** à `billetplus.reservation.duree` (5 min). Sinon, **l'application refuse de démarrer** (contrôle dans le constructeur). `billetplus.commande.max-en-attente-par-telephone: 3`, par événement, compté sur les commandes en attente encore dans leur délai.
- **`ConsulterCommandeUseCase`** : commande inconnue, jeton absent ou faux → la même `NoSuchElementException` « Commande introuvable » (404).
- **Routes** (section 4.1), publiques dans `SecurityConfig` (`POST /api/orders`, `GET /api/orders/*`). Réponses en **`Cache-Control: no-store`**. Le jeton circule dans l'en-tête `X-Jeton-Commande`, jamais dans l'URL. `CommandeDto` n'expose ni le téléphone ni l'empreinte.
- **Validation** : `CreateOrderRequest` (`telephone` `@Size(max = 30)` avant normalisation en 13 caractères, 1 à 10 lignes, quantité de 1 à 20) ; catégorie en double ou téléphone invalide → 400 (`IllegalArgumentException` du domaine).
- **Tests** : `CreateOrderUseCaseTest` (8, synchronisation de transaction déclenchée à la main), `ConsulterCommandeUseCaseTest` (2), `PlacesEnVenteServiceTest` (3), `CommandesHttpIntegrationTest` (6, vrais PostgreSQL et Redis).

### 4.17 Limitation de débit (branche n° 14)

- **Où** : `shared.limitation`, mécanisme technique transverse, sans règle métier (ADR 0001). C'est un **intercepteur Spring MVC** sur `/api/**`, et non un filtre de servlet :
  - il s'exécute **après Spring Security** : la clé `COMPTE` connaît le compte authentifié ;
  - il s'exécute **avant la lecture et la validation du corps** : une requête invalide (400) ou un mauvais mot de passe (401) comptent aussi.
- **Compteur** : `RedisCompteurRequetes`, script Lua atomique `INCR` puis `PEXPIRE` au premier appel de la fenêtre. Clé `limitation:{règle}:{ip:… | compte:…}:{index de fenêtre}`. Les clés expirent avec leur fenêtre, sans nettoyage.
- **Fenêtres fixes** alignées sur l'horloge : jusqu'à deux fois la limite à cheval sur deux fenêtres. Le mécanisme freine un script ; il ne calcule pas un quota exact.
- **Règles dans `application.yml`** (`billetplus.limitation.regles`), validées au démarrage (nom unique, méthode, chemin, limite ≥ 1, fenêtre ≥ 1 s) :

| Règle | Route | Clé | Limite |
|---|---|---|---|
| `creation-commande` | `POST /api/orders` | IP | 20 / min |
| `lecture-commande` | `GET /api/orders/*` | IP | 120 / min |
| `connexion` | `POST /api/auth/login` | IP | 10 / min |
| `inscription` | `POST /api/auth/register` | IP | 5 / 10 min |
| `rafraichissement` | `POST /api/auth/refresh` | IP | 30 / min |
| `scan` | `POST /api/events/*/scans` | COMPTE | 120 / min |
| `creation-agent` | `POST /api/agents` | COMPTE | 30 / h |

- **Réponse** : 429, `Retry-After` en secondes (arrondi au-dessus, au moins 1), même format JSON que les autres erreurs (`GlobalExceptionHandler`).
- **Clé `COMPTE`** : principal du jeton ; un anonyme est compté par IP.
- **Adresse IP** : `request.getRemoteAddr()`, **jamais `X-Forwarded-For` lu directement** (tout client peut l'écrire). Derrière un proxy en production, c'est `server.forward-headers-strategy: native` (profil `prod`, section 4.18) qui fournit l'IP du client : Tomcat ne lit `X-Forwarded-For` que si la connexion vient d'un proxy de confiance.
- **NAT des opérateurs mobiles** : beaucoup d'acheteurs peuvent partager une même IP publique. Les limites par IP sont donc larges, et à **ajuster après mesure** lors de l'ouverture de la billetterie pilote.
- **Redis en panne** : requête acceptée et `WARN` journalisé. La limitation ne doit pas, à elle seule, arrêter les ventes.
- **`billetplus.limitation.active: false`** désactive tout. Ne jamais le faire en production.
- **Tests** : `LimitationDebitInterceptorTest` (7, horloge réglable), `LimitationDebitPropertiesTest` (3), `RedisCompteurRequetesIntegrationTest` (3, dont 200 appels simultanés sans perte), `LimitationDebitHttpIntegrationTest` (4).
- **Conséquence pour les tests du contexte complet** : `ContexteCompletTestBase` donne une **IP aléatoire à chaque requête MockMvc** (`@Import` d'un `MockMvcBuilderCustomizer`). Sinon, toutes les classes viendraient de `127.0.0.1` et épuiseraient ensemble les limites. Un test qui veut une IP précise la fixe lui-même. Le contexte reste unique : seule la première classe paie le démarrage. Les tests HTTP de limitation attendent la minute suivante s'il en reste moins de 15 s, pour ne jamais chevaucher deux fenêtres.

### 4.18 Préparation de la production (branche n° 15)

- **Défaut corrigé, qui aurait bloqué le pilote** : `billetplus.security.qr-signing.public-key-path` était figé sur `classpath:keys/ed25519_public.pem` (la clé de dev commitée). Avec une clé privée de production, **chaque billet aurait été refusé au contrôle d'entrée**. Les deux clés sont désormais paramétrables (`QR_PRIVATE_KEY_PATH`, `QR_PUBLIC_KEY_PATH`), et `Ed25519Config.verifierLaPaire` **refuse de démarrer** si la clé publique n'est pas celle de la clé privée (signature d'un message de test).
- **Profil `prod`** (`application.yml`) : chaque secret est une variable d'environnement **sans valeur par défaut** (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PASSWORD`, `JWT_SECRET`, `QR_PRIVATE_KEY_PATH`, `QR_PUBLIC_KEY_PATH`) : absente, l'application refuse de démarrer. Également :
  - `server.forward-headers-strategy: native` : IP du client derrière le proxy, lue seulement depuis un proxy de confiance (valeur par défaut de Tomcat : adresses privées et boucle locale ; à changer par `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` si le proxy a une adresse publique) ;
  - `flyway.baseline-on-migrate: false` ; Swagger et `/api-docs` désactivés ; journaux en `INFO` ;
  - `server.error.include-message` et `include-stacktrace` à `never`.
- **`shared.config.VerificationProduction`** (profil `prod`) : refuse de démarrer avec le secret JWT de dev ou un secret de moins de 32 octets, avec une clé du classpath, avec `billetplus.limitation.active: false`, ou avec un Redis dont `maxmemory-policy` n'est pas `noeviction`. Si `CONFIG GET` est refusé (Redis géré), le démarrage continue avec un `WARN`. Les erreurs sont toutes signalées ensemble, **sans jamais afficher les valeurs**.
- **Actuator** : seul `/actuator/health` est exposé (public, `{"status":"UP"}`, sans détail ; il vérifie PostgreSQL et Redis). La page de découverte `/actuator` est désactivée, et les autres endpoints répondent 401 (anonyme) ou 404.
- **`backend/Dockerfile`** : multi-étapes (JDK pour compiler, JRE pour l'exécution), utilisateur `billetplus` sans privilège (uid 999), `SPRING_PROFILES_ACTIVE=prod`, `-XX:MaxRAMPercentage=75`. Les tests ne tournent pas dans le build d'image : la CI les exécute. **`backend/.dockerignore` exclut toute clé privée** : sans lui, la clé de dev présente en local aurait été copiée dans le jar.
- **`infra/docker-compose.prod.yml`** : déploiement de référence sur une machine.
  - Redis avec `requirepass`, `maxmemory-policy noeviction` et `appendonly yes`.
  - PostgreSQL et Redis ne sont publiés sur aucun port.
  - Le backend est publié sur `127.0.0.1` seulement, derrière un proxy TLS à installer (Caddy ou nginx), qui doit **remplacer** `X-Forwarded-For`.
  - Les clés sont montées en lecture seule depuis `infra/secrets/`.
  - Variables dans `infra/.env.prod` (modèle : `infra/.env.prod.example`). `infra/.env.prod` et `infra/secrets/` sont ignorés par Git.
- **Vérifié réellement le 30/10/2026**, avec l'image construite et des secrets jetables (supprimés ensuite) :
  - le jar ne contient aucune clé privée, et le processus tourne en uid 999 ;
  - secret JWT trop court → refus (par jjwt, `WeakKeyException`) ; secret de dev → refus par `VerificationProduction`, dont le message ne contient pas le secret ;
  - paire de clés incohérente → refus ; Redis passé en `allkeys-lru` → refus ;
  - configuration correcte → `healthy`, 8 migrations appliquées, `/actuator/health` 200 `UP`, `/swagger-ui.html` et `/api-docs` 404, `/actuator/env` 401 ;
  - **limitation par IP derrière le proxy** : 10 connexions refusées en 401 puis 429 (`Retry-After: 49`) pour `X-Forwarded-For: 203.0.113.50`, et 401 pour `203.0.113.51`. Les clés Redis portent les IP transmises par le proxy.
- **Tests** : `Ed25519PaireDeClesTest` (2), `VerificationProductionTest` (6), `SondeSanteHttpIntegrationTest` (2).
- **Reste hors du dépôt** : choisir l'hébergeur, installer le proxy TLS et le nom de domaine, générer la paire Ed25519 de production (`openssl genpkey -algorithm ed25519`, hors du dépôt), et mettre en place les **sauvegardes PostgreSQL** (aucune n'existe).

### 4.19 Passerelle de paiement UnitechPay (branche n° 9)

- **ADR 0007** (`docs/architecture/adr/0007-passerelle-de-paiement-unitechpay.md`) : **UnitechPay** (`pay.unitech.sn`, API v1.2.0) **remplace l'intégration directe de Wave** du plan initial. Moyens retenus par SirrDev : **Wave** (`create_wave_payment`) et **Orange Money** (`create_orange_om`). Les mentions de « Wave » dans les sections plus anciennes de ce document désignent désormais la passerelle.
- **Port `orders.application.port.PasserellePaiement`** : `ouvrir(DemandePaiement)` → `PaiementCree(reference, transactionId, url)`. Ni le domaine ni les cas d'usage ne connaissent UnitechPay.
- **Adaptateur `orders.infrastructure.paiement.UnitechPayPasserelle`** (`RestClient`, délais de 5 s pour la connexion et 15 s pour la lecture) :
  - requête : `POST https://api.unitech.sn/api.php?action=…`, `Authorization: Bearer <UNITECHPAY_API_KEY>`, corps `{amount, customer_number (9 chiffres), description, callback_success?, callback_cancel?}` ;
  - traduction : clé refusée (401/403) → 503 et `ERROR` dans le journal ; autre 4xx ou `success: false` → `PaiementRefuseException` (409, message générique) ; 5xx, délai dépassé ou réponse incohérente → `shared.exception.ServiceExterneIndisponibleException` (503, message générique) ;
  - une réponse réussie est contrôlée : référence présente (100 caractères au plus), lien `https`, **même montant que celui demandé** ;
  - la clé n'est jamais journalisée. Sans clé (dev, CI), toute ouverture de paiement répond 503 sans requête réseau.
- **Domaine** : `Paiement(moyen, reference, transactionId, url, ouvertLe)`, `MoyenPaiement`. `Order.ouvrirPaiement` ne réussit **qu'une fois**, sur une commande en attente, et à au moins `billetplus.paiement.marge-minimale` (60 s) de l'expiration.
- **`OuvrirPaiementUseCase`** : lecture par le jeton, puis appel de la passerelle **hors transaction**, puis enregistrement en courte transaction (`TransactionTemplate`, verrou de version).
  - Même moyen redemandé → même lien, sans rappeler la passerelle. Autre moyen → 409. Commande gratuite → 409.
  - Si deux demandes simultanées ouvrent chacune un paiement, une seule est enregistrée ; l'autre est journalisée en `WARN` avec sa référence (paiement orphelin, qui expire chez UnitechPay).
- **Migration V9** : colonnes `paiement_*` sur `orders`, toutes nulles ou toutes renseignées (`num_nulls`), `paiement_moyen` limité à `WAVE` / `ORANGE_MONEY`, **index unique `uq_orders_paiement_reference`** (clé de rapprochement du webhook).
- **Configuration** : `billetplus.paiement.*` (`UNITECHPAY_API_KEY`, `UNITECHPAY_URL_RETOUR_SUCCES`, `UNITECHPAY_URL_RETOUR_ANNULATION`, avec `{orderId}` remplacé). Le profil `prod` exige la clé, et `VerificationProduction` refuse aussi une clé vide. Limitation : `ouverture-paiement` 20 / min par IP. `infra/docker-compose.prod.yml` et `infra/.env.prod.example` sont à jour.
- **Tests** : `UnitechPayPasserelleTest` (8, faux serveur `MockRestServiceServer`), `OuvrirPaiementUseCaseTest` (9), domaine (+4), persistance (+2, dont l'unicité de la référence), `PaiementHttpIntegrationTest` (5). La passerelle est un `@MockBean` de `ContexteCompletTestBase` : elle est commune à toutes les classes, donc le contexte reste unique.
- **Non vérifié** : aucun appel réel à UnitechPay (pas d'environnement de test). **Premier essai réel à faire par SirrDev** avec sa clé et un petit montant (100 F), une fois la n° 10 livrée.

### 4.20 Confirmation du paiement et émission des billets (branche n° 10)

- **Port public `tickets.api.publique.EmissionBillets`** (ADR 0005), implémenté par `EmissionBilletsService` qui délègue à `IssueTicketUseCase`. Il rejoint la transaction de l'appelant.
- **Lecture du webhook** (`orders.infrastructure.paiement.UnitechPayNotifications`, port `LecteurNotificationsPaiement`) :
  - signature « méthode 2 » : HMAC-SHA256 de `event|reference|amount|status|signed_at` avec la clé d'API, comparée en temps constant (majuscules acceptées) ;
  - chaque champ est reconstitué **comme PHP le concatène** (code de référence de la passerelle) : `5000.0` s'écrit `5000`, un champ absent ou `null` donne une chaîne vide ;
  - seuls les champs signés sortent (`NotificationPaiement(type, reference, montant)`). `transaction_id` et `method` ne servent jamais à décider ;
  - un paiement n'est « reçu » que si `event = payment_completed` **et** `status = completed` ; `payment_failed` et `payment_expired` sont des échecs définitifs ; tout autre événement est sans effet ;
  - sans clé configurée, tout webhook est refusé (401).
- **`ConfirmerPaiementUseCase`**, avec la commande retrouvée **par sa référence** :
  - déjà `PAYEE` ou `PAYEE_SANS_BILLET` → sans effet (**idempotence** : un webhook rejoué ne change rien) ;
  - montant différent → `PAYEE_SANS_BILLET` (`MONTANT_INCORRECT`) ; commande annulée → `PAIEMENT_APRES_ANNULATION` ; délai dépassé ou commande expirée → `RESERVATION_EXPIREE` ;
  - sinon, **transaction A** : `lock_timeout` de 5 s (`DelaiAttenteVerrous`, `SET LOCAL`), relecture de la commande, `marquerPayee`, puis émission de chaque ligne dans l'ordre croissant de `categoryId` ;
  - si A échoue sur une **`BusinessConflictException` ou une `NoSuchElementException`** : A est annulée en entier, registre d'émission compris (vérifié en base). La **transaction B**, séparée, enregistre `PAYEE_SANS_BILLET`, avec le motif `VENTE_FERMEE` si une catégorie n'est plus en vente (`PlacesEnVente`), sinon `PLACES_INDISPONIBLES`, puis les places sont rendues. L'exception n'est jamais attrapée dans A ;
  - **conflit de version** : jusqu'à 3 tentatives, puis 503 (première réponse à la n° 16) ;
  - **panne technique** (base, signature QR) : remontée telle quelle, la commande reste en attente et le webhook pourra être rejoué. Rien n'est classé « sans billet » à tort ;
  - échec définitif sur une commande en attente → `ANNULEE`, puis places rendues ; sur une commande payée, sans effet ;
  - référence inconnue → `ERROR` dans le journal (paiement orphelin à rembourser) et 200.
- **Webhook** `POST /api/paiements/unitechpay/webhook` (`PaiementWebhookController`, public, sans limitation de débit) : corps de 16 Ko au plus. Une `BusinessConflictException` inattendue est journalisée en `ERROR` et donne un 503, **jamais un 409**. Ce cas a été détecté par une sonde de régression : sans ce garde-fou, elle aurait répondu 409.
- **Décision B** : `GET /api/admin/commandes-a-rembourser` (`ADMIN`, `no-store`) liste les commandes `PAYEE_SANS_BILLET`, les plus anciennes d'abord (500 au plus), avec le téléphone, le montant, le motif, le moyen et la référence du paiement.
- **Migration V10** : motifs `MONTANT_INCORRECT` et `PAIEMENT_APRES_ANNULATION` (contrainte `orders_motif_sans_billet_check` recréée). Le domaine accepte `marquerPayeeSansBillet` depuis `ANNULEE`, car les webhooks peuvent arriver dans le désordre.
- **Tests** :
  - `UnitechPayNotificationsTest` (9, HMAC recalculé indépendamment) ;
  - `ConfirmerPaiementUseCaseTest` (12, faux dépôt et faux gestionnaire de transactions qui **restaure le dépôt à l'annulation**, ce qu'un mock ne sait pas faire) ;
  - persistance (+3, dont le `lock_timeout` limité à la transaction) ;
  - `ConfirmationPaiementHttpIntegrationTest` (7, de la commande aux billets, avec le vrai webhook signé et le vrai module `tickets`).
  - `ContexteCompletTestBase` fournit une clé de test (`CLE_UNITECHPAY_DE_TEST`).
- **Sondes de régression faites** : sans comparaison de signature, le webhook forgé est accepté (200 au lieu de 401) ; sans la transaction B, le paiement n'est pas enregistré (409 au lieu de 200). Les deux fichiers ont été restaurés, et la suite complète relancée.

### 4.21 Billets de l'acheteur (branche n° 12)

- **Port public `tickets.api.publique.BilletsDeCommande`** : `billetsDe(orderId)` → `List<BilletVendu>`. `BilletVendu` est une **interface** (`id`, `categoryId`, `categoryNom`, `statut`, `qr`), implémentée par un record privé de `BilletsDeCommandeService`. C'est la façon de faire sortir une valeur d'`api.publique`, qui ne peut contenir que des interfaces (6ᵉ règle ArchUnit). Le module `tickets` ne contrôle aucune propriété : c'est le rôle de l'appelant.
- **QR recalculé à chaque lecture, jamais stocké** : `QRSigner.sign(id, eventId, emisLe)`, avec les mêmes données qu'à l'émission. C'est correct parce que **la signature Ed25519 est déterministe** (RFC 8032), ce que vérifie `Ed25519QrRecalculeTest` avec une vraie paire de clés (même QR, et toujours valable).
- **QR seulement pour un billet `EMIS`** : un billet scanné, annulé ou remboursé est renvoyé avec son statut, sans QR.
- **`ConsulterBilletsUseCase`** : jeton de la commande (404 sinon) et commande **`PAYEE`** exigée (409 avant le paiement ou pour une commande à rembourser : la PWA réessaie après le webhook).
- **Route** `GET /api/orders/{id}/billets` : publique dans `SecurityConfig`, `Cache-Control: no-store`, limitation `lecture-billets` à 60 / min par IP. Billets triés par catégorie puis identifiant, nom de catégorie lu une fois par catégorie.
- **Tests** : `Ed25519QrRecalculeTest` (1), `BilletsDeCommandeServiceTest` (3), `ConsulterBilletsUseCaseTest` (3), `BilletsAcheteurHttpIntegrationTest` (3, de la commande au QR : webhook signé, émission réelle, QR propre à chaque billet).
- **Export PDF** : pas fait côté backend ; *fait depuis dans la PWA, sur l'appareil (section 4.25)*. La PWA peut afficher les QR à partir de cette route. Un PDF (envoi, impression) reste possible plus tard, à partir du même port.

### 4.22 Tableau de bord de l'organisateur et remboursements d'un événement annulé (branche n° 13)

- **`tickets.application.usecase.TableauDeBordUseCase`** (lecture de niveau 1) et `GET /api/events/{eventId}/tableau-de-bord`. La route est sous `GET /api/events/**`, publique dans `SecurityConfig` : c'est `@PreAuthorize("hasRole('ORGANISATEUR')")` qui exige un organisateur, et le cas d'usage qui exige **l'organisateur de cet événement** (`StatutEvenements.estOrganisateur`). Sinon, 404 avec le même message qu'un événement inconnu, comme `CreateTicketCategoryUseCase`. `Cache-Control: no-store`.
- **Par catégorie** (triées par nom) :
  - `quotaTotal` et `vendus` (`quota_vendu`) ;
  - `enCoursDePaiement` : places des réservations Redis **encore valables** (`SeatReservationStore.placesReservees`, script Lua, expirées exclues même avant nettoyage) ;
  - `disponibles` = quota restant − en cours, **jamais négatif** ;
  - `scannes`, `annules` (annulés et remboursés) ;
  - `chiffreAffairesFcfa` = billets valables (émis et scannés) × prix. Le prix d'une catégorie ne change jamais, c'est donc le prix payé. Montant **brut** : la commission UnitechPay n'est pas déduite.
- Totaux de l'événement : `vendus`, `enCoursDePaiement`, `scannes`, `chiffreAffairesFcfa`.
- **Comptage des billets en une requête** : `TicketRepository.compterParCategorieEtStatut` (`GROUP BY category_id, status`, servie par `idx_tickets_event_id`).
- **Décision B complétée** : `StatutEvenements.estAnnule(eventId)` (nouveau, port public d'`events`). `GET /api/admin/commandes-a-rembourser` liste aussi les commandes **`PAYEE` d'un événement annulé**, avec le motif `EVENEMENT_ANNULE`, en plus des `PAYEE_SANS_BILLET` (500 au plus).
- **Tests** : `TableauDeBordUseCaseTest` (3), Redis (+1 : réservations expirées exclues), `TableauDeBordHttpIntegrationTest` (3 : ventes réelles par webhook signé, paiement en cours dans Redis, scan ; accès 404 / 403 / 401 ; annulation par la vraie route, puis remboursement listé et chiffre d'affaires à 0).

### 4.23 Mesure de charge et vente atomique du quota (branche n° 16)

- **Mesure** (`ChargeConfirmationsIntegrationTest`, dans la suite normale) : 40 commandes de 2 places sur une même catégorie, paiements ouverts, puis **40 `ConfirmerPaiementUseCase.traiter` lancés au même instant**. Exigence : les 40 donnent leurs billets.
  - **Avant** (quota vendu par `save()` sous `@Version`, relance de 3 tentatives) : **20 réussies, 20 en `ObjectOptimisticLockingFailureException`**. Ce sont autant de clients payés sans billet si la passerelle ne relance pas.
  - **Après** : 40 sur 40 en 1,4 à 1,8 s. Mesure ponctuelle à **150 simultanées : 150 sur 150 en 4,1 s** (constante remise à 40 ensuite).
- **Décision (mise à jour de l'ADR 0003)** : `TicketCategoryRepository.consommerQuota(categoryId, quantite)`, requête native `UPDATE ticket_categories SET quota_vendu = quota_vendu + :q, version = version + 1 WHERE id = :id AND quota_vendu + :q <= quota_total`. 0 ligne modifiée → `QuotaDepasseException`. Adaptateur en `MANDATORY`.
  - Les émissions concurrentes s'attendent sur le **verrou de ligne** ; PostgreSQL réévalue la condition sur la valeur à jour. Aucune survente, aucune mise à jour perdue, aucun conflit de version.
  - Dans `IssueTicketUseCase`, la requête passe **en dernier**, après l'insertion et la signature des billets, pour tenir le verrou le moins longtemps possible. `TicketCategory.reserver` reste un refus rapide sur l'état lu, toujours fondé, puisque `quota_vendu` ne fait que croître pendant la vente.
  - La version est incrémentée : un `save()` de la catégorie fondé sur une lecture plus ancienne échoue toujours (`refuse_une_reservation_calculee_sur_une_version_perimee` reste vert).
- **Tests mis à jour** : à 8 fils, avec un quota suffisant, **toutes** les émissions réussissent (avant : une par tour). Avec un quota de 10 pour 8 lots de 3, **exactement 3** réussissent, et chaque refus est un `QuotaDepasseException`. Test unitaire ajouté : quota épuisé par une émission concurrente → refus, aucune réservation libérée.
- **Limite connue** : la file d'attente est bornée par le pool de connexions (HikariCP, 10 par défaut). Au-delà de quelques centaines de confirmations simultanées sur une même catégorie, l'attente du pool (30 s) ou le `lock_timeout` (5 s) pourraient renvoyer des 503. La passerelle devrait alors relancer : c'est sa politique de relance, à confirmer par Unitech.

### 4.24 Frontend PWA complet et migration V5_1 (`feature/web-parcours-achat`)

> **Historique.** Le code frontend décrit ici (`web/src/design-system/`, `shared/`, `routes/`) a été **supprimé par la refonte du 30/10/2026** (section 4.26). La migration `V5_1` reste en vigueur. Les chiffres de vérification ci-dessous concernent l'ancien code.

**Backend, migration `V5_1__retrograder_agents_sans_createur.sql`** : V6 échouait sur les bases contenant d'anciens comptes `AGENT` créés par l'ancien `/register` (avant le correctif du 20/10), qui n'ont pas de créateur. Le backend ne démarrait plus. `V5_1`, exécutée entre V5 et V6, les repasse en `ORGANISATEUR`, le rôle que donne l'inscription publique : aucune donnée perdue, aucun privilège gagné. Sur une base neuve, aucune ligne n'est touchée. V6 n'a pas été modifiée.

**Design system** (`web/src/design-system/`, voir `docs/frontend-design-system.md`) : intégré avec Vitest et l'alias `@/`. Modifications, chacune pour une raison précise :
- `navigationItems.ts` : `buyerNavItems`, `organizerNavItems` et `tabPanelProps` sortis de `Navigation.tsx`, car la règle `react-refresh` faisait échouer le lint de la CI ;
- `Organizer.tsx` : la table cachée de `BarChart` est enveloppée d'une `div` en `srOnly`. Une `<table>` ne se réduit pas à 1 px, et la page débordait sur téléphone ;
- `Event.tsx` : genre et prix « dès » facultatifs, absents de l'API publique ;
- `tokens.css`, `Navigation.module.css`, `base.module.css` : adaptation à l'écran, par media queries uniquement. Largeur maximale 480 px sur téléphone, 720 px à partir de 768 px, 1120 px à partir de 1024 px ; sur ordinateur, la barre d'action se cale à droite. *La navigation en haut posée ici dans le design system a été retirée par la branche suivante, au profit d'un en-tête de site applicatif (section 4.25).*

**Socle commun** (`web/src/shared/`) :
- `httpClient` : ajoute le jeton, et sur un 401 **rafraîchit une fois** puis rejoue la requête ; si le rafraîchissement échoue, la session est fermée. Les en-têtes de l'appelant s'ajoutent à `Content-Type` au lieu de l'écraser, ce qui était un défaut du squelette. Il lève une `ApiError` avec le message du backend ;
- session : jetons dans `localStorage`, pour que l'agent ouvre le scanner sans se reconnecter. Risque XSS assumé, à couvrir par une CSP au déploiement ;
- `AuthContext`, `RequireRole` (garde par rôle), `ConnexionPage` commune. Le paramètre `?retour=` n'accepte qu'un chemin interne, jamais une adresse externe ;
- `miseEnPage.module.css` : grilles en 1, 2 ou 3 colonnes, deux colonnes avec colonne latérale collante, aux seuils 768 et 1024 px.

**Acheteur** (`routes/acheteur`, routes publiques, appels en `skipAuth`) :
- Découvrir (recherche par titre), fiche, choix des billets (20 par catégorie au plus, comme le backend), commande avec compte à rebours, total **renvoyé par le serveur**, paiement Wave ou Orange Money (redirection vers le lien), puis billets avec QR ;
- `/commandes/:id` relit la commande toutes les 5 secondes tant qu'elle attend son paiement : c'est la page de retour à configurer chez UnitechPay (`UNITECHPAY_URL_RETOUR_SUCCES`, avec `{orderId}`) ;
- le **jeton de commande est enregistré dans IndexedDB** avant de quitter la page de réservation. Mes billets liste les commandes de cet appareil ;
- aucun QR n'est fabriqué : seuls les billets `EMIS` reçoivent un QR (fourni par le backend), les autres sont affichés avec leur statut.

**Organisateur** (`routes/organisateur`) : connexion, inscription (puis connexion automatique), mes événements, création (le lieu est créé dans le même formulaire, faute de liste des lieux ; heure saisie = heure de Dakar = UTC), fiche avec onglets Ventes (tableau de bord), Billets (catégories et ajout) et Agents (affecter ou retirer, sans état affiché, faute d'API), publication, annulation confirmée en deux temps, page Mes agents.

**Contrôle d'entrée** (`routes/scanner`) :
- caméra (`BarcodeDetector`, sinon `jsqr`), **douchette USB ou saisie** dans le même champ ;
- en ligne : verdict du serveur. Agent non affecté : 403 expliqué ;
- **hors ligne** (réseau coupé ou serveur indisponible) : signature Ed25519 vérifiée sur l'appareil avec `@noble/ed25519` et la clé publique `/keys/ed25519_public.pem`, désormais précachée par Workbox avec les polices. Le scan est mis en file dans IndexedDB et envoyé au retour du réseau. Un double passage à deux portes hors ligne n'est pas détectable (avertissement affiché) ;
- le backend ne liste pas les événements d'un agent : l'agent choisit parmi les événements publics.

**Performance** : organisateur et scanner chargés à la demande (`lazy`). Le paquet de l'acheteur passe de 136 à **77 Ko gzippés**.

**Vérifications** : `tsc`, lint (0 avertissement), **73 tests Vitest**, build. **Parcours complet dans Edge contre le vrai backend, 39 vérifications sur 39** (script hors dépôt) : inscription, création, catégories, publication, agent créé et affecté, commande sur mobile (10 000 FCFA du serveur), erreur de paiement lisible (pas de clé UnitechPay en local), tableau de bord (2 places en cours), billets payés (paiement **simulé en base**) avec 2 QR, scan réel autorisé puis refusé au second passage, aucun débordement sur 8 écrans à 390 et 1280 px, aucune erreur JavaScript. Captures relues : cinq défauts visuels trouvés et corrigés.

**Non vérifié** : paiement réel (clé UnitechPay), caméra sur un vrai téléphone (exige HTTPS ou `localhost`).

### 4.25 Billet PDF et adaptation complète à l'ordinateur (`feature/web-billet-pdf-ordinateur`)

> **Historique.** `billetPdf.ts` et `NavigationSite` ont été **supprimés par la refonte** (section 4.26). Le billet PDF a été **refait** dans le nouveau frontend en reprenant ces choix (section 4.27).

**Billet PDF** (`web/src/routes/acheteur/billetPdf.ts`) :
- produit **sur l'appareil**, à partir des QR signés renvoyés par `GET /api/orders/{id}/billets` : aucun QR n'est fabriqué, le contenu signé est seulement dessiné. Aucune route backend n'a été ajoutée ; le PDF fonctionne hors ligne une fois les billets affichés ;
- A5 portrait, un billet par page : bandeau Billet+, titre, date (heure de Dakar), lieu, catégorie, QR de 92 mm, code de secours `BP-XXXX-XXXX`, avertissement « jeton porteur », identifiant de commande ;
- QR **vectoriel** (`qrcode` calcule la matrice, jsPDF dessine les modules en rectangles, zone de silence de 4 modules) : net à toute taille d'impression ;
- jsPDF et qrcode (licence MIT) sont chargés **au clic** (`import()` dynamique) : un morceau séparé de 139 Ko gzippés, le paquet principal reste à 78 Ko ;
- seuls les billets `EMIS` avec QR sont exportés ; bouton désactivé s'il n'y en a aucun ; message d'erreur si la génération échoue (les QR restent à l'écran) ;
- les polices standard du PDF n'ont pas l'espace fine insécable du français (U+202F) : elle est remplacée par une espace.

**Adaptation à l'ordinateur** :
- `shared/ui/EnTete.tsx` (`NavigationSite`) : à partir de 1024 px, **en-tête de site fixe** (logo vers l'accueil et liens de l'espace courant) ; en dessous, la barre basse du design system. Les écrans de parcours (fiche, choix, commande) ont l'en-tête sans barre basse (`barreMobile={false}`), pour ne pas gêner la barre d'action. **Le composant doit être le premier enfant de `Screen`** : il pose la réserve de hauteur sous l'en-tête fixe ;
- design system : la navigation redevient mobile seulement ; `Screen dark` (scanner) étend son fond à toute la fenêtre (`box-shadow` + `clip-path`, sans défilement horizontal) ; fond de page blanc ;
- `miseEnPage.module.css` : `apresTitre` (écart sous la barre de titre), `affiche` (carrée, puis 16:9 à 1024 px), `carteCentree` (connexion et inscription en carte centrée à partir de 768 px), `lecture`, `etapes`, `empileesMobile` (actions en pleine largeur sur téléphone) ;
- Mes billets en cartes d'événement (`resumeLocal`), création d'événement en deux colonnes avec les trois étapes à droite.

**Vérifications (30/10/2026)** : `tsc`, lint (0 avertissement), **76 tests Vitest** sous Node 24 et Node 20 (dont 3 pour le PDF), build. Contre le vrai backend, dans Edge :
- 17 écrans capturés à 1440 × 900 et relus un par un, aucun débordement, aucune erreur JavaScript ;
- clic sur « Télécharger mes billets (PDF) » à 390 et 1440 px : fichier `billets-<titre>.pdf`, 2 pages, codes affichés présents ;
- **le QR du PDF, relu depuis le fichier et décodé par jsqr, est identique octet pour octet au QR signé renvoyé par l'API** ;
- parcours complet : 40 vérifications sur 40 (la vérification du téléchargement s'y ajoute), relancé 4 fois de suite à l'identique. Deux exécutions intermédiaires lues à travers un tube ont affiché des comptes partiels (39, puis 1) sans ligne d'échec ; cause non établie.

**Non vérifié** : impression réelle et lecture du PDF imprimé par la caméra d'un téléphone ; ouverture du PDF sur iOS (Safari ouvre parfois le fichier au lieu de l'enregistrer).

### 4.26 Refonte complète du frontend (`feature/web-refonte`, `feature/web-espace-organisateur`, `feature/web-controle-entree`)

**Décision de SirrDev (30/10/2026)** : supprimer tout `web/` et repartir de zéro, sur une direction visuelle validée sur maquettes (canevas « Billet+ — proposition de design », 7 écrans) : fond blanc et gris lavande, violet `#5B3DF5` (action), ambre `#FFB020` (accent, toujours avec texte foncé), Space Grotesk / Inter embarquées, affiches géométriques générées. Référence complète : `docs/frontend-design-system.md`.

**Organisation** (à respecter par les branches suivantes) :
- `src/ui/` (composants, point d'entrée unique `@/ui`), `src/commun/` (http, formats, session, cache), `src/espaces/<espace>/` (acheteur, organisateur, controle), chacun importé **uniquement par son `index.ts`** (ESLint `isolerEspace`). `src/ui` et `src/commun` n'importent jamais un espace (règle ESLint, vérifiée par un fichier sonde).
- Mobile d'abord, `min-width: 720px` puis `1024px`, jamais `max-width`. Fonctions et constantes dans des `.ts` (règle `react-refresh`).
- Build : `tsc --noEmit && vite build` (plus de `tsconfig.tsbuildinfo` suivi par Git). Contrôle d'entrée chargé à la demande (`React.lazy`).

**Décisions prises pendant la refonte** :
- **Argent** : rien n'est calculé côté client. Avant création de la commande, le récapitulatif est un « Total estimé » (prix unitaires du backend) ; le montant ferme est `montantTotalFcfa`. Aucun stock, genre, prix « dès » ni frais affiché : le backend ne les expose pas.
- **Jeton de commande (ADR 0006)** : `verifierStockage()` **avant** `POST /api/orders` (sinon jeton perdu et places bloquées) ; jeton enregistré dans IndexedDB avant la redirection ; transmis seulement dans `X-Jeton-Commande`.
- **Paiement (ADR 0007)** : `allerPayer` refuse tout lien qui n'est pas `https:` (défense en profondeur ; le backend le vérifie déjà).
- **Session** : jetons dans `localStorage` (limite XSS assumée, CSP à poser au déploiement). **Renouvellement préventif** du jeton d'accès à moins de 30 s de son échéance : le backend ignore un jeton expiré et sert la vue **anonyme** des routes publiques (404 pour un brouillon), jamais 401 (vérifié en HTTP). Un seul rafraîchissement partagé ; coupure réseau ≠ refus (la session reste). Profil mémorisé : le scanner se rouvre sans réseau. Déconnexion propagée aux onglets.
- **Organisateur** : un événement publié d'un autre organisateur (200 sur la route publique) s'affiche « introuvable », comme un brouillon (404).
- **Cache du service worker** : uniquement `GET /api/events`, `/api/events/{uuid}`, `/api/events/{uuid}/categories` **sans `Authorization`** (`estLecturePubliqueEvenements`, testée). En mode `generateSW`, Workbox **recopie le texte** de la fonction dans `sw.js` : elle doit rester autonome (section 10, n° 46).
- **Contrôle d'entrée** : QR coupé au **premier** point comme `Ed25519QRSigner` ; vérification **stricte RFC 8032** (`zip215: false`) ; en-tête DER de la clé contrôlé. **Interopérabilité testée** avec un QR signé par la JCA du JDK 21 (paire jetable). En ligne, le serveur fait foi et **un 403 n'est jamais contourné** ; réseau absent, délai de 5 s dépassé, 429 ou 5xx : signature vérifiée sur l'appareil, entrée mise en file. Une seule synchronisation à la fois (sinon faux doublons). La déconnexion efface les QR gardés sur l'appareil.

**Vérifications (30/10/2026)** : `tsc`, lint (0 avertissement), **134 tests Vitest** (Node 24 et Node 20), build. Relectures `security-reviewer` et `archi-guardian` à chaque branche, deux passes ; aucun point bloquant restant. **Tests HTTP contre Billet+ sur 8081** (scripts hors dépôt) : parcours acheteur 27/27 ; espace organisateur 46/48 (écarts = attentes du script : e-mail déjà pris → 400, annulation de l'événement publié d'un autre → 403, conformes au code) ; route de scan 11/11.

**Non vérifié** : affichage sur un vrai téléphone, caméra réelle, scan d'un billet réellement émis (il faut un paiement confirmé). *L'affichage dans un navigateur a été contrôlé depuis : section 4.27.*

### 4.27 Événements de l'agent, billet PDF, icônes, contrôle visuel (30/10/2026, tard le soir)

**Route `GET /api/events/affectes`** (`feature/backend-events-affectes`) :
- rôle `AGENT` (`@PreAuthorize`), agent lu dans le jeton ; renvoie des `EvenementPublicDto` (sans `organizerId`) des événements **publiés** auxquels il est affecté, début dans les 24 dernières heures ou à venir, ou fin encore à venir (`EvenementsAControlerService.DUREE_SANS_FIN_CONNUE`), du plus proche au plus lointain ;
- lecture de niveau 1 interne au module `events` (sous-requête JPQL sur `AffectationAgent`, `@EntityGraph` du lieu) : aucune frontière franchie, pas d'ADR ;
- règle dans `SecurityConfig` avant `GET /api/events/**`, en **défense en profondeur** : une sonde a montré que, sans elle, l'anonyme reçoit déjà 401 (`GlobalExceptionHandler` sur le refus de `@PreAuthorize`). Le test du 401 ne prouve donc que le résultat ;
- limite de débit `evenements-affectes` (60 par minute et par compte) ;
- la liste ne donne aucun droit : la route de scan revérifie l'affectation (403) ;
- PWA : l'agent voit directement ses événements ; la dernière liste reçue est gardée dans `localStorage` (`billetplus.controle.evenements`, rattachée au compte) pour rouvrir le scanner sans réseau, et **effacée à toute fin de session** (`effacerJetons`).
- Tests : 2 unitaires (horloge fixe), 4 HTTP sur le contexte complet (ordre, brouillon, annulé, non affecté, collègue, fenêtre de 24 h, 401, 403).

**Billet PDF** (`feature/web-billet-pdf`, reprise des choix de la section 4.25) : `web/src/espaces/acheteur/billetPdf.ts` et `pages/BoutonBilletsPdf.tsx`. Généré sur l'appareil, A5, un billet par page, QR **vectoriel** de la chaîne signée (niveau M), code de secours, avertissement « jeton porteur », identifiant de commande, **jamais** le jeton ni le téléphone. Seuls les billets `EMIS` avec QR ; aucun bouton s'il n'y en a pas. `jspdf` (verrouillé `~4.2.1`) et `qrcode` chargés au clic. **Preuve** : le vrai dessin (`dessinerQr`) est rastérisé dans le test puis décodé par jsQR, et redonne exactement le contenu signé ; une erreur de coordonnées, injectée volontairement, fait échouer ce test. Précache : le morceau `billetPdf` reste précaché (PDF hors ligne) ; les dépendances optionnelles de jsPDF (html2canvas, DOMPurify, canvg), nommées `jspdf-optionnel-*` par `chunkFileNames`, en sont exclues (≈ 110 Ko gzippés de moins par installation).

**Icônes** (`feature/web-icones-pwa`) : `icon-192.png`, `icon-512.png`, `icon-maskable-512.png` (dessin dans la zone sûre), `apple-touch-icon.png` (180 px), produites par `web/scripts/generer-icones.mjs` (Node pur : dessin géométrique suréchantillonné, PNG encodé avec `zlib`, aucune dépendance). `icon.svg` redessiné avec la **même géométrie** (tracés, aucune police). Manifeste et `index.html` mis à jour ; les quatre PNG sont précachés.

**Premier contrôle visuel du nouveau frontend** (script Playwright hors dépôt, `playwright-core` + Edge installé, build de production `vite preview` + backend réel) : **32 écrans** (16 parcours × 390 et 1280 px : acheteur de Découvrir aux billets, organisateur, scanner jusqu'au refus), **0 débordement horizontal, 0 erreur JavaScript, 0 erreur de console** (hors 503 attendu de l'ouverture du paiement sans clé UnitechPay). Seul l'état « commande payée » est simulé (interception de deux réponses). Captures relues : **deux défauts trouvés et corrigés** (`fix/web-retouches-visuelles`) : « Mercredi 4 **N**ovembre » (`text-transform: capitalize`, remplacé par `majusculeInitiale`) et l'échec d'ouverture du paiement resté affiché sur une commande devenue payée (il voyage dans l'état de navigation, qui survit au rechargement : désormais affiché seulement tant que la commande attend son paiement).

**Relectures** : `security-reviewer` et `archi-guardian` sur chaque branche ; aucun point bloquant ; leurs corrections appliquées (liste effacée à la déconnexion, limite de débit, nommage des morceaux exclus du précache, test du dessin réel, bouton masqué sans billet valable).

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
- ~~Pas de liste publique d'événements ni de recherche~~ : **résolu** par la branche n° 5 (section 4.14). Restent : **recherche insensible aux accents** (`unaccent` et un index adapté, à faire si le besoin se confirme), recherche par artiste, lieu ou date, et recherche géographique (PostGIS non mappé).
- **Lieux** : tout organisateur peut créer des lieux globaux, sans propriétaire ni détection de doublon (acceptable au MVP). `VenueDto.seatingPlan` est une `String` sérialisée telle quelle (aucun endpoint ne la définit encore) : à revoir quand le plan de salle sera construit.
- **`EventDto.organizerId`** est exposé publiquement par `GET /api/events/{id}` : identifiant interne du module `auth`, non secret mais à retirer si le front n'en a pas besoin. *(La liste publique, elle, ne l'expose pas : `EvenementPublicDto`, section 4.14.)*
- ~~`POST /api/orders` est public et sans limitation par IP~~ : **en partie résolu** par la branche n° 14 (20 créations par minute et par IP, section 4.17). Reste : un attaquant qui dispose de nombreuses IP peut encore bloquer des places par vagues de 4 minutes. Si cela se produit, il faudra une vérification du téléphone (code SMS, ADR 0006) ou un défi anti-robot. La limite par IP fonctionne derrière le proxy depuis la n° 15 (vérifié, section 4.18), à condition que le proxy remplace `X-Forwarded-For`.
- **Commande gratuite** : une catégorie à 0 FCFA donne une commande de 0 FCFA, que la passerelle ne peut pas encaisser. **Aujourd'hui, son paiement est refusé (409)** et elle expire : à trancher (confirmation directe sans passerelle, ou refus des catégories gratuites à la création).
- **Questions envoyées à Unitech (ADR 0007), réponses à reporter ici** : politique de relance et code de réponse attendu du webhook, consultation d'une transaction seule, unité de `validity` et durée de vie d'un paiement, champ `signature` dans la méthode 1, environnement de test, montants minimum et maximum, format de `customer_number`, adresses IP des webhooks. **Tant que la politique de relance est inconnue, un webhook perdu laisse un client payé sans billet** : un rapprochement périodique sur `action=transactions` sera nécessaire avant le pilote.
- **Paiement orphelin** : deux demandes simultanées de paiement pour une même commande ouvrent deux paiements chez UnitechPay, dont un seul est enregistré (`WARN` avec la référence). Si l'acheteur paie l'orphelin, le webhook ne trouve aucune commande pour cette référence : il est journalisé en `ERROR`, avec la référence et le montant (n° 10), et **n'apparaît pas dans la liste admin**. Il faut donc surveiller ces `ERROR` dans les journaux jusqu'au rapprochement périodique.
- ~~Commandes `PAYEE` d'un événement annulé absentes de la liste admin~~ : **résolu** par la n° 13 (motif `EVENEMENT_ANNULE`, section 4.22). Limite : la liste est bornée à 500 sans pagination, et elle parcourt tous les événements qui ont des commandes payées. À paginer si le volume grandit.
- **Premier essai réel du paiement** (SirrDev) : configurer le webhook chez UnitechPay (`configure_webhook`, URL HTTPS de production `/api/paiements/unitechpay/webhook`), puis payer une commande de 100 F et vérifier que le statut passe à `PAYEE` et que les billets sont émis. **C'est ce test qui confirmera la reconstitution de la signature** (format réel de `amount` et de `signed_at`).
- **Énumération d'emails** : `POST /api/auth/register` répond 400 « Un compte existe déjà avec cet email », ce qui permet de tester l'existence d'un compte. Risque faible au MVP ; à revoir avec l'ouverture de l'inscription aux acheteurs (V2). L'inscription est désormais limitée à 5 par 10 minutes et par IP (section 4.17), ce qui ralentit une énumération sans l'empêcher.
- ~~Tests HTTP partiels~~ : **résolu** par la branche n° 4. 31 tests HTTP couvrent désormais toutes les routes (section 4.13). Les scénarios manuels de la section 6 restent un complément.
- ~~Un autre organisateur reçoit 403, et non 404, sur l'événement brouillon de quelqu'un d'autre~~ : **résolu** (branche n° 4). 404 sur un brouillon, 403 sur un événement visible (section 4.13).
- `EventTest.refuse_de_publier_un_evenement_deja_publie` attend `IllegalStateException` : il passe (sous-classe) mais gagnerait à attendre `BusinessConflictException`.
- `Event` n'a aucun moyen d'atteindre `TERMINE` (pas de transition automatique ; job planifié prévu en V2). `annuler()` sur un événement déjà `ANNULE` repasse silencieusement à `ANNULE` : à décider (no-op explicite ou 409).
- `EventService` (niveau 2) lève `AccessDeniedException` de Spring Security : acceptable (comme `AuthService`), mais pour les modules niveau 3 prévoir une exception de domaine.
- Warning au démarrage « Using generated security password » : bruit inoffensif (`UserDetailsServiceAutoConfiguration`) ; peut être exclu plus tard.
- Le refresh token n'est pas révocable (limite assumée au MVP, documentée dans `AuthService`).
- `QuotaDepasseException` étend `BusinessConflictException` (409) depuis `feature/backend-tickets-jpa-mapper`, et `TicketAlreadyScannedException` depuis la PR #26.
- ~~Un événement annulé n'annule pas ses billets~~ : **résolu par l'ADR 0005** (merge `1c73296`). L'annulation passe les billets `EMIS` à `ANNULE` dans la même transaction, et le contrôle d'entrée les refuse. Limite restante : la durée de cette transaction croît avec le nombre de billets (à surveiller pour un très grand événement).
- **Route de scan livrée** (merge `fa562bc`) : le contrat de `ValidateTicketScanUseCase` est respecté par `ScanTicketUseCase` (section 4.12). La **limitation de débit par compte agent** est faite (120 scans par minute, section 4.17).
- **Le QR est un jeton porteur, statique et permanent** (limite de conception, PR #26) : une copie (photo, capture d'écran, e-mail transféré) présentée **avant** le titulaire est acceptée, et le vrai titulaire reçoit `DEJA_SCANNE`. Le « premier arrivé gagne » protège la jauge, pas l'acheteur. Pistes pour plus tard :
  - un code tournant de courte durée, ajouté au QR signé, dans l'application de l'acheteur ;
  - un contrôle d'identité nominatif pour les billets chers ;
  - ne jamais renvoyer le QR après le jour J.
- **En mode hors ligne** (ADR 0004), la PWA scanner vérifie la signature mais **ne détecte pas un double scan entre deux portes**, ni un billet annulé ou remboursé, ni l'affectation de l'agent : seul le serveur le sait. ~~La synchronisation des scans faits hors ligne reste à concevoir~~ : **livrée** (section 4.26) ; les doublons et refus constatés à la synchronisation sont affichés « à signaler au responsable ». Limite assumée, affichée à l'agent (bandeau « Sans réseau »).
- **Frontend refait (section 4.26), risques connus** :
  - ~~billet PDF absent~~, ~~icônes PNG absentes~~, ~~aucune route des événements affectés à un agent~~ : **résolus** (section 4.27) ;
  - **React Router 6 : deux avis de sécurité « modérés »** relevés par `npm audit --omit=dev` (GHSA-wrjc-x8rr-h8h6 : redirection ouverte par barre oblique inverse dans `<Link>` et `useNavigate` ; GHSA-337j-9hxr-rhxg : rendu serveur, non utilisé). Risque faible ici : les redirections ne suivent que des chemins internes (`location.pathname`, filtrés par préfixe). Correction : passer à React Router 7, changement majeur à faire dans sa propre branche (les drapeaux `v7_startTransition` et `v7_relativeSplatPath` sont déjà activés) ;
  - **un agent retiré** d'un événement le voit encore dans la liste gardée hors ligne jusqu'à la prochaine réponse du réseau ; la route de scan le refuse (403) et la synchronisation propose d'effacer ses entrées ;
  - **lieu orphelin** : la création d'un événement crée d'abord le lieu ; si l'événement échoue, le lieu reste (pas de liste ni de suppression des lieux) ;
  - **rotation de la clé Ed25519** : il faut republier la PWA ; d'ici là, un appareil à l'ancienne clé refuse tous les billets hors ligne (échec sûr). La clé de `web/public/keys/` doit être celle de la production (checklist de déploiement) ;
  - **navigation privée** (Safari, Firefox) : IndexedDB en mémoire passe le contrôle `verifierStockage`, puis est vidée à la fermeture : un acheteur peut y perdre le lien vers ses billets ;
  - **CSP** à poser au déploiement (jetons de session dans `localStorage`).
- ~~Conflits de version massifs sous forte demande~~ : **résolu** par la n° 16 (section 4.23). Mesure : 20 confirmations perdues sur 40 avec le verrou de version ; 40 sur 40, puis 150 sur 150, avec la requête atomique de l'ADR 0003, désormais activée.
- ~~Contrôle « événement `PUBLIE` » bloqué par un désaccord entre l'ADR 0001 et ArchUnit~~ : **tranché par l'ADR 0005** (port public `api.publique` pour lire, événement applicatif pour réagir). Pour `orders`, la même voie est disponible : lire par `StatutEvenements`, et ajouter si besoin un port public au module `tickets`.
- **Précision des dates du QR** : le QR signe `emisLe` à la **milliseconde** (`Ed25519QRSigner`, champ `iat`), alors que `Ticket` le garde à la microseconde. Toute comparaison entre un QR vérifié et la base doit se faire après `truncatedTo(ChronoUnit.MILLIS)` (branche 11).
- **Contrat de `orders` envers `IssueTicketUseCase`** (relectures des PR #16 et #20, Javadoc d'`execute`). *Depuis l'ADR 0007, « Wave » s'y lit « UnitechPay » : notification = webhook UnitechPay, signée en HMAC-SHA256 avec la clé d'API.* :
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
    - remboursement nécessaire pour le cas rare « payé sans billet » (risque résiduel, section 4.9). **Décision (B) : remboursement en V2** ; au MVP, `orders` doit seulement **rendre ces commandes visibles** (statut dédié et liste pour l'administrateur), et elles sont remboursées à la main.
  - **Révisions imposées par la décision (A), achat sans compte** (à reporter dans l'ADR 0006) : là où ce contrat dit « par compte » ou « acheteur du jeton », lire **par numéro de téléphone et par IP**. L'`orderId` reste généré par le serveur. L'accès d'un acheteur à sa commande et à ses billets se fait par un **jeton d'accès secret propre à la commande** (aléatoire, au moins 128 bits, stocké haché), transmis par lien ; jamais par l'`orderId` seul, qui n'est pas un secret.
- Messages 409/404 du module tickets : contiennent des UUID et le quota restant. À rendre génériques si un contrôleur acheteur les relaie.
- ~~`IssueTicketUseCase` ne vérifie pas que l'événement est `PUBLIE`~~ : **résolu** (`VenteFermeeException`, 409), comme `ReserveSeatsUseCase` (404 identique à une catégorie inconnue).
- Une seconde émission refusée avec une **quantité différente** de la première mériterait d'être journalisée comme anomalie : elle signale un appelant défaillant.
- **Coût d'une émission** : chaque `save()` d'un billet neuf fait un `SELECT` puis un `INSERT` (relecture de l'entité gérée), soit 40 requêtes pour 20 billets. Acceptable au MVP ; piste si besoin : une méthode de port dédiée à la création, ou un `saveAll`.
- **Redis à durcir en production** (relecture sécurité de la PR #22) : Redis porte désormais un état critique pour la vente (les réservations). Il faudra un mot de passe ou des ACL, un réseau privé, et **`maxmemory-policy noeviction`** : une politique LRU supprimerait des réservations sans prévenir, ce qui mènerait au « payé sans billet ». *(Le volet dev est fait : PR #24 publie Redis et PostgreSQL sur `127.0.0.1` seulement.)* **Résolu pour la production** par la n° 15 : `requirepass`, `noeviction` (contrôlé au démarrage) et aucun port publié dans `infra/docker-compose.prod.yml` (section 4.18). Le mot de passe Redis apparaît dans la ligne de commande du conteneur : acceptable sur une machine dédiée, sinon passer par un fichier `redis.conf` monté.
- **Mot de passe PostgreSQL de dev commité** (`billetplus_dev`, dans `infra/docker-compose.yml` et `application.yml`) : acceptable en dev depuis que le port n'est publié que sur `127.0.0.1` (PR #24). En production, il vient de `DATABASE_PASSWORD`, sans valeur par défaut (profil `prod`, section 4.18).
- **Démarrage du contexte complet confirmé** le 29/10/2026 par `ContexteCompletIntegrationTest` : tous les beans se câblent (dont `ValidateTicketScanUseCase`, `CancelEventTicketsUseCase` et `StatutEvenements`), et la sortie ne contient plus aucune ligne `Could not safely identify store assignment` ni `Bootstrapping Spring Data Redis repositories`. Un `spring-boot:run` sur la base de dev n'a pas été relancé depuis la PR #22.
- Une `RedisSystemException` (erreur de script, mémoire pleine) n'est pas une `DataAccessResourceFailureException` : elle finit en 500, pas en 503.
- En multi-instances, les horloges doivent être synchronisées (NTP) : une instance en avance retire trop tôt des réservations encore valides.
- `TicketCategory.libererQuota` plafonne silencieusement `quotaVendu` à 0 au lieu de signaler une sur-libération : à décider avec le cas d'usage d'annulation.
- Plusieurs Javadoc du module `tickets` citent des noms de branches (`feature/backend-tickets-quota-lock`…), qui vieilliront : préférer un renvoi vers un ADR lors des prochaines retouches.

---

## 8. Environnement et commandes qui fonctionnent (Windows / PowerShell)

- JDK 21 (21.0.11), Maven wrapper, Docker Desktop. Node : **24 sur le poste, 20 en CI** ; toujours relancer les tests frontend sous Node 20 avant de pousser : `npx -y node@20 node_modules/vitest/vitest.mjs run` depuis `web/` (section 10, n° 42 et 45). Dépôt local : `C:\Users\bague\Desktop\billetplus`.
- **Démarrer l'infra depuis la racine du dépôt** : `docker compose -f infra/docker-compose.yml up -d`, puis attendre `healthy` : `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"` (juste après le démarrage, le statut est `health: starting`).
- **Conflit de port 8080** : le conteneur `footbooking-api` (autre projet de SirrDev) publie `0.0.0.0:8080->8080` et occupe donc le port 8080 de Windows (visible via `Get-NetTCPConnection -LocalPort 8080 -State Listen` : processus `com.docker.backend` et `wslrelay`, pas `java`). Ne pas l'arrêter : lancer Billet+ sur 8081 avec `$env:SERVER_PORT = "8081"` (variable valable pour le terminal courant, `application.yml` n'est pas modifié), puis `.\mvnw spring-boot:run`. `footbooking-db` utilise le port 5433 et ne gêne pas Billet+ (5432).
- Lancer le backend depuis `backend/` : `.\mvnw spring-boot:run`. Démarrage OK : PostgreSQL 16.4, Flyway applique V1–V3, `ddl-auto: validate` passe, Tomcat démarré. **Sans Docker démarré, l'application échoue avec `Connection to localhost:5432 refused`** (erreur sur `flywayInitializer`) — ce n'est pas un défaut du code ; les tests unitaires, eux, restent verts sans base.
- Arrêter l'infra sans perdre les données : `docker compose -f infra/docker-compose.yml stop`.
- Tests : `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"` (guillemets obligatoires). Résultat de référence au 30/10/2026, tard le soir : **442 tests, 0 échec, 0 ignoré** (67 classes, Docker démarré ; sans Docker, les tests d'intégration sont ignorés). Le test du contexte complet prend environ 40 s, dont 25 s de démarrage des conteneurs et du contexte. Les tests d'intégration prennent environ 25 s (PostGIS) et 7 s (Redis). **Démarrage réel confirmé le 29/10/2026 au soir** après la PR #22 : `Started BilletPlusApplication` sur 8081, Flyway « 5 migrations validées, schéma à jour », `ddl-auto: validate` sans erreur. Ce démarrage n'ouvre pas de connexion Redis : elle est ouverte au premier appel. Après la suppression d'une classe, utiliser `.\mvnw clean test` : ArchUnit lit les `.class` de `target/`, et un `.class` orphelin fausserait le résultat.
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
42. **Tests WebCrypto sous jsdom et Node 20** : `crypto.subtle.digest` refuse un `ArrayBuffer` créé dans le royaume de jsdom (`not instance of ArrayBuffer`), alors que Node 22 et plus l'acceptent. La CI (Node 20) échouait, pas le poste local. Solution : `// @vitest-environment node` en tête du fichier de test. Reproduire en local avec `npx -y node@20 node_modules/vitest/vitest.mjs run`.
43. **L'outil d'écriture de Claude Code transforme `\uXXXX` en caractère réel** : une regex `/[\u202f]/` devient une espace invisible, et ESLint signale `no-irregular-whitespace`. Solution : réécrire l'échappement par script (`chr(92)`), puis le vérifier avec `grep`.
44. **En-tête fixe et réserve de hauteur** : un `position: fixed` sort du flux ; la réserve qui le compense doit précéder le contenu. Rendu en fin d'écran, `NavigationSite` laissait le titre passer sous l'en-tête (défaut vu sur les captures, corrigé en le plaçant en tête de chaque `Screen`).
45. **`@noble/ed25519` sous jsdom et Node 20** (refonte, 30/10/2026) : le n° 42 est revenu sous une autre forme. Sous jsdom + Node 20, WebCrypto **accepte un `Uint8Array` mais refuse un `ArrayBuffer`** (vérifié par une sonde), et noble passe un `ArrayBuffer` à `crypto.subtle.digest`. Les tests de signature passaient sous Node 24 (poste) et échouaient sous Node 20 (CI). Le test des écrans du scanner a besoin de jsdom : `@vitest-environment node` ne suffit pas. Solution (`fix/web-test-ed25519-node20`) : dans `web/src/test/setup.ts`, `etc.sha512Async` qui transmet un `Uint8Array`. Tests seulement. Leçon : relancer sous Node 20 avant de pousser (section 8).
46. **Workbox recopie le texte de `urlPattern`** (mode `generateSW`) : une règle `({url, request}) => maFonction(url, request)` devient dans `dist/sw.js` un appel à `maFonction`, **non définie** dans le service worker (`ReferenceError` sur chaque requête interceptée). Vu en lisant `dist/sw.js`, aucun test ne l'aurait trouvé. Solution : passer une fonction **autonome** (`urlPattern: estLecturePubliqueEvenements`, aucune référence extérieure), testée en la reconstruisant depuis son seul texte (`new Function`). Après tout changement, relire `dist/sw.js`.
47. **Le backend ignore un jeton expiré au lieu de répondre 401** (`JwtAuthenticationFilter`, `catch (JwtException ignored)`) : sur une route en `permitAll` (`GET /api/events/{id}`), la requête est traitée comme anonyme (brouillon → 404). Un client qui ne rafraîchit que sur 401 croit alors son brouillon disparu. Solution côté PWA : renouvellement préventif d'après le champ `exp` du JWT (section 4.26). Vérifié en HTTP : jeton invalide sur un brouillon → 404.
48. **La validation native du navigateur court-circuite les messages accessibles** : un `min` sur un `datetime-local` bloquait l'envoi avant le contrôle de l'application, et l'utilisateur ne voyait qu'une bulle du navigateur au lieu du message relié au champ (`role="alert"`). Trouvé par un test. Solution : pas de `min`, le contrôle est fait par l'application.
49. **Limitation de débit déclenchée par les scripts HTTP** : enchaîner deux scénarios (plusieurs inscriptions et connexions) donne 429 (« réessayez dans 330 s »), puis des 401 en cascade, faute de jeton. Ce n'est pas un défaut. Solution : limiter le nombre de comptes créés par scénario, ou attendre la fin de la fenêtre.
50. **Port 5173 déjà occupé** : un `vite` lancé par SirrDev tournait déjà ; le `vite` de Claude Code a échoué (`Port 5173 is already in use`) et les contrôles ont touché le serveur existant. Diagnostic : `netstat -ano` puis la ligne de commande du processus. Le serveur existant servait bien le nouveau code (même dossier `web/`) : le résultat restait valable, mais il faut l'établir, pas le supposer.
51. **Tuer un `spring-boot:run` lancé en arrière-plan** : arrêter la commande Maven ne termine pas le processus Java enfant, qui garde le port 8081. Identifier son PID (`netstat -ano`, à comparer au PID affiché dans le journal de démarrage), puis `taskkill /PID <pid> /F`. Même chose pour `vite preview` (vérifier sa ligne de commande avant de l'arrêter).
52. **Un test de sécurité peut passer sans prouver la règle qu'il vise** : le test « sans jeton : 401 » de `/api/events/affectes` restait vert **sans** la règle de `SecurityConfig`, car `GlobalExceptionHandler` répond déjà 401 à un anonyme refusé par `@PreAuthorize`. Trouvé en retirant la règle (sonde, n° 23). Leçon : un test d'autorisation ne prouve que le résultat ; écrire dans le commentaire ce qu'il prouve vraiment.
53. **Tests de bout en bout instables sous Node 20 dans la suite complète** : passaient seuls, échouaient une fois sur plusieurs ensemble (délai `findBy` de Testing Library, 1 s ; limite Vitest de 5 s par test pour un parcours qui attend l'écoulement réel d'un verdict de 2,5 s). Solution : `configure({ asyncUtilTimeout: 3000 })` dans `setup.ts`, et 15 s pour les tests qui attendent des minuteries réelles. Vérifier par **plusieurs** exécutions complètes sous Node 20 avant de conclure.
54. **`setup.ts` s'exécute aussi pour les tests en `@vitest-environment node`** : un `window.scrollTo = ...` y faisait échouer tous les tests du PDF (`window is not defined`). Protéger par `typeof window !== 'undefined'`.
55. **jsPDF entraîne html2canvas, DOMPurify et canvg** (morceaux chargés à la demande pour `.html()` et le SVG) : `globPatterns: '**/*.js'` les précachait pour tous les visiteurs (le précache passait de 0,6 à 1,4 Mo). Les nommer explicitement (`chunkFileNames` → `jspdf-optionnel-*`) puis les exclure ; un motif générique (`index.es-*`) aurait pu exclure un autre morceau sans signal. Après une mise à jour de jsPDF, relire la liste du précache dans `dist/sw.js`.
56. **Captures Playwright en `fullPage`** : les éléments fixes (en-tête, barre basse) restent à leur place dans la première fenêtre et semblent flotter au milieu de la page. Artefact de capture, pas un défaut : le juger sur une capture de la fenêtre seule.
57. **Une erreur de build non reproduite** (`error during build`, une seule fois sur quatre builds identiques, code de sortie non relevé) : cause non établie. Hypothèse non vérifiée : fichier de `dist/` verrouillé par Windows pendant le nettoyage. Relancer et lire le code de sortie (`echo $?`) avant de conclure.

---

## 11. Prochaine étape précise

**Avant de commencer** : `git switch main`, `git pull` (le `main` local doit être sur `e662790` ou plus récent), puis « Sync now » dans le projet. Vérifier dans l'onglet Actions que la CI est verte sur les derniers pushs de `main` (non visible depuis Claude Code). Docker démarré, sinon les tests d'intégration sont ignorés en local (en CI, l'étape « Refuser les tests ignorés » ferait échouer le run).

**Mode de travail depuis le 29/10/2026 au soir** : SirrDev a autorisé Claude Code à coder, tester, relire, commiter, pousser **et merger** lui-même. Faute de `gh` (non installé) et parce que le dépôt est privé, les merges se font **en local** (`git merge --no-ff`, puis `git push origin main`), sans PR. Avant chaque merge, Claude Code lance la commande exacte de la CI (`./mvnw clean verify`), puis le contrôle des tests ignorés. La CI tourne sur le push de `main`, mais **seul SirrDev peut en voir le résultat**. Installer `gh` (`winget install GitHub.cli`, puis `gh auth login`) permettrait de revenir aux PR et de lire la CI.

### 11.1 Ce qui reste pour finir le backend MVP (inventaire du 29/10/2026)

Ordre proposé, du plus bloquant au moins bloquant. **(P)** = branche du plan initial ; **(H)** = hors plan, identifiée dans le code.

| # | Branche | Origine | Dépend de | Taille |
|---|---|---|---|---|
| 1 | ~~Création et gestion des **catégories de billets**~~ ✅ merge `232d58e` | H | ADR 0005 ✅ | courte |
| 2 | ~~**Comptes `AGENT`**, affectation, premier `ADMIN`~~ ✅ merge `24dc753` | H | — | moyenne |
| 3 | ~~**Route de scan**~~ ✅ merge `fa562bc` | H | 2 | courte |
| 4 | ~~**Tests HTTP** des routes anciennes, décision 403 ou 404~~ ✅ (section 4.13) | H | — | moyenne |
| 5 | ~~**Liste publique et recherche** d'événements~~ ✅ (section 4.14) | H | — | moyenne |
| 6 | ~~**Identité de l'acheteur** : achat sans compte, ADR 0006~~ ✅ (section 4.15) | H | ✅ décision A | moyenne |
| 7 | ~~`orders-domain-model`~~ ✅ (section 4.15) | P | 6 | moyenne |
| 8 | ~~`orders-create-usecase`~~ ✅ (section 4.16) | P | 7 | moyenne |
| 9 | ~~`orders-wave-gateway`~~ → `orders-payment-gateway`, UnitechPay ✅ (section 4.19) | P | 7 | lourde |
| 10 | ~~`orders-confirm-payment-usecase`~~ ✅ (section 4.20) | P | 8, 9 | lourde |
| 11 | ~~**Remboursement**~~ → **V2** (décision B). Au MVP : statut « payé sans billet » et liste admin ✅ (section 4.20) ; reste les commandes payées d'un événement annulé (n° 13) | H | 10 | courte |
| 12 | ~~`tickets-pdf-export`~~ → billets de l'acheteur avec QR ✅ (section 4.21) ; PDF généré par la PWA (section 4.25), supprimé par la refonte puis **refait** ✅ (section 4.27) | P | 6 | moyenne |
| 13 | ~~**Tableau de bord organisateur**~~ ✅ (section 4.22) | H | 10 | moyenne |
| 14 | ~~`rate-limiting`~~ ✅ (section 4.17) | P | 8, 3 | moyenne |
| 15 | ~~**Préparation de la production**~~ ✅ (section 4.18) ; restent hors du dépôt : hébergeur, proxy TLS, clés de production, sauvegardes | H | — | moyenne |
| 16 | ~~Décision **conflits de version massifs**~~ ✅ mesure faite, requête atomique activée (section 4.23) | H | 10 | courte |

Soit **16 branches**, plus la mise à jour du HANDOFF après chaque lot. Selon la décision 6 et la taille réelle de `orders`, le total varie entre 16 et 19.

**Décisions de SirrDev (30/10/2026)** :
- **(A) Identité de l'acheteur : achat sans compte.** L'acheteur donne son numéro de téléphone (Wave) ; il reçoit ses billets par un lien secret. Pas de rôle acheteur ni d'inscription ouverte au MVP. Conséquences : section 7, « Révisions imposées par la décision (A) ».
- **(B) Remboursement : en V2.** Pour l'événement pilote, procédure manuelle, à partir de la liste des commandes « payé sans billet » et de celles d'un événement annulé, que `orders` doit fournir.

### 11.2 Ce qui reste pour le backend MVP

L'inventaire du §11.1 est **terminé** (n° 16 comprise, section 4.23). Le parcours complet est en place : publication, recherche, commande sans compte, paiement Wave ou Orange Money, billets avec QR, contrôle d'entrée, tableau de bord, remboursements listés.

**Restent, par ordre d'importance avant l'événement pilote** :
1. **Premier essai réel du paiement** (SirrDev, section 7) : il valide la signature des vrais webhooks UnitechPay.
2. **Réponses d'Unitech**, puis **rapprochement périodique** (`action=transactions`) : la seule protection contre un webhook perdu.
3. **Déploiement** (section 4.18) : hébergeur, proxy TLS, clés de production, **sauvegardes PostgreSQL**.
4. ~~n° 16, mesure de charge~~ ✅ (section 4.23). À refaire sur l'infrastructure de production avant l'ouverture de la billetterie, avec un outil de charge HTTP (k6 ou Gatling) : création de commandes, ouverture des paiements, webhooks.
5. **Commande gratuite** : à trancher (section 7).
6. ~~Frontend PWA~~ ✅ **refait** le 30/10/2026 (sections 4.26 et 4.27). ~~Billet PDF~~ ✅, ~~icônes PNG~~ ✅, ~~`tsconfig.tsbuildinfo` suivi par Git~~ ✅. Reste :
   - essai sur de **vrais téléphones** (Android et iOS), en HTTPS : affichage, caméra du scanner, installation de la PWA, ouverture du PDF sur iOS ;
   - **React Router 7** (avis de sécurité modérés, section 7), dans sa propre branche.
7. **Écarts entre le cahier des charges et le backend, à décider** : catégories d'événements (Musique, Sport…) et recherche par ville, date ou prix (MUST), commission de 5 % (§ 4.1). Pour le pilote, SirrDev a choisi de suivre le backend actuel. *La maquette validée montrait genre, prix « dès » et frais : ils ne sont pas codés tant que le backend ne les fournit pas.*

### 11.3 Suite proposée

Le code du MVP est complet. Ce qui reste demande **SirrDev** (décision, compte ou matériel) :
1. **Premier paiement réel UnitechPay** et réponses d'Unitech (section 7) : valide la signature des webhooks, puis le **rapprochement périodique** (`action=transactions`) contre le webhook perdu.
2. **Commande gratuite** (catégorie à 0 FCFA, section 7) : confirmation directe sans passerelle, ou refus à la création.
3. **Déploiement** (section 4.18) : hébergeur, proxy TLS, clés de production (la clé publique de `web/public/keys/` doit être celle de la production), sauvegardes, **CSP**, `UNITECHPAY_URL_RETOUR_SUCCES` vers `/commandes/{orderId}`.
4. **Essais sur téléphones réels** (section 11.2, n° 6).

Branche de code disponible sans décision : **`feature/web-react-router-7`** (avis de sécurité, section 7) : mise à jour de `react-router-dom`, relecture de `Navigate`, `useNavigate`, `NavLink end`, `future` flags à retirer ; tests et parcours visuel à relancer.
---

## 12. Premier message suggéré pour la nouvelle conversation

> Je reprends Billet+ (voir HANDOFF.md du projet). Le code du MVP est complet (sections 4.26 et 4.27) ; `main` est poussé, la CI est [verte/rouge], et le projet est resynchronisé. Réponses d'Unitech à nos questions : [...]. Décision sur la commande gratuite : [...]. Propose la suite : [rapprochement UnitechPay | React Router 7 | déploiement | retours des essais sur téléphone].

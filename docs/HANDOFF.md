# Document de passation — Billet+

Dernière mise à jour : 20 octobre 2026 (fin de la conversation « correctif /register » : PR `fix/auth-register-role-restriction` mergée dans `main`, après la PR `feature/backend-events-crud-api` du 19/10). Ce document complète le contexte du projet (ADR, dépôt GitHub synchronisé) pour reprendre le travail sans rien réexpliquer. En cas de doute entre ce document et le code, **le code du dépôt fait foi** — mais attention : la synchronisation GitHub ne voit que les fichiers **commités, poussés et mergés**, et elle n'est rafraîchie qu'après un clic sur « Sync now » dans le projet.

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

**Avancement estimé** (ordre de grandeur, pas une mesure) : backend ≈ 35 % (6 branches planifiées mergées sur 17, plus un correctif de sécurité) ; MVP complet ≈ 20 % (le frontend n'a aucune branche commencée). Les 11 branches restantes sont plus lourdes que celles déjà faites (module `orders`, paiement Wave, verrou de quota).

### Phase 0 — Socle technique : ✅ mergée

Docker (PostGIS + Redis), squelette Spring Boot, squelette PWA, CI GitHub Actions (backend, frontend, ArchUnit), ESLint, Vitest.

### Backend — branches

| # | Branche | Statut | Contenu |
|---|---|---|---|
| 1 | `backend-auth-user-entity` | ✅ Mergée | `User`, `Role`, migration V2 |
| 2 | `backend-auth-tokens` | ✅ Mergée | JWT, refresh, login/register/me, BCrypt |
| 3 | `backend-tickets-domain-model` | ✅ Mergée | `TicketCategory`, `Ticket` (POJO purs) |
| 4 | `backend-tickets-ed25519-signer` | ✅ Mergée | Port `QRSigner`, impl Ed25519 |
| 5 | `backend-events-entity-migration` | ✅ Mergée (commit `93426d0` ; sinon incluse via la PR de la branche 6 — à vérifier avec `git log main`) | `Event`, `Venue`, migration V3 |
| 6 | `backend-events-crud-api` | ✅ **Mergée le 19/10/2026** (4 commits : `428baad`, `a14d67e`, `66ba9ce`, `4731c90`) | API REST événements/lieux, sécurité par méthode, 409, visibilité des brouillons (section 4) |
| — | `fix/auth-register-role-restriction` | ✅ **Mergée le 20/10/2026** (hors plan initial) | Inscription publique limitée au rôle `ORGANISATEUR` (section 4.5) |
| 7 | `backend-tickets-jpa-mapper` | ⬜ **Suivante** (section 11) | |
| 8–17 | `tickets-issue-usecase`, `tickets-quota-lock`, `tickets-redis-seat-lock`, `tickets-validate-scan-usecase`, `tickets-pdf-export`, `orders-domain-model`, `orders-create-usecase`, `orders-wave-gateway`, `orders-confirm-payment-usecase`, `rate-limiting` | ⬜ | |

Branches volontairement supprimées (ne pas recréer) : `backend-auth-rbac-roles` (absorbée dans auth-tokens), `backend-events-categories-quotas` (redondante : `TicketCategory` vit dans `tickets/domain`).

### Branche d'alignement : `fix/infra-align-repo-with-handoff` — ⬜ à appliquer (statut à confirmer)

Des versions corrigées ont été produites le 11–12 octobre (à confirmer si copiées dans le dépôt) :
- `infra/docker-compose.yml` : image `postgis/postgis:16-3.4`, `version: "3.9"` supprimée, `SPRING_DATA_REDIS_HOST` dans le bloc backend commenté, **mappage de port `5432:5432`**.
- `CONTRIBUTING.md` : PowerShell, plus de Dev A/B, 4 règles ArchUnit sans `allowEmptyShould`, section niveau 3.
- `.gitattributes` : commentaire nettoyé, binaires `*.docx`, `*.ico`, `*.woff2`.
- `qodana.yaml` : `projectJDK: "21"`.
- Cahier des charges `.docx` régénéré (vrai Word) : Ed25519, PWA, section 5.8.

Commits proposés : `fix(infra): ...`, `docs(contributing): ...`, `chore(repo): ...`. **Mettre ce statut à ✅ une fois mergée.**

⚠️ Le `docker-compose.yml` **local** de SirrDev est modifié (image PostGIS correcte) et **n'est pas commité** : il apparaît dans `git status` sous `../infra/docker-compose.yml`. Il appartient à la branche d'alignement — ne **jamais** le commiter dans une autre branche (donc jamais de `git add .`).

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

**55 tests unitaires, tous verts le 20/10/2026** (53 + 2 nouveaux dans `AuthServiceTest`, qui en compte maintenant 4) ; 4 règles ArchUnit actives. **Aucun test d'intégration** ne démarre le contexte Spring (voir section 7).

### 4.5 Correctif `fix/auth-register-role-restriction` (mergé le 20/10/2026)

**Problème corrigé** : `POST /api/auth/register` (public) acceptait un champ `role` (`ORGANISATEUR`, `AGENT` ou `ADMIN`) : n'importe qui pouvait s'auto-créer ADMIN.

**Décisions (à respecter)** :
- `RegisterRequest` n'a **plus de champ `role`** : le client ne choisit jamais son rôle. Un champ `role` envoyé quand même est ignoré par Jackson (Spring Boot désactive `FAIL_ON_UNKNOWN_PROPERTIES`) et n'est jamais lu.
- `AuthService.register(..., Role)` est **supprimée**, remplacée par `AuthService.registerOrganisateur(email, motDePasse, nomComplet)`, qui fixe le rôle `ORGANISATEUR`. **Aucune méthode publique d'`AuthService` n'accepte un `Role`** : à conserver. Les comptes `AGENT` (créés par un organisateur) et `ADMIN` (créé hors API) auront chacun leur propre cas d'usage, avec son propre contrôle d'accès, quand le besoin se présentera.
- Validation alignée sur le schéma V2 (leçon 15) : `email` et `nomComplet` ≤ 255 caractères, mot de passe 8 à 72 caractères (limite de BCrypt, qui ignore tout au-delà de 72 octets).
- **Conséquence** : aucune route ne peut plus créer un `AGENT` ni un `ADMIN`. Aucune fonctionnalité actuelle n'en a besoin, mais c'est un chantier à prévoir (section 7).

**Vérifié en HTTP le 20/10/2026** (script de la section 6.2) : inscription avec `role = "ADMIN"` → 201, puis `GET /api/auth/me` → `"role":"ORGANISATEUR"` ; email de 300 caractères → 400 avec le message de validation.

---

## 5. Workflow de livraison d'une branche (procédure rodée)

Chaque bloc est **autonome** : chemins complets, **aucune variable définie dans un bloc précédent** (une variable `$main` définie plus haut n'existe pas dans une nouvelle session PowerShell et donne `Invalid path '/auth'`). Depuis `backend/` :

```powershell
# 1. Tests : tout doit être vert avant de commiter
.\mvnw test
.\mvnw test "-Dtest=ArchitectureTest"

# 2. Repartir d'un index propre (ne touche pas aux fichiers)
git restore --staged .

# 3. Un « git add » par chemin explicite — JAMAIS « git add . » (docker-compose.yml est modifié localement)
git add src/main/java/com/billetplus/.../Fichier.java
git diff --cached --stat        # vérifier la liste exacte avant de commiter

# 4. Commit (Conventional Commits), puis contrôle
git commit -m "feat(contexte): description"
git log --oneline -3

# 5. Push, PR, attente des workflows verts, merge
git push -u origin feature/<contexte>-<description>
```

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

- **Aucune route ne peut créer un `AGENT` ni un `ADMIN`** (conséquence du correctif `/register`). À prévoir : (a) un cas d'usage « un organisateur crée un compte agent » (module `auth`, avec son propre contrôle d'accès) avant la branche de validation de scan ; (b) un premier `ADMIN` créé hors API (par exemple un `ApplicationRunner` lisant des variables d'environnement, jamais un hash ou un mot de passe commité). Aucune fonctionnalité actuelle n'en dépend.
- **Incohérence ADMIN** : `@PreAuthorize("hasRole('ORGANISATEUR')")` **exclut ADMIN** des opérations sur les événements, alors que `POST /api/venues` accepte `ORGANISATEUR` **ou** `ADMIN`. Devenue théorique (personne ne peut devenir ADMIN) ; à trancher avec le back-office (WON'T V1).
- **Pas de liste publique d'événements ni de recherche** (voir section 4.1).
- **Lieux** : tout organisateur peut créer des lieux globaux, sans propriétaire ni détection de doublon (acceptable au MVP). `VenueDto.seatingPlan` est une `String` sérialisée telle quelle (aucun endpoint ne la définit encore) : à revoir quand le plan de salle sera construit.
- **`EventDto.organizerId`** est exposé publiquement par `GET /api/events/{id}` : identifiant interne du module `auth`, non secret mais à retirer si le front n'en a pas besoin.
- **Énumération d'emails** : `POST /api/auth/register` répond 400 « Un compte existe déjà avec cet email », ce qui permet de tester l'existence d'un compte. Risque faible au MVP ; à revoir avec l'ouverture de l'inscription aux acheteurs (V2) et le rate limiting (branche 17).
- **Tests d'intégration absents** : aucun test ne démarre le contexte Spring, d'où le fait que des défauts de configuration (port DB, sécurité, chargement paresseux) n'étaient visibles qu'au lancement réel. Envisager `@SpringBootTest` + Testcontainers + `MockMvc` pour verrouiller les règles d'autorisation, de visibilité et d'inscription (« un champ `role` envoyé est ignoré »). Attention : `V1__enable_extensions.sql` active PostGIS, donc le conteneur de test doit utiliser une image PostGIS (`DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")`), pas `postgres` standard.
- `EventTest.refuse_de_publier_un_evenement_deja_publie` attend `IllegalStateException` : il passe (sous-classe) mais gagnerait à attendre `BusinessConflictException`.
- `Event` n'a aucun moyen d'atteindre `TERMINE` (pas de transition automatique ; job planifié prévu en V2). `annuler()` sur un événement déjà `ANNULE` repasse silencieusement à `ANNULE` : à décider (no-op explicite ou 409).
- `EventService` (niveau 2) lève `AccessDeniedException` de Spring Security : acceptable (comme `AuthService`), mais pour les modules niveau 3 prévoir une exception de domaine.
- Warning au démarrage « Using generated security password » : bruit inoffensif (`UserDetailsServiceAutoConfiguration`) ; peut être exclu plus tard.
- Le refresh token n'est pas révocable (limite assumée au MVP, documentée dans `AuthService`).
- `QuotaDepasseException` étend `BusinessConflictException` (409) depuis `feature/backend-tickets-jpa-mapper`. `TicketAlreadyScannedException` reste à traiter (branche 11).
- **CI backend absente de `main`** : le commit `32b0ba4` (« fix(web): ajoute la configuration Vitest... », 19/08) a supprimé par erreur `.github/workflows/backend-ci.yml` et `architecture-check.yml`. Depuis, aucune PR backend ne passe les tests ni ArchUnit en CI. À restaurer depuis `0e38483` dans `fix/ci-restore-backend-workflows`.
- **Garde anti double émission non atomique** (`IssueTicketUseCase`) : deux appels simultanés pour la même (commande, catégorie) passent tous deux `existsByOrderIdAndCategoryId` ; seul l'échec de version sur `ticket_categories` fait tomber le second. Si `tickets-quota-lock` remplace le `@Version` par un `UPDATE` SQL atomique, cette protection disparaît. À faire dans cette branche : table d'idempotence `ticket_issuances(order_id, category_id)` avec clé primaire (migration V5), insérée en premier dans la transaction, et test à deux threads.
- **Précision des dates du QR** : le QR signe `emisLe` à la **milliseconde** (`Ed25519QRSigner`, champ `iat`), alors que `Ticket` le garde à la microseconde. Toute comparaison entre un QR vérifié et la base doit se faire après `truncatedTo(ChronoUnit.MILLIS)` (branche 11).
- **Contrat de `orders-confirm-payment` envers `IssueTicketUseCase`** : catégorie, commande et quantité lues en base, jamais dans la notification Wave ; signature et anti-rejeu Wave vérifiés avant l'appel ; montant recalculé côté serveur ; passage à PAYE dans la même transaction ; un 409 `BilletsDejaEmisException` traité comme un succès idempotent (200 à Wave). Plafond : 20 billets par émission (`QUANTITE_MAX_PAR_EMISSION`), à appliquer aussi à la création de commande.
- Messages 409/404 du module tickets : contiennent des UUID et le quota restant. À rendre génériques si un contrôleur acheteur les relaie.

---

## 8. Environnement et commandes qui fonctionnent (Windows / PowerShell)

- JDK 21 (21.0.11), Maven wrapper, Docker Desktop, Node 20. Dépôt local : `C:\Users\bague\Desktop\billetplus`.
- **Démarrer l'infra depuis la racine du dépôt** : `docker compose -f infra/docker-compose.yml up -d`, puis attendre `healthy` : `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"` (juste après le démarrage, le statut est `health: starting`).
- **Conflit de port 8080** : le conteneur `footbooking-api` (autre projet de SirrDev) publie `0.0.0.0:8080->8080` et occupe donc le port 8080 de Windows (visible via `Get-NetTCPConnection -LocalPort 8080 -State Listen` : processus `com.docker.backend` et `wslrelay`, pas `java`). Ne pas l'arrêter : lancer Billet+ sur 8081 avec `$env:SERVER_PORT = "8081"` (variable valable pour le terminal courant, `application.yml` n'est pas modifié), puis `.\mvnw spring-boot:run`. `footbooking-db` utilise le port 5433 et ne gêne pas Billet+ (5432).
- Lancer le backend depuis `backend/` : `.\mvnw spring-boot:run`. Démarrage OK : PostgreSQL 16.4, Flyway applique V1–V3, `ddl-auto: validate` passe, Tomcat démarré. **Sans Docker démarré, l'application échoue avec `Connection to localhost:5432 refused`** (erreur sur `flywayInitializer`) — ce n'est pas un défaut du code ; les tests unitaires, eux, restent verts sans base.
- Arrêter l'infra sans perdre les données : `docker compose -f infra/docker-compose.yml stop`.
- Tests : `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"` (guillemets obligatoires). Résultat de référence au 20/10/2026 : 55 tests, 0 échec.
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
- **ArchUnit** : 5 règles actives (depuis `feature/backend-tickets-jpa-mapper` : domaines de niveau 3 sans JPA, Hibernate ni Spring), aucun `allowEmptyShould`. Après tout nouveau module/package, vérifier qu'elles s'appliquent à au moins une classe.
- `.gitattributes` : LF partout sauf `mvnw.cmd` ; les warnings `LF will be replaced by CRLF` sont normaux.
- Les fichiers de projet Claude sont en lecture seule : toute correction se produit en fichier de sortie que SirrDev copie dans son dépôt, commite, puis **resynchronise (« Sync now »)**. Les fichiers non encore commités/mergés ne sont **pas visibles** de Claude : les lui coller dans le message.

---

## 10. Problèmes déjà rencontrés et résolus

1. **JDK** : Java 21 strict (`pom.xml`, `qodana.yaml`).
2. **ArchUnit** : `ignoreDependency(...)` après `.should().notDependOnEachOther()` ; pas de `allowEmptyShould`.
3. **Image Docker** : `postgres:16` n'a pas PostGIS → `postgis/postgis:16-3.4` ; après changement d'image, `docker compose -f infra/docker-compose.yml down -v`.
4. **`docker compose` depuis la racine**, pas depuis `backend/`.
5. **Mappage de port Docker** : `"HÔTE:CONTENEUR"` — PostgreSQL écoute toujours sur 5432 *dans* le conteneur. `5433:5433` est faux ; si 5432 était pris : `5433:5432`.
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

---

## 11. Prochaine étape précise

**Avant de commencer** : `git switch main` puis `git pull` (le `main` local doit contenir les PR des branches 6 et du correctif `/register`), puis « Sync now » dans le projet. Idéalement, solder d'abord la branche d'alignement `fix/infra-align-repo-with-handoff` (section 3), qui supprime le `docker-compose.yml` modifié de ton `git status`.

**Branche** : `feature/backend-tickets-jpa-mapper`.

**Objectif** : persister `TicketCategory` et `Ticket` (POJO purs) en niveau 3 strict : entités JPA séparées + mappers + adaptateurs, **sans aucune annotation JPA dans `domain/`**.

**Fichiers à créer** :
```
tickets/infrastructure/persistence/
├── TicketCategoryJpaEntity.java
├── TicketJpaEntity.java
├── TicketCategoryMapper.java
├── TicketMapper.java
├── JpaTicketCategoryRepositoryAdapter.java
└── JpaTicketRepositoryAdapter.java
tickets/application/port/
├── TicketCategoryRepository.java
└── TicketRepository.java
db/migration/
└── V4__create_ticket_categories_and_tickets.sql
```

**Faits du code existant utiles au mapper** :
- `TicketCategory.reconstituer(id, eventId, nom, prix, quotaTotal, quotaVendu)` et `Ticket.reconstituer(id, eventId, categoryId, orderId, status, emisLe, scanneLe)` existent pour reconstruire sans revalider.
- `Money` (`shared`) est un `BigInteger` en FCFA (`Money.ofFcfa(long)`, `valeur()`) : colonne `BIGINT`/`NUMERIC` et conversion explicite dans le mapper.
- `TicketStatus` : `EMIS`, `SCANNE`, `ANNULE`, `REMBOURSE` → `CHECK` en SQL comme pour `events.status` en V3.
- Style de la migration V3 : `UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `TIMESTAMPTZ`, `CHECK (status IN (...))`, commentaire « référence par UUID uniquement, pas de FK cross-module ». **Pas de `FOREIGN KEY` vers `events`**.
- Limites de taille : aligner toute validation future sur le schéma (leçon 15).

**Point d'attention** : colonne `version` (`@Version`, `BIGINT NOT NULL DEFAULT 0`) sur `ticket_categories` pour le verrouillage optimiste de `tickets-quota-lock`.

**Point de conception à trancher** *(proposition, non actée)* : le domaine `TicketCategory` n'a volontairement pas de champ `version`. Si l'adaptateur recrée une entité JPA neuve depuis le domaine à chaque `save`, la version est perdue et le verrouillage ne protège plus rien. Piste : l'adaptateur **recharge l'entité JPA gérée**, y copie les champs du domaine, puis la sauvegarde (Hibernate conserve et contrôle la version), sans rien ajouter au domaine. Attention à l'identifiant assigné en mémoire : avec un `@Id` non généré, `save()` de Spring Data fait un `merge` (SELECT avant INSERT) sauf si l'entité implémente `Persistable` ou porte un `@Version` null à la création (`Long` wrapper).

---

## 12. Premier message suggéré pour la nouvelle conversation

> Je reprends Billet+ (voir HANDOFF.md du projet). Les PR `feature/backend-events-crud-api` et `fix/auth-register-role-restriction` sont mergées et le projet est resynchronisé. Je veux attaquer [`feature/backend-tickets-jpa-mapper` | `fix/infra-align-repo-with-handoff`]. Dis-moi de quels fichiers tu as besoin avant de produire quoi que ce soit.

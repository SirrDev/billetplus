# Billet+ — instructions pour Claude Code

Plateforme de billetterie événementielle (Sénégal/Dakar) : monolithe modulaire Spring Boot (JDK 21) + PWA React.
Développeur : SirrDev, **seul sur tout le backend** (plus aucune référence à un découpage à deux développeurs).
État réel du projet : `docs/HANDOFF.md` — à lire avant de parler d'avancement ou de prochaine étape. En cas de désaccord entre le HANDOFF et le code, **le code fait foi**.

## Façon de travailler

- Répondre **en français**. Sorties complètes, prêtes pour la production, sans omission.
- Expliquer le *pourquoi* des commandes et des choix en une ou deux phrases : SirrDev apprend en cours de route.
- Ne jamais affirmer « vérifié », « les tests passent » ou « ça marche » sans l'avoir exécuté dans la session. Donner le résultat réel (nombre de tests, code HTTP). Ce qui n'a pas pu être exécuté est dit comme tel.
- Environnement : **Windows + PowerShell**. Toute commande destinée à SirrDev est en PowerShell natif : jamais de `\` de continuation ni de `$(...)` bash ; backtick pour couper une ligne ; `Invoke-RestMethod` (jamais `curl`, alias d'`Invoke-WebRequest`) ; guillemets autour des `-D...` Maven ; chaque bloc autonome (chemins complets, aucune variable définie dans un bloc précédent). Un script long va dans un fichier `.ps1` (PSReadLine plante au collage de longs blocs).
- Éviter les accents et les caractères spéciaux dans les corps JSON et les messages de commit (PowerShell 5.1 les encode mal).
- Ne jamais lire, afficher ni demander de secrets réels (clé privée Ed25519, secret JWT de prod, mots de passe).

## Architecture (décisions actées — voir `docs/architecture/adr/`)

Ne pas remettre en cause sans raison **nouvelle et explicite**. Si une demande contredit un ADR, le dire avant d'écrire du code.

- **ADR 0001** : monolithe modulaire, un package par module (`com.billetplus.<module>`). Pas de microservices ni de Kubernetes.
- **ADR 0002** : hexagonale à rigueur variable. Critère : *un bug ici peut-il coûter de l'argent ou créer une incohérence irréversible ?*
- **ADR 0003** : PostgreSQL + Flyway (`ddl-auto: validate`) + Redis. PostGIS activé mais pas encore mappé en JPA.
- **ADR 0004** : PWA React (`web/`) ; QR codes signés en **Ed25519** (clé privée côté backend uniquement, clé publique côté scanner).

| Module | Niveau | Ce que ça impose |
|---|---|---|
| `tickets`, `orders` | 3 | Domaine POJO pur (zéro annotation JPA ni Spring), entité JPA séparée + mapper + adaptateur, un cas d'usage par classe |
| `auth`, `events` | 2 | Port défini par le module + JPA-as-domain |
| lecture pure (dashboards, exports) | 1 | Couches simples |

Structure d'un module : `domain/`, `application/port/`, `application/usecase/`, `infrastructure/`, `api/`.
Dépendances : `api` → `application` → `domain` ; `infrastructure` implémente les ports. Le domaine ne dépend jamais de l'infrastructure.

**Isolation inter-module (vérifiée par ArchUnit)** : jamais de relation JPA entre modules, `UUID` brut uniquement, sans `FOREIGN KEY` en base. Le package `shared` ne contient aucune logique métier (types techniques : `Money`, handler d'exceptions, `BusinessConflictException`).

## Commandes (PowerShell, depuis `C:\Users\bague\Desktop\billetplus`)

```powershell
# Infra : TOUJOURS depuis la racine du dépôt, jamais depuis backend/
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml stop        # arrêt sans perte de données

# Backend : le port 8080 est occupé par footbooking-api -> 8081
Set-Location backend
$env:SERVER_PORT = "8081"
.\mvnw spring-boot:run

# Tests
.\mvnw test
.\mvnw test "-Dtest=ArchitectureTest"
```

- Sans Docker démarré, l'application échoue avec `Connection to localhost:5432 refused` sur Flyway : ce n'est pas un bug du code (les tests unitaires restent verts).
- Après un changement d'image PostgreSQL : `docker compose -f infra/docker-compose.yml down -v` (sans `-v`, l'ancien volume persiste).
- Un test HTTP doit d'abord **vérifier que c'est Billet+ qui répond** (le port 8080 sert FootBooking, qui renvoie des 400/404 trompeurs) et afficher le corps des erreurs. Voir le skill `http-check`.

## Git

- Branches : `feature/<contexte>-<description>` ou `fix/<contexte>-<description>`, créées **juste avant usage**, depuis un `main` à jour (skill `new-branch`).
- Commits en Conventional Commits, un commit par changement logique. Tests verts (`.\mvnw test` + `ArchitectureTest`) **avant** tout commit (skill `ship`).
- **Jamais `git add .`, `git add -A` ni `git commit -a`** : un `git add` par chemin explicite, puis `git diff --cached --stat` pour vérifier la liste avant de commiter.
- Ne jamais pousser, merger ni supprimer une branche sans demande explicite. Jamais de `--force`.
- Après un merge : « Sync now » dans le projet Claude, puis mise à jour de `docs/HANDOFF.md` (skill `update-handoff`).

## Pièges déjà rencontrés (ne pas les reproduire)

- **Image Docker** `postgis/postgis:16-3.4`, pas `postgres:16` (`V1__enable_extensions.sql` crée l'extension PostGIS). Les tests d'intégration futurs (Testcontainers) utilisent la même image.
- **Spring Boot 3** : `spring.data.redis.host` / `SPRING_DATA_REDIS_HOST`, pas `spring.redis.*`.
- **Identifiants générés en mémoire** (`UUID.randomUUID()` dans le constructeur), jamais `@GeneratedValue`.
- **Validation alignée sur le schéma** : toute colonne `VARCHAR(n)` a son `@Size(max = n)` côté DTO, sinon PostgreSQL refuse et l'API répond 500 au lieu de 400. Mot de passe 8 à 72 caractères (limite BCrypt).
- **Chargement paresseux** : `LAZY` + `spring.jpa.open-in-view: false` ⇒ `@EntityGraph` pour toute relation lue hors transaction.
- **Spring Security 6** : `/error` doit être `permitAll`, sinon tout 400/404/500 devient un 403 pour un anonyme. L'ordre des règles de `SecurityConfig` compte (`GET /api/events/mine` avant la règle publique `GET /api/events/**`).
- `@PreAuthorize` s'évalue **après** la validation `@Valid` : pour tester l'autorisation, utiliser une route sans corps.
- **Aucune méthode publique d'`AuthService` n'accepte un `Role`** : le client ne choisit jamais son rôle (`registerOrganisateur` fixe `ORGANISATEUR`). Idem : l'organisateur d'un événement vient du jeton, jamais du corps de la requête.
- **Un brouillon non visible répond 404** avec le même message qu'un identifiant inconnu. Toute lecture exposée passe par `getVisible`, jamais par `get`.
- **409** = `BusinessConflictException` (étend `IllegalStateException`) ou `OptimisticLockingFailureException` (conflit de `@Version`, message générique). Une `IllegalStateException` ordinaire reste un 500.
- **Adaptateurs niveau 3** : `save()` est en `@Transactional(propagation = MANDATORY)`. Tout cas d'usage qui lit puis modifie une catégorie ou un billet doit être `@Transactional`, sinon le verrou de version ne protège rien (l'appel échoue exprès).
- **Adaptateur qui lève une `BusinessConflictException` : `@Component`, jamais `@Repository`.** Spring traduit les exceptions des beans `@Repository` et convertit toute `IllegalStateException` (donc toute `BusinessConflictException`) en `InvalidDataAccessApiUsageException` : le 409 devient un 500. `@DataJpaTest` ne charge pas cette traduction : l'activer dans le test avec `@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)` (voir `JpaTicketIssuanceRepositoryAdapter`).
- **Insertion idempotente** : `INSERT ... ON CONFLICT DO NOTHING` natif + nombre de lignes insérées, pas `save()` (un `merge` sur clé composite fait un `UPDATE` silencieux) ni `persist` (erreur 23505 qui interrompt la transaction PostgreSQL).
- **ArchUnit** : 5 règles actives (la 5e interdit JPA, Hibernate et Spring dans les domaines de niveau 3), **jamais de `allowEmptyShould(true)`**. Après tout nouveau package, vérifier que les règles s'appliquent à au moins une classe (une règle sur un ensemble vide passe au vert sans rien contrôler).
- **Flyway** : ne jamais modifier une migration déjà appliquée ; ajouter la suivante. `.gitattributes` force le LF, sauf `mvnw.cmd`.

## Agents et skills du projet (`.claude/`)

Quand et comment les appeler : `docs/guide-claude-code.md`.

| Agents (lecture seule, sauf `test-writer`) | Rôle |
|---|---|
| `archi-guardian` | Relit un diff ou un module contre les ADR 0001–0004 |
| `security-reviewer` | Autorisation, secrets, signature Ed25519, validation des entrées |
| `db-schema-reviewer` | Migrations Flyway et cohérence entités/DTO/schéma |
| `test-writer` | Écrit et exécute les tests unitaires |

| Skills | Usage |
|---|---|
| `/new-branch <feature\|fix> <contexte-description>` | Crée la branche depuis un `main` à jour |
| `/scaffold-usecase <module> <NomDuCasDUsage>` | Squelette port + cas d'usage + test, selon le niveau du module |
| `/pre-pr` | Lance les trois relecteurs en parallèle sur `main...HEAD` |
| `/ship` | Tests, index propre, ajouts explicites, commits (ne pousse pas) |
| `/http-check` | Scénario HTTP avec contrôle d'identité du serveur |
| `/update-handoff` | Met à jour `docs/HANDOFF.md` après un merge |

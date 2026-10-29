# Contribuer à Billet+

## Prérequis

- **JDK 21** : le `pom.xml` cible `<java.version>21</java.version>` strictement. Vérifier avec `java -version` puis `.\mvnw -version` (dossier `backend/`).
- **Docker Desktop**, pour PostgreSQL + PostGIS et Redis.
- **Node.js 20**, version utilisée par la CI frontend.
- **Git Bash** (fourni avec Git for Windows) uniquement pour exécuter le script de génération de clés, qui est un script bash.

## Démarrage rapide (PowerShell)

Toutes les commandes se lancent **depuis la racine du dépôt**, sauf mention contraire.

```powershell
# 1. Paire de clés Ed25519 de développement (clé privée gitignorée, clé publique partagée backend + web)
bash infra/scripts/generate-ed25519-keys.sh

# 2. PostgreSQL (image PostGIS) + Redis.
#    Le chemin infra/docker-compose.yml est relatif à la racine : ne pas lancer depuis backend/.
docker compose -f infra/docker-compose.yml up -d

# 3. Backend (Flyway applique les migrations au démarrage)
cd backend
.\mvnw spring-boot:run

# 4. Frontend, dans un second terminal, depuis la racine
cd web
npm install
npm run dev
```

> **Image PostgreSQL** : le projet exige `postgis/postgis:16-3.4`. L'image `postgres:16` standard n'embarque pas PostGIS, et la migration `V1__enable_extensions.sql` échouerait. Si un conteneur a déjà été créé avec la mauvaise image, supprimer aussi son volume : `docker compose -f infra/docker-compose.yml down -v` (sans `-v`, le volume existant persiste et conserve l'ancien état).

## Convention de nommage des branches

```
feature/<contexte>-<description>   # nouvelle fonctionnalité
fix/<contexte>-<description>       # correction de bug
```

- Créer chaque branche **juste avant de l'utiliser**, jamais toutes à l'avance : une branche créée trop tôt se désynchronise de `main`.
- La liste des branches planifiées et leur avancement sont suivis dans `HANDOFF.md`.

## Convention de commit — Conventional Commits

```
feat(tickets): ajoute le verrouillage optimiste sur le quota
fix(scanner): corrige la vérification Ed25519 en mode hors ligne
docs(adr): documente le choix PostgreSQL vs NoSQL
test(orders): ajoute les tests unitaires sur les transitions d'état
chore(ci): sépare le job ArchUnit du reste des tests backend
```

## Règle de rigueur architecturale par module

Avant de créer un nouveau module, se référer à [ADR 0002](docs/architecture/adr/0002-hexagonale-par-module.md) :

> Ce module a-t-il un invariant métier qui peut coûter de l'argent ou créer une incohérence irréversible s'il est violé (double vente, remboursement en double) ?
> - Oui → niveau 3 (domaine pur, un use case par classe).
> - Non, mais la logique va évoluer → niveau 2 (port + JPA-as-domain).
> - Non, CRUD ou lecture pure → niveau 1.

Répartition actuelle : `tickets` et `orders` en niveau 3 ; `auth` et `events` en niveau 2.

### Particularités d'un module de niveau 3 (`tickets`, `orders`)

Un module de niveau 3 manipule **deux modèles distincts**, ce qui est le coût d'entrée principal pour qui découvre le projet :

| Modèle | Emplacement | Contraintes |
|---|---|---|
| Domaine (POJO) | `domain/` | Aucune annotation JPA, aucune dépendance à Spring ou à l'infrastructure |
| Entité JPA | `infrastructure/persistence/` | Porte les annotations de mapping ; jamais exposée hors de `infrastructure/` |

- Un **mapper** convertit le domaine vers l'entité JPA et inversement ; un **adaptateur** implémente le port défini dans `application/port/`.
- Le domaine fournit une fabrique `reconstituer(...)` qui recrée un objet depuis la base **sans revalider** les invariants de création.
- Les identifiants sont **générés en mémoire** (`UUID.randomUUID()` dans le constructeur), jamais par `@GeneratedValue` : un objet de domaine construit dans un test, sans Hibernate, doit déjà avoir son `id`.

## Isolation inter-module

- Un module ne référence un autre module que par **UUID brut**, jamais par relation JPA (`@ManyToOne`, `@OneToMany`).
- Aucune contrainte `FOREIGN KEY` en base entre tables de modules différents (exemple : `events.organizer_id` vers `users.id`).
- La communication passe par un événement applicatif (`ApplicationEventPublisher`) ou par l'appel d'un port public du module.

Ces règles sont vérifiées par ArchUnit : ne jamais les contourner même si cela semble plus simple.

## Règles ArchUnit (`ArchitectureTest.java`)

Cinq règles sont actives, sans aucun `allowEmptyShould(true)` :

1. `domaine_ne_depend_jamais_de_infrastructure`
2. `domaine_ne_depend_jamais_de_api`
3. `modules_sont_independants` (le package `shared` est le seul point de partage autorisé, et ne contient aucune logique métier)
4. `ports_ne_dependent_pas_de_spring_data`
5. `domaines_niveau_3_sans_jpa_ni_spring` : aucune dépendance à `jakarta.persistence`, `org.hibernate` ni `org.springframework` dans `tickets.domain` et `orders.domain`. Ne vise pas `auth` ni `events`, qui sont en niveau 2 (JPA-as-domain) par décision (ADR 0002).

À chaque ajout d'un nouveau module ou package (`domain/`, `application/port/`) :

- [ ] Vérifier que les règles s'appliquent bien à au moins une classe du nouveau package. Une règle sur un ensemble vide passe au vert sans rien contrôler.
- [ ] **Ne pas réintroduire `allowEmptyShould(true)`** pour « faire passer » un test : si une règle ne trouve aucune classe, c'est le signe qu'un package est mal nommé ou mal placé.
- [ ] Relancer `.\mvnw test "-Dtest=ArchitectureTest"` depuis `backend/`.

Pour prouver qu'une règle (nouvelle ou modifiée) contrôle réellement quelque chose, la tester **à l'envers** : ajouter temporairement une classe sonde qui la viole (par exemple une classe annotée `@jakarta.persistence.Entity` dans `tickets/domain/`), constater l'échec, supprimer la sonde, puis relancer avec `.\mvnw clean test`. Le `clean` est indispensable : ArchUnit lit les `.class` de `target/`, et celui de la sonde y resterait.

## Tests avant de pousser

```powershell
# Backend, depuis backend/
.\mvnw test                              # tous les tests, ArchUnit inclus
.\mvnw test "-Dtest=ArchitectureTest"    # uniquement les règles d'architecture

# Frontend, depuis web/
npm run lint
npm run test
```

> Les guillemets autour de `-Dtest=...` évitent que PowerShell n'interprète le point ou la virgule du paramètre.

## Fins de ligne

`.gitattributes` normalise tout en **LF**, sauf `mvnw.cmd` (CRLF). Sous Windows, l'avertissement `LF will be replaced by CRLF` est normal : ce n'est pas une erreur.

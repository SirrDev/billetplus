# Guide d'utilisation de Claude Code sur Billet+

Ce guide dit **quand** et **comment** appeler chaque agent et chaque skill du projet. Il complète `CLAUDE.md` (les règles) et `docs/HANDOFF.md` (l'état du projet).

## 1. Comprendre les quatre briques

| Brique | Qui la déclenche | Ce qu'elle fait | Analogie |
|---|---|---|---|
| `CLAUDE.md` | Chargé automatiquement à chaque session | Rappelle les règles du projet : français, PowerShell, ADR, pièges connus | Le règlement intérieur |
| **Règles** (`.claude/rules/`) | Chargées automatiquement quand Claude touche un fichier concerné | Pureté du domaine (`tickets`/`orders`), règles Flyway | Les consignes affichées dans un atelier précis |
| **Agents** (`.claude/agents/`) | Claude, ou toi par une phrase | Un relecteur qui travaille **dans son propre contexte** et ne te rend qu'un rapport | Un collègue spécialisé à qui on confie une relecture |
| **Skills** (`.claude/skills/`) | **Toi seul**, avec `/nom` | Une procédure en plusieurs étapes, toujours la même | Une checklist exécutée à la lettre |

**Pourquoi des agents plutôt que de tout demander à la session principale ?** Un agent lit beaucoup de fichiers dans un contexte à part : ta conversation reste courte et lisible, et la relecture n'est pas influencée par ce que vous venez de coder ensemble. C'est ce qui donne un avis plus indépendant.

**Pourquoi des skills déclenchés par toi seul ?** Ils créent des branches, des commits ou lancent des tests : tu veux choisir le moment.

## 2. Le cycle d'une branche

```
/new-branch  →  coder  →  relecteurs  →  /pre-pr  →  /ship  →  git push (toi)  →  PR  →  merge  →  /update-handoff
```

| Étape | Outil | Quand |
|---|---|---|
| 1. Créer la branche | `/new-branch feature backend-tickets-jpa-mapper` | Juste avant de commencer (jamais à l'avance) |
| 2. Cadrer | Une phrase libre : « Lis la section 11 de `docs/HANDOFF.md` et propose un plan avant de coder » | Début de session |
| 3. Squelette | `/scaffold-usecase tickets IssueTicket` | Chaque nouveau cas d'usage |
| 4. Tests | Agent `test-writer` | Après chaque classe de domaine ou cas d'usage |
| 5. Relecture au fil de l'eau | `archi-guardian`, `db-schema-reviewer`, `security-reviewer` | Après un bloc de code cohérent (voir § 4) |
| 6. Contrôle final | `/pre-pr` | Quand la branche est terminée, **avant** de livrer |
| 7. Livrer | `/ship` | Quand `/pre-pr` ne signale rien de bloquant |
| 8. Pousser | `git push -u origin <branche>` (toi) | Après `/ship` |
| 9. Après le merge | `/update-handoff` | Sur `main` à jour, après « Sync now » |

## 3. Les skills, un par un

### `/new-branch <feature|fix> <contexte-description>`
- **Quand** : au début de chaque branche.
- **Exemple** : `/new-branch fix auth-agent-account-creation`
- **Ce qu'il fait** : refuse un nom invalide, s'arrête si des fichiers non commités traînent (ils suivraient le changement de branche), puis `main` → `pull` → création.
- **À ne pas faire** : créer plusieurs branches d'avance (elles se désynchronisent de `main`).

### `/scaffold-usecase <module> <NomDuCasDUsage>`
- **Quand** : pour un nouveau cas d'usage dans `tickets`, `orders`, `auth` ou `events`.
- **Exemple** : `/scaffold-usecase tickets IssueTicketUseCase`
- **Ce qu'il fait** : lit l'ADR 0002 et le module existant, annonce le niveau (3 pour `tickets`/`orders`, 2 pour `auth`/`events`), crée le port, la classe de cas d'usage et son test, exécute les tests.
- **Ce qu'il ne fait pas** : contrôleur, migration, commit. À toi de les demander ensuite.
- **À ne pas faire** : l'utiliser pour un simple mapper ou une entité JPA (ce n'est pas un cas d'usage).

### `/pre-pr`
- **Quand** : branche terminée, avant `/ship`.
- **Ce qu'il fait** : détecte ce qui a changé, lance en **parallèle** les relecteurs utiles sur `main...HEAD`, exécute les tests, puis te rend **un** verdict : `PRÊT`, `À CORRIGER` ou `BLOQUANT`.
- **Que faire du résultat** : corriger les points bloquants, relancer `/pre-pr`, puis `/ship`.

### `/ship`
- **Quand** : après `/pre-pr`, ou pour une petite modification évidente.
- **Ce qu'il fait** : tests verts (sinon il s'arrête) → inventaire des fichiers → un commit logique à la fois : `git restore --staged .`, `git add` par chemin explicite, `git diff --cached --stat`, `git commit`. Il signale et **exclut** tout fichier qui ne semble pas appartenir à la branche.
- **Ce qu'il ne fait jamais** : pousser, merger, `git add .`.
- **Pourquoi `--stat` avant chaque commit** : c'est le seul moment où l'on voit exactement ce qui va entrer dans l'historique.

### `/http-check <ce qu'il faut vérifier>`
- **Quand** : une route d'autorisation, de visibilité ou de validation change, ou avant de dire « ça marche ».
- **Exemple** : `/http-check vérifier qu'un organisateur ne peut pas publier l'événement d'un autre (403) et qu'un brouillon d'un autre répond 404`
- **Prérequis (toi)** : Docker démarré, puis dans un autre terminal :
  ```powershell
  Set-Location C:\Users\bague\Desktop\billetplus\backend
  $env:SERVER_PORT = "8081"
  .\mvnw spring-boot:run
  ```
- **Pourquoi 8081 et le contrôle d'identité** : `footbooking-api` occupe le 8080 et renvoie des 400/404 trompeurs. Le script vérifie d'abord que c'est bien Billet+ qui répond.
- **Ce que tu obtiens** : un tableau étape / attendu / obtenu / extrait du corps.

### `/update-handoff [branche]`
- **Quand** : après le merge, sur `main` à jour, après « Sync now ».
- **Exemple** : `/update-handoff backend-tickets-jpa-mapper`
- **Ce qu'il fait** : met à jour le tableau des branches, la date, le nombre de tests (mesuré), les décisions prises, les risques et la prochaine étape. Il ne devine rien : ce qui est incertain reste « à confirmer ».
- **Ensuite** : `/ship` pour commiter le résultat (`docs(handoff): ...`).

## 4. Les agents, un par un

**Comment les appeler** (trois façons, de la plus courante à la plus rare) :

| Méthode | Exemple |
|---|---|
| Phrase libre | « Utilise l'agent `archi-guardian` pour relire le module `tickets` » |
| @-mention | `@"archi-guardian (agent)"` suivi de ta demande |
| Session entière | `claude --agent archi-guardian` (toute la session devient ce relecteur) |

Claude peut aussi les déléguer de lui-même quand la description le suggère (« à utiliser de façon proactive »). Si tu veux être sûr qu'une relecture a lieu, **demande-la explicitement**.

### `archi-guardian` : l'architecture
- **Quand** : après tout bloc de code dans `backend/src/main/java`, surtout un nouveau module, un nouveau package ou un mapper.
- **Il contrôle** : niveaux 2/3, domaine sans annotation JPA ni Spring, isolation inter-module (UUID brut, aucune relation JPA), `shared` sans logique métier, règles ArchUnit.
- **Exemple** : « `archi-guardian`, relis `tickets/infrastructure/persistence` et dis-moi si le domaine reste pur »

### `security-reviewer` : la sécurité
- **Quand** : tout code qui touche auth, `SecurityConfig`, contrôleurs, signature ou scan de billets, quota, paiement.
- **Il contrôle** : rôle et propriété vérifiés, identité tirée du jeton, 401/403/404, validation, secrets, Ed25519, double scan, quota atomique, énumération d'emails.
- **Particularité** : il utilise le modèle `opus` (plus lent, plus coûteux) : réserve-le aux changements sensibles, pas à une faute de frappe.
- **Il ne lit jamais** une clé privée : il vérifie seulement où elle est référencée.

### `db-schema-reviewer` : la base de données
- **Quand** : migration, entité JPA, DTO d'entrée ou repository modifié.
- **Il contrôle** : migrations appliquées jamais modifiées, entité ↔ schéma (`validate`), `@Size` ↔ `VARCHAR`, aucune clé étrangère inter-module, `@EntityGraph` sur les relations paresseuses.
- **Ce qu'il ne peut pas voir** : le démarrage réel de l'application. Lance-la pour confirmer.

### `test-writer` : les tests
- **Quand** : après chaque classe de domaine ou cas d'usage.
- **Exemple** : « `test-writer`, couvre `Ticket` : cas nominal, chaque refus, et le double scan »
- **Il écrit** dans `backend/src/test/` seulement, et exécute la suite.
- **Limite** : pas de test de concurrence réelle avec des mocks. Pour le verrou de quota, il faut un test d'intégration avec Testcontainers (image `postgis/postgis:16-3.4`).

## 5. Quels outils pour quelle branche

Recommandation d'après le plan du `HANDOFF.md` (à ajuster avec ton jugement) :

| Branche | Outils conseillés |
|---|---|
| `backend-tickets-jpa-mapper` | `archi-guardian` (domaine pur, mapper), `db-schema-reviewer` (entités ↔ schéma), `test-writer` (aller-retour du mapper, `reconstituer`) |
| `tickets-issue-usecase` | `/scaffold-usecase`, `test-writer`, `archi-guardian`, `security-reviewer` (signature Ed25519) |
| `tickets-quota-lock` | `db-schema-reviewer`, `security-reviewer` (atomicité), `test-writer`, puis un test d'intégration |
| `tickets-redis-seat-lock` | `archi-guardian` (port, adaptateur), `security-reviewer`, `test-writer` |
| `tickets-validate-scan-usecase` | `/scaffold-usecase`, `security-reviewer` **obligatoire** (double scan, vérification hors ligne), `/http-check` |
| `tickets-pdf-export` | `archi-guardian`, `security-reviewer` (fuites d'information) |
| `orders-domain-model` | `/scaffold-usecase`, `archi-guardian`, `test-writer` (transitions d'état) |
| `orders-create-usecase`, `orders-confirm-payment-usecase` | `security-reviewer` **obligatoire** (prix côté serveur, idempotence), `test-writer`, `archi-guardian` |
| `orders-wave-gateway` | `security-reviewer` **obligatoire** (signature et rejeu des notifications), `archi-guardian` |
| `rate-limiting` | `security-reviewer`, `/http-check` |
| Création de comptes `AGENT` (HANDOFF § 7) | `security-reviewer` obligatoire, `/http-check` |

Le frontend (`web/`) n'est pas couvert par ces agents, qui sont écrits pour le backend.

## 5 bis. Exemple de session complète

```text
> /new-branch feature backend-tickets-jpa-mapper
> Lis la section 11 de docs/HANDOFF.md et propose un plan avant de coder.
  (tu valides ou tu corriges le plan)
> Implémente le plan.
> test-writer, couvre les mappers : aller-retour domaine ↔ entité, et reconstituer sans revalidation.
> archi-guardian et db-schema-reviewer, relisez la branche.
  (tu lis les rapports ; tu fais corriger ce qui est signalé)
> /pre-pr
> /ship
```

Puis, toi, dans PowerShell : `git push -u origin feature/backend-tickets-jpa-mapper`, PR sur GitHub, attente des workflows verts, merge, `git switch main`, `git pull`, « Sync now » dans le projet Claude, et enfin `/update-handoff backend-tickets-jpa-mapper`.

## 6. Lire un rapport sans se faire piéger

- Un relecteur peut se tromper dans les deux sens : oublier un défaut, ou en signaler un qui n'existe pas. Va voir le `fichier:ligne` cité avant de corriger.
- Chaque rapport distingue ce qui est **démontré** dans le code de ce qui est **suspecté**, et liste les **points non vérifiables**. Ces derniers sont ta liste de tests à faire (HTTP, démarrage réel, concurrence).
- « Aucun problème » signifie « aucun problème dans ce qui a été vérifié » : lis la section « Points vérifiés ».
- Un agent ne remplace pas `.\mvnw test` ni `ArchitectureTest` : ce sont des contrôles automatiques, les agents sont des relectures.
- Tout ce qui touche à l'argent ou aux accès mérite ton propre regard, même après un verdict `RAS`.

## 7. Hygiène de session

- **Une branche = une session.** Pour une nouvelle branche, redémarre avec un contexte propre (`/clear`, ou une nouvelle session).
- Au démarrage, `/context` doit lister `CLAUDE.md` dans les **Memory files**. Sinon, tu n'es pas lancé depuis la racine du dépôt.
- **Nouveau dossier créé en cours de session** (par exemple `.claude/agents/`) : redémarre Claude Code pour qu'il soit pris en compte.
- Donne des demandes précises : un fichier, un module, un comportement attendu. « Améliore le code » ne produit rien d'exploitable.
- Si une réponse contredit un ADR, ne cède pas : relis l'ADR, et exige une raison **nouvelle et explicite** pour le remettre en cause.

## 8. Quand quelque chose ne marche pas

| Symptôme | Cause probable | Que faire |
|---|---|---|
| `/ship` ou `/new-branch` n'apparaît pas | Fichiers non chargés | Vérifie `.claude/skills/<nom>/SKILL.md` ; relance Claude Code depuis la racine |
| Un agent n'est pas utilisé | Description trop vague pour ta demande | Demande-le nommément, ou utilise `@"nom (agent)"` |
| Prompt de permission pour `mvnw` | Règle absente pour ton shell | Choisis « Yes, and don't ask again » ; elle va dans `.claude/settings.local.json` (non commité) |
| `/http-check` répond « ce serveur n'est pas Billet+ » | Application non lancée, ou lancée sur un autre port | Lance-la sur 8081 (voir `/http-check`) |
| Échec au démarrage `Connection to localhost:5432 refused` | Docker éteint | `docker compose -f infra/docker-compose.yml up -d`, depuis la racine |
| `/ship` refuse de continuer | Un test est rouge | Corrige : on ne commite pas du rouge |
| Un fichier inattendu apparaît dans `git status` | Fichier personnel, copie téléchargée, clé | Ne l'ajoute pas ; compare avec `.gitignore` |

## 9. Faire évoluer cette configuration

- Un piège rencontré deux fois mérite une ligne dans la section « Pièges » de `CLAUDE.md`. Garde ce fichier sous 200 lignes (au-delà, l'adhérence aux consignes baisse) ; déplace le spécifique dans `.claude/rules/` avec un `paths:`.
- Un `SKILL.md` ou un agent qui se trompe : corrige son texte, commite-le comme du code (`chore(claude): ...`).
- Deux instructions qui se contredisent sont lues arbitrairement : cherche-les (`/doctor prompt-audit`).
- Les préférences strictement personnelles vont dans `CLAUDE.local.md` et `.claude/settings.local.json`, tous deux ignorés par Git.

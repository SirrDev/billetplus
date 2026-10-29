---
name: new-branch
description: "Crée une branche Billet+ (feature/ ou fix/) depuis un main à jour, en respectant la convention de nommage."
disable-model-invocation: true
argument-hint: <feature|fix> <contexte-description>
arguments: [kind, slug]
allowed-tools: Bash(git status *) Bash(git switch *) Bash(git pull *) Bash(git branch --list *)
---

Crée la branche `$kind/$slug`.

1. **Valide le nom** avant toute commande git :
   - `$kind` vaut exactement `feature` ou `fix` ;
   - `$slug` est en minuscules, mots séparés par des tirets, et commence par un **contexte** (`backend`, `web`, `infra`, `auth`...) suivi d'une description (exemples : `backend-tickets-jpa-mapper`, `auth-register-role-restriction`) ;
   - si le nom est invalide, propose un nom corrigé et arrête-toi pour confirmation.
2. Vérifie l'état : `git status --short`. Si des fichiers sont modifiés ou non suivis, **liste-les et demande quoi faire** : ils suivraient le changement de branche et finiraient dans la mauvaise PR. Ne fais ni `git stash` ni `git restore` sans accord.
3. `git switch main`, puis `git pull` : une branche créée depuis un `main` périmé se désynchronise (la branche d'alignement l'a montré).
4. Vérifie qu'elle n'existe pas déjà (`git branch --list "$kind/$slug"`), puis crée-la : `git switch -c $kind/$slug`.
5. Confirme en une ligne : branche créée, `main` à jour sur quel commit (`git log --oneline -1`).

Rappel à donner si la branche touche `tickets` ou `orders` : niveau 3 (domaine pur, voir `CLAUDE.md`), et créer chaque branche seulement au moment de l'utiliser.

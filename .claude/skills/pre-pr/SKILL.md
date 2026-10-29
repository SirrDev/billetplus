---
name: pre-pr
description: "Relecture complète avant une PR Billet+ : lance en parallèle archi-guardian, security-reviewer et db-schema-reviewer sur le diff main...HEAD, puis synthétise."
disable-model-invocation: true
allowed-tools: Bash(git status *) Bash(git diff *) Bash(git log *)
---

Prépare la PR de la branche courante.

1. `git status --short` et `git log --oneline main..HEAD` : si la branche est `main`, ou s'il n'y a aucun commit d'avance, arrête-toi et dis pourquoi. Liste les fichiers modifiés : `git diff main...HEAD --name-only`.
2. Choisis les relecteurs selon ce qui a changé (sinon, tous les trois) :
   - `archi-guardian` : tout changement dans `backend/src/main/java`.
   - `security-reviewer` : auth, `SecurityConfig`, contrôleurs, signature/scan de billets, paiement.
   - `db-schema-reviewer` : migrations, entités JPA, DTO d'entrée, repositories.
3. **Lance les relecteurs choisis en parallèle** (un seul message, plusieurs appels d'agent), en leur indiquant le périmètre : `git diff main...HEAD`. Chacun est en lecture seule.
4. Pendant ce temps, exécute depuis `backend/` : `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"`, et note le résultat réel.
5. **Synthèse** (ne recopie pas les rapports) :
   - Verdict global : `PRÊT`, `À CORRIGER` ou `BLOQUANT`.
   - Problèmes dédoublonnés, du plus grave au moins grave, avec `fichier:ligne` et la correction.
   - Résultat des tests (nombre exécuté, échecs).
   - Ce qui n'a pas pu être vérifié (démarrage réel, HTTP, concurrence).
6. S'il n'y a rien de bloquant, propose un titre de PR (Conventional Commits) et une description courte : contexte, changements, vérifications faites. Ne crée ni ne pousse rien.

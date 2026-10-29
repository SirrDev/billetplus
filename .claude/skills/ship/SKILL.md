---
name: ship
description: "Livre le travail en cours de la branche Billet+ : tests verts, index propre, ajouts par chemin explicite, commits Conventional Commits. Ne pousse jamais."
disable-model-invocation: true
allowed-tools: Bash(git status *) Bash(git diff *) Bash(git log *) Bash(git restore --staged *) Bash(git add *) Bash(git commit *) Bash(./mvnw *) Bash(.\mvnw *)
---

Prépare les commits de la branche courante. Procédure rodée du projet : respecte l'ordre.

1. **Branche** : `git status --short` et nom de la branche. Si on est sur `main`, arrête-toi : demande de créer une branche (`/new-branch`).
2. **Tests, avant tout commit** : depuis `backend/`, `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"` (si seul `web/` ou la doc a changé, adapte : `npm run lint` et `npm run test` depuis `web/`). **Si un test échoue, arrête-toi** et rapporte l'échec réel : on ne commite pas du rouge.
3. **Inventaire** : liste les fichiers modifiés et non suivis, puis regroupe-les en **commits logiques** (un changement cohérent par commit). Tout fichier qui ne semble pas appartenir au travail de la branche (`.idea`, fichiers temporaires, copie téléchargée, clé, `.env`) est **signalé et exclu** ; ne le rajoute pas sans accord.
4. Pour **chaque** commit :
   - `git restore --staged .` (repart d'un index propre, ne touche pas aux fichiers) ;
   - un `git add <chemin>` par fichier ou dossier précis, **jamais** `git add .`, `-A` ni `commit -a` ;
   - `git diff --cached --stat` : compare la liste **exactement** à ce que le commit annonce ; au moindre fichier inattendu, arrête-toi ;
   - `git commit -m "<type>(<contexte>): <description>"` : Conventional Commits en français, sans accents ni caractères spéciaux, impératif, moins de 72 caractères (`feat`, `fix`, `docs`, `test`, `chore`, `refactor`).
5. Termine par `git log --oneline -5` et `git status --short` (doit être vide, hors fichiers volontairement exclus).
6. **Ne pousse pas.** Donne la commande exacte pour SirrDev, en PowerShell, avec son explication :
   `git push -u origin <branche>` (`-u` associe la branche locale à la distante pour les prochains `push` / `pull`), puis propose le titre et la description de PR (ou renvoie vers `/pre-pr` si la relecture n'a pas été faite).

Résume en trois lignes : tests (résultat réel), commits créés, fichiers exclus.

---
name: update-handoff
description: "Met à jour docs/HANDOFF.md après le merge d'une branche Billet+ (tableau des branches, date, tests, prochaine étape) sans inventer d'information."
disable-model-invocation: true
allowed-tools: Bash(git status *) Bash(git log *) Bash(git diff *) Bash(./mvnw *) Bash(.\mvnw *)
---

Mets à jour `docs/HANDOFF.md` après un merge. Branche concernée (facultative) : $ARGUMENTS

## Règle d'or

Le HANDOFF décrit l'**état réel**. N'écris que ce que tu peux établir : l'historique Git, le code, le résultat d'un test exécuté maintenant, ou ce que SirrDev te dit. Ce qui est incertain reste marqué « à confirmer », pas deviné.

## Étapes

1. **Lire** `docs/HANDOFF.md` en entier, puis `git status --short` (le dépôt doit être sur `main` à jour ; sinon, dis-le) et `git log --oneline -15`.
2. **Identifier ce qui a changé** depuis la date d'en-tête : branches mergées (messages `Merge pull request #N`, commits associés), fichiers touchés. Si la branche mergée est `$ARGUMENTS`, concentre-toi sur elle.
3. **Mesurer** : exécute `.\mvnw test` depuis `backend/` et relève le nombre **réel** de tests et d'échecs. Si tu ne peux pas l'exécuter, laisse l'ancien chiffre avec la mention « non remesuré ».
4. **Mettre à jour**, section par section :
   - **En-tête** : date du jour et une phrase sur ce qui vient d'être mergé.
   - **Section 3** (état du dépôt) : ligne de la branche mergée → `✅ Mergée le JJ/MM/AAAA` avec les hash de commits ; avancement estimé (mentionné comme un ordre de grandeur) ; avertissements devenus faux supprimés.
   - **Section 4 ou dédiée** : décisions prises pendant la branche (routes, règles de sécurité, exceptions, conventions) que les branches suivantes doivent respecter.
   - **Section 7** (points ouverts) : ajoute les nouveaux risques connus, retire ceux qui sont résolus.
   - **Section 10** (problèmes résolus) : ajoute les pièges rencontrés, avec la cause et la solution.
   - **Section 11** (prochaine étape) : désigne la branche suivante du plan avec son objectif, ses fichiers et ses pièges.
5. **Montrer le diff** (`git diff docs/HANDOFF.md`) et lister explicitement ce que tu as laissé « à confirmer ». Ne commite pas : `/ship` s'en charge, avec un commit `docs(handoff): ...`.

## Interdits

Ne change aucune décision d'architecture actée. N'efface pas l'historique utile (les décisions passées restent). N'invente ni hash de commit, ni numéro de PR, ni date de merge : si le Git ne les donne pas, demande.

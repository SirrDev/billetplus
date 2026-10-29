---
name: http-check
description: "Exécute un scénario HTTP contre le backend Billet+ local (port 8081) en vérifiant d'abord que c'est bien Billet+ qui répond, et affiche codes ET corps des réponses. À utiliser pour valider autorisation, visibilité, validation et conflits d'une route."
disable-model-invocation: true
argument-hint: <ce qu'il faut vérifier>
allowed-tools: Bash(powershell *)
---

Valide en HTTP : $ARGUMENTS

## Pourquoi ce skill existe

Le port 8080 de la machine est occupé par `footbooking-api` (autre projet) : un test lancé dessus renvoie des 400/404 trompeurs (corps avec `"error":"Bad Request"` et `"path"`, que le `GlobalExceptionHandler` de Billet+ ne produit jamais). Billet+ tourne donc sur **8081**, et tout scénario commence par un **contrôle d'identité**.

## Étapes

1. **Prérequis** : Docker démarré (`docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"`, base `healthy`) et application lancée par SirrDev dans un autre terminal : `$env:SERVER_PORT = "8081"` puis `.\mvnw spring-boot:run` depuis `backend/`. Si le serveur ne répond pas, **dis-le et arrête-toi** : ne le lance pas en arrière-plan sans accord, et ne conclus rien.
2. **Écrire le scénario dans un fichier `.ps1`** (jamais en bloc collé : PSReadLine plante sur les longs blocs), à partir de `scenario-template.ps1` de ce dossier : garde le contrôle d'identité et la fonction `Send-Request`, remplace uniquement la partie « Scénario ». Place le fichier dans le dossier temporaire de la session, pas dans le dépôt.
3. **Exécuter** : `powershell -NoProfile -ExecutionPolicy Bypass -File <chemin>.ps1`.
4. **Rapporter un tableau** : étape, requête, code attendu, code obtenu, extrait du corps. Marque clairement ce qui diffère de l'attendu et ce qui n'a pas pu être exécuté.

## Règles de scénario

- Les tokens d'accès expirent après 15 minutes : crée les comptes en début de scénario.
- Suffixe aléatoire dans les e-mails (`Get-Random`) pour pouvoir relancer sans conflit.
- Pas d'accents dans les corps JSON (PowerShell 5.1 les encode mal).
- Depuis le correctif `/register`, tout compte créé par l'API est `ORGANISATEUR` : aucune route ne crée un `AGENT` ni un `ADMIN`. Pour tester un agent, il faut passer le rôle en base ; demande-le à SirrDev plutôt que de modifier la base.
- Pour tester l'**autorisation**, utilise une route sans corps (un `@PreAuthorize` s'évalue après `@Valid` : un corps invalide donnerait 400 avant 403).
- Ne teste pas contre un autre serveur que Billet+ ; ne copie jamais de secrets réels dans un script.

---
name: scaffold-usecase
description: "Génère le squelette d'un cas d'usage Billet+ (port, classe de cas d'usage, test) selon le niveau de rigueur du module (ADR 0002). À utiliser pour ajouter un cas d'usage à tickets, orders, auth ou events."
disable-model-invocation: true
argument-hint: <module> <NomDuCasDUsage>
arguments: [module, usecase]
---

Crée le cas d'usage `$usecase` dans le module `$module`.

## 1. Comprendre avant d'écrire

- Lis `docs/architecture/adr/0002-hexagonale-par-module.md` (niveaux) et le `CLAUDE.md`.
- Détermine le niveau du module (`tickets`, `orders` : 3 ; `auth`, `events` : 2 ; autre module : applique la règle de décision de l'ADR et **annonce ton choix**).
- Lis le module existant (`backend/src/main/java/com/billetplus/$module/`) : packages, noms, style des ports et des cas d'usage voisins, exceptions utilisées, tests existants. **Imite l'existant** plutôt que d'inventer une convention. Si le module n'existe pas encore, dis-le et propose la structure avant de créer quoi que ce soit.

## 2. Ce que tu produis

- `application/port/` : le port nécessaire s'il n'existe pas déjà (interface, aucune dépendance à Spring Data ni à JPA).
- `application/usecase/$usecase.java` : **une classe par cas d'usage**, dépendant uniquement de ports et du domaine. Les erreurs métier étendent `BusinessConflictException` (409) quand c'est un conflit.
- **Niveau 3** : la logique et les invariants vivent dans le **domaine** (POJO pur, id généré en mémoire) ; le cas d'usage orchestre. Si une persistance est requise, crée aussi (ou signale qu'il reste à faire) l'entité JPA séparée, le mapper et l'adaptateur dans `infrastructure/persistence/`.
- **Niveau 2** : port + entité JPA servant de modèle ; pas de mapper.
- `src/test/...` : un test du cas d'usage avec port simulé (Mockito), cas nominal + chaque refus métier, noms de tests en français sous forme d'énoncé.
- Aucun contrôleur ni migration, sauf demande explicite.

## 3. Vérifier

- Compile et exécute depuis `backend/` : `.\mvnw test` puis `.\mvnw test "-Dtest=ArchitectureTest"`.
- Si un nouveau package a été créé, vérifie que les 7 règles ArchUnit s'y appliquent (jamais de `allowEmptyShould`).
- Rapporte le résultat **réel** des tests, la liste des fichiers créés, et ce qui reste à faire (adaptateur JPA, contrôleur, migration). Ne commite rien : `/ship` s'en charge.

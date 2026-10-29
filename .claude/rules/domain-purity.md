---
paths:
  - "backend/src/main/java/com/billetplus/{tickets,orders}/domain/**/*.java"
---

# Domaine de niveau 3 (`tickets`, `orders`) : pureté stricte

Ces fichiers sont des POJO de domaine. Règles (ADR 0002) :

- **Aucune annotation JPA** (`@Entity`, `@Id`, `@Column`, `@Version`...), **aucune annotation Spring**, aucun import de `jakarta.persistence`, `org.springframework` ou de `..infrastructure..`. La persistance vit dans `infrastructure/persistence/` (entité JPA + mapper + adaptateur).
- L'identifiant est généré **en mémoire** (`UUID.randomUUID()` dans le constructeur), jamais par `@GeneratedValue` : un objet construit dans un test, sans Hibernate, a déjà son `id`.
- Les invariants sont protégés par le domaine lui-même (constructeur et méthodes), pas par un service externe.
- Une fabrique `reconstituer(...)` recrée l'objet depuis la base **sans revalider** les invariants de création.
- Référence à un autre module : `UUID` brut uniquement, jamais l'objet ou l'entité d'un autre module.
- Les erreurs de conflit métier étendent `BusinessConflictException` (traduites en 409), pas une exception technique.

# 0003 — PostgreSQL comme base de données unique, plutôt qu'un système NoSQL distribué

## Statut

Acceptée — 17 août 2026

## Contexte

Le domaine billetterie de Billet+ présente trois propriétés qui contraignent fortement le choix de la base de données :

1. **Intégrité transactionnelle critique.** Une vente de billet touche plusieurs entités en même temps (décrémenter un quota, créer une commande, créer un ou plusieurs billets, enregistrer un paiement) et doit être atomique : si une seule écriture échoue, aucune ne doit être committée — sinon le produit vend plus de billets que de places disponibles (overselling) ou facture sans émettre de billet.
2. **Contraintes d'unicité et de forte concurrence.** Un siège ne peut être vendu qu'une fois sur un événement donné. Lors de la mise en vente d'un événement à forte demande, des milliers de requêtes concurrentes peuvent viser les mêmes dernières places disponibles au même instant.
3. **Besoins de reporting et d'agrégation.** Le dashboard organisateur (ventes par heure, revenus, taux de remplissage) et les exports comptables reposent sur des jointures et des agrégations classiques entre événements, billets, commandes et paiements.

Ces trois propriétés pointent structurellement vers une base **relationnelle avec garanties ACID fortes**. Le principal argument en faveur d'un système NoSQL (document store type MongoDB, base large-column type Cassandra) serait un besoin de schéma flexible ou d'écriture massivement distribuée — aucun des deux n'est vérifié ici, et opter pour un tel système obligerait à répliquer en code applicatif ce qu'une base relationnelle garantit nativement (atomicité, unicité, isolation des transactions concurrentes).

Il faut par ailleurs vérifier que le choix relationnel reste pertinent à la volumétrie réelle attendue, et non uniquement par défaut.

### Volumétrie attendue

| Palier | Événements/mois | Billets/an (cumul) |
|---|---|---|
| MVP — Année 1 (Dakar) | 20 | ≈ 120 000 |
| V2 — Année 2 (3 villes) | 80 | ≈ 600 000 |
| V3 — Année 3+ (10 villes) | 300 | ≈ 2 à 3,5 millions |

Même au scénario mature à trois ans, le volume cumulé reste de l'ordre de quelques millions de lignes — largement dans les capacités d'une instance PostgreSQL unique correctement indexée, qui tient nativement des dizaines voire centaines de millions de lignes par table avec un partitionnement basique. Aucun système distribué n'est justifié à cette échelle.

## Décision

**PostgreSQL 16+ est la base de données unique de Billet+**, en instance managée unique au MVP, avec une stratégie de montée en charge progressive (réplique de lecture, puis partitionnement) plutôt qu'un système distribué dès le départ.

### Maîtrise de la concurrence à l'achat (anti-overselling)

Deux mécanismes complémentaires, choisis selon le niveau de charge :

- **Verrouillage optimiste JPA (`@Version`)** sur le quota de chaque catégorie de billet, pour le trafic courant.
- **Sous très forte charge** (pic de mise en vente) : une requête SQL atomique, sans lecture puis écriture séparées, donc sans race condition possible :

```sql
UPDATE ticket_categories
SET quota_vendu = quota_vendu + :quantite
WHERE id = :categoryId
  AND quota_vendu + :quantite <= quota_total;
-- 0 ligne affectée → quota épuisé, rollback applicatif
```

- **Réservation temporaire du panier** pendant le tunnel de paiement (Wave, potentiellement 1 à 2 minutes) via **Redis**, avec une clé à expiration courte plutôt qu'un état en base : si le paiement échoue ou expire, la clé expire automatiquement et la place redevient disponible, sans job de nettoyage nécessaire.

### Modélisation des besoins semi-structurés, sans base spécialisée séparée

- Le plan de salle (structure variable selon le lieu) est stocké en `JSONB`, indexé en `GIN` — évite d'introduire un document store dédié pour ce seul besoin.
- La recherche géolocalisée ("événements à moins de 5 km") utilise l'extension **PostGIS**.
- La recherche texte (artiste, titre, lieu) utilise l'extension **pg_trgm** — couvre les besoins du MVP sans introduire Elasticsearch.

### Migrations de schéma versionnées

Le schéma est géré par **Flyway** (fichiers SQL numérotés, appliqués automatiquement et dans l'ordre au démarrage). Hibernate est configuré en `ddl-auto: validate` : il ne modifie jamais le schéma, il vérifie seulement sa cohérence avec les entités JPA. Le schéma devient ainsi aussi traçable et reproductible que le code applicatif — nécessaire pour un monolithe modulaire où plusieurs modules ([ADR 0001](0001-monolithe-modulaire.md)) partagent la même base et évoluent en parallèle.

### Stratégie de montée en charge

| Palier | Action |
|---|---|
| MVP (Année 1) | Une seule instance PostgreSQL (2-4 vCPU, 4-8 Go RAM), pool de connexions HikariCP par défaut |
| V2 (Année 2) | Ajout d'une réplique de lecture, pour isoler le reporting/dashboard des écritures de vente |
| V3 (Année 3+) | Partitionnement natif de `tickets` et `orders` par période (`PARTITION BY RANGE`), archivage des événements clos |

Le sharding horizontal (plusieurs bases physiques) n'est envisagé à aucun palier de ce projet : il ne deviendrait pertinent qu'à des volumétries d'un tout autre ordre de grandeur, hors de portée même du scénario mature à trois ans.

## Alternatives considérées

### MongoDB (document store)

Rejetée. Le schéma flexible qu'apporte MongoDB n'est pas un besoin réel ici — le domaine billetterie est au contraire fortement structuré et relationnel (événements, catégories, commandes, billets, paiements liés par des clés). L'absence de transactions multi-documents natives aussi robustes qu'en relationnel obligerait à réimplémenter en applicatif les garanties d'atomicité nécessaires à l'anti-overselling.

### Cassandra / base large-column distribuée

Rejetée. Pertinente pour de l'écriture massivement distribuée géographiquement à très grande échelle — un ordre de grandeur largement au-dessus du scénario mature à 3 ans de Billet+ (voir tableau de volumétrie ci-dessus). Le modèle de cohérence éventuelle de Cassandra est en outre mal adapté à un besoin d'unicité stricte (un siège vendu une seule fois).

### Elasticsearch pour la recherche

Écarté au MVP. `pg_trgm` couvre les besoins de recherche texte du MVP (artiste, titre, lieu) sans introduire de composant d'infrastructure supplémentaire à opérer. À réévaluer en V2/V3 si les besoins de recherche full-text deviennent significativement plus riches (facettes, pertinence avancée, autocomplétion à grande échelle).

### Sharding horizontal dès le MVP

Rejeté. Aucune justification à la volumétrie attendue, même au scénario mature à trois ans (2 à 3,5 millions de lignes cumulées). Introduirait une complexité opérationnelle et applicative majeure sans bénéfice mesurable.

## Conséquences

**Positives**

- Les garanties ACID de PostgreSQL éliminent structurellement le risque d'overselling et de paiement orphelin, sans logique applicative de compensation complexe.
- Un seul système à opérer et à superviser au MVP, cohérent avec une équipe restreinte.
- `JSONB`, PostGIS et `pg_trgm` couvrent les besoins semi-structurés et de recherche sans multiplier les composants d'infrastructure.
- Le schéma versionné par Flyway est reproductible à l'identique entre poste de développement, CI et production.

**Négatives**

- Une seule base de données est un point de défaillance unique au MVP ; à traiter par une politique de sauvegarde et de restauration dès le premier déploiement en production, indépendamment de la présente décision.
- La montée en charge (réplique de lecture, partitionnement) demande une intervention explicite aux paliers V2/V3 — ce n'est pas un système qui scale horizontalement de façon transparente comme le ferait une base distribuée dès le départ. Ce compromis est jugé acceptable au regard du gain de simplicité opérationnelle au MVP.
- Le verrouillage optimiste JPA peut générer des retries sous très forte charge ponctuelle (drop massif d'un événement très demandé) ; la requête SQL atomique décrite ci-dessus est le mécanisme de repli prévu pour ce cas, à activer si le volume de conflits observé le justifie.

## Mise à jour du 30 octobre 2026 : requête atomique activée (mesure n° 16)

La mesure prévue ci-dessus a été faite (`ChargeConfirmationsIntegrationTest`, vrai PostgreSQL) : **40 paiements confirmés simultanément** sur une même catégorie, avec une relance bornée à 3 tentatives côté `orders`.

- **Avec le verrouillage optimiste** (`save()` sous `@Version`) : 20 émissions réussies, **20 échecs** sur conflit de version. Tant que la politique de relance de la passerelle est inconnue (ADR 0007), chaque échec est un client payé sans billet.
- **Avec la requête atomique** (`TicketCategoryRepository.consommerQuota`) : 40 sur 40 en 1,4 à 1,8 s. Une mesure ponctuelle à **150 confirmations simultanées** donne 150 sur 150 en 4,1 s.

**Décision** : la vente du quota passe par `UPDATE ticket_categories SET quota_vendu = quota_vendu + :q, version = version + 1 WHERE id = :id AND quota_vendu + :q <= quota_total`, exécutée **en dernier** dans l'émission, pour que le verrou de la ligne soit tenu le moins longtemps possible. La règle du domaine (`TicketCategory.reserver`) reste un refus rapide sur l'état lu. La version est toujours incrémentée, pour qu'un `save()` concurrent fondé sur une lecture plus ancienne échoue. Le `CHECK` de V4 reste le dernier rempart.

Le verrouillage optimiste reste en place pour le reste : les billets (double scan) et les commandes.

## Références

- [ADR 0001 — Monolithe modulaire](0001-monolithe-modulaire.md)
- [ADR 0002 — Architecture hexagonale appliquée par module](0002-hexagonale-par-module.md)
- [PostGIS](https://postgis.net/) — extension de recherche géospatiale
- [pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html) — extension de recherche texte par trigrammes
- [Flyway](https://flywaydb.org/) — gestion de migrations de schéma versionnées

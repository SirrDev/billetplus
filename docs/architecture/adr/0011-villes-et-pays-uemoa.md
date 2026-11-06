# 0011 — Villes et pays des lieux, recherche par ville (UEMOA)

## Statut

Acceptée — 6 novembre 2026 (choix de SirrDev : plateforme pour l'Afrique de l'Ouest ; d'abord les 8 pays de l'UEMOA, tous en FCFA ; ville choisie dans une **liste fermée** tenue par Billet+ ; numéros, fuseaux horaires et moyens de paiement des autres pays reportés à un ADR dédié ; d'ici là, publication refusée hors du Sénégal ; liste de départ validée)

## Contexte

Le cahier des charges exige la recherche d'événements par ville (MUST). Un lieu (`venues`) n'a aujourd'hui qu'un nom et une adresse en texte libre : rien ne permet de savoir dans quelle ville il se trouve, ni dans quel pays.

SirrDev veut une plateforme pour l'Afrique de l'Ouest. Le reste du produit est encore sénégalais :

- **numéro de l'acheteur** : `+2217XXXXXXXX` seulement (ADR 0006, CHECK en base, PWA) ;
- **heure** : affichée en heure de Dakar (UTC) ; le Niger et le Bénin sont en UTC+1 ;
- **paiement** : Wave et Orange Money via UnitechPay (ADR 0007), disponibilité hors Sénégal *à confirmer auprès d'Unitech* ;
- **monnaie** : FCFA (XOF), commune aux 8 pays de l'UEMOA, mais pas au Ghana, au Nigeria ni à la Guinée.

## Décision

1. **Pays ouverts : les 8 de l'UEMOA**, en codes ISO 3166-1 alpha-2 : Sénégal (`SN`), Côte d'Ivoire (`CI`), Mali (`ML`), Burkina Faso (`BF`), Niger (`NE`), Bénin (`BJ`), Togo (`TG`), Guinée-Bissau (`GW`). La monnaie ne change pas (FCFA entiers, `Money`).
2. **Liste fermée de villes, en base** (module `events`, migration V18) : table `villes` (`code` stable, par exemple `SN-DAKAR` ; `nom` affiché ; `pays`). Ajouter une ville = une nouvelle migration, sans changer le code. Liste de départ, 32 villes (capitale et grandes villes, validée par SirrDev) :
   - **SN** : Dakar, Thiès, Saint-Louis, Touba, Mbour, Kaolack, Ziguinchor ;
   - **CI** : Abidjan, Bouaké, Yamoussoukro, San-Pédro, Korhogo, Daloa ;
   - **ML** : Bamako, Sikasso, Ségou, Mopti, Kayes ;
   - **BF** : Ouagadougou, Bobo-Dioulasso, Koudougou ;
   - **NE** : Niamey, Zinder, Maradi ;
   - **BJ** : Cotonou, Porto-Novo, Abomey-Calavi, Parakou ;
   - **TG** : Lomé, Kara, Sokodé ;
   - **GW** : Bissau.
3. **Chaque lieu a une ville** (`venues.ville_code`, obligatoire, clé étrangère vers `villes` : même module, donc permise). Le pays se déduit de la ville. Les lieux existants reçoivent Dakar (données de développement uniquement). La copie d'un lieu pour une nouvelle position (`Venue.copieA`) garde sa ville.
4. **Création d'un lieu** : `POST /api/venues` exige `villeCode` ; un code inconnu → 400. Le nom et l'adresse restent libres.
5. **Recherche** : `GET /api/events` accepte `ville` (code) et `pays` (code ISO), facultatifs, combinables avec la recherche par titre et la catégorie. Un code inconnu → 400 (pas une liste vide trompeuse). Index sur `venues (ville_code)`.
6. **Liste publique** : `GET /api/villes` (sans compte) rend les villes groupées par pays, dans l'ordre de la liste. La PWA ne recopie jamais la liste.
7. **Affichage** : la liste publique et la page d'un événement montrent la ville (et le pays hors Sénégal). L'écran « Découvrir » propose un choix de ville ; le formulaire de création d'un événement, un choix de ville obligatoire.
8. **Hors de cette décision** (ADR à venir, après les réponses d'Unitech) : numéros des acheteurs des autres pays, fuseau horaire par lieu, moyens de paiement par pays.
9. **D'ici là, publication refusée hors du Sénégal** (choix de SirrDev) : un lieu et un brouillon peuvent être créés dans tout l'UEMOA, mais `POST /api/events/{id}/publier` répond 409 si la ville du lieu n'est pas au Sénégal (« La vente hors du Sénégal n'est pas encore ouverte »). Aucun acheteur ne paie donc un événement affiché à une heure fausse. La règle tombera avec l'ADR multi-pays.

## Alternatives considérées

- **Ville saisie librement** (recherche sans casse ni accents) : écartée par SirrDev. Fautes de frappe et variantes (« St-Louis », « Saint Louis ») couperaient la recherche.
- **Liste dans le code** (énumération Java et TypeScript) : écartée. Il faudrait livrer le backend et la PWA pour chaque ville ajoutée, et garder deux listes égales.
- **Ville déduite de la position GPS** : écartée. La position est facultative, et un service de géocodage ajouterait une dépendance externe.
- **Ouvrir toute l'Afrique de l'Ouest** : écartée pour l'instant. Ghana, Nigeria, Guinée, Gambie, Sierra Leone, Liberia, Cap-Vert et Mauritanie ont chacun leur monnaie : il faudrait d'abord un chantier multi-devises.

## Conséquences

- **Positives** : la recherche par ville est exacte ; ajouter une ville ne demande qu'une migration ; la PWA suit la liste du backend.
- **Négatives et limites connues** :
  - une ville absente de la liste bloque la création du lieu : l'organisateur doit demander son ajout (à prévoir dans l'aide, ou choisir la ville la plus proche) ;
  - tant que l'ADR multi-pays n'existe pas, seuls les événements du Sénégal se vendent : un organisateur d'un autre pays peut préparer son événement, pas le publier (point 9) ;
  - un événement déjà publié dont on déplacerait le lieu hors du Sénégal : impossible, la position et le lieu d'un événement publié sont figés (section 4.35 du HANDOFF).

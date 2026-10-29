# 0006 — Achat sans compte : la commande porte l'identité de l'acheteur et son propre secret d'accès

## Statut

Acceptée — 30 octobre 2026

## Contexte

Le module `orders` doit savoir **qui** achète, pour trois raisons :

1. **Rendre les billets à leur acheteur**, et à lui seul : un billet est un QR signé, donc un jeton porteur (HANDOFF, section 7). Quiconque lit la commande d'un autre peut entrer à sa place.
2. **Limiter les abus** : des commandes non payées bloquent des places pendant la réservation temporaire (5 minutes, ADR 0003, branche `tickets-redis-seat-lock`). Il faut pouvoir plafonner les commandes en attente et le débit de création « par acheteur ».
3. **Rembourser** une commande payée sans billet, ou celle d'un événement annulé.

Deux voies étaient possibles :

- **Un compte acheteur** : un nouveau rôle, une inscription ouverte au public et une connexion avant l'achat. Le HANDOFF classait cette inscription en V2.
- **Un achat sans compte** : l'acheteur donne son numéro de téléphone, celui qu'il utilise avec Wave, et paie.

Le public visé (Dakar, achat sur mobile, souvent au dernier moment) abandonne un achat qui commence par la création d'un compte. Le paiement Wave identifie déjà le payeur par son téléphone.

Le remboursement pose une seconde question : faut-il l'automatiser dès le MVP ? L'API de remboursement de Wave, les cas partiels et la comptabilité de l'organisateur ajoutent un travail lourd, pour des cas rares à l'échelle de l'événement pilote.

## Décision

### 1. Achat sans compte (décision A de SirrDev)

- **Pas de rôle acheteur ni d'inscription ouverte au MVP.** `POST /api/auth/register` continue de créer uniquement des organisateurs.
- **L'identité de l'acheteur est une donnée de la commande** : un **numéro de téléphone mobile sénégalais**, normalisé au format international (`+2217XXXXXXXX`). Il n'est pas vérifié par un code au MVP : il sert de critère de limitation et de contact, pas de preuve d'identité.
- **L'accès à une commande et à ses billets passe par un jeton secret propre à la commande** :
  - aléatoire, 256 bits (`SecureRandom`), encodé en Base64 URL ;
  - **remis une seule fois**, dans la réponse de création de la commande, puis jamais renvoyé par l'API ;
  - **seule son empreinte SHA-256 est stockée**. Une fuite de la base ne donne accès à aucune commande. Un sel est inutile : le jeton est aléatoire et assez long pour rendre une recherche exhaustive impossible ;
  - comparé en temps constant (`MessageDigest.isEqual`) ;
  - transmis à l'acheteur par un lien (`/commandes/{id}#jeton=...`), que la PWA conserve. L'identifiant de commande seul n'ouvre rien : ce n'est pas un secret.
- **Les limites « par compte » du contrat de `orders` deviennent des limites par numéro de téléphone et par adresse IP** (création de commande, commandes en attente par événement).
- L'`orderId` reste **généré par le serveur** et persisté avant toute réservation de places.

### 2. Remboursement en V2 (décision B de SirrDev)

- **Aucun remboursement automatique au MVP.** Pour l'événement pilote, les remboursements se font à la main, depuis l'espace Wave Business de l'organisateur.
- Le module `orders` doit en revanche **rendre ces cas visibles**, sans perte :
  - un statut dédié **`PAYEE_SANS_BILLET`**, avec un motif, pour un paiement reçu sans que les billets aient pu être émis (réservation expirée, vente fermée, quota épuisé) ;
  - les commandes payées d'un **événement annulé**, retrouvées par leur événement et leur statut ;
  - une liste de ces commandes pour l'administrateur (branche à venir).
- Le statut `REMBOURSE` des billets (V4) et un éventuel statut de commande remboursée seront utilisés par la V2.

## Alternatives considérées

### Compte acheteur obligatoire

Rejetée pour le MVP. Elle simplifierait l'accès aux billets (« mes billets » après connexion) et les limites par compte. En revanche, elle ajoute une inscription au parcours d'achat, ouvre l'inscription publique, avec les risques d'énumération d'emails et de comptes jetables déjà relevés, et demande la récupération de mot de passe. La décision pourra être revue en V2 : une commande sans compte pourra être rattachée plus tard à un compte par son numéro de téléphone.

### Vérification du téléphone par code (SMS)

Reportée. Elle rendrait les limites par téléphone fiables, mais elle exige un fournisseur SMS, son coût, et une étape de plus avant le paiement. Au MVP, le paiement Wave lui-même joue ce rôle de vérification : une commande non payée expire sans billet.

### Jeton d'accès stocké en clair, ou dérivé de l'identifiant (HMAC)

Rejetées. En clair, une fuite de la base ouvrirait toutes les commandes. Un HMAC de l'identifiant dépend d'une clé serveur dont la rotation invaliderait tous les liens déjà envoyés, et ne permet pas de révoquer un lien en particulier.

### Remboursement automatique dès le MVP

Rejeté : son coût ne se justifie pas au volume de l'événement pilote, et il dépend de l'API de remboursement de Wave, pas encore étudiée. Ce qui est indispensable, c'est de **ne jamais perdre** une commande à rembourser : c'est le rôle du statut `PAYEE_SANS_BILLET`.

## Conséquences

**Positives**

- Le parcours d'achat se limite à choisir ses places, donner son téléphone et payer.
- L'accès aux billets ne dépend d'aucun compte. Il est révocable commande par commande, en remplaçant l'empreinte.
- Aucune commande payée sans billet ne disparaît : elle a un statut et un motif.

**Négatives**

- **Un lien perdu est une commande perdue** tant qu'aucun renvoi n'existe : la PWA doit conserver le lien, et un renvoi par SMS ou WhatsApp au numéro de la commande est à prévoir (après le MVP, ou avant l'événement pilote si le besoin se confirme).
- Un numéro non vérifié peut être celui d'un autre : les limites par téléphone sont contournables. La limite par IP et la courte durée de la réservation restent les vrais remparts au MVP.
- Les remboursements manuels demandent une procédure écrite pour l'organisateur pilote.

## Références

- [ADR 0002 — Hexagonale par module](0002-hexagonale-par-module.md) (`orders` en niveau 3)
- [ADR 0003 — PostgreSQL vs NoSQL](0003-postgresql-vs-nosql.md) (réservation temporaire dans Redis)
- [ADR 0005 — Communication entre modules](0005-communication-entre-modules.md) (lecture du prix et de l'état de vente par un port public)
- `docs/HANDOFF.md`, section 7 : contrat de `orders` envers `IssueTicketUseCase`

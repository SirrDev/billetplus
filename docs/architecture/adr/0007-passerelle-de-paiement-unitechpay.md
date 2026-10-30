# 0007 — Passerelle de paiement : UnitechPay, pour Wave et Orange Money

## Statut

Acceptée — 30 octobre 2026

## Contexte

Le plan initial prévoyait une intégration directe de **Wave** (branche `orders-wave-gateway`). SirrDev a choisi la passerelle **UnitechPay** (`pay.unitech.sn`, API REST v1.2.0), qui encaisse au Sénégal par Wave, Orange Money et Max It, avec un seul contrat, une seule clé d'API et un seul webhook.

La documentation publique (`https://pay.unitech.sn/documentation`) décrit :

- l'API : `https://api.unitech.sn/api.php?action=<action>`, avec la clé d'API en `Authorization: Bearer`. Montants en XOF ;
- la création d'un paiement : `create_wave_payment` ou `create_orange_om`, avec `amount` et `customer_number` (9 chiffres dans les exemples). La réponse contient `transaction_id`, `reference` (**générée par UnitechPay**, par exemple `wave_66a1b2_1716540000`) et `payment_url` ;
- un webhook unique (`configure_webhook`), dont les événements sont `payment_completed`, `payment_failed` et `payment_expired`. Il est signé en HMAC-SHA256 **avec la clé d'API comme secret**, de deux façons :
  - méthode 1 : l'en-tête `X-UnitechPay-Signature`, HMAC du corps brut ;
  - méthode 2 : le champ `signature`, HMAC de la chaîne `event|reference|amount|status|signed_at`.

Elle ne documente **pas** :

- la politique de relance du webhook ni le code de réponse attendu ;
- la consultation d'une transaction seule ;
- la protection contre le rejeu ;
- la durée de validité d'un paiement (`validity: 300`, unité non précisée) ;
- les montants minimum et maximum au Sénégal ;
- un environnement de test.

Ces questions ont été posées à Unitech par SirrDev (voir le HANDOFF).

## Décision

1. **UnitechPay est la seule passerelle du MVP**, avec deux moyens de paiement : **Wave** (`create_wave_payment`) et **Orange Money** (`create_orange_om`). Max It pourra s'ajouter sans changer le reste.
2. **Un port `PasserellePaiement` dans `orders.application.port`** isole UnitechPay : le domaine et les cas d'usage ne connaissent ni son URL, ni ses champs, ni sa signature. L'adaptateur HTTP vit dans `orders.infrastructure.paiement`. Changer de passerelle ne touche que l'adaptateur.
3. **Un seul paiement par commande.** Rouvrir le paiement d'une commande renvoie le même lien, et changer de moyen de paiement exige une nouvelle commande. Deux paiements ouverts pour une même commande pourraient tous deux aboutir, et le second serait encaissé sans billet.
4. **La commande conserve la `reference` et le `transaction_id` de UnitechPay.** La référence est unique en base : c'est la clé de rapprochement du webhook.
5. **Le webhook (branche suivante) n'identifie la commande que par des champs signés.** La méthode 2 ne couvre que `event`, `reference`, `amount`, `status` et `signed_at`. On retrouve donc la commande par `reference`, on compare `amount` au montant de la commande, et on n'utilise jamais `transaction_id` ni `method` pour décider. Le rejeu est neutralisé par l'idempotence : un webhook déjà traité pour une référence ne change plus rien.
6. **La clé d'API est un secret de production critique.** Elle donne accès aux fonds et permet de forger un webhook « payé ». Elle passe uniquement par la variable `UNITECHPAY_API_KEY`, n'est jamais journalisée, et le profil `prod` refuse de démarrer sans elle.
7. **Aucun appel HTTP ne se fait dans une transaction PostgreSQL ouverte.** L'appel à UnitechPay se fait hors transaction, et la commande est mise à jour ensuite, sous verrou de version.

## Alternatives considérées

- **Wave en direct** : un seul moyen de paiement, et un contrat marchand Wave à obtenir. Abandonnée au profit d'un agrégateur couvrant aussi Orange Money.
- **Signature par l'en-tête (méthode 1)** : elle couvre tout le corps, donc aussi `transaction_id`. Elle échoue cependant si un intermédiaire modifie le corps, et la documentation ne dit pas si le champ `signature` est inclus dans le hachage. La méthode 2 est retenue, avec la règle 5 qui la rend suffisante.

## Conséquences

**Positives**

- Wave et Orange Money avec une seule intégration ; le reste de l'application est indépendant de la passerelle.
- Un paiement ne peut pas être doublé par une réouverture.

**Négatives**

- **Dépendance à un prestataire peu documenté.** Sans politique de relance connue, un webhook perdu laisserait un client payé sans billet. Il faudra un rapprochement périodique à partir de la liste `transactions`, dès que son format sera connu.
- **Pas d'environnement de test** : les tests automatiques utilisent un faux serveur, et le premier essai réel se fera avec un petit montant.
- La clé d'API joue deux rôles (appels et signature du webhook) : sa rotation impose de reconfigurer les deux en même temps.

## Références

- [ADR 0002 — Hexagonale par module](0002-hexagonale-par-module.md) (`orders` en niveau 3)
- [ADR 0006 — Achat sans compte](0006-achat-sans-compte.md) (le numéro de l'acheteur sert de `customer_number`)
- Documentation UnitechPay : https://pay.unitech.sn/documentation

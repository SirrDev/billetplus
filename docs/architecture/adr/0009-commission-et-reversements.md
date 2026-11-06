# 0009 — Commission de Billet+ et reversements aux organisateurs

## Statut

Proposée — 6 novembre 2026 (choix de SirrDev : commission déduite de l'organisateur, 5 % par défaut figés dans chaque commande, arrondi au franc inférieur, frais UnitechPay absorbés, reversements suivis à la main)

## Contexte

Le cahier des charges (§ 4.1) prévoit une commission de 5 % sur les ventes. Le backend n'en tient aucun compte : l'acheteur paie le prix des billets, et le tableau de bord de l'organisateur affiche un chiffre d'affaires brut.

L'argent suit déjà un seul chemin (ADR 0007) : tous les paiements arrivent sur **le compte marchand UnitechPay de Billet+** (une seule clé d'API). Billet+ reverse ensuite à chaque organisateur sa part. Rien ne calcule aujourd'hui cette part, ni ne garde la trace de ce qui a été reversé.

Contraintes existantes :

- **Le montant payé est vérifié** : le webhook UnitechPay n'identifie la commande que par des champs signés, dont le montant, comparé à `Order.montantTotal()` (ADR 0007). Toute modification du prix payé toucherait ce contrôle.
- **`orders` est de niveau 3** (ADR 0002) : l'argent se calcule dans un domaine pur, testé sans Spring.
- **Aucun nombre à virgule pour l'argent** : `Money` en FCFA entiers.
- **Remboursement manuel en V2** (ADR 0006, décision B) : les commandes `PAYEE_SANS_BILLET` et celles d'un événement annulé sont listées pour être remboursées à la main.

## Décision

1. **Commission déduite de l'organisateur.** L'acheteur paie le prix affiché, sans frais ajoutés : le montant de la commande, le paiement et le webhook ne changent pas. L'organisateur reçoit le net.
2. **Taux figé dans chaque commande.** Le taux courant vient de la configuration (`billetplus.commission.taux-points-base`, **500 = 5 %**, en points de base pour rester en entiers). Il est **recopié dans la commande à sa création** (colonne `taux_commission_points_base`, migration V16) : changer le taux plus tard ne modifie aucune vente passée. Un taux propre à un organisateur ou à un événement pourra s'ajouter sans toucher à cette règle.
3. **Calcul par commande, arrondi au franc inférieur** (domaine `Order`) : `commission = plancher(montant × taux / 10 000)`, `net = montant − commission`. Le franc d'écart revient à l'organisateur. Une commande gratuite a une commission nulle.
4. **Seules les commandes `PAYEE` d'un événement non annulé comptent.** Une commande `PAYEE_SANS_BILLET`, ou une commande payée d'un événement annulé, est remboursée en entier à l'acheteur : ni commission ni net. Un billet transféré ne change rien (il reste attaché à sa commande, ADR 0008).
5. **Frais UnitechPay absorbés par Billet+.** L'organisateur voit « montant − 5 % ». Les frais de la passerelle sont pris sur la commission ; leur taux réel est **à confirmer** auprès d'Unitech.
6. **Recettes d'un événement** (module `orders`, lecture de niveau 1) : `GET /api/events/{id}/recettes`, réservé à l'organisateur de l'événement (même 404 qu'un événement inconnu sinon, propriété lue par le port public `StatutEvenements`, ADR 0005). Réponse : brut, commission, net, déjà reversé, reste à reverser. Le tableau de bord de la PWA l'affiche à côté des chiffres du module `tickets`, qui ne change pas : le module `tickets` ne dépend pas de `orders`.
7. **Reversements suivis à la main** (administrateur seulement) :
   - **liste** `GET /api/admin/reversements` : par événement ayant des commandes payées, brut, commission, net, total reversé, reste à reverser ;
   - **enregistrement** `POST /api/admin/reversements` : événement, montant, référence du virement (Wave, Orange Money), date. Table `reversements` (V16), une ligne par virement : **plusieurs reversements par événement** (un acompte, puis le solde après des ventes tardives). Un reversement ne se modifie ni ne se supprime ; une erreur se corrige par un reversement négatif motivé ;
   - aucun virement n'est déclenché par Billet+ : l'administrateur paie lui-même, puis l'enregistre.

## Alternatives considérées

- **Frais ajoutés au prix payé par l'acheteur** : écarté. Il changerait le montant payé, donc la commande, le webhook signé et les écrans d'achat, et un public qui découvre l'achat en ligne accepte mal des frais découverts au paiement.
- **Taux lu dans la configuration au moment du calcul** : écarté. Un changement de taux réécrirait les recettes et la commission de toutes les ventes passées.
- **Commission calculée sur le total de l'événement** : écarté. Elle ne se vérifie pas commande par commande, et un remboursement isolé obligerait à tout recalculer.
- **Reversement automatique par l'API UnitechPay** : écarté au MVP. Le versement vers un organisateur n'est pas documenté dans les réponses actuelles d'Unitech, et une erreur de virement est irréversible.
- **Commission dans le module `tickets`** (à côté du chiffre d'affaires du tableau de bord) : écarté. L'argent payé et son partage appartiennent au module `orders` ; `tickets` ne compte que des billets.

## Conséquences

- **Positives** : aucune modification du paiement ni du webhook ; chaque commande garde son taux ; l'organisateur voit ce qu'il va recevoir ; l'administrateur voit ce qu'il doit encore reverser.
- **Négatives et limites connues** :
  - **Événement annulé après un reversement** : les acheteurs sont remboursés en entier, mais l'organisateur a déjà reçu son net. Le reste à reverser devient négatif (« trop reversé ») : à récupérer hors de Billet+.
  - **Frais UnitechPay d'une commande remboursée** : à la charge de Billet+ (pas de commission pour les couvrir).
  - **Commandes antérieures à V16** : elles reçoivent le taux de 5 % par la migration (données de développement uniquement à ce jour).
  - **Brut du tableau de bord et brut des recettes** : le premier compte les billets valables (module `tickets`), le second les commandes payées (module `orders`). Ils coïncident tant que chaque commande payée a ses billets émis ; un écart signale une commande `PAYEE` sans billets, à examiner.

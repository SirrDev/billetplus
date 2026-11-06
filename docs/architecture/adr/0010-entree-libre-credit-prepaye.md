# 0010 — Entrée libre payée par l'organisateur sur un crédit prépayé

## Statut

Acceptée — 6 novembre 2026 (choix de SirrDev : entrée libre autorisée ; l'organisateur paie Billet+ 75 FCFA par billet gratuit si l'événement a 100 places ou moins, toutes catégories confondues, 50 FCFA au-delà ; crédit prépayé en FCFA, débité à chaque billet émis, rechargé par paiement dans l'application)

## Contexte

Une catégorie peut coûter 0 FCFA (`TicketCategory` l'accepte), mais la commande qui en résulte ne peut pas être payée : une passerelle n'encaisse pas 0 F. Aujourd'hui, `OuvrirPaiementUseCase` la refuse (409) et elle expire sans billet.

Billet+ se rémunère par une commission sur les ventes (ADR 0009). Une entrée libre ne rapporte rien : 5 % de 0 F. SirrDev a décidé que l'organisateur paie un forfait par billet gratuit émis, sur un crédit acheté d'avance.

Contraintes existantes :

- **`orders` est de niveau 3** (ADR 0002) : l'argent se calcule dans un domaine pur.
- **Achat sans compte** (ADR 0006) : l'acheteur n'est identifié que par un mobile **non vérifié** (pas de SMS au MVP). N'importe qui peut donc demander des billets gratuits sous plusieurs numéros, et chaque billet coûte de l'argent à l'organisateur.
- **Paiement** (ADR 0007) : un seul paiement par commande ; aucun appel HTTP dans une transaction ; le webhook n'identifie ce qu'il paie que par des champs signés (`reference`, `amount`), la référence étant générée par la passerelle.
- **Places** : réservées dans Redis à la création de la commande, puis émises par une requête SQL atomique sur le quota (module `tickets`, ports `PlacesEnVente` et `EmissionBillets`).

## Décision

1. **Commande gratuite confirmée tout de suite.** Une commande dont **toutes** les lignes sont à 0 FCFA ne passe pas par la passerelle. Elle se demande par une **route dédiée**, `POST /api/orders/entree-libre` (même corps qu'une commande) : `CreerEntreeLibreUseCase` la crée et réserve ses places (par `CreateOrderUseCase`), puis **débite le crédit de l'organisateur**, la marque `PAYEE` et émet ses billets, **dans la même transaction**. `POST /api/orders` refuse une commande entièrement gratuite (400), et l'entrée libre refuse toute catégorie payante (400). L'acheteur reçoit ses billets aussitôt. Si une étape échoue (places, crédit, quota), tout est annulé : ni commande, ni débit, ni billet.
2. **Pas de commande mixte.** Une commande qui mélange des catégories gratuites et payantes est refusée (400) : l'acheteur fait deux commandes. Sinon un paiement UnitechPay déclencherait aussi un débit du crédit, dans le webhook, avec un échec possible après encaissement.
3. **Tarif par billet, figé dans la commande.** Le module `tickets` fournit le **nombre total de places de l'événement** (somme des quotas de toutes ses catégories, payantes comprises), lu **à chaque commande**. Si ce total vaut **100 ou moins**, un billet gratuit coûte **75 FCFA** ; sinon **50 FCFA**. Le tarif retenu est **recopié dans la commande** (colonne dédiée, comme le taux de commission, ADR 0009) ; le montant débité (tarif × nombre de billets) se recalcule et figure dans le journal du crédit. Seuil et tarifs viennent de la configuration (`billetplus.entree-libre.*`) et sont vérifiés au démarrage.
4. **Crédit prépayé par organisateur** (module `orders`, migration V17) :
   - un **solde en FCFA entiers**, jamais négatif (`CHECK (solde_fcfa >= 0)`), une ligne par organisateur ;
   - un **journal des mouvements**, jamais modifié : recharge (+), débit d'une commande gratuite (−), avec la commande ou la recharge concernée. Le solde doit toujours égaler la somme du journal ; un test le vérifie ;
   - **SQL direct** (`JdbcCreditRepository`, sans entité JPA) : le débit conditionnel ne s'exprime pas en JPA sans lire puis écrire. `ddl-auto: validate` ne contrôle donc pas ces deux tables : les tests d'intégration le font ;
   - l'organisateur d'un événement est lu par le port public `StatutEvenements` (ADR 0005), jamais dans la requête.
5. **Débit atomique, sans double dépense.** Le débit est une seule requête : `UPDATE ... SET solde_fcfa = solde_fcfa - :montant WHERE organisateur_id = :id AND solde_fcfa >= :montant`. Une ligne modifiée : le débit est fait. Aucune : crédit insuffisant. Deux commandes simultanées ne peuvent pas dépenser le même franc : PostgreSQL verrouille la ligne jusqu'à la fin de la transaction. Le débit a lieu **avant** l'émission des billets.
6. **Crédit insuffisant → 409, sans rien réserver durablement.** L'acheteur lit « Les entrées gratuites de cet événement sont momentanément épuisées. » : le solde de l'organisateur ne lui est jamais montré. Les places réservées dans Redis sont rendues après l'annulation de la transaction, comme pour tout échec de création.
7. **Limite contre l'épuisement du crédit.** Une commande gratuite garde les plafonds actuels (20 billets par catégorie, 10 catégories), plus un **plafond de billets gratuits par numéro et par événement** (configuration, 10 par défaut) et une **limitation de débit par IP** propre à sa route (règle `entree-libre` : 20 par 10 minutes ; d'où la route dédiée, la limitation se réglant par chemin). Le plafond par numéro est strict : un verrou consultatif PostgreSQL (`pg_advisory_xact_lock`) sur le couple (numéro, événement) fait passer l'une après l'autre deux demandes simultanées du même numéro. Le numéro n'étant pas vérifié, la limite par IP reste le vrai rempart.
8. **Recharge par paiement dans l'application.** L'organisateur choisit un montant (bornes en configuration : **1 000 FCFA au moins**, 1 000 000 au plus), un moyen (Wave ou Orange Money) et le numéro qui paie (exigé par la passerelle). Limitation `recharge-credit` : 10 par heure et par compte. `POST /api/credit/recharges` crée une **recharge en attente**, ouvre le paiement **hors transaction** (même procédure que `OuvrirPaiementUseCase`) et renvoie le lien de paiement. Le **webhook** cherche la référence signée d'abord parmi les commandes, puis parmi les recharges :
   - montant signé égal → recharge `PAYEE`, solde crédité, mouvement journalisé, **une seule fois** (un webhook rejoué ne crédite rien de plus) ;
   - montant différent → recharge marquée en anomalie, **rien n'est crédité**, journalisée en `ERROR`, à régulariser à la main ;
   - paiement échoué ou expiré → recharge `ECHOUEE` ;
   - paiement reçu pour une recharge déjà `ECHOUEE` ou `ANOMALIE` (webhooks dans le désordre) → rien crédité, journalisé en `ERROR`, à régulariser à la main.
9. **Écrans.** Organisateur : solde, historique des mouvements, bouton « Recharger » ; avertissement à la création d'une catégorie gratuite si le solde ne couvre pas ses places. Acheteur : prix « Gratuit », billets affichés sans étape de paiement.

10. **Réponses de SirrDev du 6 novembre 2026** :
    - **événement annulé : aucun re-crédit.** Les billets gratuits émis restent dus ; l'annulation ne crée aucun mouvement ;
    - **crédit restant non remboursable** en argent : il ne sert que sur Billet+ (à écrire dans les conditions d'utilisation) ;
    - **transfert d'un billet gratuit autorisé** (ADR 0008), **sans nouveau débit** : le billet a déjà été payé ;
    - **ajout de places après des billets gratuits** : les billets suivants prennent le nouveau tarif, les précédents gardent le leur (chaque commande garde le tarif de son moment).

## Alternatives considérées

- **Abonnement** : écarté par SirrDev au profit du crédit prépayé. Un abonnement ne suit pas le nombre de billets, et il faudrait gérer l'échéance et le renouvellement.
- **Facturation après coup** (dette de l'organisateur, réglée plus tard ou retenue sur ses reversements) : écarté. Un organisateur d'entrée libre n'a pas de reversement sur lequel retenir, et Billet+ porterait le risque d'impayé.
- **Tarif calculé une fois à la publication** : écarté par SirrDev (« à chaque billet émis »). Il serait faux dès qu'une catégorie est ajoutée après la publication.
- **Débit dans le module `tickets`** au moment de l'émission : écarté. L'argent appartient au module `orders` (comme la commission, ADR 0009) ; `tickets` ne compte que des billets.
- **Verrou `@Version` sur le crédit** : écarté. Avec la requête conditionnelle, PostgreSQL sérialise les débits sans relance ni conflit visible ; un verrou optimiste ferait échouer des commandes gratuites simultanées pour rien.
- **Commande gratuite qui reste en attente puis se confirme par une route dédiée** : écarté. Un aller-retour de plus, sans rien à vérifier entre les deux.

## Conséquences

- **Positives** : une entrée libre fonctionne de bout en bout ; Billet+ est payé d'avance, sans impayé possible ; le webhook et le paiement des commandes payantes ne changent pas, sauf la recherche de la référence parmi les recharges ; le solde ne peut ni devenir négatif ni être dépensé deux fois.
- **Négatives et limites connues** :
  - **Épuisement volontaire** : un tiers peut demander les billets gratuits sous de faux numéros et vider le crédit de l'organisateur. Les plafonds et la limite par IP le freinent sans l'empêcher ; une vérification du numéro par SMS le bloquerait (hors MVP).
  - **Ampleur de l'épuisement** : depuis une seule IP, 20 demandes de 10 billets par 10 minutes, soit 200 billets (15 000 FCFA à 75 F) ; sans borne avec plusieurs IP et de faux numéros. Les billets obtenus sont définitifs (commandes `PAYEE`). Pistes V2 : plafond par événement et par heure, plafond de dépense choisi par l'organisateur. En production, l'adresse du proxy TLS doit être reconnue (`internalProxies`), sinon toutes les requêtes partagent son IP et la limite `entree-libre` bloquerait tout le monde.
  - **Solde approché** : un tiers qui fait varier la quantité demandée peut encadrer le solde de l'organisateur (le refus ne débite rien). Le message reste générique ; la fuite est jugée faible.
  - **Événement annulé** : l'organisateur ne récupère pas le crédit débité pour ses billets gratuits (décision de SirrDev) ; l'écran d'annulation doit le dire avant confirmation.
  - **Commande gratuite pendant une panne Redis** : refusée comme une commande payante (la réservation passe par Redis).
  - **Recharge payée mais webhook perdu** : la recharge reste en attente, le solde n'est pas crédité. Même risque que pour une commande (ADR 0007) ; le rapprochement périodique (`action=transactions`) le couvrira.
  - **Recharge en anomalie** (montant différent) : régularisée à la main par l'administrateur, hors de l'application au MVP.
  - **Frais UnitechPay sur une recharge** : à la charge de Billet+, comme pour une vente (taux *à confirmer* auprès d'Unitech).
  - **Seuil lu à chaque commande** : le module `orders` dépend d'une nouvelle méthode du port `PlacesEnVente` (nombre total de places de l'événement).

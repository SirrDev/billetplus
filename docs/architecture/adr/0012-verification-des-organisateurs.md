# 0012 — Vérification des organisateurs et reversement après l'événement

## Statut

Acceptée — 6 novembre 2026 (choix de SirrDev : reversement à partir de J+1 et seulement si au moins un billet a été scanné ; pièces déposées dans l'application, chiffrées dans PostgreSQL, gardées 5 ans après le dernier reversement ; pas de validation préalable des événements, mais suspension possible)

## Contexte

N'importe qui peut créer un compte organisateur (`POST /api/auth/register`) et publier aussitôt. Un fraudeur peut donc mettre en vente un faux événement, ou l'événement d'un autre, puis réclamer l'argent des billets.

L'argent ne lui arrive jamais directement : tous les paiements tombent sur le compte marchand UnitechPay de Billet+, et l'administrateur reverse à la main (ADR 0009). Mais rien n'empêche aujourd'hui la mise en vente, ni un reversement avant l'événement, ni un reversement vers un numéro quelconque.

## Décision

1. **Statut de vérification du compte organisateur** (module `auth`) : `A_VERIFIER` (à l'inscription) → `EN_REVUE` (pièces déposées) → `VERIFIE` ou `REFUSE` (motif obligatoire, nouveau dépôt possible) ; `SUSPENDU` à tout moment, par l'administrateur (motif obligatoire). Les agents et l'administrateur ne sont pas concernés.
2. **Pièces déposées dans l'application** :
   - **pièce d'identité** du responsable (CNI ou passeport), obligatoire ;
   - **justificatif du numéro de reversement** (capture du compte Wave ou Orange Money montrant le titulaire), obligatoire ;
   - **NINEA ou registre du commerce**, obligatoire si l'organisateur déclare une structure (association, entreprise).
   L'organisateur déclare aussi son **numéro de reversement** (mobile sénégalais, comme les acheteurs) et le **nom du titulaire**, qui doit correspondre à la pièce d'identité ou à la structure.
3. **Fichiers** : PDF, JPEG ou PNG, **5 Mo au plus**, type vérifié sur le contenu (signature du fichier), pas sur le nom. **Chiffrés par l'application** (AES-256-GCM, une clé de chiffrement fournie par une variable d'environnement, jamais en base ni journalisée ; la production refuse de démarrer sans elle) et stockés dans PostgreSQL. Seul l'administrateur les lit, par une route authentifiée, en téléchargement, `Cache-Control: no-store`. L'organisateur voit la liste de ses pièces (type, date), pas leur contenu.
4. **Conservation : 5 ans après la plus récente de ces deux dates : le dernier virement à l'organisateur (une correction négative ne compte pas) et le dépôt de la pièce.** Une tâche quotidienne supprime les pièces échues et le journalise (sans donnée personnelle). Durée *à confirmer avec un juriste* ; **déclaration du traitement à la CDP** (Commission de protection des données personnelles du Sénégal) à faire par SirrDev.
5. **Publication réservée aux comptes vérifiés** : `POST /api/events/{id}/publier` → 409 « Votre compte doit être vérifié avant de publier » si le statut n'est pas `VERIFIE`. Un compte non vérifié prépare ses brouillons (lieu, catégories, équipe). Le statut est lu par le port public `Comptes` (ADR 0005).
6. **Suspension** (administrateur, motif obligatoire) :
   - **d'un compte** : connexion refusée, publication et reversement refusés, et **tous ses événements à venir sont annulés** (mêmes effets qu'une annulation : billets annulés, commandes payées listées à rembourser) ;
   - **d'un événement** : annulation administrative, mêmes effets ; l'organisateur en est informé dans son espace.
   La suspension d'un compte est définitive au MVP (réactivation hors application).
7. **Reversement** (`POST /api/admin/reversements`, montant positif ; une correction négative reste toujours possible) refusé (409) si :
   - l'organisateur n'est pas `VERIFIE` ;
   - **le lendemain de l'événement n'est pas atteint** (fin de l'événement, à défaut son début, + 1 jour, heure de Dakar) ;
   - **aucun billet de l'événement n'a été scanné**, sauf **dérogation motivée** de l'administrateur (entrée sans contrôle), enregistrée avec le reversement.
   La liste des reversements affiche, pour chaque événement, le **numéro de reversement vérifié** et le titulaire : l'administrateur ne vire que vers lui. **Tout changement sur un compte vérifié** (numéro, titulaire, type de titulaire, structure, NINEA, ou nouvelle pièce) le repasse en `EN_REVUE` : l'administrateur revoit le dossier avant le prochain virement.
8. **Règles ajoutées après la relecture de sécurité** :
   - **dossier figé pendant l'examen** : en `EN_REVUE`, ni déclaration ni dépôt (409) ; sinon un numéro changé pendant l'examen serait accepté sans avoir été vu ;
   - **pas de publication d'un événement déjà commencé** (409) : un événement daté dans le passé, publié puis vendu, atteindrait J+1 tout de suite ;
   - **J+1 compté depuis la plus tardive** de la fin de l'événement et de sa **dernière vente payée** ;
   - **les agents d'un compte suspendu sont désactivés** avec lui ;
   - **chaque virement garde une copie du numéro** vers lequel il a été fait (`reversements.numero_reversement`) ;
   - **dérogation de scan motivée en 10 caractères au moins** ;
   - l'administrateur **télécharge** les pièces (jamais ouvertes dans la page de Billet+).
9. **Comptes existants** : passent en `A_VERIFIER` (données de développement uniquement à ce jour) ; leurs événements déjà publiés ne sont pas touchés.

## Alternatives considérées

- **Vérification hors application** (pièces par WhatsApp ou e-mail, case cochée) : écartée par SirrDev ; plus simple, mais sans trace des pièces dans Billet+.
- **Stockage objet (S3) ou disque du serveur** : écartés. PostgreSQL est déjà sauvegardé et ne demande aucun service de plus ; le volume (quelques Mo par organisateur) le permet.
- **Validation de chaque événement par l'administrateur** : écartée par SirrDev ; la vérification du compte et le reversement après l'événement suffisent, la suspension couvre le reste.
- **Reversement dès la vente** : écarté. C'est précisément ce qui permettrait à un faux organisateur de partir avec l'argent.

## Conséquences

- **Positives** : un faux organisateur ne peut ni vendre (pas de publication sans pièces vérifiées) ni encaisser (pas de reversement avant l'événement, sans scan, ou vers un autre numéro) ; l'administrateur peut tout arrêter en un clic.
- **Négatives et limites connues** :
  - **pièces d'identité stockées par Billet+** : donnée sensible ; la perte de la clé de chiffrement rend les pièces illisibles, sa fuite les expose ; sauvegarde de la clé à part, rotation hors MVP ;
  - **vérification humaine** : l'administrateur compare les pièces à l'œil ; un faux document crédible peut passer ;
  - **organisateur honnête payé plus tard** : rien avant J+1 ; un acompte n'est pas prévu ;
  - **compte suspendu** : ses acheteurs sont remboursés à la main (décision B, ADR 0006) ;
  - **un seul billet scanné suffit** à lever la condition de scan ; un organisateur peut faire scanner un billet par son propre agent. La liste montre le nombre de billets scannés : l'administrateur juge (un ratio scannés / vendus serait une amélioration) ;
  - **jeton d'accès d'un organisateur suspendu** valable encore 15 minutes au plus : il ne peut ni publier, ni recevoir de reversement, ni modifier sa vérification, mais peut lire ses données et préparer des brouillons impubliables ;
  - **limitation des dépôts** appliquée après la lecture du fichier (intercepteur Spring) : jusqu'à 6 Mo reçus par requête refusée ; stockage borné à 3 pièces par compte ;
  - **déclaration CDP et durée de conservation** à faire valider hors du code ;
  - **après la purge des pièces**, la déclaration (titulaire, numéro, structure, NINEA) et l'historique des décisions restent en base : à préciser dans la déclaration CDP.

# 0008 — Transfert d'un billet : un nouveau QR pour le destinataire, ouvert par un lien et son numéro

## Statut

Acceptée — 31 octobre 2026 (règles choisies par SirrDev : destinataire identifié par son numéro, lien transmis par l'expéditeur, ouverture par le lien **et** le numéro)

## Contexte

Un acheteur offre souvent un billet ou le revend à un proche. Sans fonction prévue, il fait une capture d'écran du QR. Le QR est un jeton porteur (ADR 0004) : l'acheteur et le destinataire en gardent alors chacun une copie valable, et le premier qui la présente entre.

Trois contraintes existent déjà :

- **Pas de compte acheteur** (ADR 0006) : ni l'expéditeur ni le destinataire n'ont de compte. L'acheteur est identifié par son mobile, et sa commande s'ouvre avec un jeton secret.
- **Pas d'envoi de SMS** : Billet+ n'a pas de fournisseur SMS, et en choisir un (compte, coût par message) est une décision à part.
- **Le QR signe la date d'émission du billet** (`iat`), et le contrôle en ligne refuse un QR dont la date ne correspond pas à la base (`ValidateTicketScanUseCase`).

## Décision

1. **Qui transfère** : le détenteur du jeton de la commande, billet par billet (`POST /api/orders/{id}/billets/{ticketId}/transfert`). Le module `orders` vérifie le jeton et que la commande est payée, puis appelle le port public `TransfertBillets` du module `tickets` (ADR 0005, comme `EmissionBillets`).
2. **Conditions** (domaine `Ticket.transfererA`, module `tickets` de niveau 3) : billet valable (`EMIS`), jamais transféré, événement ouvert à la vente. Le billet doit appartenir à la commande, sinon la réponse est le 404 d'un identifiant inconnu.
3. **Transfert définitif, une seule fois.** Sinon, un vendeur pourrait céder un billet puis le reprendre. Le destinataire ne peut pas le transférer à son tour (MVP).
4. **Révocation de l'ancien QR** : le billet reçoit une nouvelle date de QR (`qr_signe_le`, migration V12), différente à la milliseconde, et le QR est re-signé avec elle. Le contrôle en ligne compare désormais le QR à cette date : l'ancien QR, resté chez l'expéditeur, est refusé (« invalide »). La commande de l'acheteur affiche le billet `TRANSFERE`, sans QR, avec le numéro du destinataire masqué.
5. **Accès du destinataire** : un jeton de 256 bits (`JetonTransfert`, même construction que le jeton de commande ; seule son empreinte SHA-256 est stockée), remis **une seule fois** à l'expéditeur. La PWA en fait un lien `/billets/recu/{ticketId}#<jeton>` : le jeton est dans le fragment, que le navigateur n'envoie jamais au serveur. L'expéditeur l'envoie lui-même (WhatsApp, SMS depuis son téléphone).
6. **Ouverture par deux facteurs** : `POST /api/billets/{ticketId}/consultation`, jeton dans l'en-tête `X-Jeton-Billet`, mobile du destinataire dans le corps. Un billet inconnu, non transféré, un jeton ou un numéro faux donnent tous le même 404. Débit limité par IP.
7. **Le remboursement reste attaché à la commande** : si l'événement est annulé, c'est le payeur qui est remboursé (ADR 0006), pas le destinataire.

## Alternatives considérées

- **Envoi automatique par SMS** : écarté au MVP (fournisseur et coût à choisir). Le lien partagé ne coûte rien et peut être complété plus tard par un envoi automatique, sans changer le reste.
- **Lien seul, sans numéro** : plus simple, mais un lien transmis à la mauvaise personne ou intercepté donnerait le billet.
- **Transfert annulable** (tant que le destinataire n'a pas ouvert le billet) : utile en cas d'erreur de numéro, mais rouvre la fraude du vendeur qui reprend un billet cédé. Écarté : la PWA fait saisir le numéro deux fois.
- **Billet rattaché à un compte du destinataire** : contraire à l'ADR 0006.

## Conséquences

- **Le contrôle hors ligne ne détecte pas l'ancien QR** : sa signature reste authentique. Un scanner sans réseau l'accepte, comme il accepte un billet déjà passé à une autre porte (limite déjà connue, ADR 0004 et `docs/frontend-design-system.md`). Le serveur signale le doublon à la synchronisation.
- **Le transfert ne protège pas contre un expéditeur malhonnête** : c'est lui qui voit le lien, et il connaît le numéro du destinataire. Il peut donc ouvrir le billet transféré. Entre inconnus, le transfert vaut ce que vaut la confiance envers l'expéditeur ; il protège surtout contre la copie involontaire (capture d'écran gardée).
- Le lien est gardé sur l'appareil de l'expéditeur (IndexedDB), pour qu'il puisse le renvoyer.
- **Numéro mal saisi** : le transfert étant définitif, le billet n'est plus récupérable sans intervention manuelle en base. La double saisie réduit ce risque sans l'éliminer.
- Le billet reçu est gardé sur l'appareil du destinataire (IndexedDB, version 2 de la base) : il s'affiche sans réseau et figure dans « Mes billets ».
- **Le statut `TRANSFERE` n'existe que dans la vue de l'acheteur** (`BilletVendu`, contrat figé par `BilletsDeCommandeServiceTest`) : en base, un billet transféré reste `EMIS` jusqu'à son scan, pour que le contrôle d'entrée reste inchangé.
- **Format du mobile écrit à plusieurs endroits** (`orders.domain.NumeroTelephone`, `Ticket.transfererA`, `ConsulterBilletTransfereRequest`, contrainte de V12) : duplication assumée, un module ne dépendant pas du domaine d'un autre, comme pour `JetonTransfert`.
- **Migration V12 sous verrou** : ajout de colonne, remplissage de `qr_signe_le` et contraintes s'exécutent dans une transaction qui bloque la table `tickets` le temps de la parcourir. Négligeable au MVP ; sur une grosse table, découper la migration (contraintes en `NOT VALID` puis validées à part).
- Nouvelles colonnes `tickets.qr_signe_le`, `transfere_a`, `transfert_jeton_empreinte`, `transfere_le` (V12), avec une contrainte « transfert complet ou absent ».

## Références

- ADR 0004 (QR Ed25519, contrôle hors ligne), ADR 0005 (communication entre modules), ADR 0006 (achat sans compte)
- `tickets/domain/Ticket.java` (`transfererA`, `appartientAuDestinataire`), `tickets/application/usecase/TransfertBilletService.java`, `ConsulterBilletTransfereUseCase.java`, `ValidateTicketScanUseCase.java`
- `orders/application/usecase/TransfererBilletUseCase.java`
- `web/src/espaces/acheteur/pages/TransfertBilletPage.tsx`, `BilletRecuPage.tsx`

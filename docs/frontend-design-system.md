# Frontend Billet+ — design et composants

PWA unique React 18 + TypeScript strict + Vite ([ADR 0004](architecture/adr/0004-pwa-vs-react-native.md)), refondue en octobre 2026 sur la direction visuelle validée (canevas « Billet+ — proposition de design »).

## 1. Direction visuelle

| Élément | Choix |
|---|---|
| Fond | blanc, gris lavande froid `#F4F3F8` pour les écrans à cartes |
| Encre | `#0F1115` (texte), `#4A4F5C` (secondaire) |
| Action | violet `#5B3DF5` (texte blanc dessus : 6,3:1) |
| Accent festif | ambre `#FFB020`, **toujours avec texte foncé** |
| Titres / texte | Space Grotesk / Inter, embarquées (`@fontsource-variable/*`) : affichées hors ligne |
| Affiches | formes géométriques générées depuis l'identifiant (`Affiche`) : aucune image distante |

Toutes les valeurs sont des variables `--bp-*` dans `web/src/styles/tokens.css` (contrastes mesurés en commentaire). Aucun composant n'écrit une couleur en dur.

## 2. Organisation du code (`web/src/`)

| Dossier | Contenu | Règle |
|---|---|---|
| `styles/` | `tokens.css`, `global.css` (remise à zéro, focus visible, `prefers-reduced-motion`) | seul style global |
| `ui/` | composants réutilisables, CSS Modules | point d'entrée **unique** `@/ui` (`ui/index.ts`) |
| `commun/` | `http.ts` (client API, `ApiError`, `messageErreur`, option `authentifie`), `format.ts` (FCFA, dates de Dakar, mobile +221), `session.ts` et `auth.ts` (jetons, session), `FournisseurSession.tsx` (`FournisseurSession`, garde `ExigerRole`), `cacheHorsLigne.ts` (règle du service worker) | ne dépend d'aucun espace (ESLint) ; peut utiliser `@/ui` |
| `espaces/<espace>/` | `api.ts` (contrats = DTO Java), stockage local, `pages/`, `index.ts` | ne s'importe que par son `index.ts` (ESLint `no-restricted-imports`) |

Espaces livrés : **acheteur** (sans compte, avec `/aide`), **organisateur** (`/organisateur/...`, dont `/organisateur/compte`), **contrôle d'entrée** (`/scanner`, `/scanner/connexion`, `/scanner/evenements/:id`, dossier `espaces/controle`) et **administration** (`/admin/connexion`, `/admin`, dossier `espaces/admin`) ; une règle `isolerEspace(...)` par espace. Toute adresse inconnue affiche `commun/PageIntrouvable` (retour adapté à l'espace), jamais une redirection silencieuse. Le contrôle et l'administration sont **chargés à la demande** (`React.lazy` dans `App.tsx`) : `jsqr` et `@noble/ed25519` ne pèsent pas sur le bundle de l'acheteur. `src/ui` et `src/commun` n'importent jamais un espace : une règle ESLint l'interdit.

Dépendances propres au contrôle : `@noble/ed25519` (vérification des QR avec la clé publique seule) et `jsqr` (décodage caméra, tous navigateurs, y compris Safari iOS sans `BarcodeDetector`).

## 3. Composants (`@/ui`)

| Fichier | Composants |
|---|---|
| `Cadre.tsx` | `Cadre` (en-tête, contenu centré, navigation ; `etroit`, `sansNav`, `fondGris`, `liens`, `accueil`, `actions`, `bandeauHorsLigne`). Bandeau « hors connexion » sous l'en-tête, dans une région `role="status"` toujours présente ; désactivé dans le scanner, qui affiche son propre état. Liens avec `libelleCourt` : libellé court dans la barre basse du téléphone, libellé complet dès 720 px et toujours comme nom accessible |
| `Bouton.tsx` | `Bouton`, `BoutonLien` (une navigation reste un `<a>`) |
| `CarteEvenement.tsx` | `GrilleEvenements`, `CarteEvenement`, `EvenementALaUne` |
| `Formulaire.tsx` | `ChampTexte` (libellé, aide, erreur, multiligne), `ChampRecherche`, `ChampTelephone` (+221, mise en forme), `ChoixCartes` (radios natifs) |
| `Pilotage.tsx` | `Indicateurs`, `Indicateur` (avec barre de remplissage `progressbar`), `Pastille` (icône + texte), `Tableau` (`caption`, `th scope`, défile sur mobile) |
| `Billetterie.tsx` | `Panneau`, `BarreAction`, `SelecteurQuantite`, `LigneCategorie`, `Recapitulatif`, `CompteARebours`, `PasseBillet` |
| `Retour.tsx` | `Avis`, `EtatVide`, `Chargement` |
| `Controle.tsx` | `EcranControle` (fond sombre), `Viseur` (cadre de la caméra), `PanneauVerdict` (icône + « Laisser entrer » / « Refuser l'entrée », `aria-live="assertive"`, mention « Contrôle sans réseau »), `CompteursControle` |
| `Affiche.tsx`, `Icone.tsx` | visuels décoratifs (`aria-hidden`) ; icônes dont `retour`, `suivant`, `aide`, `compte`, `copier`, `telecharger` |
| `outils.ts` | `motifDe`, `codeBillet`, `useCompteARebours`, `useEnLigne`, `navAcheteur` (3 entrées), `navOrganisateur` (4 entrées, libellés courts) |

Règle pour tout composant **ajouté** : il vit dans le fichier de son groupe, est exporté par `ui/index.ts`, et a un test de comportement **et** de nom accessible dans `ui/ui.test.tsx`. Aujourd'hui, seuls `SelecteurQuantite`, `ChampTelephone`, `ChoixCartes`, `PasseBillet` et `Cadre` y sont testés directement ; les autres sont couverts par les tests de parcours (`espaces/acheteur/parcours.test.tsx`, `espaces/organisateur/organisateur.test.tsx`). Fonctions et constantes vont dans un `.ts` (rechargement à chaud de Vite).

## 4. Adaptatif

Mobile d'abord ; `@media (min-width: 720px)` (tablette) puis `1024px` (ordinateur), jamais `max-width`. Les valeurs sont en dur dans les CSS (une variable CSS ne fonctionne pas dans `@media`).

| Format | Navigation | Grilles |
|---|---|---|
| < 720 px | barre basse | 1 colonne |
| ≥ 720 px | liens dans l'en-tête (même `<nav>`, même ordre de tabulation) | 2 colonnes, événement en 2 colonnes |
| ≥ 1024 px | idem | 3 colonnes, paiement formulaire + récapitulatif |

## 5. Règles de conception

- **Argent : le backend fait foi.** Les composants affichent, ne calculent pas. `formatFcfa` lève une erreur sur un montant non entier. Avant création de la commande, le récapitulatif est un **« Total estimé »** (prix unitaires du backend × quantités) ; le montant ferme est `montantTotalFcfa` de la commande. Ni stock (volontairement caché à l'acheteur, voir `TicketCategoryDto`), ni frais, ni genre, ni prix « dès » : le backend ne les expose pas. Côté organisateur, recettes, vendus, disponibles et entrées viennent du tableau de bord du backend ; seul `quotaTotal` des catégories est additionné, pour la barre de remplissage.
- **Jeton de commande (ADR 0006).** Vérifier le stockage (`verifierStockage`) **avant** `POST /api/orders`, enregistrer le jeton dans IndexedDB **avant** toute redirection, ne le transmettre que dans l'en-tête `X-Jeton-Commande`, jamais dans une URL, un state de navigation ou un cache du service worker.
- **Paiement (ADR 0007).** Redirection uniquement vers un lien `https:` renvoyé par le backend (`allerPayer`). Après retour, `/commandes/:id` suit le statut toutes les 4 s.
- **QR (ADR 0004).** `PasseBillet` dessine la chaîne signée Ed25519 du backend telle quelle ; aucun secret côté client.
- **Billet PDF** (`espaces/acheteur/billetPdf.ts`, bouton `pages/BoutonBilletsPdf.tsx`) : généré **sur l'appareil**, sans route backend ; A5, un billet par page, QR **vectoriel** (`qrcode`, niveau M comme à l'écran) dessinant la chaîne signée telle quelle, code de secours, avertissement « jeton porteur », identifiant de commande, **jamais** le jeton de commande ni le téléphone. Seuls les billets `EMIS` avec QR ; sans billet valable, pas de bouton. `jspdf` (verrouillé en `~4.2.1`) et `qrcode` sont chargés **au clic**. Tests en `@vitest-environment node` : le dessin réel (`dessinerQr`) est rastérisé puis décodé par jsQR, et doit redonner exactement le contenu signé.
- **Dates** toujours à l'heure de Dakar (`Africa/Dakar`).
- **Accessibilité (WCAG 2.2 AA visé)** : libellé visible sur chaque champ, erreurs en `role="alert"`, cibles de 44 px, état jamais porté par la couleur seule, champs à 16 px (pas de zoom iOS), compte à rebours `role="timer"` non annoncé à chaque seconde.

## 6. Service worker

- Précache (`globPatterns`) : HTML, JS, CSS, SVG/PNG/ICO, polices `woff2` (sauf sous-ensembles vietnamien, cyrillique, grec), clé **publique** `keys/ed25519_public.pem` (vérification hors ligne du scanner), et le morceau JS du contrôle chargé à la demande : le scanner s'ouvre sans réseau après une première visite. Le morceau `billetPdf` est précaché aussi (PDF hors ligne) ; les dépendances optionnelles de jsPDF (html2canvas, DOMPurify, canvg), nommées `jspdf-optionnel-*` par `chunkFileNames`, en sont exclues (`globIgnores`). Après une mise à jour de jsPDF, relire la liste du précache dans `dist/sw.js`. **Rotation de clé** : republier la PWA ; d'ici là, un appareil à l'ancienne clé refuse tous les billets hors ligne (échec sûr). La clé livrée doit être celle de `billetplus.security.qr-signing.public-key-path` en production (point de la checklist de déploiement).
- Cache d'exécution `evenements-publics` (`NetworkFirst`) : uniquement `GET /api/events`, `/api/events/{uuid}` et `/api/events/{uuid}/categories`, **sans en-tête `Authorization`** (`estLecturePubliqueEvenements`, testée). Workbox ignore `Authorization` dans sa clé de cache : une réponse d'organisateur (`/mine`, quotas, tableau de bord) ne doit jamais y entrer. « disponible » peut être périmé hors ligne : la création de commande revalide.
- **Piège :** en mode `generateSW`, Workbox recopie le TEXTE de la fonction `urlPattern` dans `dist/sw.js`. Elle doit rester autonome (aucun import ni variable extérieure), sinon `ReferenceError` dans le service worker. Un test la reconstruit depuis son seul texte ; après un changement, vérifier `dist/sw.js`.
- Jamais de cache sur `/api/orders` (réponses `no-store` côté backend) : les billets hors ligne viennent d'IndexedDB.

## 6 bis. Session (organisateur, puis agent de contrôle)

- Jetons dans `localStorage` (clé `billetplus.session`) : jeton d'accès 15 min, de rafraîchissement 7 jours. Limite assumée au MVP : lisibles par un script injecté (XSS) ; React échappe le contenu de l'API et la CSP du déploiement doit interdire les scripts tiers. Un cookie httpOnly demanderait un changement du backend.
- `http(chemin, { authentifie: true })` ajoute le Bearer ; les routes publiques et celles de l'acheteur n'en envoient jamais.
- **Renouvellement préventif** : le backend ignore un jeton expiré et sert alors la vue ANONYME sur les routes publiques (404 pour un brouillon), jamais 401. Le client lit donc `exp` (sans vérifier la signature) et renouvelle à moins de 30 s de l'échéance. Sur 401 : un seul rafraîchissement partagé, puis rejeu. Refus du backend : session fermée ; coupure réseau : session gardée.
- La garde `ExigerRole` n'évite qu'un écran inutile : le backend reste seul juge (`@PreAuthorize`). Le client ne choisit jamais son rôle ni l'organisateur d'un événement. Un événement publié d'un autre organisateur (route publique, 200) s'affiche « introuvable » dans l'espace organisateur, comme un brouillon (404).
- Déconnexion propagée aux autres onglets (événement `storage`).
- Profil (id, e-mail, nom, rôle ; rien de secret) mémorisé à la connexion (`billetplus.profil`) : sans réseau, la session reste ouverte avec ce profil, pour que le scanner se rouvre le jour J. Un jeton **refusé** par le backend ferme toujours la session. Routes `/scanner` : rôles `AGENT` et `ORGANISATEUR`.

## 6 ter. Contrôle d'entrée (ADR 0004)

- **Format du QR** (`Ed25519QRSigner`) : `{"tid":…,"eid":…,"iat":…}.<signature hex>`, coupé au **premier** point comme le backend ; signature sur les octets UTF-8 du JSON. Vérification **stricte RFC 8032** (`zip215: false`), alignée sur la JCA ; en-tête DER de la clé contrôlé (OID Ed25519). Un test vérifie un QR signé par la JCA du JDK.
- **Décision** (`espaces/controle/decision.ts`) : le serveur fait foi quand il répond (`POST /api/events/{id}/scans`, délai 5 s) ; **un 403 n'est jamais contourné** par le hors-ligne. Réseau absent, délai dépassé, 429 ou 5xx : signature vérifiée sur l'appareil, billet d'un autre événement refusé (`eid` signé), billet déjà entré **par cet appareil** refusé, sinon entrée acceptée et mise en file.
- **Limite assumée (à dire aux équipes)** : hors ligne, un billet annulé, remboursé ou déjà passé à une **autre** porte ne peut pas être détecté ; l'affectation de l'agent n'est pas vérifiée non plus. Le serveur le signale à la synchronisation (doublons et refus affichés « à signaler au responsable »). Écran : bandeau « Sans réseau » et mention « Contrôle sans réseau » sur chaque verdict.
- **Mémoire de l'appareil** (`fileHorsLigne.ts`, IndexedDB `billetplus-controle` v1) : `scans` (entrées à envoyer) et `vus` (billets entrés ici). Une seule synchronisation à la fois par événement (sinon faux doublons) ; refus définitifs (4xx) retirés de la file ; réseau, 401, 429 et 5xx gardent la file. La déconnexion **efface** cette mémoire (QR = jetons porteurs), après avertissement s'il reste des entrées non envoyées ; après un 403, l'agent peut effacer les entrées qui ne seront jamais acceptées.
- **Caméra** (`useLecteurQr.ts`) : caméra arrière, jsQR toutes les 200 ms, pistes arrêtées au démontage (y compris sur l'écran « non affecté »). Saisie « douchette » (lecteur qui tape le contenu puis Entrée), bornée à 1024 caractères.
- **Choix de l'événement** : l'agent voit les événements auxquels il est affecté (`GET /api/events/affectes`, rôle AGENT : publiés, début dans les 24 dernières heures ou à venir, ou fin encore à venir) ; l'organisateur voit ses événements publiés (`/events/mine`). La liste ne donne aucun droit : la route de scan revérifie l'affectation (403). Route authentifiée, donc hors cache du service worker : la **dernière liste reçue** est gardée dans `localStorage` (`billetplus.controle.evenements`, rattachée au compte) pour rouvrir le scanner sans réseau, et effacée à toute fin de session.

## 6 quater. Administration (remboursements manuels, décision B)

- Connexion `/admin/connexion` réservée au rôle `ADMIN` (compte créé hors API par les variables d'environnement du backend, `PremierAdminInitializer`) ; un autre rôle est refusé et ses jetons effacés. Pas de barre de navigation : un seul écran.
- `/admin` lit `GET /api/admin/commandes-a-rembourser` (payées sans billet, ou payées pour un événement annulé) : événement, motif lisible (`MOTIFS` ; un motif inconnu reste affiché tel quel), téléphone, moyen, référence, montant ; total des montants du backend. **Lecture seule** : aucun remboursement n'est déclenché par l'application.
- Ordinateur et tablette : tableau ; **téléphone : une carte par commande** (c'est là que se font les remboursements Wave et Orange Money). Copie du téléphone et de la référence en un geste, retour « Copié » / « Copie impossible » dans une région live hors du bouton.
- Export CSV (`remboursements.ts`) : `;` et BOM UTF-8 (Excel en français), formules neutralisées (`= + - @` préfixés d'une apostrophe), téléphone au format national (un `+` en tête serait pris pour une formule). Données personnelles : avertissement affiché, jamais en cache ni en stockage local.
- Plafond de 500 commandes côté serveur, sans pagination : un avis le signale.

## 7. Commandes (PowerShell, depuis `C:\Users\bague\Desktop\billetplus\web`)

```powershell
npm run dev        # http://localhost:5173, proxy /api -> 8081
npm run lint       # 0 avertissement exigé (CI)
npm run test       # Vitest + Testing Library (jsdom ; fake-indexeddb dans le test du stockage local)
npm run build      # tsc --noEmit puis vite build
```

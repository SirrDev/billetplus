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

Espaces livrés : **acheteur** (sans compte) et **organisateur** (`/organisateur/...`). À venir : contrôle d'entrée (`/scanner/...`) ; ajouter alors sa règle `isolerEspace('scanner')` dans `web/.eslintrc.cjs`. `src/ui` et `src/commun` n'importent jamais un espace : une règle ESLint l'interdit.

## 3. Composants (`@/ui`)

| Fichier | Composants |
|---|---|
| `Cadre.tsx` | `Cadre` (en-tête, contenu centré, navigation ; `etroit`, `sansNav`, `fondGris`, `liens`, `accueil`, `actions`) |
| `Bouton.tsx` | `Bouton`, `BoutonLien` (une navigation reste un `<a>`) |
| `CarteEvenement.tsx` | `GrilleEvenements`, `CarteEvenement`, `EvenementALaUne` |
| `Formulaire.tsx` | `ChampTexte` (libellé, aide, erreur, multiligne), `ChampRecherche`, `ChampTelephone` (+221, mise en forme), `ChoixCartes` (radios natifs) |
| `Pilotage.tsx` | `Indicateurs`, `Indicateur` (avec barre de remplissage `progressbar`), `Pastille` (icône + texte), `Tableau` (`caption`, `th scope`, défile sur mobile) |
| `Billetterie.tsx` | `Panneau`, `BarreAction`, `SelecteurQuantite`, `LigneCategorie`, `Recapitulatif`, `CompteARebours`, `PasseBillet` |
| `Retour.tsx` | `Avis`, `EtatVide`, `Chargement` |
| `Affiche.tsx`, `Icone.tsx` | visuels décoratifs (`aria-hidden`) |
| `outils.ts` | `motifDe`, `codeBillet`, `useCompteARebours`, `navAcheteur`, `navOrganisateur` |

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
- **Dates** toujours à l'heure de Dakar (`Africa/Dakar`).
- **Accessibilité (WCAG 2.2 AA visé)** : libellé visible sur chaque champ, erreurs en `role="alert"`, cibles de 44 px, état jamais porté par la couleur seule, champs à 16 px (pas de zoom iOS), compte à rebours `role="timer"` non annoncé à chaque seconde.

## 6. Service worker

- Précache (`globPatterns`) : HTML, JS, CSS, SVG/PNG/ICO, polices `woff2` (sauf sous-ensembles vietnamien, cyrillique, grec), clé **publique** `keys/ed25519_public.pem` (pour le futur scanner hors ligne).
- Cache d'exécution `evenements-publics` (`NetworkFirst`) : uniquement `GET /api/events`, `/api/events/{uuid}` et `/api/events/{uuid}/categories`, **sans en-tête `Authorization`** (`estLecturePubliqueEvenements`, testée). Workbox ignore `Authorization` dans sa clé de cache : une réponse d'organisateur (`/mine`, quotas, tableau de bord) ne doit jamais y entrer. « disponible » peut être périmé hors ligne : la création de commande revalide.
- **Piège :** en mode `generateSW`, Workbox recopie le TEXTE de la fonction `urlPattern` dans `dist/sw.js`. Elle doit rester autonome (aucun import ni variable extérieure), sinon `ReferenceError` dans le service worker. Un test la reconstruit depuis son seul texte ; après un changement, vérifier `dist/sw.js`.
- Jamais de cache sur `/api/orders` (réponses `no-store` côté backend) : les billets hors ligne viennent d'IndexedDB.

## 6 bis. Session (organisateur, puis agent de contrôle)

- Jetons dans `localStorage` (clé `billetplus.session`) : jeton d'accès 15 min, de rafraîchissement 7 jours. Limite assumée au MVP : lisibles par un script injecté (XSS) ; React échappe le contenu de l'API et la CSP du déploiement doit interdire les scripts tiers. Un cookie httpOnly demanderait un changement du backend.
- `http(chemin, { authentifie: true })` ajoute le Bearer ; les routes publiques et celles de l'acheteur n'en envoient jamais.
- **Renouvellement préventif** : le backend ignore un jeton expiré et sert alors la vue ANONYME sur les routes publiques (404 pour un brouillon), jamais 401. Le client lit donc `exp` (sans vérifier la signature) et renouvelle à moins de 30 s de l'échéance. Sur 401 : un seul rafraîchissement partagé, puis rejeu. Refus du backend : session fermée ; coupure réseau : session gardée.
- La garde `ExigerRole` n'évite qu'un écran inutile : le backend reste seul juge (`@PreAuthorize`). Le client ne choisit jamais son rôle ni l'organisateur d'un événement. Un événement publié d'un autre organisateur (route publique, 200) s'affiche « introuvable » dans l'espace organisateur, comme un brouillon (404).
- Déconnexion propagée aux autres onglets (événement `storage`).

## 7. Commandes (PowerShell, depuis `C:\Users\bague\Desktop\billetplus\web`)

```powershell
npm run dev        # http://localhost:5173, proxy /api -> 8081
npm run lint       # 0 avertissement exigé (CI)
npm run test       # Vitest + Testing Library (jsdom ; fake-indexeddb dans le test du stockage local)
npm run build      # tsc --noEmit puis vite build
```

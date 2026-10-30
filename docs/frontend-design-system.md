# Design system Billet+ — composants React (PWA)

Bibliothèque de composants du frontend Billet+ : **React 18 + TypeScript strict + Vite**, conforme à l'[ADR 0004](architecture/adr/0004-pwa-vs-react-native.md) (PWA unique, trois rôles par routes).
Elle traduit en code le prototype « Vibrant et festif » (canvas Design « Billet+ Mobile »).

- Emplacement : `web/src/design-system/`
- Point d'entrée **unique** : `web/src/design-system/index.ts`
- Aucune dépendance de style (pas de Tailwind) : CSS Modules + variables CSS préfixées `--bp-*`.
- Aucun style global : seul `<Screen>` pose la typographie et l'anneau de focus, dans son sous-arbre.

## 1. Installation

Pourquoi ces paquets : `react-router-dom` pour les liens/onglets de navigation, `qrcode.react` pour *dessiner* le QR (il ne le signe jamais), `@fontsource-variable/*` pour **embarquer** les polices dans le bundle — indispensable pour une PWA hors ligne (une police chargée depuis Google Fonts ne s'afficherait pas sans réseau).

Depuis la racine du dépôt, dans PowerShell :

```powershell
# On se place dans le projet frontend (là où se trouve package.json)
Set-Location web

# Dépendances d'exécution
npm install react-router-dom qrcode.react @fontsource-variable/bricolage-grotesque @fontsource-variable/plus-jakarta-sans

# Dépendances de test (uniquement en développement : -D)
npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event axe-core
```

Si `react-router-dom` est déjà installé dans votre projet, npm ne fait que vérifier sa présence. Les composants ont été validés avec **react-router-dom 6** (`Link`, `NavLink`).

### Configuration de Vitest

Si vous n'avez pas encore de tests, ajoutez dans `web/vite.config.ts` :

```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react() /*, VitePWA(...) */],
  test: {
    environment: 'jsdom',   // simule un navigateur pour tester les composants
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } }, // noms de classes lisibles dans les tests
  },
});
```

Créez `web/src/test/setup.ts` :

```ts
import '@testing-library/jest-dom/vitest';
```

Dans `tsconfig.json`, ajoutez `"types": ["vite/client", "vitest/globals", "@testing-library/jest-dom"]` si ce n'est pas déjà fait.

### Vérification

```powershell
npx tsc --noEmit    # typage strict
npx vitest run      # 40 tests (composants, formats, accessibilité)
npm run build       # le bundle se construit, polices comprises
```

## 2. Utilisation

```tsx
import { Screen, TopBar, TicketCategoryRow, PriceSummary, StickyActionBar, ButtonLink } from '@/design-system';
```

Une page type (écran « Choisir ses billets ») :

```tsx
<Screen withActionBar>
  <TopBar title="Vos billets" backTo={`/evenements/${id}`} backLabel="Retour à l'événement" />
  {categories.map((c) => (
    <TicketCategoryRow key={c.id} {...c} quantity={qty[c.id]} onQuantityChange={(n) => setQty({ ...qty, [c.id]: n })} />
  ))}
  <PriceSummary lines={quote.lines} totalFcfa={quote.totalFcfa} />
  <StickyActionBar>
    <ButtonLink to="/paiement" fullWidth>Payer {formatFcfa(quote.totalFcfa)}</ButtonLink>
  </StickyActionBar>
</Screen>
```

Pour consulter tous les composants en situation, montez la galerie sur une route **réservée au développement** :

```tsx
import { ComponentGallery } from '@/design-system/gallery/ComponentGallery';
{import.meta.env.DEV && <Route path="/dev/composants" element={<ComponentGallery />} />}
```

> L'alias `@/` suppose un `paths` dans `tsconfig.json` et `resolve.alias` dans Vite. Sans alias, utilisez un chemin relatif vers `design-system`.

## 3. Catalogue et correspondance avec les écrans du prototype

| Écran du prototype | Composants utilisés |
|---|---|
| 1 Découvrir | `Screen`, `TopBar`, `IconLink`, `ChipGroup`, `EventFeaturedCard`, `EventRow`, `BottomNav` |
| 2 Événement | `Poster`, `Badge`, `TicketCategoryRow` (version lecture), `StickyActionBar`, `ButtonLink` |
| 3 Choisir ses billets | `TicketCategoryRow`, `QuantityStepper`, `PriceSummary`, `StickyActionBar` |
| 4 Paiement Wave | `CountdownNotice`, `PriceSummary`, `RadioCardGroup`, `PhoneField`, `Notice` |
| 5 Paiement confirmé | `SuccessHero`, `PriceSummary`, `ButtonLink` |
| 6 Mes billets | `SegmentedTabs`, `tabPanelProps`, `EventFeaturedCard`, `EventRow`, `EmptyState` |
| 7 Mon billet | `TicketPass`, `Notice`, `Button` |
| 8 Ventes (mobile) / 12 Tableau de bord (bureau) | `MetricCard`, `ProgressBar`, `BarChart`, `DataTable`, `Badge` |
| 9 Contrôle d'entrée | `ScannerViewfinder`, `ScanResultPanel`, `EntryCounter`, `Notice`, `Screen dark` |
| 10 Nouvel événement / 11 Catégorie de billet | `TextField`, `ChipGroup`, `Toggle`, `PriceSummary` |
| 13 Connexion | `PhoneField`, `TextField`, `Button` |
| 14 Mon compte | `ListRow`, `Toggle`, `BottomNav` |
| 15 Transférer un billet | `RadioCardGroup`, `PhoneField`, `Notice` |
| 16 Filtres / 17 Résultats | `ChipGroup`, `FilterTag`, `EventRow` (avec `highlight`) |
| 18 Paiement refusé / expiré | `EmptyState` (`tone="danger"` / `"warning"`), `Notice` |
| 19 États vides / 20 Pas de connexion | `EmptyState` |

### Bibliothèque par fichier

| Fichier | Composants |
|---|---|
| `Button.tsx` | `Button`, `ButtonLink`, `IconButton`, `IconLink` |
| `Chip.tsx` | `Chip`, `ChipGroup`, `FilterTag` |
| `Form.tsx` | `TextField`, `PhoneField`, `Toggle`, `RadioCardGroup` |
| `Feedback.tsx` | `Badge`, `Notice`, `ProgressBar`, `EmptyState`, `SuccessHero` |
| `Navigation.tsx` | `TopBar`, `BottomNav`, `SegmentedTabs` (+ `buyerNavItems`, `organizerNavItems`) |
| `Layout.tsx` | `Screen`, `StickyActionBar`, `ListRow`, `VisuallyHidden` |
| `Event.tsx` | `Poster`, `EventFeaturedCard`, `EventRow` |
| `Ticketing.tsx` | `QuantityStepper`, `TicketCategoryRow`, `PriceSummary`, `CountdownNotice`, `TicketPass` |
| `Organizer.tsx` | `MetricCard`, `BarChart`, `DataTable` |
| `Scanner.tsx` | `ScannerViewfinder`, `ScanResultPanel`, `EntryCounter` |
| `lib/format.ts` | `formatFcfa`, `formatEventDate`, `formatCountdown`, `formatSnPhone`, `sanitizeSnPhone`, `isValidSnMobile` |
| `hooks/useCountdown.ts` | `useCountdown` |

## 4. Règles de conception à respecter

**Argent — le backend fait foi.** `PriceSummary`, `TicketCategoryRow` et `MetricCard` *affichent* des montants, ils n'en calculent aucun. Le frais de service (5 % dans le prototype) est une règle métier : il doit venir de l'API (un devis `quote` avec lignes et total), jamais d'un calcul côté client. La galerie calcule 5 % uniquement pour la démonstration. Les montants sont des **entiers** en FCFA : `formatFcfa` lève une erreur sur un nombre décimal, pour détecter un bug plutôt que d'afficher un prix faux.

**QR code — le client dessine, ne signe jamais** (ADR 0004). `TicketPass` reçoit `qrPayload`, la chaîne signée Ed25519 produite par le backend, et la rend telle quelle. La vérification hors ligne côté scanner (`@noble/ed25519`, clé publique uniquement) est un autre module : ces composants ne contiennent ni clé ni secret.

**Compte à rebours — l'heure vient du serveur.** `CountdownNotice` attend un `deadline` calculé par le backend à la création de la réservation (verrou Redis). L'horloge d'un téléphone peut être fausse. Le hook se cale sur `Date.now()` à chaque tick, donc un onglet mis en veille ne dérive pas.

**Dates — toujours l'heure de Dakar.** `formatEventDate` force `Africa/Dakar` (UTC+0, sans changement d'heure) : un acheteur en voyage voit l'heure locale de l'événement, pas celle de son téléphone.

**Accessibilité (WCAG 2.2 AA visé)**
- Chaque champ a un `<label>` visible ; les erreurs sont annoncées (`role="alert"`).
- Cibles tactiles de **44 px minimum** (boutons ronds, puces, stepper, onglets).
- L'état n'est **jamais porté par la couleur seule** : badges et verdicts du scanner ont icône + texte (« Laisser entrer » / « Refuser l'entrée »).
- Onglets au motif WAI-ARIA (`tabIndex` itinérant, flèches, Début/Fin) ; `BottomNav` pose `aria-current="page"` ; `RadioCardGroup` utilise des radios natifs dans un `fieldset`.
- `BarChart` expose un tableau masqué avec toutes les valeurs (le graphique SVG seul est inutilisable au lecteur d'écran).
- Le compte à rebours n'est pas annoncé à chaque seconde (`role="timer"`, `aria-live="off"`).
- Champs de saisie à **16 px** : iOS ne zoome pas la page au focus.
- `prefers-reduced-motion` respecté.

**Contrastes mesurés** (ratio WCAG, seuil AA : 4,5 pour le texte, 3 pour les éléments graphiques) : texte principal 18,0 · texte secondaire 9,1 · violet sur blanc 7,3 · bouton orange avec texte foncé 5,8 · vert « valide » 5,7 · ambre « déjà utilisé » 6,6 · rouge « invalide » 6,7 · texte clair sur fond sombre 12,2 · placeholder 5,5. Interrupteur : curseur blanc sur piste grise 3,7 et sur piste verte 4,4 (éléments graphiques, seuil 3).

## 5. Ce qui n'est PAS inclus (volontairement)

- Les **pages** et le routage : la bibliothèque fournit des briques, pas les écrans branchés à l'API.
- Le **décodage de la caméra** (`getUserMedia` + lecture du QR) et la vérification de signature : `ScannerViewfinder` accepte un `<video>` en enfant et ne dessine que le cadre.
- Le **mode sombre** : le prototype est en thème clair ; les couleurs sont centralisées dans `styles/tokens.css`, un thème sombre se ferait en redéfinissant ces variables.
- Les **icônes d'application** et le `manifest.webmanifest` (config `vite-plugin-pwa`).

## 6. Points d'attention pour l'intégration PWA

- `@fontsource-variable/*` embarque plusieurs sous-ensembles (latin, latin-ext, vietnamien). Le navigateur ne télécharge que ceux nécessaires, mais Workbox précache tout ce que `globPatterns` couvre. Pour alléger le premier chargement, importez les sous-ensembles ciblés (ex. `@fontsource-variable/plus-jakarta-sans/wght.css` en limitant `unicode-range`) ou excluez `*-vietnamese-*.woff2` du précache.
- Les affiches (`Poster`) sont des SVG générés : aucune image distante, donc rien à précacher. Pour de vraies images d'organisateur, prévoyez une stratégie de cache runtime (`CacheFirst` avec expiration) et conservez `Poster` comme repli hors ligne.
- `TicketPass` doit lire les billets depuis **IndexedDB** (ADR 0004) afin de rester affichable sans réseau ; la source de données n'est pas dans la bibliothèque.

## 7. Contribution

Branche : `feature/web-design-system`. Un composant ajouté doit : (1) vivre dans le fichier de son groupe, (2) être exporté dans `index.ts`, (3) apparaître dans `ComponentGallery`, (4) avoir un test qui vérifie son comportement **et** son nom accessible. Ne jamais importer un fichier interne (`components/…`, `*.module.css`) depuis une page : passer par `index.ts`.

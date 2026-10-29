# 0004 — PWA React plutôt que React Native pour le frontend

## Statut

Acceptée — 17 août 2026

## Contexte

Billet+ a besoin de trois surfaces client, aux usages différents :

- **Organisateur** : création d'événement, dashboard de ventes — usage majoritairement bureau/web.
- **Acheteur** : recherche, achat, consultation des billets — usage majoritairement mobile.
- **Scanner** : validation des billets à l'entrée d'un événement — usage mobile, avec un besoin fort de fonctionnement hors ligne (connectivité instable le jour J, déjà identifié comme risque dans le cahier des charges).

L'option initialement retenue pour les deux surfaces mobiles était **React Native**, avec un web séparé en React pour l'organisateur — soit deux bases de code distinctes (web et mobile) à maintenir en parallèle, plus la contrainte de publication sur les stores (App Store, Google Play) pour toute mise à jour de l'app acheteur ou scanner.

Pour un MVP à livrer en 3 mois avec une équipe restreinte, cette duplication a un coût réel :

- Deux écosystèmes techniques à maîtriser (React web + React Native), avec des composants et parfois des logiques dupliqués entre les deux.
- Le passage par les stores ajoute un délai de validation (particulièrement variable côté Apple) qui contredit l'objectif d'itération rapide autour du premier événement pilote.
- Le scanner en particulier n'a pas besoin de fonctionnalités propres au natif profond (accès matériel avancé) au-delà de la caméra, disponible en web via `getUserMedia`.

À l'inverse, une **Progressive Web App (PWA)** permet un unique codebase React, installable sur l'écran d'accueil sans passer par un store, avec un support natif du mode hors ligne via Service Worker — directement pertinent pour le scanner en zone de connectivité instable.

Cette option introduit cependant deux points d'attention techniques à trancher explicitement : la fiabilité du scan caméra en environnement web sous forte charge (file d'attente à l'entrée d'un concert), et surtout un problème de sécurité qui n'existait pas de la même façon en React Native.

### Problème de sécurité découvert lors de l'analyse

Le mécanisme de vérification de QR code initialement prévu ([voir cahier des charges](../../cahier-des-charges/)) reposait sur une **signature HMAC à secret partagé**, vérifiable hors ligne par l'app scanner. Sur une PWA, ce secret se retrouverait embarqué dans le bundle JavaScript exécuté côté client — visible en clair dans les DevTools de n'importe quel navigateur. N'importe qui pourrait l'extraire et forger de faux billets valides. Ce risque existait déjà, à un degré moindre, en React Native (un APK reste rétro-ingénierable), mais une PWA le rend trivial à exploiter, ce qui impose de revoir le mécanisme de signature avant d'aller plus loin sur cette option.

## Décision

**Le frontend Billet+ est développé comme une PWA React unique (Vite + `vite-plugin-pwa`), couvrant les trois rôles (organisateur, acheteur, scanner) par un système de routes, plutôt que des applications React Native séparées.**

### Ce que la PWA doit fournir

| Capacité | Mécanisme retenu |
|---|---|
| Installable sur mobile | `manifest.webmanifest` (icône, `display: standalone`) |
| Fonctionnement hors ligne | Service Worker (Workbox via `vite-plugin-pwa`), cache de l'app shell et des réponses API critiques |
| Persistance locale (billets, file d'actions en attente) | IndexedDB (`idb`) |
| Scan de QR code | `getUserMedia` + décodage QR côté client |
| Notifications | Web Push API (support Android/Chrome mature ; support iOS Safari plus récent, à valider par test avant d'en dépendre pour une fonctionnalité critique) |
| Apple/Google Wallet | Reste géré côté backend (génération de fichier `.pkpass`/équivalent téléchargeable), indépendant du choix de frontend — non affecté par cette décision |

### Correction du mécanisme de signature des billets

En conséquence directe de cette décision, le mécanisme de QR code passe d'une **signature à secret partagé (HMAC)** à une **signature asymétrique (Ed25519)** :

- Le backend seul détient la **clé privée**, utilisée pour signer chaque QR code à l'émission du billet.
- La PWA scanner n'embarque que la **clé publique** correspondante, qui permet uniquement la vérification, jamais la génération d'une signature valide.
- La vérification reste entièrement réalisable hors ligne, sans appel réseau, avec les mêmes propriétés d'horodatage anti-rejeu que prévu initialement — seul le type de clé change.

```javascript
// PWA scanner — vérifie avec la clé publique uniquement, jamais la clé privée
import { verify } from '@noble/ed25519';

async function verifyTicketQR(qrPayload, signatureHex, publicKeyHex) {
  const message = new TextEncoder().encode(qrPayload);
  return await verify(signatureHex, message, publicKeyHex); // fonctionne 100 % offline
}
```

Cette correction s'applique que le client soit une PWA ou une app native : elle est actée ici parce que c'est l'analyse de l'option PWA qui l'a mise en évidence, mais elle constitue une bonne pratique indépendante du choix de plateforme.

### Architecture retenue

```
Client (PWA React, un seul codebase)
 ├─ routes/organisateur   routes/acheteur   routes/scanner
 ├─ Service Worker (cache app + API)
 ├─ IndexedDB (billets, file de synchronisation offline)
 └─ Vérification QR locale (clé publique Ed25519)
        │  API REST
        ▼
Backend Spring Boot (clé privée Ed25519)
```

## Alternatives considérées

### React Native pour l'acheteur et le scanner, React web séparé pour l'organisateur

Rejetée comme option par défaut du MVP. Duplique l'effort de développement entre deux écosystèmes, ajoute la contrainte de publication sur les stores, sans bénéfice net pour les besoins réels du MVP (le scan QR et le mode hors ligne sont couverts par les capacités web modernes). Reste une option de repli si les limites de la PWA (voir « Conséquences négatives ») se révèlent bloquantes après le premier événement pilote.

### Application hybride (Capacitor/Ionic) au-dessus du même code React

Non retenue au MVP, mais notée comme **option de migration à coût réduit** si un besoin natif profond apparaît plus tard (accès matériel non couvert par les API web, distribution obligatoire via store pour une catégorie d'utilisateurs). Capacitor permet d'empaqueter la même base React existante sans réécriture complète — cohérent avec la logique de réversibilité déjà appliquée aux autres décisions d'architecture de ce projet.

### Garder le secret HMAC partagé, en l'obfusquant côté client

Rejetée. L'obfuscation de code JavaScript ralentit l'extraction mais ne l'empêche pas — un attaquant motivé (le cas d'usage ici est directement la fraude à la revente ou l'entrée sans billet, donc un motif financier réel) finit par retrouver le secret. Le passage à une signature asymétrique élimine le problème structurellement plutôt que de le rendre seulement plus coûteux à exploiter.

## Conséquences

**Positives**

- Un seul codebase React à maintenir pour les trois rôles, cohérent avec une équipe restreinte et un délai de 3 mois.
- Aucun délai de validation de store pour itérer sur l'app acheteur ou scanner pendant la phase pilote.
- Le correctif Ed25519 supprime un risque de sécurité réel qui n'était pas visible tant que le client restait envisagé comme une app native fermée.
- Le mode hors ligne du scanner (Service Worker + IndexedDB) répond directement au risque de connectivité instable le jour J déjà identifié dans le cahier des charges.

**Négatives**

- Le support des notifications push et de certaines capacités PWA reste historiquement moins mature sur iOS Safari que sur Android Chrome ; à valider par un test réel sur le parc d'appareils des organisateurs et acheteurs cibles avant d'en dépendre pour une fonctionnalité jugée critique (ex. rappel de billet).
- La fiabilité du scan caméra via `getUserMedia` sous forte charge (file d'entrée d'un concert, luminosité variable) n'a pas encore été validée en conditions réelles ; à tester spécifiquement lors du premier événement pilote, avec repli possible vers Capacitor si les résultats sont insuffisants.
- L'intégration Apple/Google Wallet, si elle est développée en V3, restera un flux backend indépendant du frontend — aucune dépendance nouvelle introduite par ce choix, mais aucun raccourci natif disponible non plus.

## Références

- [ADR 0001 — Monolithe modulaire](0001-monolithe-modulaire.md)
- [ADR 0002 — Architecture hexagonale appliquée par module](0002-hexagonale-par-module.md)
- [vite-plugin-pwa](https://vite-pwa-org.netlify.app/) — génération de Service Worker et manifest
- [@noble/ed25519](https://github.com/paulmillr/noble-ed25519) — signature/vérification Ed25519 en JavaScript
- [Capacitor](https://capacitorjs.com/) — option de migration vers un empaquetage natif si nécessaire

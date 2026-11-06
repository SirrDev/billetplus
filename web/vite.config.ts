import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { estLecturePubliqueEvenements } from './src/commun/cacheHorsLigne';

// PWA unique pour les trois espaces : voir docs/architecture/adr/0004-pwa-vs-react-native.md.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      workbox: {
        // pem : clé PUBLIQUE de vérification des QR, utile au scanner hors ligne (ADR 0004).
        // Les sous-ensembles vietnamien, cyrillique et grec des polices ne servent pas : hors précache.
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2,pem}'],
        // Hors précache aussi : les morceaux « jspdf-optionnel-* » (html2canvas, DOMPurify, canvg),
        // que jsPDF ne charge que pour .html() et le SVG, inutilisés par le billet PDF (≈ 110 Ko
        // gzippés économisés à chaque installation, en données mobiles). Ils restent dans le build.
        // Le morceau billetPdf, lui, RESTE précaché : le PDF se génère sans réseau.
        // Hors précache également : la carte de l'organisateur (Leaflet), qui ne sert à rien sans
        // réseau puisque ses tuiles viennent d'OpenStreetMap, et qu'un acheteur n'ouvre jamais.
        globIgnores: [
          '**/*-vietnamese-*.woff2',
          '**/*-cyrillic-*.woff2',
          '**/*-greek-*.woff2',
          '**/jspdf-optionnel-*.js',
          '**/carteLeaflet-*.{js,css}',
        ],
        // Jamais de cache sur les commandes et les billets : réponses no-store, jeton en en-tête.
        // Les billets hors ligne viennent d'IndexedDB (espaces/acheteur/commandesLocales.ts).
        // Lectures PUBLIQUES des événements seulement, jamais une requête authentifiée :
        // règle et justification dans src/commun/cacheHorsLigne.ts (testée).
        runtimeCaching: [
          {
            // Fonction passée telle quelle : Workbox recopie son corps dans sw.js.
            urlPattern: estLecturePubliqueEvenements,
            handler: 'NetworkFirst',
            // Nom neuf : l'ancien cache « evenements » (règle plus large) n'est plus relu.
            options: { cacheName: 'evenements-publics', networkTimeoutSeconds: 4 },
          },
        ],
      },
      manifest: {
        name: 'Billet+',
        short_name: 'Billet+',
        description: 'Billetterie événementielle à Dakar',
        lang: 'fr',
        display: 'standalone',
        start_url: '/',
        background_color: '#FFFFFF',
        theme_color: '#5B3DF5',
        // PNG exigés par Android et Chrome pour l'installation (générés par scripts/generer-icones.mjs) ;
        // « maskable » : dessin dans la zone sûre, que le système peut rogner en cercle.
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
        ],
      },
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        // Nom explicite pour les dépendances optionnelles de jsPDF : l'exclusion du précache
        // (globIgnores) vise ce préfixe, jamais un nom générique qui pourrait toucher autre chose.
        chunkFileNames: (morceau) =>
          morceau.moduleIds.some((id) => /node_modules[\\/](canvg|html2canvas|dompurify)[\\/]/.test(id))
            ? 'assets/jspdf-optionnel-[name]-[hash].js'
            : 'assets/[name]-[hash].js',
      },
    },
  },
  resolve: {
    // Même alias que "paths" dans tsconfig.json.
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      // Billet+ écoute sur 8081 (8080 est pris par FootBooking, qui répondrait des 400/404 trompeurs).
      // Autre cible : $env:BILLETPLUS_API_URL = "http://localhost:9090" avant npm run dev.
      // Sans préfixe VITE_ : lue ici seulement, jamais injectée dans le code du navigateur.
      '/api': {
        target: process.env.BILLETPLUS_API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
});

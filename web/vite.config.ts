import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// PWA unique pour les trois espaces : voir docs/architecture/adr/0004-pwa-vs-react-native.md.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg'],
      workbox: {
        // pem : clé PUBLIQUE de vérification des QR, utile au scanner hors ligne (ADR 0004).
        // Les sous-ensembles vietnamien et cyrillique des polices ne servent pas : hors précache.
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2,pem}'],
        globIgnores: ['**/*-vietnamese-*.woff2', '**/*-cyrillic-*.woff2', '**/*-greek-*.woff2'],
        // Jamais de cache sur les commandes et les billets : réponses no-store, jeton en en-tête.
        // Les billets hors ligne viennent d'IndexedDB (espaces/acheteur/commandesLocales.ts).
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/events'),
            handler: 'NetworkFirst',
            options: { cacheName: 'evenements', networkTimeoutSeconds: 4 },
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
        icons: [{ src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
    }),
  ],
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

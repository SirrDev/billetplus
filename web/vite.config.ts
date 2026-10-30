import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// Voir docs/architecture/adr/0004-pwa-vs-react-native.md pour la justification
// du choix PWA et du mode offline pour le scanner.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon-192.png', 'icon-512.png'],
      workbox: {
        // pem : clé PUBLIQUE de vérification des QR, nécessaire au scanner hors ligne (ADR 0004).
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2,pem}'],
        runtimeCaching: [
          {
            // Cache-first sur les billets déjà chargés — nécessaire pour que
            // MyTicketsPage et le scanner fonctionnent hors connexion.
            urlPattern: /\/api\/tickets\/.*/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'tickets-cache',
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 500, maxAgeSeconds: 60 * 60 * 24 * 7 },
            },
          },
          {
            urlPattern: /\/api\/events\/.*/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'events-cache',
              networkTimeoutSeconds: 3,
            },
          },
        ],
      },
      manifest: {
        name: 'Billet+',
        short_name: 'Billet+',
        description: 'Billetterie événementielle mobile-first — Dakar',
        display: 'standalone',
        start_url: '/',
        background_color: '#ffffff',
        theme_color: '#1F2A44',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  resolve: {
    // Même alias que "paths" dans tsconfig.json : import { Button } from '@/design-system'.
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      // En dev, évite les soucis CORS avec le backend local. Billet+ écoute sur 8081
      // (8080 est pris par FootBooking, qui répondrait des 400/404 trompeurs).
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
    setupFiles: ['./src/setupTests.ts'],
    // Noms de classes CSS Modules lisibles (non hachés) dans les tests du design system.
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
});

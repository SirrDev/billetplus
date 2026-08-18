import { defineConfig } from 'vite';
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
        globPatterns: ['**/*.{js,css,html,svg,png,ico}'],
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
  server: {
    port: 5173,
    proxy: {
      // En dev, évite les soucis CORS avec le backend local
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});

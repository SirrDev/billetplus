/**
 * Règle du cache d'exécution du service worker (vite.config.ts) : seules les lectures
 * PUBLIQUES des événements sont gardées pour le hors-ligne.
 *
 * Jamais une requête authentifiée : Workbox ignore l'en-tête Authorization dans sa clé de
 * cache, la réponse d'un organisateur (/api/events/mine, catégories avec quotas, tableau de
 * bord) serait servie à une autre session du même appareil. Jamais /api/orders : jeton de
 * commande en en-tête et réponses no-store (les billets hors ligne viennent d'IndexedDB).
 *
 * ATTENTION : en mode generateSW, Workbox RECOPIE le texte de cette fonction dans sw.js.
 * Elle doit rester autonome : aucune variable, import ni fonction extérieure dans son corps
 * (sinon ReferenceError dans le service worker). Vérifié après build : dist/sw.js.
 */
export function estLecturePubliqueEvenements({ url, request }: { url: URL; request: Request }): boolean {
  return (
    request.method === 'GET' &&
    !request.headers.has('Authorization') &&
    /^\/api\/events(\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\/categories)?)?$/i.test(url.pathname)
  );
}

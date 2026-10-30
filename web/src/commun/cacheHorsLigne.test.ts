import { estLecturePubliqueEvenements } from './cacheHorsLigne';

const ID = '65ef836a-2197-4702-bc26-9723a3a585b0';

function verifier(chemin: string, init: RequestInit = {}): boolean {
  const url = new URL(chemin, 'https://billetplus.test');
  return estLecturePubliqueEvenements({ url, request: new Request(url, init) });
}

describe('fonction recopiée dans sw.js par Workbox', () => {
  it("reste autonome : elle marche une fois sortie de son module (comme dans le service worker)", () => {
    // Reconstruite depuis son seul texte, sans accès à la portée de ce module.
    const copie = new Function(`return (${estLecturePubliqueEvenements.toString()})`)() as typeof estLecturePubliqueEvenements;
    const url = new URL(`https://billetplus.test/api/events/${ID}`);
    expect(copie({ url, request: new Request(url) })).toBe(true);
    expect(copie({ url: new URL('https://billetplus.test/api/events/mine'), request: new Request(url) })).toBe(false);
  });
});

describe('cache hors ligne du service worker', () => {
  it.each([`/api/events`, `/api/events/${ID}`, `/api/events/${ID}/categories`])('garde la lecture publique %s', (chemin) => {
    expect(verifier(chemin)).toBe(true);
  });

  it.each([
    '/api/events/mine',
    `/api/events/${ID}/tableau-de-bord`,
    `/api/events/${ID}/agents/${ID}`,
    `/api/orders/${ID}`,
    `/api/orders/${ID}/billets`,
    '/api/events-autre',
  ])('ne garde jamais %s', (chemin) => {
    expect(verifier(chemin)).toBe(false);
  });

  it('ne garde jamais une requête authentifiée, même sur une route publique', () => {
    expect(verifier(`/api/events/${ID}/categories`, { headers: { Authorization: 'Bearer x' } })).toBe(false);
  });

  it('ne garde jamais une écriture', () => {
    expect(verifier('/api/events', { method: 'POST' })).toBe(false);
  });
});

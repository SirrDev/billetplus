import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enregistrerJetons, lireJetons } from '../auth/session';
import { ApiError, httpClient } from './httpClient';

const reponse = (status: number, corps?: unknown) =>
  new Response(corps === undefined ? null : JSON.stringify(corps), { status });

describe('httpClient', () => {
  const appels: Array<{ url: string; auth: string | null; jeton: string | null; type: string | null }> = [];

  beforeEach(() => {
    localStorage.clear();
    appels.length = 0;
  });
  afterEach(() => vi.unstubAllGlobals());

  function simuler(...reponses: Response[]) {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        const entetes = new Headers(init.headers);
        appels.push({
          url,
          auth: entetes.get('Authorization'),
          jeton: entetes.get('X-Jeton-Commande'),
          type: entetes.get('Content-Type'),
        });
        return reponses.shift() ?? reponse(500);
      }),
    );
  }

  it('garde Content-Type quand l’appelant ajoute un en-tête, et lit le message d’erreur du backend', async () => {
    simuler(reponse(404, { status: 404, message: 'Commande introuvable' }));

    await expect(httpClient('/orders/1', { skipAuth: true, headers: { 'X-Jeton-Commande': 'abc' } })).rejects.toEqual(
      new ApiError(404, 'Commande introuvable'),
    );
    expect(appels[0]).toMatchObject({ url: '/api/orders/1', jeton: 'abc', type: 'application/json', auth: null });
  });

  it('rafraîchit le jeton expiré une seule fois puis rejoue la requête', async () => {
    enregistrerJetons({ accessToken: 'ancien', refreshToken: 'r1' });
    simuler(reponse(401), reponse(200, { accessToken: 'nouveau', refreshToken: 'r2' }), reponse(200, [{ id: 'e1' }]));

    await expect(httpClient('/events/mine')).resolves.toEqual([{ id: 'e1' }]);
    expect(appels.map((a) => [a.url, a.auth])).toEqual([
      ['/api/events/mine', 'Bearer ancien'],
      ['/api/auth/refresh', null],
      ['/api/events/mine', 'Bearer nouveau'],
    ]);
    expect(lireJetons()).toEqual({ accessToken: 'nouveau', refreshToken: 'r2' });
  });

  it('ferme la session si le rafraîchissement est refusé', async () => {
    enregistrerJetons({ accessToken: 'ancien', refreshToken: 'perime' });
    simuler(reponse(401), reponse(401));

    await expect(httpClient('/events/mine')).rejects.toMatchObject({ status: 401 });
    expect(lireJetons()).toBeNull();
  });

  it('une réponse sans corps (201, 204) ne lève pas d’erreur', async () => {
    simuler(reponse(204));
    await expect(httpClient('/events/1/agents/2', { method: 'PUT' })).resolves.toBeUndefined();
  });
});

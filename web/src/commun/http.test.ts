import { ApiError, http, messageErreur } from './http';
import { effacerJetons, enregistrerJetons, lireJetons } from './session';

function reponse(status: number, corps?: unknown): Response {
  return new Response(corps === undefined ? null : JSON.stringify(corps), { status });
}

describe('http', () => {
  it('ajoute Content-Type sans écraser les en-têtes de l appelant', async () => {
    const appel = vi.spyOn(globalThis, 'fetch').mockResolvedValue(reponse(200, { ok: true }));
    await http('/orders/1', { headers: { 'X-Jeton-Commande': 'secret' } });
    const [url, init] = appel.mock.calls[0]!;
    expect(url).toBe('/api/orders/1');
    expect(init?.headers).toEqual({ 'Content-Type': 'application/json', 'X-Jeton-Commande': 'secret' });
  });

  it('renvoie undefined pour une réponse sans corps', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(http('/x')).resolves.toBeUndefined();
  });

  it('lève une ApiError avec le message du backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(reponse(409, { status: 409, message: 'Places insuffisantes' }));
    await expect(http('/orders')).rejects.toEqual(new ApiError(409, 'Places insuffisantes'));
  });

  it('donne un message générique quand le corps n est pas du JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>', { status: 502 }));
    await expect(http('/x')).rejects.toMatchObject({ status: 502, message: 'Erreur 502' });
  });
});

describe('requêtes authentifiées', () => {
  beforeEach(() => effacerJetons());

  const entete = (init: RequestInit | undefined) => (init?.headers as Record<string, string> | undefined)?.Authorization;

  it("n'envoie jamais le jeton sur une route publique", async () => {
    enregistrerJetons({ accessToken: 'acces', refreshToken: 'rafraichi' });
    const appel = vi.spyOn(globalThis, 'fetch').mockResolvedValue(reponse(200, []));
    await http('/events');
    expect(entete(appel.mock.calls[0]![1])).toBeUndefined();
  });

  it('rafraîchit une seule fois pour des requêtes parties ensemble, puis les rejoue', async () => {
    enregistrerJetons({ accessToken: 'expire', refreshToken: 'r1' });
    const appel = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      if (String(url).endsWith('/auth/refresh')) {
        return reponse(200, { accessToken: 'neuf', refreshToken: 'r2', tokenType: 'Bearer' });
      }
      return entete(init) === 'Bearer neuf' ? reponse(200, { ok: true }) : reponse(401, { message: 'Jeton expire' });
    });

    await expect(Promise.all([http('/events/mine', { authentifie: true }), http('/agents', { authentifie: true })])).resolves.toEqual([
      { ok: true },
      { ok: true },
    ]);
    expect(appel.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1);
    expect(lireJetons()).toEqual({ accessToken: 'neuf', refreshToken: 'r2' });
  });

  /** Faux JWT : seule la charge utile (exp) compte pour le client, la signature est du ressort du backend. */
  const jwt = (expDansSecondes: number) => {
    const charge = btoa(JSON.stringify({ sub: 'o1', exp: Math.floor(Date.now() / 1000) + expDansSecondes }))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    return `entete.${charge}.signature`;
  };

  it("renouvelle d'avance un jeton expiré : le backend répondrait la vue anonyme, pas 401", async () => {
    const expire = jwt(-60);
    enregistrerJetons({ accessToken: expire, refreshToken: 'r1' });
    const appel = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      String(url).endsWith('/auth/refresh')
        ? reponse(200, { accessToken: 'neuf', refreshToken: 'r2', tokenType: 'Bearer' })
        : reponse(200, { id: 'e1' }),
    );
    await http('/events/e1', { authentifie: true });
    expect(String(appel.mock.calls[0]![0])).toMatch(/\/auth\/refresh$/);
    expect(entete(appel.mock.calls[1]![1])).toBe('Bearer neuf');
    expect(appel.mock.calls.some(([, i]) => entete(i) === `Bearer ${expire}`)).toBe(false);
  });

  it("n'appelle pas le rafraîchissement pour un jeton encore valable", async () => {
    enregistrerJetons({ accessToken: jwt(600), refreshToken: 'r1' });
    const appel = vi.spyOn(globalThis, 'fetch').mockResolvedValue(reponse(200, []));
    await http('/events/mine', { authentifie: true });
    expect(appel).toHaveBeenCalledTimes(1);
  });

  it('garde la session si le réseau coupe pendant le rafraîchissement', async () => {
    enregistrerJetons({ accessToken: jwt(-60), refreshToken: 'r1' });
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(http('/events/mine', { authentifie: true })).rejects.toBeInstanceOf(TypeError);
    expect(lireJetons()?.refreshToken).toBe('r1');
  });

  it("reprend les jetons d'une autre requête au lieu de rafraîchir une seconde fois", async () => {
    enregistrerJetons({ accessToken: 'ancien', refreshToken: 'r1' });
    const appel = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      if (String(url).endsWith('/auth/refresh')) return reponse(200, { accessToken: 'x', refreshToken: 'y', tokenType: 'Bearer' });
      if (entete(init) === 'Bearer ancien') {
        // Pendant que cette requête était en vol, une autre a renouvelé la session.
        enregistrerJetons({ accessToken: 'deja-neuf', refreshToken: 'r2' });
        return reponse(401, { message: 'Jeton expire' });
      }
      return reponse(200, { ok: true });
    });
    await expect(http('/agents', { authentifie: true })).resolves.toEqual({ ok: true });
    expect(appel.mock.calls.some(([u]) => String(u).endsWith('/auth/refresh'))).toBe(false);
    expect(entete(appel.mock.calls.at(-1)![1])).toBe('Bearer deja-neuf');
  });

  it('ferme la session si le rafraîchissement est refusé', async () => {
    enregistrerJetons({ accessToken: 'expire', refreshToken: 'revoque' });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      String(url).endsWith('/auth/refresh') ? reponse(401, { message: 'Refus' }) : reponse(401, { message: 'Jeton expire' }),
    );
    await expect(http('/events/mine', { authentifie: true })).rejects.toMatchObject({ status: 401 });
    expect(lireJetons()).toBeNull();
  });
});

describe('délai d’expiration', () => {
  it('abandonne une requête sans réponse, avec une erreur qui n’est pas une ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      (_u, init) =>
        new Promise((_ok, ko) => {
          init?.signal?.addEventListener('abort', () => ko(init.signal?.reason));
        }),
    );
    const erreur = await http('/events/e1/scans', { method: 'POST', delaiMs: 30 }).catch((e: unknown) => e);
    expect(erreur).not.toBeInstanceOf(ApiError);
    expect((erreur as DOMException).name).toBe('TimeoutError');
  });
});

describe('messageErreur', () => {
  it('traduit les cas que l acheteur doit comprendre', () => {
    expect(messageErreur(new ApiError(404, 'Commande introuvable'))).toBe('Commande introuvable');
    expect(messageErreur(new ApiError(429, 'x'))).toMatch(/patientez/);
    expect(messageErreur(new ApiError(500, 'NullPointerException'))).toMatch(/indisponible/);
    expect(messageErreur(new TypeError('Failed to fetch'))).toMatch(/Connexion impossible/);
  });
});

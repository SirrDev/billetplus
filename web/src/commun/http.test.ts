import { ApiError, http, messageErreur } from './http';

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

describe('messageErreur', () => {
  it('traduit les cas que l acheteur doit comprendre', () => {
    expect(messageErreur(new ApiError(404, 'Commande introuvable'))).toBe('Commande introuvable');
    expect(messageErreur(new ApiError(429, 'x'))).toMatch(/patientez/);
    expect(messageErreur(new ApiError(500, 'NullPointerException'))).toMatch(/indisponible/);
    expect(messageErreur(new TypeError('Failed to fetch'))).toMatch(/Connexion impossible/);
  });
});

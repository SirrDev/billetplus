import { ApiError } from '@/commun/http';
import type { ReponseScan } from './api';
import { decider, type Dependances } from './decision';

const EVT = '65ef836a-2197-4702-bc26-9723a3a585b0';

function deps(scanner: Dependances['scanner'], extra: Partial<Dependances> = {}): Dependances & {
  file: string[];
  entrees: string[];
} {
  const file: string[] = [];
  const entrees: string[] = [];
  return {
    file,
    entrees,
    scanner,
    verifierHorsLigne: async () => ({ valide: true, ticketId: 't1', eventId: EVT }),
    dejaEntreIci: async () => null,
    noterEntree: async (t) => void entrees.push(t),
    mettreEnAttente: async (qr) => void file.push(qr),
    maintenant: () => new Date('2026-11-14T21:05:00Z'),
    ...extra,
  };
}

const repond = (r: ReponseScan) => async () => r;
const echoue = (e: unknown) => async () => Promise.reject(e);

describe('décision en ligne : le serveur fait foi', () => {
  it("laisse entrer sur ACCEPTE et retient l'entrée sur l'appareil", async () => {
    const d = deps(repond({ issue: 'ACCEPTE', ticketId: 't9' }));
    expect(await decider('qr', EVT, d)).toMatchObject({ type: 'entrer', titre: 'Laisser entrer', horsLigne: false });
    expect(d.entrees).toEqual(['t9']);
    expect(d.file).toEqual([]);
  });

  it.each<[string, ReponseScan, RegExp]>([
    ['déjà scanné, avec l’heure du premier passage', { issue: 'DEJA_SCANNE', ticketId: 't', premierScanLe: '2026-11-14T20:58:00Z' }, /premier passage à 20 h 58/],
    ['annulé', { issue: 'NON_VALIDE', ticketId: 't', statut: 'ANNULE' }, /annulé/],
    ['remboursé', { issue: 'NON_VALIDE', ticketId: 't', statut: 'REMBOURSE' }, /remboursé/],
    ['autre événement', { issue: 'AUTRE_EVENEMENT', ticketId: 't' }, /autre événement/],
    ['invalide', { issue: 'INVALIDE' }, /invalide/],
  ])('refuse un billet %s', async (_cas, reponse, detail) => {
    const d = deps(repond(reponse));
    const v = await decider('qr', EVT, d);
    expect(v).toMatchObject({ type: 'refuser', titre: "Refuser l'entrée" });
    expect(v.type === 'refuser' && v.detail).toMatch(detail);
    expect(d.entrees).toEqual([]);
  });

  it("laisse entrer un billet accepté par le serveur même si la mémoire locale échoue", async () => {
    const d = deps(repond({ issue: 'ACCEPTE', ticketId: 't9' }), { noterEntree: async () => Promise.reject(new Error('quota')) });
    expect(await decider('qr', EVT, d)).toMatchObject({ type: 'entrer', horsLigne: false });
  });

  it('bascule hors ligne quand le serveur ne répond pas dans le délai', async () => {
    const d = deps(echoue(new DOMException('signal timed out', 'TimeoutError')));
    expect(await decider('qr-signe', EVT, d)).toMatchObject({ type: 'entrer', horsLigne: true });
    expect(d.file).toEqual(['qr-signe']);
  });

  it("ne contourne JAMAIS un refus d'accès (403) par le contrôle hors ligne", async () => {
    const d = deps(echoue(new ApiError(403, 'Acces refuse')));
    expect(await decider('qr', EVT, d)).toEqual({ type: 'non-affecte' });
    expect(d.file).toEqual([]);
    expect(d.entrees).toEqual([]);
  });

  it('demande une reconnexion si la session est fermée (401)', async () => {
    expect(await decider('qr', EVT, deps(echoue(new ApiError(401, 'x'))))).toEqual({ type: 'session' });
  });

  it('refuse sans appel réseau un contenu vide ou trop long', async () => {
    const scanner = vi.fn();
    expect(await decider('   ', EVT, deps(scanner))).toMatchObject({ type: 'refuser' });
    expect(await decider('x'.repeat(1025), EVT, deps(scanner))).toMatchObject({ type: 'refuser' });
    expect(scanner).not.toHaveBeenCalled();
  });
});

describe('décision hors ligne : signature vérifiée sur l’appareil', () => {
  it.each([
    ['réseau absent', new TypeError('Failed to fetch')],
    ['serveur en panne', new ApiError(503, 'Indisponible')],
  ])('laisse entrer un billet signé (%s) et le met en file', async (_cas, erreur) => {
    const d = deps(echoue(erreur));
    expect(await decider('qr-signe', EVT, d)).toMatchObject({ type: 'entrer', horsLigne: true });
    expect(d.file).toEqual(['qr-signe']);
    expect(d.entrees).toEqual(['t1']);
  });

  it('refuse un QR dont la signature ne correspond pas', async () => {
    const d = deps(echoue(new TypeError('x')), { verifierHorsLigne: async () => ({ valide: false }) });
    expect(await decider('faux', EVT, d)).toMatchObject({ type: 'refuser', horsLigne: true });
    expect(d.file).toEqual([]);
  });

  it("refuse un billet authentique d'un autre événement", async () => {
    const d = deps(echoue(new TypeError('x')), {
      verifierHorsLigne: async () => ({ valide: true, ticketId: 't1', eventId: 'autre-evenement' }),
    });
    const v = await decider('qr', EVT, d);
    expect(v.type === 'refuser' && v.detail).toMatch(/autre événement/);
    expect(d.file).toEqual([]);
  });

  it('refuse un billet déjà entré par cet appareil, avec son heure', async () => {
    const d = deps(echoue(new TypeError('x')), { dejaEntreIci: async () => '2026-11-14T20:40:00Z' });
    const v = await decider('qr', EVT, d);
    expect(v.type === 'refuser' && v.detail).toMatch(/déjà entré par cet appareil à 20 h 40/);
    expect(d.file).toEqual([]);
  });

  it("ne compte pas l'entrée si la mise en file échoue", async () => {
    const d = deps(echoue(new TypeError('x')), { mettreEnAttente: async () => Promise.reject(new Error('quota')) });
    await expect(decider('qr', EVT, d)).rejects.toThrow('quota');
    expect(d.entrees).toEqual([]);
  });
});

import 'fake-indexeddb/auto';
import { ApiError } from '@/commun/http';
import type { ReponseScan } from './api';
import {
  abandonner,
  dejaEntreIci,
  entreesIci,
  mettreEnAttente,
  noterEntree,
  scansEnAttente,
  synchroniser,
  totalEnAttente,
  viderAppareil,
} from './fileHorsLigne';

/* IndexedDB en mémoire (fake-indexeddb). Chaque test utilise son propre événement. */
let n = 0;
const nouvelEvenement = () => `evt-${++n}`;

describe('mémoire du contrôle sur l’appareil', () => {
  it("retient la première entrée d'un billet, sans l'écraser", async () => {
    const e = nouvelEvenement();
    expect(await dejaEntreIci(e, 't1')).toBeNull();
    await noterEntree(e, 't1', '2026-11-14T21:00:00Z');
    await noterEntree(e, 't1', '2026-11-14T22:00:00Z');
    expect(await dejaEntreIci(e, 't1')).toBe('2026-11-14T21:00:00Z');
    expect(await dejaEntreIci(nouvelEvenement(), 't1')).toBeNull();
    expect(await entreesIci(e)).toBe(1);
  });

  it('envoie les scans en attente dans l’ordre et compte les doublons signalés par le serveur', async () => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'qr-1', scanneLe: '2026-11-14T21:00:00Z' });
    await mettreEnAttente({ eventId: e, qr: 'qr-2', scanneLe: '2026-11-14T21:01:00Z' });
    await mettreEnAttente({ eventId: e, qr: 'qr-3', scanneLe: '2026-11-14T21:02:00Z' });
    const issues: Record<string, ReponseScan> = {
      'qr-1': { issue: 'ACCEPTE', ticketId: 't1' },
      'qr-2': { issue: 'DEJA_SCANNE', ticketId: 't2', premierScanLe: '2026-11-14T20:59:00Z' },
      'qr-3': { issue: 'NON_VALIDE', ticketId: 't3', statut: 'ANNULE' },
    };
    const envoyes: string[] = [];
    const bilan = await synchroniser(e, async (qr) => {
      envoyes.push(qr);
      return issues[qr]!;
    });
    expect(envoyes).toEqual(['qr-1', 'qr-2', 'qr-3']);
    expect(bilan).toEqual({ envoyes: 3, doublons: 1, refuses: 1 });
    expect(await scansEnAttente(e)).toBe(0);
  });

  it('garde les scans pour plus tard si le réseau coupe pendant l’envoi', async () => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'a', scanneLe: 'x' });
    await mettreEnAttente({ eventId: e, qr: 'b', scanneLe: 'y' });
    const bilan = await synchroniser(e, async (qr) => {
      if (qr === 'b') throw new TypeError('Failed to fetch');
      return { issue: 'ACCEPTE' };
    });
    expect(bilan.envoyes).toBe(1);
    expect(await scansEnAttente(e)).toBe(1);
  });

  it("remonte un refus d'accès (agent retiré de l'événement) sans perdre les scans", async () => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'a', scanneLe: 'x' });
    await expect(synchroniser(e, async () => Promise.reject(new ApiError(403, 'Acces refuse')))).rejects.toMatchObject({ status: 403 });
    expect(await scansEnAttente(e)).toBe(1);
  });

  it('ne lance jamais deux synchronisations à la fois : pas de faux doublon', async () => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'unique', scanneLe: 'x' });
    const deja = new Set<string>();
    // Le serveur répond DEJA_SCANNE à un QR qu'il a déjà reçu.
    const envoyer = async (qr: string): Promise<ReponseScan> => {
      await new Promise((r) => setTimeout(r, 10));
      const issue = deja.has(qr) ? 'DEJA_SCANNE' : 'ACCEPTE';
      deja.add(qr);
      return { issue };
    };
    const [a, b] = await Promise.all([synchroniser(e, envoyer), synchroniser(e, envoyer)]);
    expect(a).toEqual({ envoyes: 1, doublons: 0, refuses: 0 });
    expect(b).toBe(a);
    expect(deja.size).toBe(1);
  });

  it('retire un scan refusé définitivement (4xx) au lieu de bloquer la file', async () => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'casse', scanneLe: 'x' });
    await mettreEnAttente({ eventId: e, qr: 'bon', scanneLe: 'y' });
    const bilan = await synchroniser(e, async (qr) => {
      if (qr === 'casse') throw new ApiError(400, 'QR trop long');
      return { issue: 'ACCEPTE' };
    });
    expect(bilan).toEqual({ envoyes: 2, doublons: 0, refuses: 1 });
    expect(await scansEnAttente(e)).toBe(0);
  });

  it.each([401, 429, 503])('garde la file sur une réponse %i (à réessayer plus tard)', async (status) => {
    const e = nouvelEvenement();
    await mettreEnAttente({ eventId: e, qr: 'a', scanneLe: 'x' });
    const bilan = await synchroniser(e, async () => Promise.reject(new ApiError(status, 'x')));
    expect(bilan.envoyes).toBe(0);
    expect(await scansEnAttente(e)).toBe(1);
  });

  it("abandonne les scans d'un événement, puis vide l'appareil à la déconnexion", async () => {
    const e1 = nouvelEvenement();
    const e2 = nouvelEvenement();
    await mettreEnAttente({ eventId: e1, qr: 'a', scanneLe: 'x' });
    await mettreEnAttente({ eventId: e2, qr: 'b', scanneLe: 'x' });
    await noterEntree(e2, 't', 'x');
    await abandonner(e1);
    expect(await scansEnAttente(e1)).toBe(0);
    expect(await scansEnAttente(e2)).toBe(1);
    expect(await totalEnAttente()).toBeGreaterThan(0);
    await viderAppareil();
    expect(await totalEnAttente()).toBe(0);
    expect(await dejaEntreIci(e2, 't')).toBeNull();
  });

  it("n'envoie que les scans de l'événement demandé", async () => {
    const e1 = nouvelEvenement();
    const e2 = nouvelEvenement();
    await mettreEnAttente({ eventId: e1, qr: 'un', scanneLe: 'x' });
    await mettreEnAttente({ eventId: e2, qr: 'deux', scanneLe: 'x' });
    const envoyes: string[] = [];
    await synchroniser(e1, async (qr) => (envoyes.push(qr), { issue: 'ACCEPTE' }));
    expect(envoyes).toEqual(['un']);
    expect(await scansEnAttente(e2)).toBe(1);
  });
});

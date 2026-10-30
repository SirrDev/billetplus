import 'fake-indexeddb/auto';
import {
  enregistrerBillets,
  enregistrerCommande,
  lireCommandeLocale,
  listerCommandesLocales,
  verifierStockage,
  type CommandeLocale,
} from './commandesLocales';

/* IndexedDB en mémoire (fake-indexeddb) : jsdom n'en fournit pas. */
const commande = (id: string, creeLe: string): CommandeLocale => ({
  id,
  jeton: `jeton-${id}`,
  eventId: 'e1',
  titre: 'Nuit du Mbalax',
  debuteLe: '2099-11-14T21:00:00Z',
  lieu: 'Grand Théâtre',
  creeLe,
});

describe('commandesLocales', () => {
  it("vérifie le stockage sans y laisser de trace", async () => {
    await expect(verifierStockage()).resolves.toBeUndefined();
    expect(await lireCommandeLocale('__verification__')).toBeUndefined();
  });

  it('garde le jeton et rend les commandes de la plus récente à la plus ancienne', async () => {
    await enregistrerCommande(commande('a', '2026-10-01T10:00:00Z'));
    await enregistrerCommande(commande('b', '2026-10-02T10:00:00Z'));
    expect((await lireCommandeLocale('a'))?.jeton).toBe('jeton-a');
    expect((await listerCommandesLocales()).map((c) => c.id)).toEqual(['b', 'a']);
  });

  it('ajoute les billets reçus à une commande connue, sans en créer', async () => {
    const billets = [{ id: 'b1', categoryId: 'std', categorie: 'Standard', statut: 'EMIS' as const, qr: 'q' }];
    await enregistrerCommande(commande('c', '2026-10-03T10:00:00Z'));
    await enregistrerBillets('c', billets);
    await enregistrerBillets('inconnue', billets);
    expect((await lireCommandeLocale('c'))?.billets).toEqual(billets);
    expect((await lireCommandeLocale('c'))?.jeton).toBe('jeton-c');
    expect(await lireCommandeLocale('inconnue')).toBeUndefined();
  });
});

import type { CommandeARembourser } from './api';
import { libelleMotif, libelleMoyen, telephoneLisible, totalARembourser, versCsv } from './remboursements';

const COMMANDE: CommandeARembourser = {
  id: 'c1',
  eventId: 'e1',
  telephone: '+221771234567',
  montantFcfa: 25000,
  motif: 'EVENEMENT_ANNULE',
  payeeLe: '2026-11-14T21:00:00Z',
  moyenPaiement: 'WAVE',
  referencePaiement: 'UTP-123',
};

describe('libellés', () => {
  it.each([
    ['RESERVATION_EXPIREE', /fin de la réservation/],
    ['VENTE_FERMEE', /Vente fermée/],
    ['PLACES_INDISPONIBLES', /Places épuisées/],
    ['MONTANT_INCORRECT', /Montant payé différent/],
    ['PAIEMENT_APRES_ANNULATION', /déjà annulée/],
    ['EVENEMENT_ANNULE', /Événement annulé/],
  ])('donne un libellé lisible au motif %s', (motif, attendu) => {
    expect(libelleMotif(motif)).toMatch(attendu);
  });

  it("garde tel quel un motif inconnu (ajouté côté backend), plutôt que de l'effacer", () => {
    expect(libelleMotif('NOUVEAU_MOTIF')).toBe('NOUVEAU_MOTIF');
  });

  it('nomme le moyen de paiement et met le téléphone en forme', () => {
    expect(libelleMoyen('ORANGE_MONEY')).toBe('Orange Money');
    expect(libelleMoyen(null)).toBe('—');
    expect(telephoneLisible('+221771234567')).toBe('+221 77 123 45 67');
  });

  it('additionne les montants du backend sans les modifier', () => {
    expect(totalARembourser([COMMANDE, { ...COMMANDE, id: 'c2', montantFcfa: 5000 }])).toBe(30000);
    expect(totalARembourser([])).toBe(0);
  });
});

describe('export CSV', () => {
  it('commence par le BOM UTF-8 et sépare par des points-virgules (Excel en français)', () => {
    const csv = versCsv([COMMANDE], { e1: 'Nuit du Mbalax' });
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    const [entete, ligne] = csv.slice(1).split('\r\n');
    expect(entete).toContain('"Téléphone (+221)";"Montant (FCFA)"');
    expect(ligne).toBe(
      '"c1";"Nuit du Mbalax";"77 123 45 67";"25000";"Wave";"UTP-123";"samedi 14 novembre 2026 21 h 00";"Événement annulé"',
    );
  });

  it('double les guillemets et neutralise une formule de tableur', () => {
    const csv = versCsv([{ ...COMMANDE, referencePaiement: '=HYPERLINK("http://x")' }], { e1: 'Soirée "VIP"' });
    expect(csv).toContain('"Soirée ""VIP"""');
    expect(csv).toContain(`"'=HYPERLINK(""http://x"")"`);
  });

  it("garde l'identifiant de l'événement quand son titre n'est pas visible", () => {
    expect(versCsv([COMMANDE], { e1: null })).toContain('"c1";"e1";');
  });
});

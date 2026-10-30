// @vitest-environment node
// jsPDF produit un Blob : l'environnement Node suffit, et évite les écarts de royaume de jsdom.
import { describe, expect, it } from 'vitest';
import { genererBilletsPdf, nomDeFichier } from './billetPdf';

const commande = {
  titre: 'Dakar Sunset Sessions',
  date: 'sam. 14 nov. à 20\u202f00',
  lieu: 'Monument de la Renaissance',
  commandeId: '0b6f0c5e-1111-4222-8333-944445555666',
  billets: [
    { code: 'BP-AAAA-BBBB', categorie: 'VIP', qr: 'eyJ0IjoiMSJ9.c2lnbmF0dXJl' },
    { code: 'BP-CCCC-DDDD', categorie: 'Standard', qr: 'eyJ0IjoiMiJ9.c2lnbmF0dXJl' },
  ],
};

describe('genererBilletsPdf', () => {
  it('produit un PDF avec une page par billet et le code de secours lisible', async () => {
    const pdf = genererBilletsPdf(commande);
    const contenu = await pdf.text();

    expect(contenu.startsWith('%PDF')).toBe(true);
    expect(contenu).toContain('/Count 2');
    expect(contenu).toContain('BP-AAAA-BBBB');
    expect(contenu).toContain('BP-CCCC-DDDD');
  });
});

describe('nomDeFichier', () => {
  it('retire les accents et la ponctuation du titre', () => {
    expect(nomDeFichier('Soirée Mbalax : Édition 2026 !')).toBe('billets-soiree-mbalax-edition-2026.pdf');
  });

  it('garde un nom par défaut quand le titre ne contient aucun caractère utilisable', () => {
    expect(nomDeFichier('???')).toBe('billets-billetplus.pdf');
  });
});

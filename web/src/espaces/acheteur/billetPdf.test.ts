// @vitest-environment node
// jsPDF produit un Blob : l'environnement Node suffit, et évite les écarts de royaume de jsdom.
import jsQR from 'jsqr';
import { describe, expect, it } from 'vitest';
import { dessinerQr, genererBilletsPdf, MARGE_QR, nomDeFichier, segmentsQr, texte } from './billetPdf';

const ESPACE_FINE = String.fromCharCode(0x202f);

/* QR au format réel du backend (JSON + "." + 128 caractères hexadécimaux), signature factice. */
const QR_SIGNE =
  '{"tid":"4f2a91c0-0000-4000-8000-000000000001","eid":"65ef836a-2197-4702-bc26-9723a3a585b0","iat":1793000000000}.' +
  '74726447d36aeda913a9b431bc187c2818ed1d03eef08573665b217875ec6896e2a9bfe1f0859abb145dc572d8089a0d92e47da87e66fb1007247f2f62a9f900';

const commande = {
  titre: 'Nuit du Mbalax',
  date: `sam. 14 nov.${ESPACE_FINE}· 21 h 00`,
  lieu: 'Grand Théâtre National',
  commandeId: '0b6f0c5e-1111-4222-8333-944445555666',
  billets: [
    { code: 'BP-AAAA-BBBB', categorie: 'VIP', qr: QR_SIGNE },
    { code: 'BP-CCCC-DDDD', categorie: 'Standard', qr: QR_SIGNE.replace('0001', '0002') },
  ],
};

describe('genererBilletsPdf', () => {
  it('produit un PDF avec une page par billet et le code de secours lisible', async () => {
    const contenu = await genererBilletsPdf(commande).text();
    expect(contenu.startsWith('%PDF')).toBe(true);
    expect(contenu).toContain('/Count 2');
    expect(contenu).toContain('BP-AAAA-BBBB');
    expect(contenu).toContain('BP-CCCC-DDDD');
  });

  it("remplace l'espace fine, absente des polices standard du PDF", () => {
    expect(texte(`21${ESPACE_FINE}h`)).toBe('21 h');
  });
});

describe('QR dessiné dans le PDF', () => {
  /** Image noir et blanc reconstruite exactement comme le PDF dessine le QR (zone de silence comprise). */
  function imageDuPdf(contenu: string, pixelsParModule = 4) {
    const { taille, segments } = segmentsQr(contenu);
    const cote = (taille + MARGE_QR * 2) * pixelsParModule;
    const rgba = new Uint8ClampedArray(cote * cote * 4).fill(255);
    for (const s of segments) {
      for (let dy = 0; dy < pixelsParModule; dy++) {
        const y = (MARGE_QR + s.ligne) * pixelsParModule + dy;
        for (let dx = 0; dx < s.longueur * pixelsParModule; dx++) {
          const o = (y * cote + (MARGE_QR + s.colonne) * pixelsParModule + dx) * 4;
          rgba[o] = rgba[o + 1] = rgba[o + 2] = 0;
        }
      }
    }
    return { rgba, cote };
  }

  it('se décode en EXACTEMENT le contenu signé fourni par le backend', () => {
    const { rgba, cote } = imageDuPdf(QR_SIGNE);
    const lu = jsQR(rgba, cote, cote);
    expect(lu?.data).toBe(QR_SIGNE);
  });

  it('le dessin RÉEL du PDF (rectangles posés par dessinerQr) se décode en le contenu signé', () => {
    // Faux document : enregistre les rectangles noirs exactement comme jsPDF les recevrait.
    const rectangles: Array<{ x: number; y: number; l: number; h: number }> = [];
    let noir = false;
    const surface = {
      setFillColor: (r: number) => {
        noir = r !== 255;
      },
      rect: (x: number, y: number, l: number, h: number) => {
        if (noir) rectangles.push({ x, y, l, h });
      },
    } as unknown as Parameters<typeof dessinerQr>[0];
    // Mêmes valeurs que la page A5 : carré de 92 mm, posé en (28, 63).
    const [x0, y0, cote] = [28, 63, 92];
    dessinerQr(surface, QR_SIGNE, x0, y0, cote);

    // Rastérisation à 12 px/mm, à partir des seules coordonnées en millimètres.
    const echelle = 12;
    const taille = Math.round(cote * echelle);
    const rgba = new Uint8ClampedArray(taille * taille * 4).fill(255);
    for (const r of rectangles) {
      const [px0, py0] = [Math.round((r.x - x0) * echelle), Math.round((r.y - y0) * echelle)];
      const [px1, py1] = [Math.round((r.x + r.l - x0) * echelle), Math.round((r.y + r.h - y0) * echelle)];
      for (let py = py0; py < py1; py++) {
        for (let px = px0; px < px1; px++) {
          const o = (py * taille + px) * 4;
          rgba[o] = rgba[o + 1] = rgba[o + 2] = 0;
        }
      }
    }
    expect(rectangles.length).toBeGreaterThan(0);
    expect(jsQR(rgba, taille, taille)?.data).toBe(QR_SIGNE);
  });

  it('ne fusionne jamais deux billets : chaque QR garde son propre contenu', () => {
    const autre = QR_SIGNE.replace('0001', '0002');
    const { rgba, cote } = imageDuPdf(autre);
    expect(jsQR(rgba, cote, cote)?.data).toBe(autre);
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

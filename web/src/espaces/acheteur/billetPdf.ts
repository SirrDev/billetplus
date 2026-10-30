import { jsPDF } from 'jspdf';
import QRCode from 'qrcode';

/*
 * Billets en PDF (cahier des charges : « billet PDF téléchargeable »), produits SUR
 * L'APPAREIL à partir des QR signés fournis par le backend (ADR 0004) : aucun QR n'est
 * fabriqué, le contenu signé est seulement dessiné. Fonctionne hors ligne une fois les
 * billets affichés. Ce module n'est chargé qu'au clic (import dynamique), avec jsPDF.
 *
 * QR dessiné en vectoriel, module par module : net à toute taille d'impression, sans image.
 */

export interface BilletPourPdf {
  /** Code lisible de secours (BP-XXXX-XXXX). */
  code: string;
  categorie: string;
  /** Contenu exact du QR, signé par le backend (Ed25519). */
  qr: string;
}

export interface CommandePourPdf {
  titre: string;
  /** Date déjà formatée à l'heure de Dakar. */
  date: string;
  lieu: string;
  commandeId: string;
  billets: ReadonlyArray<BilletPourPdf>;
}

/* Couleurs de la charte (styles/tokens.css). */
const VIOLET: [number, number, number] = [0x5b, 0x3d, 0xf5];
const AMBRE: [number, number, number] = [0xff, 0xb0, 0x20];
const ENCRE: [number, number, number] = [0x0f, 0x11, 0x15];
const GRIS: [number, number, number] = [0x4a, 0x4f, 0x5c];

/** Zone de silence réglementaire autour du QR : 4 modules. */
export const MARGE_QR = 4;

/**
 * Les polices standard du PDF (Helvetica, encodage WinAnsi) n'ont pas les espaces fines
 * insécables du français (U+202F, U+00A0) : remplacées par des espaces ordinaires.
 */
export function texte(valeur: string): string {
  return valeur.replace(/[\u202f\u00a0]/g, ' ');
}

export interface SegmentQr {
  ligne: number;
  colonne: number;
  longueur: number;
}

/**
 * Modules noirs du QR, regroupés en segments horizontaux (moins d'objets dans le PDF).
 * Même matrice que celle que décode un lecteur : correction d'erreur M, comme à l'écran.
 */
export function segmentsQr(contenu: string): { taille: number; segments: SegmentQr[] } {
  const matrice = QRCode.create(contenu, { errorCorrectionLevel: 'M' }).modules;
  const segments: SegmentQr[] = [];
  for (let ligne = 0; ligne < matrice.size; ligne++) {
    let debut = -1;
    for (let colonne = 0; colonne <= matrice.size; colonne++) {
      const noir = colonne < matrice.size && matrice.get(ligne, colonne) === 1;
      if (noir && debut < 0) debut = colonne;
      if (!noir && debut >= 0) {
        segments.push({ ligne, colonne: debut, longueur: colonne - debut });
        debut = -1;
      }
    }
  }
  return { taille: matrice.size, segments };
}

/** Ce dont le dessin du QR a besoin dans jsPDF (permet de vérifier le dessin réel en test). */
export type SurfaceQr = Pick<jsPDF, 'setFillColor' | 'rect'>;

/** Dessine le QR dans un carré de `cote` mm, zone de silence comprise, en (x, y). */
export function dessinerQr(doc: SurfaceQr, contenu: string, x: number, y: number, cote: number): void {
  const { taille, segments } = segmentsQr(contenu);
  const module = cote / (taille + MARGE_QR * 2);
  doc.setFillColor(255, 255, 255);
  doc.rect(x, y, cote, cote, 'F');
  doc.setFillColor(...ENCRE);
  for (const s of segments) {
    doc.rect(x + (MARGE_QR + s.colonne) * module, y + (MARGE_QR + s.ligne) * module, s.longueur * module, module, 'F');
  }
}

/** Un billet par page A5 (148 × 210 mm), à imprimer ou à montrer à l'écran. */
export function genererBilletsPdf(commande: CommandePourPdf): Blob {
  const doc = new jsPDF({ unit: 'mm', format: 'a5', orientation: 'portrait' });
  const largeur = 148;
  doc.setProperties({ title: texte(`Billets - ${commande.titre}`), creator: 'Billet+' });

  commande.billets.forEach((billet, index) => {
    if (index > 0) doc.addPage('a5', 'portrait');

    // Bandeau violet, pastille ambre (logo de l'application).
    doc.setFillColor(...VIOLET);
    doc.rect(0, 0, largeur, 22, 'F');
    doc.setFillColor(...AMBRE);
    doc.circle(largeur - 14, 11, 4, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text('Billet+', 12, 14);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10);
    doc.text(`Billet ${index + 1} / ${commande.billets.length}`, largeur - 22, 14, { align: 'right' });

    // Événement
    doc.setTextColor(...ENCRE);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(17);
    const lignesTitre = doc.splitTextToSize(texte(commande.titre), largeur - 24) as string[];
    doc.text(lignesTitre.slice(0, 2), 12, 34);
    const yInfos = 34 + Math.min(lignesTitre.length, 2) * 7 + 2;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(11);
    doc.setTextColor(...GRIS);
    doc.text(texte(commande.date), 12, yInfos);
    doc.text(texte(commande.lieu), 12, yInfos + 6);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...VIOLET);
    doc.text(texte(billet.categorie), 12, yInfos + 13);

    // QR centré, puis code de secours.
    const cote = 92;
    const yQr = yInfos + 20;
    dessinerQr(doc, billet.qr, (largeur - cote) / 2, yQr, cote);
    doc.setTextColor(...ENCRE);
    doc.setFont('courier', 'bold');
    doc.setFontSize(15);
    doc.text(billet.code, largeur / 2, yQr + cote + 9, { align: 'center' });

    // Le QR est un jeton porteur : le dire sur le billet.
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(...GRIS);
    const avertissement = doc.splitTextToSize(
      "Présentez ce QR code à l'entrée, sur papier ou à l'écran. Il n'est valable qu'une fois : ne le partagez " +
        'pas, toute copie présentée avant vous serait acceptée à votre place.',
      largeur - 24,
    ) as string[];
    doc.text(avertissement, 12, 192);
    doc.text(`Commande ${commande.commandeId}`, 12, 204);
  });

  return doc.output('blob');
}

/** Nom de fichier lisible et sûr : « billets-nuit-du-mbalax.pdf ». */
export function nomDeFichier(titre: string): string {
  const base = titre
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60);
  return `billets-${base || 'billetplus'}.pdf`;
}

/** Produit le PDF et le propose au téléchargement. */
export function telechargerBilletsPdf(commande: CommandePourPdf): void {
  const url = URL.createObjectURL(genererBilletsPdf(commande));
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nomDeFichier(commande.titre);
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  // Laisser au navigateur le temps de lancer le téléchargement avant de libérer l'objet.
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

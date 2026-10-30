import { jsPDF } from 'jspdf';
import QRCode from 'qrcode';

/*
 * Billets en PDF (cahier des charges : « Billet PDF téléchargeable »), produits SUR
 * L'APPAREIL à partir des QR signés fournis par le backend : aucun QR n'est fabriqué,
 * le contenu signé est seulement dessiné. Fonctionne hors ligne, une fois les billets
 * affichés. Ce module n'est chargé qu'au clic (import dynamique), avec jsPDF.
 *
 * QR dessiné en vectoriel, module par module : net à toute taille d'impression, sans
 * image ni canvas.
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

const VIOLET: [number, number, number] = [91, 43, 224];
const ENCRE: [number, number, number] = [26, 16, 51];
const GRIS: [number, number, number] = [75, 69, 96];

/**
 * Les polices standard du PDF (Helvetica, encodage WinAnsi) n'ont pas les espaces
 * fines insécables du français (U+202F, U+00A0) : on les remplace par des espaces.
 */
function texte(valeur: string): string {
  return valeur.replace(/[\u202f\u00a0]/g, ' ');
}

/** Dessine le QR en carrés noirs, regroupés par segments horizontaux (moins d'objets dans le PDF). */
function dessinerQr(doc: jsPDF, contenu: string, x: number, y: number, cote: number): void {
  const matrice = QRCode.create(contenu, { errorCorrectionLevel: 'M' }).modules;
  const taille = matrice.size;
  const marge = 4; // zone de silence réglementaire : 4 modules
  const module = cote / (taille + marge * 2);
  doc.setFillColor(255, 255, 255);
  doc.rect(x, y, cote, cote, 'F');
  doc.setFillColor(...ENCRE);
  for (let ligne = 0; ligne < taille; ligne++) {
    let debut = -1;
    for (let colonne = 0; colonne <= taille; colonne++) {
      const noir = colonne < taille && matrice.get(ligne, colonne) === 1;
      if (noir && debut < 0) debut = colonne;
      if (!noir && debut >= 0) {
        doc.rect(x + (marge + debut) * module, y + (marge + ligne) * module, (colonne - debut) * module, module, 'F');
        debut = -1;
      }
    }
  }
}

/** Un billet par page A5 (148 × 210 mm), à imprimer ou à montrer à l'écran. */
export function genererBilletsPdf(commande: CommandePourPdf): Blob {
  const doc = new jsPDF({ unit: 'mm', format: 'a5', orientation: 'portrait' });
  const largeur = 148;
  doc.setProperties({ title: texte(`Billets - ${commande.titre}`), creator: 'Billet+' });

  commande.billets.forEach((billet, index) => {
    if (index > 0) doc.addPage('a5', 'portrait');

    // Bandeau
    doc.setFillColor(...VIOLET);
    doc.rect(0, 0, largeur, 22, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text('Billet+', 12, 14);
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text(`Billet ${index + 1} / ${commande.billets.length}`, largeur - 12, 14, { align: 'right' });

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

    // QR centré
    const cote = 92;
    const yQr = yInfos + 20;
    dessinerQr(doc, billet.qr, (largeur - cote) / 2, yQr, cote);

    // Code de secours
    doc.setTextColor(...ENCRE);
    doc.setFont('courier', 'bold');
    doc.setFontSize(15);
    doc.text(billet.code, largeur / 2, yQr + cote + 9, { align: 'center' });

    // Avertissement : le QR est un jeton porteur.
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

/** Nom de fichier lisible et sûr : « billets-dakar-sunset-sessions.pdf ». */
export function nomDeFichier(titre: string): string {
  const base = titre
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
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
  // Laisser le temps au navigateur de lancer le téléchargement avant de libérer l'objet.
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

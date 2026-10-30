// Génère les icônes PNG de la PWA (dessin géométrique, sans dépendance : PNG encodé avec zlib).
// Usage, depuis web/ : node scripts/generer-icones.mjs public
// Même dessin que public/icon.svg : carré violet arrondi, « B+ » blanc, pastille ambre.
import { deflateSync } from 'node:zlib';
import { writeFileSync } from 'node:fs';

const VIOLET = [0x5b, 0x3d, 0xf5];
const AMBRE = [0xff, 0xb0, 0x20];
const BLANC = [0xff, 0xff, 0xff];

/* Formes en coordonnées normalisées 0..1 ; distance signée < 0 à l'intérieur. */
const carreArrondi = (x, y, cx, cy, demi, rayon) => {
  const qx = Math.abs(x - cx) - demi + rayon;
  const qy = Math.abs(y - cy) - demi + rayon;
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - rayon;
};
const rectangle = (x, y, x0, y0, x1, y1) => {
  const dx = Math.max(x0 - x, 0, x - x1);
  const dy = Math.max(y0 - y, 0, y - y1);
  const dedans = x >= x0 && x <= x1 && y >= y0 && y <= y1;
  return dedans ? -Math.min(x - x0, x1 - x, y - y0, y1 - y) : Math.hypot(dx, dy);
};
const disque = (x, y, cx, cy, r) => Math.hypot(x - cx, y - cy) - r;
/** Demi-anneau droit (bosse du B) : anneau coupé à gauche de cx. */
const bosse = (x, y, cx, cy, rExt, epaisseur) => {
  const d = Math.abs(Math.hypot(x - cx, y - cy) - (rExt - epaisseur / 2)) - epaisseur / 2;
  return x < cx ? Math.max(d, rectangle(x, y, cx - 0.5, cy - rExt, cx, cy + rExt) + 1) : d;
};

/**
 * Couleur d'un point du logo. `echelle` < 1 ramène le dessin vers le centre (zone sûre
 * d'une icône « maskable », que le système peut rogner en cercle).
 */
function couleur(px, py, maskable) {
  // Fond : carré arrondi pour l'icône normale, plein cadre pour la maskable.
  if (!maskable && carreArrondi(px, py, 0.5, 0.5, 0.5, 0.22) > 0) return null;
  const e = maskable ? 0.72 : 0.9;
  const x = (px - 0.5) / e + 0.5;
  const y = (py - 0.5) / e + 0.5;

  // Pastille ambre en haut à droite.
  if (disque(x, y, 0.76, 0.24, 0.11) < 0) return AMBRE;

  // « B » : fût vertical, trois barres, deux boucles en demi-anneau dont le bord extérieur
  // touche exactement les barres (haut / milieu / bas) ; « + » à droite.
  const t = 0.085; // épaisseur du trait
  const haut = 0.3, bas = 0.72, gauche = 0.2;
  const milieu = (haut + bas) / 2;
  const rHaut = (milieu + t / 2 - haut) / 2; // boucle haute : de « haut » au bas de la barre du milieu
  const rBas = (bas - (milieu - t / 2)) / 2; // boucle basse : du haut de la barre du milieu à « bas »
  const xHaut = 0.37, xBas = 0.39; // boucle basse un peu plus large, comme un B classique
  const fut = rectangle(x, y, gauche, haut, gauche + t, bas);
  const barreHaut = rectangle(x, y, gauche, haut, xHaut, haut + t);
  const barreMilieu = rectangle(x, y, gauche, milieu - t / 2, xBas, milieu + t / 2);
  const barreBas = rectangle(x, y, gauche, bas - t, xBas, bas);
  const bosseHaut = bosse(x, y, xHaut, haut + rHaut, rHaut, t);
  const bosseBas = bosse(x, y, xBas, milieu - t / 2 + rBas, rBas, t);
  const plusH = rectangle(x, y, 0.58, 0.5 - t / 2, 0.82, 0.5 + t / 2);
  const plusV = rectangle(x, y, 0.7 - t / 2, 0.38, 0.7 + t / 2, 0.62);
  if (Math.min(fut, barreHaut, barreMilieu, barreBas, bosseHaut, bosseBas, plusH, plusV) < 0) return BLANC;
  return VIOLET;
}

function rendu(taille, maskable) {
  const ech = 4; // suréchantillonnage 4 x 4 : bords lissés
  const pixels = Buffer.alloc(taille * taille * 4);
  for (let j = 0; j < taille; j++) {
    for (let i = 0; i < taille; i++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sj = 0; sj < ech; sj++) {
        for (let si = 0; si < ech; si++) {
          const c = couleur((i + (si + 0.5) / ech) / taille, (j + (sj + 0.5) / ech) / taille, maskable);
          if (c) { r += c[0]; g += c[1]; b += c[2]; a += 1; }
        }
      }
      const o = (j * taille + i) * 4;
      if (a > 0) { pixels[o] = r / a; pixels[o + 1] = g / a; pixels[o + 2] = b / a; }
      pixels[o + 3] = Math.round((a / (ech * ech)) * 255);
    }
  }
  return png(taille, pixels);
}

/* Encodeur PNG minimal : RGBA 8 bits, filtre 0, un seul bloc IDAT. */
const TABLE_CRC = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (const o of buf) c = TABLE_CRC[(c ^ o) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
function bloc(type, donnees) {
  const longueur = Buffer.alloc(4);
  longueur.writeUInt32BE(donnees.length);
  const corps = Buffer.concat([Buffer.from(type, 'ascii'), donnees]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(corps));
  return Buffer.concat([longueur, corps, crc]);
}
function png(taille, rgba) {
  const entete = Buffer.alloc(13);
  entete.writeUInt32BE(taille, 0);
  entete.writeUInt32BE(taille, 4);
  entete[8] = 8; // bits par canal
  entete[9] = 6; // RGBA
  const lignes = Buffer.alloc(taille * (taille * 4 + 1));
  for (let j = 0; j < taille; j++) rgba.copy(lignes, j * (taille * 4 + 1) + 1, j * taille * 4, (j + 1) * taille * 4);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloc('IHDR', entete),
    bloc('IDAT', deflateSync(lignes, { level: 9 })),
    bloc('IEND', Buffer.alloc(0)),
  ]);
}

const dossier = process.argv[2] ?? '.';
writeFileSync(`${dossier}/icon-192.png`, rendu(192, false));
writeFileSync(`${dossier}/icon-512.png`, rendu(512, false));
writeFileSync(`${dossier}/icon-maskable-512.png`, rendu(512, true));
writeFileSync(`${dossier}/apple-touch-icon.png`, rendu(180, true));
console.log('icônes écrites dans', dossier);

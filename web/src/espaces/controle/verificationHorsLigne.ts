import { verifyAsync } from '@noble/ed25519';

/*
 * Vérification d'un QR SANS réseau (ADR 0004) : signature Ed25519 contrôlée avec la
 * clé PUBLIQUE servie par la PWA (public/keys/ed25519_public.pem, précachée). Aucune clé
 * privée ni aucun secret côté client.
 *
 * Format (Ed25519QRSigner) : {"tid":"…","eid":"…","iat":…}.<signature hexadécimale>
 * Coupé au PREMIER point, exactement comme le backend (qrContent.split("\\.", 2)) ; la
 * signature porte sur les octets UTF-8 du JSON tel qu'il apparaît dans le QR.
 *
 * Limite assumée : hors ligne, un billet annulé ou déjà scanné à une AUTRE porte passe ce
 * contrôle. Seul le serveur le sait ; les scans hors ligne lui sont envoyés au retour du
 * réseau (fileHorsLigne.ts), qui signale alors les doublons après coup.
 */

export type ResultatHorsLigne = { valide: true; ticketId: string; eventId: string } | { valide: false };

/** Taille maximale acceptée par le backend (ScanRequest) : au-delà, aucune cryptographie. */
export const TAILLE_MAX_QR = 1024;

const CHEMIN_CLE = '/keys/ed25519_public.pem';

/** En-tête DER d'une clé publique Ed25519 SubjectPublicKeyInfo (OID 1.3.101.112), suivi des 32 octets. */
const PREFIXE_SPKI_ED25519 = [0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00];

/** PEM « PUBLIC KEY » Ed25519 -> 32 octets bruts. Toute autre clé est refusée. */
export function clePemVersOctets(pem: string): Uint8Array {
  const base64 = pem.replace(/-----(BEGIN|END) PUBLIC KEY-----/g, '').replace(/\s/g, '');
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const enteteConforme = der.length === PREFIXE_SPKI_ED25519.length + 32 && PREFIXE_SPKI_ED25519.every((o, i) => der[i] === o);
  if (!enteteConforme) throw new Error("La clé publique n'est pas une clé Ed25519");
  return der.slice(PREFIXE_SPKI_ED25519.length);
}

let clePublique: Promise<Uint8Array> | undefined;

/** Mise en cache par le service worker : disponible hors ligne après une première visite. */
export function chargerClePublique(): Promise<Uint8Array> {
  clePublique ??= fetch(CHEMIN_CLE)
    .then((r) => {
      if (!r.ok) throw new Error(`Clé publique introuvable (${r.status})`);
      return r.text();
    })
    .then(clePemVersOctets)
    .catch((e: unknown) => {
      clePublique = undefined; // nouvel essai au prochain scan
      throw e;
    });
  return clePublique;
}

function hexVersOctets(hex: string): Uint8Array | null {
  if (hex.length !== 128 || !/^[0-9a-fA-F]+$/.test(hex)) return null; // Ed25519 : 64 octets
  const octets = new Uint8Array(64);
  for (let i = 0; i < 64; i++) octets[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return octets;
}

/**
 * Toute anomalie (format, JSON, signature, clé) donne le même { valide: false } : comme le
 * backend, aucun indice différencié qui aiderait à fabriquer un faux QR.
 */
export async function verifierHorsLigne(qr: string, cle?: Uint8Array): Promise<ResultatHorsLigne> {
  if (qr.length > TAILLE_MAX_QR) return { valide: false };
  const separateur = qr.indexOf('.');
  if (separateur <= 0) return { valide: false };
  const json = qr.slice(0, separateur);
  const signature = hexVersOctets(qr.slice(separateur + 1));
  if (!signature) return { valide: false };
  let contenu: { tid?: unknown; eid?: unknown };
  try {
    contenu = JSON.parse(json) as { tid?: unknown; eid?: unknown };
  } catch {
    return { valide: false };
  }
  if (typeof contenu.tid !== 'string' || typeof contenu.eid !== 'string') return { valide: false };
  try {
    // zip215: false : vérification STRICTE RFC 8032, comme la JCA du backend (encodages non
    // canoniques et clés de faible ordre refusés).
    const ok = await verifyAsync(signature, new TextEncoder().encode(json), cle ?? (await chargerClePublique()), {
      zip215: false,
    });
    return ok ? { valide: true, ticketId: contenu.tid, eventId: contenu.eid } : { valide: false };
  } catch {
    return { valide: false };
  }
}

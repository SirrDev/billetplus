import { verifyAsync } from '@noble/ed25519';

/*
 * Vérification d'un QR SANS réseau (ADR 0004) : signature Ed25519 contrôlée avec la
 * clé PUBLIQUE, servie par la PWA (public/keys/ed25519_public.pem). Aucune clé
 * privée ni aucun secret ici.
 *
 * Format du QR (Ed25519QRSigner) : {"tid":"…","eid":"…","iat":…}.<signature en hexadécimal>
 * La signature porte sur les octets UTF-8 du JSON, tel qu'il apparaît dans le QR.
 *
 * Limite assumée : hors ligne, un billet annulé ou déjà scanné à une autre porte
 * passe ce contrôle. Seul le serveur le sait ; les scans hors ligne lui sont
 * envoyés dès le retour du réseau (fileHorsLigne.ts).
 */

export interface ResultatHorsLigne {
  valide: boolean;
  ticketId?: string;
  eventId?: string;
}

const CHEMIN_CLE = '/keys/ed25519_public.pem';
let clePublique: Promise<Uint8Array> | undefined;

/** La clé est mise en cache par la PWA (précache) : disponible hors ligne après une première visite. */
function chargerCle(): Promise<Uint8Array> {
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

/** PEM « PUBLIC KEY » (SubjectPublicKeyInfo DER) → les 32 octets bruts de la clé Ed25519 (fin du DER). */
export function clePemVersOctets(pem: string): Uint8Array {
  const base64 = pem.replace(/-----(BEGIN|END) PUBLIC KEY-----/g, '').replace(/\s/g, '');
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  if (der.length < 32) throw new Error('Clé publique Ed25519 invalide');
  return der.slice(der.length - 32);
}

function hexVersOctets(hex: string): Uint8Array | null {
  if (!/^[0-9a-fA-F]+$/.test(hex) || hex.length % 2 !== 0) return null;
  const octets = new Uint8Array(hex.length / 2);
  for (let i = 0; i < octets.length; i++) octets[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return octets;
}

export async function verifierHorsLigne(qr: string, cle?: Uint8Array): Promise<ResultatHorsLigne> {
  const separateur = qr.lastIndexOf('.');
  if (separateur <= 0) return { valide: false };
  const json = qr.slice(0, separateur);
  const signature = hexVersOctets(qr.slice(separateur + 1));
  if (!signature || signature.length !== 64) return { valide: false };

  let contenu: { tid?: unknown; eid?: unknown };
  try {
    contenu = JSON.parse(json) as { tid?: unknown; eid?: unknown };
  } catch {
    return { valide: false };
  }
  if (typeof contenu.tid !== 'string' || typeof contenu.eid !== 'string') return { valide: false };

  try {
    const ok = await verifyAsync(signature, new TextEncoder().encode(json), cle ?? (await chargerCle()));
    return ok ? { valide: true, ticketId: contenu.tid, eventId: contenu.eid } : { valide: false };
  } catch {
    return { valide: false };
  }
}

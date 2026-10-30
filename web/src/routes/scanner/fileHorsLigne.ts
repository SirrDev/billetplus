import { openDB, type IDBPDatabase } from 'idb';
import { ApiError } from '@/shared/api/httpClient';
import { scanner } from './api';

/**
 * Scans faits sans réseau, gardés sur l'appareil (IndexedDB) et envoyés au serveur
 * dès le retour de la connexion. Le serveur enregistre alors l'entrée, et détecte
 * après coup un billet présenté deux fois (à deux portes hors ligne).
 */
export interface ScanEnAttente {
  id?: number;
  eventId: string;
  qr: string;
  scanneLe: string;
}

const MAGASIN = 'scans';
let base: Promise<IDBPDatabase> | undefined;

function ouvrir(): Promise<IDBPDatabase> {
  base ??= openDB('billetplus-scanner', 1, {
    upgrade(db) {
      db.createObjectStore(MAGASIN, { keyPath: 'id', autoIncrement: true });
    },
  });
  return base;
}

export async function mettreEnAttente(scan: ScanEnAttente): Promise<void> {
  await (await ouvrir()).add(MAGASIN, scan);
}

export async function nombreEnAttente(): Promise<number> {
  return (await ouvrir()).count(MAGASIN);
}

/**
 * Envoie les scans en attente, dans l'ordre. Un scan traité par le serveur (accepté,
 * refusé, déjà scanné) est retiré ; une panne réseau arrête l'envoi, qui reprendra.
 *
 * @return le nombre de scans envoyés
 */
export async function synchroniser(): Promise<number> {
  const db = await ouvrir();
  const scans = (await db.getAll(MAGASIN)) as ScanEnAttente[];
  let envoyes = 0;
  for (const scan of scans) {
    try {
      await scanner(scan.eventId, scan.qr);
    } catch (e) {
      // Erreur du serveur sur CE scan (403, 400...) : il ne passera jamais, on le retire.
      // Panne réseau ou serveur indisponible : on s'arrête, on réessaiera plus tard.
      if (!(e instanceof ApiError) || e.status >= 500 || e.status === 429 || e.status === 401) break;
    }
    await db.delete(MAGASIN, scan.id as number);
    envoyes++;
  }
  return envoyes;
}

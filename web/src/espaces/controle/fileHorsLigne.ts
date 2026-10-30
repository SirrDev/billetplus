import { openDB, type IDBPDatabase } from 'idb';
import { ApiError } from '@/commun/http';
import type { ReponseScan } from './api';

/**
 * Mémoire du contrôle d'entrée SUR L'APPAREIL (IndexedDB) :
 * - `scans` : entrées autorisées sans réseau, envoyées au serveur dès son retour. Le serveur
 *   enregistre alors l'entrée et signale après coup un billet présenté deux fois (deux
 *   portes hors ligne) : c'est la limite assumée du hors-ligne (ADR 0004) ;
 * - `vus` : billets déjà entrés par CET appareil, pour refuser une seconde présentation
 *   même sans réseau.
 * Le contenu du QR est un jeton porteur : il ne quitte l'appareil que vers le backend.
 */
export interface ScanEnAttente {
  id?: number;
  eventId: string;
  qr: string;
  scanneLe: string;
}

interface BilletVu {
  eventId: string;
  ticketId: string;
  entreLe: string;
}

const SCANS = 'scans';
const VUS = 'vus';

let base: Promise<IDBPDatabase> | undefined;

function ouvrir(): Promise<IDBPDatabase> {
  base ??= openDB('billetplus-controle', 1, {
    upgrade(db) {
      db.createObjectStore(SCANS, { keyPath: 'id', autoIncrement: true });
      db.createObjectStore(VUS, { keyPath: ['eventId', 'ticketId'] });
    },
  });
  return base;
}

export async function mettreEnAttente(scan: Omit<ScanEnAttente, 'id'>): Promise<void> {
  await (await ouvrir()).add(SCANS, scan);
}

export async function scansEnAttente(eventId: string): Promise<number> {
  const tous = (await (await ouvrir()).getAll(SCANS)) as ScanEnAttente[];
  return tous.filter((s) => s.eventId === eventId).length;
}

/** Heure d'entrée si ce billet est déjà passé par cet appareil pour cet événement. */
export async function dejaEntreIci(eventId: string, ticketId: string): Promise<string | null> {
  const vu = (await (await ouvrir()).get(VUS, [eventId, ticketId])) as BilletVu | undefined;
  return vu?.entreLe ?? null;
}

export async function noterEntree(eventId: string, ticketId: string, entreLe: string): Promise<void> {
  const db = await ouvrir();
  // Ne remplace jamais une première entrée : l'heure affichée reste celle du premier passage.
  if (!(await db.get(VUS, [eventId, ticketId]))) await db.put(VUS, { eventId, ticketId, entreLe });
}

export async function entreesIci(eventId: string): Promise<number> {
  const tous = (await (await ouvrir()).getAll(VUS)) as BilletVu[];
  return tous.filter((v) => v.eventId === eventId).length;
}

export interface BilanSynchronisation {
  envoyes: number;
  /** Billets que le serveur avait DÉJÀ vus (autre porte) : à signaler au responsable. */
  doublons: number;
  /** Refus du serveur constatés après coup (billet annulé, autre événement, invalide). */
  refuses: number;
}

/**
 * Une seule synchronisation à la fois par événement (montage, retour du réseau et minuterie
 * peuvent la déclencher ensemble) : sinon un même QR partirait deux fois et le second envoi,
 * répondu DEJA_SCANNE, ferait croire à une fraude. Un appel pendant un envoi en cours
 * reçoit le bilan de cet envoi.
 */
const enCours = new Map<string, Promise<BilanSynchronisation>>();

/**
 * Envoie les scans en attente d'un événement, dans l'ordre. Un scan traité par le serveur
 * est retiré, quelle que soit son issue, comme un scan refusé définitivement (4xx). Réseau
 * absent, délai dépassé, 5xx, 429 ou 401 arrêtent l'envoi, qui reprendra plus tard.
 * Un 403 (agent retiré de l'événement) est remonté, la file est gardée.
 */
export function synchroniser(eventId: string, envoyer: (qr: string) => Promise<ReponseScan>): Promise<BilanSynchronisation> {
  const deja = enCours.get(eventId);
  if (deja) return deja;
  const envoi = envoyerFile(eventId, envoyer).finally(() => enCours.delete(eventId));
  enCours.set(eventId, envoi);
  return envoi;
}

async function envoyerFile(eventId: string, envoyer: (qr: string) => Promise<ReponseScan>): Promise<BilanSynchronisation> {
  const db = await ouvrir();
  const scans = ((await db.getAll(SCANS)) as ScanEnAttente[]).filter((s) => s.eventId === eventId);
  const bilan: BilanSynchronisation = { envoyes: 0, doublons: 0, refuses: 0 };
  for (const scan of scans) {
    let reponse: ReponseScan | null = null;
    try {
      reponse = await envoyer(scan.qr);
    } catch (e) {
      if (!(e instanceof ApiError)) break; // réseau absent ou délai dépassé
      if (e.status === 403) throw e;
      if (e.status >= 500 || e.status === 429 || e.status === 401) break;
      // Autre 4xx : refus définitif, à retirer pour ne pas bloquer la file.
    }
    if (scan.id !== undefined) await db.delete(SCANS, scan.id);
    bilan.envoyes++;
    if (reponse?.issue === 'DEJA_SCANNE') bilan.doublons++;
    else if (reponse?.issue !== 'ACCEPTE') bilan.refuses++;
  }
  return bilan;
}

/** Nombre total de scans non synchronisés, tous événements confondus (avant déconnexion). */
export async function totalEnAttente(): Promise<number> {
  return (await ouvrir()).count(SCANS);
}

/** Abandonne les scans en attente d'un événement (agent retiré : ils ne seront jamais acceptés). */
export async function abandonner(eventId: string): Promise<void> {
  const db = await ouvrir();
  const scans = (await db.getAll(SCANS)) as ScanEnAttente[];
  await Promise.all(scans.filter((s) => s.eventId === eventId && s.id !== undefined).map((s) => db.delete(SCANS, s.id!)));
}

/**
 * Efface toute la mémoire du contrôle sur l'appareil (scans en attente et billets vus) :
 * à la déconnexion, pour qu'un téléphone partagé ne garde pas de QR (jetons porteurs).
 */
export async function viderAppareil(): Promise<void> {
  const db = await ouvrir();
  await Promise.all([db.clear(SCANS), db.clear(VUS)]);
}

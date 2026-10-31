import { openDB, type IDBPDatabase } from 'idb';
import type { Billet, BilletRecu } from './api';

/**
 * Commandes de l'acheteur, gardées SUR SON APPAREIL (IndexedDB, ADR 0004).
 * Sans compte (ADR 0006), le jeton d'accès n'est remis qu'une fois, à la création :
 * s'il est perdu, la commande l'est aussi. Il est donc enregistré immédiatement,
 * avec de quoi afficher la commande et ses billets sans réseau.
 */
export interface CommandeLocale {
  id: string;
  jeton: string;
  eventId: string;
  titre: string;
  debuteLe: string;
  lieu: string;
  creeLe: string;
  /** Derniers billets reçus : affichables hors ligne à l'entrée. */
  billets?: Billet[];
  /**
   * Liens des billets transférés (ADR 0008), par billet : le jeton du destinataire n'est
   * remis qu'une fois ; le garder ici permet de renvoyer le lien s'il s'est perdu.
   */
  liensTransfert?: Record<string, string>;
}

/**
 * Billet reçu par transfert, gardé sur l'appareil du destinataire (affichable hors
 * ligne). Le jeton et le mobile sont les deux facteurs qui l'ouvrent : comme le jeton
 * d'une commande, ils ne quittent pas l'appareil.
 */
export interface BilletRecuLocal {
  id: string;
  jeton: string;
  telephone: string;
  titre: string;
  debuteLe: string;
  lieu: string;
  recuLe: string;
  billet: BilletRecu;
}

const BASE = 'billetplus-acheteur';
const MAGASIN = 'commandes';
const RECUS = 'billetsRecus';

let base: Promise<IDBPDatabase> | undefined;

function ouvrir(): Promise<IDBPDatabase> {
  // Version 2 : billets reçus par transfert. Les commandes de la version 1 sont gardées.
  base ??= openDB(BASE, 2, {
    upgrade(db, ancienneVersion) {
      if (ancienneVersion < 1) db.createObjectStore(MAGASIN, { keyPath: 'id' });
      if (ancienneVersion < 2) db.createObjectStore(RECUS, { keyPath: 'id' });
    },
  });
  return base;
}

/**
 * Vérifie que l'appareil peut garder une commande AVANT de la créer : sans stockage
 * (navigation privée stricte, quota plein), le jeton serait perdu et les places
 * resteraient bloquées jusqu'à expiration. Lève une erreur si l'écriture échoue.
 */
export async function verifierStockage(): Promise<void> {
  const db = await ouvrir();
  const transaction = db.transaction(MAGASIN, 'readwrite');
  // Écriture puis suppression d'une clé témoin dans la même transaction (atomique : aucune
  // trace même si l'onglet se ferme). Attendues ensemble : un rejet n'est jamais orphelin.
  // Limite connue : une navigation privée à stockage en mémoire passe ce contrôle.
  await Promise.all([
    transaction.store.put({ id: '__verification__' }),
    transaction.store.delete('__verification__'),
    transaction.done,
  ]);
}

export async function enregistrerCommande(commande: CommandeLocale): Promise<void> {
  await (await ouvrir()).put(MAGASIN, commande);
}

export async function lireCommandeLocale(id: string): Promise<CommandeLocale | undefined> {
  return (await ouvrir()).get(MAGASIN, id) as Promise<CommandeLocale | undefined>;
}

/** Les plus récentes d'abord. */
export async function listerCommandesLocales(): Promise<CommandeLocale[]> {
  const toutes = (await (await ouvrir()).getAll(MAGASIN)) as CommandeLocale[];
  return toutes.sort((a, b) => b.creeLe.localeCompare(a.creeLe));
}

export async function enregistrerBillets(id: string, billets: Billet[]): Promise<void> {
  const commande = await lireCommandeLocale(id);
  if (commande) await enregistrerCommande({ ...commande, billets });
}

/**
 * Enregistre le lien d'un billet transféré et retire son QR des billets gardés : l'ancien
 * QR n'est plus valable, il ne doit plus s'afficher, même hors ligne.
 */
export async function enregistrerTransfert(id: string, ticketId: string, lien: string, transfereA: string): Promise<void> {
  const commande = await lireCommandeLocale(id);
  if (!commande) return;
  await enregistrerCommande({
    ...commande,
    liensTransfert: { ...commande.liensTransfert, [ticketId]: lien },
    billets: commande.billets?.map((b) =>
      b.id === ticketId ? { ...b, statut: 'TRANSFERE' as const, qr: null, transfereA } : b,
    ),
  });
}

export async function enregistrerBilletRecu(billet: BilletRecuLocal): Promise<void> {
  await (await ouvrir()).put(RECUS, billet);
}

export async function lireBilletRecu(id: string): Promise<BilletRecuLocal | undefined> {
  return (await ouvrir()).get(RECUS, id) as Promise<BilletRecuLocal | undefined>;
}

/** Les plus récents d'abord. */
export async function listerBilletsRecus(): Promise<BilletRecuLocal[]> {
  const tous = (await (await ouvrir()).getAll(RECUS)) as BilletRecuLocal[];
  return tous.sort((a, b) => b.recuLe.localeCompare(a.recuLe));
}

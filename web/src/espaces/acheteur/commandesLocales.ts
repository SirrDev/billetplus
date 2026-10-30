import { openDB, type IDBPDatabase } from 'idb';
import type { Billet } from './api';

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
}

const BASE = 'billetplus-acheteur';
const MAGASIN = 'commandes';

let base: Promise<IDBPDatabase> | undefined;

function ouvrir(): Promise<IDBPDatabase> {
  base ??= openDB(BASE, 1, {
    upgrade(db) {
      db.createObjectStore(MAGASIN, { keyPath: 'id' });
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

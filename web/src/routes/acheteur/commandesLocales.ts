import { openDB, type IDBPDatabase } from 'idb';

/**
 * Commandes de l'acheteur, gardées SUR SON APPAREIL (IndexedDB, ADR 0004).
 * Sans compte (ADR 0006), le jeton d'accès n'est remis qu'une fois, à la
 * création : s'il est perdu, la commande et ses billets le sont aussi. On le
 * stocke donc immédiatement, avec de quoi afficher la commande hors ligne.
 */
export interface CommandeLocale {
  id: string;
  jeton: string;
  eventId: string;
  titre: string;
  debuteLe: string;
  venueNom: string;
  creeLe: string;
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

export async function enregistrerCommande(commande: CommandeLocale): Promise<void> {
  await (await ouvrir()).put(MAGASIN, commande);
}

export async function lireCommandeLocale(id: string): Promise<CommandeLocale | undefined> {
  return (await ouvrir()).get(MAGASIN, id);
}

/** Les plus récentes d'abord. */
export async function listerCommandesLocales(): Promise<CommandeLocale[]> {
  const toutes = (await (await ouvrir()).getAll(MAGASIN)) as CommandeLocale[];
  return toutes.sort((a, b) => b.creeLe.localeCompare(a.creeLe));
}

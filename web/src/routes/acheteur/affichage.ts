import type { EventSummary, PosterVariant } from '@/design-system';
import { ApiError } from '@/shared/api/httpClient';
import type { EvenementPublic } from './api';
import type { CommandeLocale } from './commandesLocales';

const AFFICHES: ReadonlyArray<PosterVariant> = ['sunset', 'rings', 'bars'];

/** Affiche choisie d'après l'identifiant : stable d'un affichage à l'autre. */
export function afficheDe(id: string): PosterVariant {
  let somme = 0;
  for (const caractere of id) somme = (somme + caractere.charCodeAt(0)) % 997;
  return AFFICHES[somme % AFFICHES.length];
}

/**
 * Un événement du backend, au format des cartes du design system. Ni genre ni
 * prix « dès » : le backend ne les fournit pas dans sa liste, on n'en invente pas.
 */
export function versResume(evenement: EvenementPublic): EventSummary {
  return {
    id: evenement.id,
    title: evenement.titre,
    startsAt: new Date(evenement.debuteLe),
    venue: evenement.venueNom,
    poster: afficheDe(evenement.id),
  };
}

/** Une commande gardée sur l'appareil, au format des cartes d'événement (Mes billets). */
export function resumeLocal(commande: CommandeLocale): EventSummary {
  return {
    id: commande.id,
    title: commande.titre,
    startsAt: new Date(commande.debuteLe),
    venue: commande.venueNom,
    poster: afficheDe(commande.eventId),
  };
}

/** Code lisible à l'œil, en secours du QR : dérivé de l'identifiant du billet (BP-XXXX-XXXX). */
export function codeBillet(id: string): string {
  const hex = id.replace(/-/g, '').toUpperCase();
  return `BP-${hex.slice(0, 4)}-${hex.slice(4, 8)}`;
}

/** Message lisible d'une erreur d'appel : celui du backend, ou un message de repli. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof ApiError) {
    if (erreur.status === 429) return 'Trop de demandes : patientez une minute avant de réessayer.';
    if (erreur.status >= 500) return 'Le service est momentanément indisponible. Réessayez dans un instant.';
    return erreur.message;
  }
  return 'Connexion impossible. Vérifiez votre réseau et réessayez.';
}

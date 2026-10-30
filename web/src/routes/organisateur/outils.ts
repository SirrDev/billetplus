import { ApiError } from '@/shared/api/httpClient';

/** Message lisible d'une erreur d'appel : celui du backend (validation, conflit), ou un repli. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof ApiError) {
    if (erreur.status === 401) return 'Votre session a expiré : reconnectez-vous.';
    if (erreur.status === 403) return "Cette action n'est pas autorisée pour votre compte.";
    if (erreur.status === 429) return 'Trop de demandes : patientez une minute.';
    if (erreur.status >= 500) return 'Le service est momentanément indisponible. Réessayez dans un instant.';
    return erreur.message;
  }
  return 'Connexion impossible. Vérifiez votre réseau et réessayez.';
}

/**
 * Valeur d'un champ datetime-local (« 2026-11-14T20:00 »), lue à l'heure de Dakar.
 * Dakar est à UTC+0 toute l'année (pas d'heure d'été) : l'heure saisie EST l'heure UTC,
 * quel que soit le fuseau de l'ordinateur de l'organisateur.
 */
export function heureDeDakarVersIso(valeur: string): string {
  return new Date(`${valeur}:00Z`).toISOString();
}

/** Montant saisi par l'organisateur (« 5 000 », « 5000 ») en FCFA entiers, ou null s'il est invalide. */
export function lireMontant(saisie: string): number | null {
  const chiffres = saisie.replace(/[\s\u00a0\u202f]/g, '');
  return /^\d{1,10}$/.test(chiffres) ? Number(chiffres) : null;
}

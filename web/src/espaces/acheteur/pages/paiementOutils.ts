import { ErreurAffichable } from '@/commun/http';
import type { MoyenPaiement } from '../api';

export function nomDuMoyen(moyen: MoyenPaiement): string {
  return moyen === 'WAVE' ? 'Wave' : 'Orange Money';
}

/**
 * Redirection vers la page de paiement de la passerelle (UnitechPay, ADR 0007).
 * Le backend n'accepte déjà qu'un lien https (UnitechPayPasserelle) ; ce contrôle est
 * une défense en profondeur : un lien « javascript: » s'exécuterait dans l'origine
 * Billet+ et pourrait lire les jetons et billets gardés dans IndexedDB.
 */
const LIEN_REFUSE =
  'Lien de paiement refusé par sécurité. Votre commande est gardée : réessayez dans un instant ou choisissez un autre moyen.';

export function allerPayer(url: string): void {
  let lien: URL;
  try {
    lien = new URL(url);
  } catch {
    throw new ErreurAffichable(LIEN_REFUSE);
  }
  if (lien.protocol !== 'https:') {
    throw new ErreurAffichable(LIEN_REFUSE);
  }
  window.location.assign(lien.href);
}

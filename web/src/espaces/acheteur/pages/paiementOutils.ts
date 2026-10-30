import type { MoyenPaiement } from '../api';

export function nomDuMoyen(moyen: MoyenPaiement): string {
  return moyen === 'WAVE' ? 'Wave' : 'Orange Money';
}

/** Redirection vers la page de paiement de la passerelle (UnitechPay, ADR 0007). */
export function allerPayer(url: string): void {
  window.location.assign(url);
}

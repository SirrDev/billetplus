import { http } from '@/commun/http';

/*
 * Espace administrateur : remboursements manuels (décision B, ADR 0006). Contrat recopié
 * de orders/api/dto/CommandeARembourserDto. Route réservée au rôle ADMIN, réponses no-store :
 * elles contiennent des données personnelles (téléphone de l'acheteur).
 */

/** Motifs : MotifSansBillet (orders/domain) et EVENEMENT_ANNULE (ListerCommandesARembourserUseCase). */
export type MotifRemboursement =
  | 'RESERVATION_EXPIREE'
  | 'VENTE_FERMEE'
  | 'PLACES_INDISPONIBLES'
  | 'MONTANT_INCORRECT'
  | 'PAIEMENT_APRES_ANNULATION'
  | 'EVENEMENT_ANNULE';

/** CommandeARembourserDto. */
export interface CommandeARembourser {
  id: string;
  eventId: string;
  /** Format du backend : +2217XXXXXXXX. */
  telephone: string;
  montantFcfa: number;
  motif: MotifRemboursement | string;
  payeeLe: string | null;
  moyenPaiement: 'WAVE' | 'ORANGE_MONEY' | null;
  referencePaiement: string | null;
}

export function commandesARembourser(): Promise<CommandeARembourser[]> {
  return http<CommandeARembourser[]>('/admin/commandes-a-rembourser', { authentifie: true });
}

/** Titre d'un événement (route publique) ; null s'il n'est plus visible (brouillon...). */
export async function titreEvenement(id: string): Promise<string | null> {
  try {
    return (await http<{ titre: string }>(`/events/${encodeURIComponent(id)}`)).titre;
  } catch {
    return null;
  }
}

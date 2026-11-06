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

/** RecettesDto (orders/api/dto, ADR 0009). Montants en FCFA ; reste négatif = trop reversé. */
export interface Recettes {
  commandes: number;
  brutFcfa: number;
  commissionFcfa: number;
  netFcfa: number;
  reverseFcfa: number;
  resteAReverserFcfa: number;
}

/** ReversementsDto.Ligne. */
export interface LigneReversement {
  eventId: string;
  evenementAnnule: boolean;
  recettes: Recettes;
}

export function lireReversements(): Promise<LigneReversement[]> {
  return http<LigneReversement[]>('/admin/reversements', { authentifie: true });
}

/** EnregistrerReversementRequest : l'administrateur vient du jeton, jamais du corps. */
export function enregistrerReversement(corps: {
  eventId: string;
  montantFcfa: number;
  reference: string;
  motif: string | null;
  reverseLe: string;
}): Promise<unknown> {
  return http<unknown>('/admin/reversements', { authentifie: true, method: 'POST', body: JSON.stringify(corps) });
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

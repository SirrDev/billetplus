import { http, httpFichier } from '@/commun/http';

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

/** ReversementsDto.Ligne, avec les conditions du virement (ADR 0012). */
export interface LigneReversement {
  eventId: string;
  evenementAnnule: boolean;
  recettes: Recettes;
  /** Premier jour où un virement est permis (lendemain de l'événement), AAAA-MM-JJ. */
  reversableLe: string | null;
  billetsScannes: number;
  /** Numéro vérifié de l'organisateur ; null s'il n'est pas vérifié (aucun virement possible). */
  numeroReversement: string | null;
  titulaireReversement: string | null;
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
  /** Virement sans billet scanné (entrée sans contrôle) : motif obligatoire, sinon null. */
  derogationScans?: string | null;
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

export type StatutVerification = 'A_VERIFIER' | 'EN_REVUE' | 'VERIFIE' | 'REFUSE' | 'SUSPENDU';
export type TypePiece = 'IDENTITE' | 'JUSTIFICATIF_REVERSEMENT' | 'NINEA_RCCM';

/** VerificationDto (ADR 0012), sans le contenu des pièces. */
export interface VerificationOrganisateur {
  organisateurId: string;
  nomComplet: string | null;
  email: string | null;
  statut: StatutVerification;
  typeTitulaire: 'PERSONNE' | 'STRUCTURE' | null;
  nomStructure: string | null;
  ninea: string | null;
  numeroReversement: string | null;
  titulaireReversement: string | null;
  motif: string | null;
  soumisLe: string | null;
  decideLe: string | null;
  piecesExigees: TypePiece[];
  pieces: Array<{ type: TypePiece; typeMime: string; taille: number; deposeLe: string }>;
}

export function lireVerifications(statut: StatutVerification): Promise<VerificationOrganisateur[]> {
  return http<VerificationOrganisateur[]>(`/admin/verifications?statut=${statut}`, { authentifie: true });
}

/** Pièce déchiffrée par le backend, pour la consulter (jamais gardée par la PWA). */
export function lirePiece(organisateurId: string, type: TypePiece): Promise<Blob> {
  return httpFichier(`/admin/verifications/${encodeURIComponent(organisateurId)}/pieces/${type}`, {
    authentifie: true,
  });
}

export function accepterVerification(organisateurId: string): Promise<VerificationOrganisateur> {
  return http<VerificationOrganisateur>(`/admin/verifications/${encodeURIComponent(organisateurId)}/acceptation`, {
    authentifie: true,
    method: 'POST',
  });
}

export function refuserVerification(organisateurId: string, motif: string): Promise<VerificationOrganisateur> {
  return http<VerificationOrganisateur>(`/admin/verifications/${encodeURIComponent(organisateurId)}/refus`, {
    authentifie: true,
    method: 'POST',
    body: JSON.stringify({ motif }),
  });
}

/** Suspension définitive : connexion bloquée, événements à venir annulés (ADR 0012). */
export function suspendreCompte(organisateurId: string, motif: string): Promise<VerificationOrganisateur> {
  return http<VerificationOrganisateur>(`/admin/verifications/${encodeURIComponent(organisateurId)}/suspension`, {
    authentifie: true,
    method: 'POST',
    body: JSON.stringify({ motif }),
  });
}

/** Suspension d'un événement : vente fermée, billets annulés, acheteurs à rembourser (ADR 0012). */
export function suspendreEvenement(eventId: string, motif: string): Promise<unknown> {
  return http<unknown>(`/admin/evenements/${encodeURIComponent(eventId)}/suspension`, {
    authentifie: true,
    method: 'POST',
    body: JSON.stringify({ motif }),
  });
}

import { http } from '@/commun/http';

/*
 * Contrats du contrôle d'entrée, recopiés des DTO Java (tickets/api/dto/ScanRequest,
 * ScanResponse, tickets/application/usecase/ResultatScan, events/api/dto).
 * POST /api/events/{eventId}/scans : rôle AGENT ou ORGANISATEUR, 403 si l'utilisateur ne
 * contrôle pas cet événement. L'événement vient de l'URL, l'agent du jeton.
 */

export type IssueScan = 'ACCEPTE' | 'DEJA_SCANNE' | 'NON_VALIDE' | 'AUTRE_EVENEMENT' | 'INVALIDE';

/** ScanResponse (@JsonInclude NON_NULL : une issue INVALIDE ne porte que son nom). */
export interface ReponseScan {
  issue: IssueScan;
  ticketId?: string;
  /** Présent pour DEJA_SCANNE seulement. */
  premierScanLe?: string;
  /** Présent pour NON_VALIDE seulement (ANNULE, REMBOURSE). */
  statut?: string;
}

/**
 * Champs utiles au contrôle, communs à EvenementPublicDto (/affectes, /{id}) et EventDto
 * (/mine). `status` n'existe que dans EventDto : /affectes ne l'envoie pas, et c'est normal
 * (le backend n'y renvoie que des événements publiés).
 */
export interface EvenementControle {
  id: string;
  titre: string;
  debuteLe: string;
  venueNom: string;
  status?: string;
}

/** Au-delà, la porte bascule sur la vérification hors ligne au lieu d'attendre. */
export const DELAI_SCAN_MS = 5000;

export function scanner(eventId: string, qrContent: string): Promise<ReponseScan> {
  return http<ReponseScan>(`/events/${encodeURIComponent(eventId)}/scans`, {
    authentifie: true,
    delaiMs: DELAI_SCAN_MS,
    method: 'POST',
    body: JSON.stringify({ qrContent }),
  });
}

/** Route publique, sans jeton : mise en cache par le service worker (hors ligne). */
export function lireEvenement(id: string): Promise<EvenementControle> {
  return http<EvenementControle>(`/events/${encodeURIComponent(id)}`);
}

/**
 * Agent : les événements auxquels il est affecté, publiés, à venir ou en cours
 * (GET /api/events/affectes, rôle AGENT). La liste ne donne aucun droit : la route de
 * scan vérifie de nouveau l'affectation.
 */
export function evenementsAffectes(): Promise<EvenementControle[]> {
  return http<EvenementControle[]>('/events/affectes', { authentifie: true });
}

/** Organisateur : ses propres événements (il peut contrôler les siens). */
export function evenementsDeLOrganisateur(): Promise<EvenementControle[]> {
  return http<EvenementControle[]>('/events/mine', { authentifie: true });
}

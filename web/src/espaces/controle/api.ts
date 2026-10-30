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

/** EventDto / EvenementPublicDto : champs utiles au contrôle (route publique). */
export interface EvenementControle {
  id: string;
  titre: string;
  debuteLe: string;
  venueNom: string;
  status?: string;
}

interface PageEvenements {
  contenu: EvenementControle[];
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

/** Événements publiés (le backend n'expose pas les affectations d'un agent). */
export async function evenementsPublics(q: string): Promise<EvenementControle[]> {
  const parametres = new URLSearchParams({ page: '0', taille: '20' });
  if (q.trim()) parametres.set('q', q.trim());
  return (await http<PageEvenements>(`/events?${parametres}`)).contenu;
}

/** Organisateur : ses propres événements (il peut contrôler les siens). */
export function evenementsDeLOrganisateur(): Promise<EvenementControle[]> {
  return http<EvenementControle[]>('/events/mine', { authentifie: true });
}

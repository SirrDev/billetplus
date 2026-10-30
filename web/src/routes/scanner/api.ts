import { httpClient } from '@/shared/api/httpClient';

/** Issue d'un scan (ResultatScan.Issue côté backend). */
export type IssueScan = 'ACCEPTE' | 'DEJA_SCANNE' | 'NON_VALIDE' | 'AUTRE_EVENEMENT' | 'INVALIDE';

/** ScanResponse. */
export interface ResultatScan {
  issue: IssueScan;
  ticketId: string | null;
  premierScanLe: string | null;
  statut: string | null;
}

/** EvenementPublicDto (liste publique, pour choisir l'événement à contrôler). */
export interface EvenementAControler {
  id: string;
  titre: string;
  debuteLe: string;
  venueNom: string;
}

/**
 * POST /api/events/{eventId}/scans, avec le compte de l'agent (ou de l'organisateur).
 * Le serveur refuse un agent non affecté à l'événement (403).
 */
export function scanner(eventId: string, qrContent: string): Promise<ResultatScan> {
  return httpClient<ResultatScan>(`/events/${encodeURIComponent(eventId)}/scans`, {
    method: 'POST',
    body: JSON.stringify({ qrContent }),
  });
}

/**
 * Le backend n'a pas de liste des événements d'un agent : l'agent choisit parmi les
 * événements publics à venir ou en cours, et le serveur contrôle son affectation.
 */
export function evenementsAControler(q: string): Promise<{ contenu: EvenementAControler[] }> {
  const parametres = new URLSearchParams({ page: '0', taille: '50' });
  if (q.trim()) parametres.set('q', q.trim());
  return httpClient<{ contenu: EvenementAControler[] }>(`/events?${parametres}`, { skipAuth: true });
}

export function lireEvenement(id: string): Promise<EvenementAControler & { status: string }> {
  return httpClient<EvenementAControler & { status: string }>(`/events/${encodeURIComponent(id)}`);
}

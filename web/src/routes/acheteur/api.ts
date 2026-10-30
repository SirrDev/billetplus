import { httpClient } from '@/shared/api/httpClient';

/*
 * Contrats de l'API publique de Billet+ utilisés par le parcours d'achat. Le
 * backend fait référence : ces types recopient ses DTO, champ pour champ.
 * Dates en ISO-8601 (Instant Java), montants en FCFA entiers.
 */

/** EvenementPublicDto (GET /api/events). */
export interface EvenementPublic {
  id: string;
  titre: string;
  description: string | null;
  debuteLe: string;
  termineLe: string | null;
  venueId: string;
  venueNom: string;
  venueAdresse: string;
}

/** PageEvenementsPublicsDto. */
export interface PageEvenements {
  contenu: EvenementPublic[];
  page: number;
  taille: number;
  total: number;
  pages: number;
}

/** EventDto (GET /api/events/{id}). */
export interface Evenement {
  id: string;
  titre: string;
  description: string | null;
  debuteLe: string;
  termineLe: string | null;
  venueId: string;
  venueNom: string;
  status: string;
}

/** TicketCategoryDto, vue acheteur : pas de quota, seulement « disponible ». */
export interface CategorieBillet {
  id: string;
  nom: string;
  prixFcfa: number;
  disponible: boolean;
}

export type StatutCommande = 'EN_ATTENTE_PAIEMENT' | 'PAYEE' | 'PAYEE_SANS_BILLET' | 'EXPIREE' | 'ANNULEE';
export type MoyenPaiement = 'WAVE' | 'ORANGE_MONEY';

export interface LigneCommande {
  categoryId: string;
  quantite: number;
  prixUnitaireFcfa: number;
  sousTotalFcfa: number;
}

/** CommandeDto. */
export interface Commande {
  id: string;
  eventId: string;
  status: StatutCommande;
  lignes: LigneCommande[];
  montantTotalFcfa: number;
  creeLe: string;
  expireLe: string;
  payeeLe: string | null;
}

/** CommandeCreeeResponse : la SEULE réponse qui contienne le jeton (ADR 0006). */
export interface CommandeCreee {
  commande: Commande;
  jetonAcces: string;
}

/** LienPaiementDto. */
export interface LienPaiement {
  moyen: MoyenPaiement;
  urlPaiement: string;
  expireLe: string;
}

/** BilletDto : qr seulement pour un billet encore valable (EMIS). */
export interface Billet {
  id: string;
  categoryId: string;
  categorie: string;
  statut: 'EMIS' | 'SCANNE' | 'ANNULE' | 'REMBOURSE';
  qr: string | null;
}

const EN_TETE_JETON = 'X-Jeton-Commande';

export function rechercherEvenements(q: string, page = 0, taille = 20): Promise<PageEvenements> {
  const parametres = new URLSearchParams({ page: String(page), taille: String(taille) });
  if (q.trim()) parametres.set('q', q.trim());
  return httpClient<PageEvenements>(`/events?${parametres}`, { skipAuth: true });
}

export function lireEvenement(id: string): Promise<Evenement> {
  return httpClient<Evenement>(`/events/${encodeURIComponent(id)}`, { skipAuth: true });
}

export function lireCategories(eventId: string): Promise<CategorieBillet[]> {
  return httpClient<CategorieBillet[]>(`/events/${encodeURIComponent(eventId)}/categories`, { skipAuth: true });
}

export function creerCommande(
  eventId: string,
  telephone: string,
  lignes: ReadonlyArray<{ categoryId: string; quantite: number }>,
): Promise<CommandeCreee> {
  return httpClient<CommandeCreee>('/orders', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ eventId, telephone, lignes }),
  });
}

export function lireCommande(id: string, jeton: string): Promise<Commande> {
  return httpClient<Commande>(`/orders/${encodeURIComponent(id)}`, {
    skipAuth: true,
    headers: { [EN_TETE_JETON]: jeton },
  });
}

export function ouvrirPaiement(id: string, jeton: string, moyen: MoyenPaiement): Promise<LienPaiement> {
  return httpClient<LienPaiement>(`/orders/${encodeURIComponent(id)}/paiement`, {
    method: 'POST',
    skipAuth: true,
    headers: { [EN_TETE_JETON]: jeton },
    body: JSON.stringify({ moyen }),
  });
}

export function lireBillets(id: string, jeton: string): Promise<Billet[]> {
  return httpClient<Billet[]>(`/orders/${encodeURIComponent(id)}/billets`, {
    skipAuth: true,
    headers: { [EN_TETE_JETON]: jeton },
  });
}

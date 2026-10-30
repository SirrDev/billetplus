import { http } from '@/commun/http';

/*
 * Contrats de l'API publique utilisés par le parcours d'achat. Le backend fait foi :
 * ces types recopient ses DTO champ pour champ (events/api/dto, tickets/api/dto,
 * orders/api/dto). Dates en ISO-8601 (Instant Java), montants en FCFA entiers.
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
  organizerId: string;
  status: string;
  creeLe: string;
}

/** TicketCategoryDto, vue acheteur : « disponible », jamais le stock. */
export interface CategorieBillet {
  id: string;
  nom: string;
  prixFcfa: number;
  disponible: boolean;
}

export type StatutCommande = 'EN_ATTENTE_PAIEMENT' | 'PAYEE' | 'PAYEE_SANS_BILLET' | 'EXPIREE' | 'ANNULEE';
export type MoyenPaiement = 'WAVE' | 'ORANGE_MONEY';

/** LigneCommandeDto. */
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

/** CommandeCreeeResponse : la SEULE réponse qui contienne le jeton en clair (ADR 0006). */
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

/** Plafonds de CreateOrderRequest / Order : 20 places par catégorie, 10 catégories. */
export const QUANTITE_MAX_PAR_CATEGORIE = 20;

/** Jamais dans l'URL (journaux, historique) : OrderController.EN_TETE_JETON. */
const EN_TETE_JETON = 'X-Jeton-Commande';

export function rechercherEvenements(q: string, page = 0, taille = 12): Promise<PageEvenements> {
  const parametres = new URLSearchParams({ page: String(page), taille: String(taille) });
  if (q.trim()) parametres.set('q', q.trim());
  return http<PageEvenements>(`/events?${parametres}`);
}

export function lireEvenement(id: string): Promise<Evenement> {
  return http<Evenement>(`/events/${encodeURIComponent(id)}`);
}

export function lireCategories(eventId: string): Promise<CategorieBillet[]> {
  return http<CategorieBillet[]>(`/events/${encodeURIComponent(eventId)}/categories`);
}

export function creerCommande(
  eventId: string,
  telephone: string,
  lignes: ReadonlyArray<{ categoryId: string; quantite: number }>,
): Promise<CommandeCreee> {
  return http<CommandeCreee>('/orders', {
    method: 'POST',
    body: JSON.stringify({ eventId, telephone, lignes }),
  });
}

export function lireCommande(id: string, jeton: string): Promise<Commande> {
  return http<Commande>(`/orders/${encodeURIComponent(id)}`, { headers: { [EN_TETE_JETON]: jeton } });
}

export function ouvrirPaiement(id: string, jeton: string, moyen: MoyenPaiement): Promise<LienPaiement> {
  return http<LienPaiement>(`/orders/${encodeURIComponent(id)}/paiement`, {
    method: 'POST',
    headers: { [EN_TETE_JETON]: jeton },
    body: JSON.stringify({ moyen }),
  });
}

/** 409 tant que la commande n'est pas payée : l'appelant réessaie. */
export function lireBillets(id: string, jeton: string): Promise<Billet[]> {
  return http<Billet[]>(`/orders/${encodeURIComponent(id)}/billets`, { headers: { [EN_TETE_JETON]: jeton } });
}

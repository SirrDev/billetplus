import type { CategorieEvenement } from '@/commun/categories';
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
  /** CategorieEvenement (MUSIQUE, SPORT...) ; libellé par commun/categories. */
  categorie: string;
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
  /** null pour un autre visiteur que l'organisateur (le backend ne l'expose qu'à lui). */
  organizerId: string | null;
  status: string;
  creeLe: string;
  categorie: string;
}

/** VenueDto (route publique GET /venues/{id}) : l'adresse n'est pas dans EventDto. */
export interface LieuPublic {
  id: string;
  nom: string;
  adresse: string;
  latitude: number | null;
  longitude: number | null;
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

/**
 * BilletDto : qr seulement pour un billet encore valable (EMIS). TRANSFERE : cédé à un
 * autre mobile (ADR 0008), sans QR ; transfereA porte alors ce mobile, masqué.
 */
export interface Billet {
  id: string;
  categoryId: string;
  categorie: string;
  statut: 'EMIS' | 'SCANNE' | 'ANNULE' | 'REMBOURSE' | 'TRANSFERE';
  qr: string | null;
  transfereA?: string | null;
}

/** BilletTransfereDto : billet reçu par transfert, vu par son destinataire. */
export interface BilletRecu {
  id: string;
  eventId: string;
  categorie: string;
  statut: 'EMIS' | 'SCANNE' | 'ANNULE' | 'REMBOURSE';
  qr: string | null;
}

/** Plafonds de CreateOrderRequest / Order : 20 places par catégorie, 10 catégories. */
export const QUANTITE_MAX_PAR_CATEGORIE = 20;

/** Jamais dans l'URL (journaux, historique) : OrderController.EN_TETE_JETON. */
const EN_TETE_JETON = 'X-Jeton-Commande';

/** @param categorie null : toutes (filtre GET /events?categorie=) */
export function rechercherEvenements(
  q: string,
  page = 0,
  taille = 12,
  categorie: CategorieEvenement | null = null,
): Promise<PageEvenements> {
  const parametres = new URLSearchParams({ page: String(page), taille: String(taille) });
  if (q.trim()) parametres.set('q', q.trim());
  if (categorie) parametres.set('categorie', categorie);
  return http<PageEvenements>(`/events?${parametres}`);
}

export function lireEvenement(id: string): Promise<Evenement> {
  return http<Evenement>(`/events/${encodeURIComponent(id)}`);
}

export function lireLieu(id: string): Promise<LieuPublic> {
  return http<LieuPublic>(`/venues/${encodeURIComponent(id)}`);
}

/**
 * Lien vers une carte : par les coordonnées du lieu si l'organisateur les a données,
 * sinon par son adresse. Lien sortant, aucune donnée de l'acheteur.
 */
export function lienCarte(lieu: Pick<LieuPublic, 'nom' | 'adresse' | 'latitude' | 'longitude'>): string {
  const cible =
    lieu.latitude !== null && lieu.longitude !== null
      ? `${lieu.latitude},${lieu.longitude}`
      : `${lieu.nom}, ${lieu.adresse}`;
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(cible)}`;
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

/**
 * Transfère un billet (ADR 0008), définitif. Renvoie le jeton du destinataire, remis
 * UNE SEULE FOIS : l'appelant l'enregistre avant toute autre chose.
 */
export function transfererBillet(
  orderId: string,
  jeton: string,
  ticketId: string,
  telephone: string,
): Promise<{ ticketId: string; jeton: string }> {
  return http<{ ticketId: string; jeton: string }>(
    `/orders/${encodeURIComponent(orderId)}/billets/${encodeURIComponent(ticketId)}/transfert`,
    { method: 'POST', headers: { [EN_TETE_JETON]: jeton }, body: JSON.stringify({ telephone }) },
  );
}

/** BilletTransfereController.EN_TETE_JETON : jamais dans l'URL. */
const EN_TETE_JETON_BILLET = 'X-Jeton-Billet';

/** Le destinataire ouvre son billet : jeton du lien ET son mobile (+2217XXXXXXXX). 404 si l'un est faux. */
export function consulterBilletRecu(ticketId: string, jeton: string, telephoneE164: string): Promise<BilletRecu> {
  return http<BilletRecu>(`/billets/${encodeURIComponent(ticketId)}/consultation`, {
    method: 'POST',
    headers: { [EN_TETE_JETON_BILLET]: jeton },
    body: JSON.stringify({ telephone: telephoneE164 }),
  });
}

/**
 * Lien à envoyer au destinataire. Le jeton est dans le fragment (#) : le navigateur ne
 * l'envoie jamais au serveur, il ne finit donc dans aucun journal.
 */
export function lienBilletRecu(origine: string, ticketId: string, jeton: string): string {
  return `${origine}/billets/recu/${encodeURIComponent(ticketId)}#${jeton}`;
}

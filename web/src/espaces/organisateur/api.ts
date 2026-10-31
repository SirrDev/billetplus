import { http } from '@/commun/http';

/*
 * Contrats de l'espace organisateur, recopiés des DTO Java (events/api/dto,
 * tickets/api/dto, tickets/application/usecase/TableauDeBordUseCase, auth/api/dto).
 * Toutes ces routes exigent le rôle ORGANISATEUR (@PreAuthorize) : jeton envoyé.
 * L'organisateur d'un événement vient du jeton, jamais du corps de la requête.
 */

export type StatutEvenement = 'BROUILLON' | 'PUBLIE' | 'ANNULE' | 'TERMINE';

/** EventDto. */
export interface EvenementOrganisateur {
  id: string;
  titre: string;
  description: string | null;
  debuteLe: string;
  termineLe: string | null;
  venueId: string;
  venueNom: string;
  organizerId: string;
  status: StatutEvenement;
  creeLe: string;
}

/** VenueDto. */
export interface Lieu {
  id: string;
  nom: string;
  adresse: string;
  latitude: number | null;
  longitude: number | null;
  seatingPlan: string | null;
}

/** TicketCategoryDto, vue organisateur : quotas présents. */
export interface CategorieOrganisateur {
  id: string;
  nom: string;
  prixFcfa: number;
  disponible: boolean;
  quotaTotal?: number;
  quotaVendu?: number;
}

/** TableauDeBordUseCase.LigneCategorie. */
export interface LigneTableauDeBord {
  categoryId: string;
  nom: string;
  prixFcfa: number;
  quotaTotal: number;
  vendus: number;
  enCoursDePaiement: number;
  disponibles: number;
  scannes: number;
  annules: number;
  chiffreAffairesFcfa: number;
}

/** TableauDeBordUseCase.TableauDeBord. */
export interface TableauDeBord {
  eventId: string;
  categories: LigneTableauDeBord[];
  vendus: number;
  enCoursDePaiement: number;
  scannes: number;
  chiffreAffairesFcfa: number;
}

/** AgentDto. */
export interface Agent {
  id: string;
  email: string;
  nomComplet: string;
  creeLe: string;
}

/* Bornes des DTO d'entrée (@Size, @Min, @Max) : reprises par les formulaires. */
export const LIMITES = {
  titre: 255,
  description: 5000,
  lieuNom: 255,
  lieuAdresse: 500,
  categorieNom: 255,
  prixMax: 1_000_000_000,
  quotaMax: 1_000_000,
} as const;

const avecJeton = { authentifie: true } as const;

export function mesEvenements(): Promise<EvenementOrganisateur[]> {
  return http<EvenementOrganisateur[]>('/events/mine', avecJeton);
}

export function lireEvenement(id: string): Promise<EvenementOrganisateur> {
  return http<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}`, avecJeton);
}

/** Coordonnées facultatives (CreateVenueRequest : latitude -90..90, longitude -180..180). */
export function creerLieu(
  nom: string,
  adresse: string,
  coordonnees: { latitude: number; longitude: number } | null = null,
): Promise<Lieu> {
  return http<Lieu>('/venues', {
    ...avecJeton,
    method: 'POST',
    body: JSON.stringify({ nom, adresse, latitude: coordonnees?.latitude ?? null, longitude: coordonnees?.longitude ?? null }),
  });
}

/**
 * Lit des coordonnées saisies ou copiées depuis une carte : « 14.6928, -17.4467 » (virgule
 * décimale française tolérée si les deux nombres sont séparés par un espace ou un « ; »).
 * null si la saisie est illisible ou hors des bornes du backend.
 */
export function lireCoordonnees(saisie: string): { latitude: number; longitude: number } | null {
  // Deux nombres (décimale « . » ou « , »), séparés par une virgule, un point-virgule ou des espaces.
  const m = /^\s*(-?\d+(?:[.,]\d+)?)\s*(?:[,;]\s*|\s+)(-?\d+(?:[.,]\d+)?)\s*$/.exec(saisie);
  if (!m) return null;
  const latitude = Number(m[1]!.replace(',', '.'));
  const longitude = Number(m[2]!.replace(',', '.'));
  if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
  return { latitude, longitude };
}

export function creerEvenement(corps: {
  titre: string;
  description: string | null;
  debuteLe: string;
  termineLe: string | null;
  venueId: string;
}): Promise<EvenementOrganisateur> {
  return http<EvenementOrganisateur>('/events', { ...avecJeton, method: 'POST', body: JSON.stringify(corps) });
}

/**
 * PUT /events/{id} : brouillon entièrement modifiable ; publié, titre et description
 * seulement (les dates renvoyées doivent être celles reçues, sinon 409).
 */
export function modifierEvenement(
  id: string,
  corps: { titre: string; description: string | null; debuteLe: string; termineLe: string | null },
): Promise<EvenementOrganisateur> {
  return http<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}`, {
    ...avecJeton,
    method: 'PUT',
    body: JSON.stringify(corps),
  });
}

export function publier(id: string): Promise<EvenementOrganisateur> {
  return http<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}/publier`, { ...avecJeton, method: 'POST' });
}

export function annuler(id: string): Promise<EvenementOrganisateur> {
  return http<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}/annuler`, { ...avecJeton, method: 'POST' });
}

export function lireCategories(eventId: string): Promise<CategorieOrganisateur[]> {
  return http<CategorieOrganisateur[]>(`/events/${encodeURIComponent(eventId)}/categories`, avecJeton);
}

export function creerCategorie(
  eventId: string,
  corps: { nom: string; prixFcfa: number; quotaTotal: number },
): Promise<CategorieOrganisateur> {
  return http<CategorieOrganisateur>(`/events/${encodeURIComponent(eventId)}/categories`, {
    ...avecJeton,
    method: 'POST',
    body: JSON.stringify(corps),
  });
}

export function lireTableauDeBord(eventId: string): Promise<TableauDeBord> {
  return http<TableauDeBord>(`/events/${encodeURIComponent(eventId)}/tableau-de-bord`, avecJeton);
}

export function mesAgents(): Promise<Agent[]> {
  return http<Agent[]>('/agents', avecJeton);
}

export function creerAgent(corps: { email: string; motDePasse: string; nomComplet: string }): Promise<Agent> {
  return http<Agent>('/agents', { ...avecJeton, method: 'POST', body: JSON.stringify(corps) });
}

/** Idempotent : réaffecter ne change rien. Agent inconnu ou d'un autre organisateur : 404. */
export function affecterAgent(eventId: string, agentId: string): Promise<void> {
  return http<void>(`/events/${encodeURIComponent(eventId)}/agents/${encodeURIComponent(agentId)}`, {
    ...avecJeton,
    method: 'PUT',
  });
}

export function retirerAgent(eventId: string, agentId: string): Promise<void> {
  return http<void>(`/events/${encodeURIComponent(eventId)}/agents/${encodeURIComponent(agentId)}`, {
    ...avecJeton,
    method: 'DELETE',
  });
}

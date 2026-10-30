import { httpClient } from '@/shared/api/httpClient';

/*
 * API de l'espace organisateur (jeton de session ajouté par httpClient). Les types
 * recopient les DTO du backend, champ pour champ : le backend fait référence.
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

/** TicketCategoryDto, vue organisateur (avec quotas). */
export interface CategorieOrganisateur {
  id: string;
  nom: string;
  prixFcfa: number;
  disponible: boolean;
  quotaTotal: number | null;
  quotaVendu: number | null;
}

/** AgentDto. */
export interface Agent {
  id: string;
  email: string;
  nomComplet: string;
  creeLe: string;
}

/** TableauDeBordUseCase.LigneCategorie. */
export interface LigneTableau {
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
  categories: LigneTableau[];
  vendus: number;
  enCoursDePaiement: number;
  scannes: number;
  chiffreAffairesFcfa: number;
}

const json = (corps: unknown) => JSON.stringify(corps);

export function inscrire(email: string, motDePasse: string, nomComplet: string): Promise<void> {
  return httpClient<void>('/auth/register', {
    method: 'POST',
    skipAuth: true,
    body: json({ email, motDePasse, nomComplet }),
  });
}

export function mesEvenements(): Promise<EvenementOrganisateur[]> {
  return httpClient<EvenementOrganisateur[]>('/events/mine');
}

export function lireEvenement(id: string): Promise<EvenementOrganisateur> {
  return httpClient<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}`);
}

export function creerLieu(nom: string, adresse: string): Promise<{ id: string }> {
  return httpClient<{ id: string }>('/venues', { method: 'POST', body: json({ nom, adresse }) });
}

export function creerEvenement(corps: {
  titre: string;
  description: string | null;
  debuteLe: string;
  termineLe: string | null;
  venueId: string;
}): Promise<EvenementOrganisateur> {
  return httpClient<EvenementOrganisateur>('/events', { method: 'POST', body: json(corps) });
}

export function publier(id: string): Promise<EvenementOrganisateur> {
  return httpClient<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}/publier`, { method: 'POST' });
}

export function annuler(id: string): Promise<EvenementOrganisateur> {
  return httpClient<EvenementOrganisateur>(`/events/${encodeURIComponent(id)}/annuler`, { method: 'POST' });
}

export function lireCategories(eventId: string): Promise<CategorieOrganisateur[]> {
  return httpClient<CategorieOrganisateur[]>(`/events/${encodeURIComponent(eventId)}/categories`);
}

export function creerCategorie(
  eventId: string,
  nom: string,
  prixFcfa: number,
  quotaTotal: number,
): Promise<CategorieOrganisateur> {
  return httpClient<CategorieOrganisateur>(`/events/${encodeURIComponent(eventId)}/categories`, {
    method: 'POST',
    body: json({ nom, prixFcfa, quotaTotal }),
  });
}

export function lireTableauDeBord(eventId: string): Promise<TableauDeBord> {
  return httpClient<TableauDeBord>(`/events/${encodeURIComponent(eventId)}/tableau-de-bord`);
}

export function mesAgents(): Promise<Agent[]> {
  return httpClient<Agent[]>('/agents');
}

export function creerAgent(email: string, motDePasse: string, nomComplet: string): Promise<Agent> {
  return httpClient<Agent>('/agents', { method: 'POST', body: json({ email, motDePasse, nomComplet }) });
}

/** Idempotent : réaffecter un agent déjà affecté ne change rien. */
export function affecterAgent(eventId: string, agentId: string): Promise<void> {
  return httpClient<void>(`/events/${encodeURIComponent(eventId)}/agents/${encodeURIComponent(agentId)}`, {
    method: 'PUT',
  });
}

/** Idempotent : retirer un agent non affecté ne change rien. */
export function retirerAgent(eventId: string, agentId: string): Promise<void> {
  return httpClient<void>(`/events/${encodeURIComponent(eventId)}/agents/${encodeURIComponent(agentId)}`, {
    method: 'DELETE',
  });
}

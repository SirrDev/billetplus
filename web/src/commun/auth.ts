import { createContext, useContext } from 'react';
import { http } from './http';
import type { Jetons } from './session';

/*
 * Authentification (organisateur, puis agent de contrôle). Contrats recopiés des DTO de
 * auth/api/dto. Le client ne choisit jamais son rôle : /register crée un ORGANISATEUR.
 */

/** MeResponse. */
export interface Utilisateur {
  id: string;
  email: string;
  nomComplet: string;
  role: 'ORGANISATEUR' | 'AGENT' | 'ADMIN';
}

/** TokenResponse. */
export interface ReponseJetons extends Jetons {
  tokenType: string;
}

/** Bornes de RegisterRequest / CreateAgentRequest (BCrypt : 72 octets au plus). */
export const MOT_DE_PASSE_MIN = 8;
export const MOT_DE_PASSE_MAX = 72;
export const TEXTE_MAX = 255;

export function seConnecter(email: string, motDePasse: string): Promise<ReponseJetons> {
  return http<ReponseJetons>('/auth/login', { method: 'POST', body: JSON.stringify({ email, motDePasse }) });
}

/** 201 sans corps : l'appelant se connecte ensuite. */
export function sInscrire(email: string, motDePasse: string, nomComplet: string): Promise<void> {
  return http<void>('/auth/register', { method: 'POST', body: JSON.stringify({ email, motDePasse, nomComplet }) });
}

/**
 * POST /auth/mot-de-passe (tout rôle connecté) : 204. Mot de passe actuel faux : 400
 * (et non 401, qui déclencherait le rafraîchissement de session).
 */
export function changerMotDePasse(motDePasseActuel: string, nouveauMotDePasse: string): Promise<void> {
  return http<void>('/auth/mot-de-passe', {
    authentifie: true,
    method: 'POST',
    body: JSON.stringify({ motDePasseActuel, nouveauMotDePasse }),
  });
}

export function lireMoi(): Promise<Utilisateur> {
  return http<Utilisateur>('/auth/me', { authentifie: true });
}

export type EtatSession =
  | { etat: 'chargement' }
  | { etat: 'anonyme' }
  | { etat: 'connecte'; utilisateur: Utilisateur };

export interface ContexteSession {
  session: EtatSession;
  connexion: (email: string, motDePasse: string) => Promise<Utilisateur>;
  deconnexion: () => void;
}

export const Session = createContext<ContexteSession | null>(null);

export function useSession(): ContexteSession {
  const contexte = useContext(Session);
  if (!contexte) throw new Error('useSession doit être utilisé sous <FournisseurSession>');
  return contexte;
}

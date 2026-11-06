import { useEffect, useState } from 'react';
import type { NomIcone } from './Icone';

/* Fonctions et constantes de src/ui : à part des composants (rechargement à chaud de Vite). */

export const MOTIFS = ['soleil', 'barres', 'formes', 'vague', 'orbite'] as const;
export type Motif = (typeof MOTIFS)[number];

/** Motif d'affiche dérivé de l'identifiant : un événement garde toujours la même affiche. */
export function motifDe(id: string): Motif {
  let somme = 0;
  for (const caractere of id) somme = (somme * 31 + caractere.charCodeAt(0)) % 9973;
  return MOTIFS[somme % MOTIFS.length] ?? 'soleil';
}

/** Code lisible à l'œil, en secours du QR : dérivé de l'identifiant du billet (BP-XXXX-XXXX). */
export function codeBillet(id: string): string {
  const hex = id.replace(/-/g, '').toUpperCase();
  return `BP-${hex.slice(0, 4)}-${hex.slice(4, 8)}`;
}

/** Secondes restantes avant l'échéance ; recalculé sur Date.now() : pas de dérive en veille. */
export function useCompteARebours(echeance: string | null): number {
  const restantA = (instant: number) => (echeance ? Math.max(0, (new Date(echeance).getTime() - instant) / 1000) : 0);
  const [restant, setRestant] = useState(() => restantA(Date.now()));
  useEffect(() => {
    if (!echeance) return;
    const tic = () => setRestant(Math.max(0, (new Date(echeance).getTime() - Date.now()) / 1000));
    tic();
    const minuterie = window.setInterval(tic, 1000);
    return () => window.clearInterval(minuterie);
  }, [echeance]);
  return restant;
}

export interface LienNav {
  vers: string;
  libelle: string;
  icone: NomIcone;
  /** Libellé affiché dans la barre basse du téléphone, si le libellé complet y est trop long. */
  libelleCourt?: string;
  /** true : actif seulement sur l'adresse exacte (l'accueil « / »). */
  exact?: boolean;
}

export const navAcheteur: ReadonlyArray<LienNav> = [
  { vers: '/', libelle: 'Découvrir', icone: 'boussole', exact: true },
  { vers: '/billets', libelle: 'Mes billets', icone: 'billet' },
  { vers: '/aide', libelle: 'Aide', icone: 'aide' },
];

export const navOrganisateur: ReadonlyArray<LienNav> = [
  { vers: '/organisateur', libelle: 'Mes événements', libelleCourt: 'Événements', icone: 'calendrier', exact: true },
  { vers: '/organisateur/evenements/nouveau', libelle: 'Nouvel événement', libelleCourt: 'Créer', icone: 'plus' },
  { vers: '/organisateur/equipe', libelle: 'Équipe', icone: 'bouclier' },
  { vers: '/organisateur/credit', libelle: 'Crédit entrée libre', libelleCourt: 'Crédit', icone: 'billet' },
  { vers: '/organisateur/compte', libelle: 'Mon compte', libelleCourt: 'Compte', icone: 'compte' },
];

/** État du réseau, mis à jour par les événements online / offline du navigateur. */
export function useEnLigne(): boolean {
  const [enLigne, setEnLigne] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));
  useEffect(() => {
    const oui = () => setEnLigne(true);
    const non = () => setEnLigne(false);
    window.addEventListener('online', oui);
    window.addEventListener('offline', non);
    return () => {
      window.removeEventListener('online', oui);
      window.removeEventListener('offline', non);
    };
  }, []);
  return enLigne;
}

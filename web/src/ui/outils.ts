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
  /** true : actif seulement sur l'adresse exacte (l'accueil « / »). */
  exact?: boolean;
}

export const navAcheteur: ReadonlyArray<LienNav> = [
  { vers: '/', libelle: 'Découvrir', icone: 'boussole', exact: true },
  { vers: '/billets', libelle: 'Mes billets', icone: 'billet' },
];

import { useEffect, useState } from 'react';
import { http } from '@/commun/http';

/*
 * Liste fermée des villes (ADR 0011), servie par GET /api/villes : la PWA ne la recopie
 * jamais. Pays de l'UEMOA, dans l'ordre du backend (Sénégal d'abord, capitale d'abord).
 */

export interface VilleListe {
  code: string;
  nom: string;
}

export interface PaysVilles {
  /** Code ISO 3166-1 alpha-2 (SN, CI...). */
  code: string;
  nom: string;
  villes: VilleListe[];
}

export function lireVilles(): Promise<PaysVilles[]> {
  return http<PaysVilles[]>('/villes');
}

/** Options d'un ChampChoix, groupées par pays ; la première est vide (aucune ville). */
export function optionsVilles(
  pays: ReadonlyArray<PaysVilles>,
  libelleVide: string,
): Array<{ valeur: string; libelle: string; groupe?: string }> {
  return [
    { valeur: '', libelle: libelleVide },
    ...pays.flatMap((p) => p.villes.map((v) => ({ valeur: v.code, libelle: v.nom, groupe: p.nom }))),
  ];
}

/** Ville affichée : son nom, suivi du pays hors du Sénégal (ADR 0011, point 7). */
export function libelleVille(lieu: { villeNom: string; pays: string; paysNom?: string }): string {
  return lieu.pays === 'SN' || !lieu.paysNom ? lieu.villeNom : `${lieu.villeNom} (${lieu.paysNom})`;
}

/** Code ISO du pays d'une ville, lu dans son code (SN-DAKAR → SN). */
export function paysDeLaVille(code: string): string {
  return code.slice(0, 2);
}

/**
 * Liste chargée une fois par écran. null tant qu'elle n'est pas là ; en cas d'échec,
 * erreur renseignée et liste vide (l'écran reste utilisable).
 */
export function useVilles(): { villes: PaysVilles[] | null; erreur: string | null } {
  const [villes, setVilles] = useState<PaysVilles[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  useEffect(() => {
    let annule = false;
    lireVilles()
      .then((liste) => {
        // Défense : une réponse qui n'est pas une liste ne doit pas faire tomber l'écran.
        if (!Array.isArray(liste)) throw new TypeError('Liste des villes illisible');
        if (!annule) setVilles(liste);
      })
      .catch(() => {
        if (annule) return;
        setErreur('La liste des villes est indisponible pour le moment.');
        setVilles([]);
      });
    return () => {
      annule = true;
    };
  }, []);
  return { villes, erreur };
}

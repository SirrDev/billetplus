import { useEffect, useState } from 'react';
import { ApiError, messageErreur } from '@/commun/http';
import { lireCategories, lireEvenement, type CategorieBillet, type Evenement } from '../api';

export type EtatEvenement =
  | { etat: 'chargement' }
  | { etat: 'introuvable' }
  | { etat: 'erreur'; message: string }
  | { etat: 'pret'; evenement: Evenement; categories: CategorieBillet[] };

/**
 * Événement et catégories, chargés ensemble. Un brouillon non visible répond 404
 * comme un identifiant inconnu (getVisible côté backend) : même écran « introuvable ».
 */
export function useEvenement(id: string | undefined): EtatEvenement & { reessayer: () => void } {
  const [resultat, setResultat] = useState<EtatEvenement>({ etat: 'chargement' });
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    if (!id) {
      setResultat({ etat: 'introuvable' });
      return;
    }
    let annule = false;
    setResultat({ etat: 'chargement' });
    Promise.all([lireEvenement(id), lireCategories(id)])
      .then(([evenement, categories]) => !annule && setResultat({ etat: 'pret', evenement, categories }))
      .catch((e: unknown) => {
        if (annule) return;
        if (e instanceof ApiError && e.status === 404) setResultat({ etat: 'introuvable' });
        else setResultat({ etat: 'erreur', message: messageErreur(e) });
      });
    return () => {
      annule = true;
    };
  }, [id, essai]);

  return { ...resultat, reessayer: () => setEssai((n) => n + 1) };
}

/** Quantités choisies, par identifiant de catégorie. Transmises de l'événement au paiement. */
export type Quantites = Record<string, number>;

export interface EtatNavigationPaiement {
  quantites: Quantites;
}

/**
 * Aperçu avant création de la commande, à partir des prix unitaires DU BACKEND.
 * Le montant à payer reste celui de la commande créée (montantTotalFcfa) : c'est lui
 * qu'affiche l'écran de commande.
 */
export function apercuTotal(categories: ReadonlyArray<CategorieBillet>, quantites: Quantites): number {
  return categories.reduce((total, c) => total + c.prixFcfa * (quantites[c.id] ?? 0), 0);
}

export function nombreDePlaces(quantites: Quantites): number {
  return Object.values(quantites).reduce((n, q) => n + q, 0);
}

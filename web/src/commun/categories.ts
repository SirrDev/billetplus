/*
 * Catégories d'événement : contrat de events/domain/CategorieEvenement (V13). Liste fermée,
 * dans l'ordre d'affichage des filtres et du choix de l'organisateur.
 */
export type CategorieEvenement = 'MUSIQUE' | 'SPORT' | 'CONFERENCE' | 'SPECTACLE' | 'FESTIVAL' | 'AUTRE';

export const CATEGORIES: ReadonlyArray<{ valeur: CategorieEvenement; libelle: string }> = [
  { valeur: 'MUSIQUE', libelle: 'Musique' },
  { valeur: 'SPORT', libelle: 'Sport' },
  { valeur: 'CONFERENCE', libelle: 'Conférence' },
  { valeur: 'SPECTACLE', libelle: 'Spectacle' },
  { valeur: 'FESTIVAL', libelle: 'Festival' },
  { valeur: 'AUTRE', libelle: 'Autre' },
];

/** Libellé d'une catégorie ; une valeur inconnue (backend plus récent) reste lisible telle quelle. */
export function libelleCategorie(valeur: string | null | undefined): string {
  return CATEGORIES.find((c) => c.valeur === valeur)?.libelle ?? valeur ?? '';
}

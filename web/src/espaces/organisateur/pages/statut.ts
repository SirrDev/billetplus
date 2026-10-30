import type { NomIcone, TonPastille } from '@/ui';
import type { StatutEvenement } from '../api';

/** Libellé, ton et icône d'un statut d'événement (EventStatus) : jamais la couleur seule. */
export const STATUTS: Record<StatutEvenement, { libelle: string; ton: TonPastille; icone: NomIcone }> = {
  BROUILLON: { libelle: 'Brouillon', ton: 'neutre', icone: 'info' },
  PUBLIE: { libelle: 'En vente', ton: 'succes', icone: 'valide' },
  ANNULE: { libelle: 'Annulé', ton: 'danger', icone: 'croix' },
  TERMINE: { libelle: 'Terminé', ton: 'info', icone: 'calendrier' },
};

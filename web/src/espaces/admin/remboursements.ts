import { formatDateLongue, formatHeure, formatMobile } from '@/commun/format';
import type { CommandeARembourser } from './api';

/** Libellé lisible de chaque motif ; un motif inconnu (ajout côté backend) reste affiché tel quel. */
export const MOTIFS: Record<string, string> = {
  RESERVATION_EXPIREE: 'Paiement reçu après la fin de la réservation',
  VENTE_FERMEE: 'Vente fermée avant le paiement',
  PLACES_INDISPONIBLES: 'Places épuisées au moment de l’émission',
  MONTANT_INCORRECT: 'Montant payé différent de la commande',
  PAIEMENT_APRES_ANNULATION: 'Paiement d’une commande déjà annulée',
  EVENEMENT_ANNULE: 'Événement annulé',
};

export function libelleMotif(motif: string): string {
  return MOTIFS[motif] ?? motif;
}

export function libelleMoyen(moyen: CommandeARembourser['moyenPaiement']): string {
  return moyen === 'WAVE' ? 'Wave' : moyen === 'ORANGE_MONEY' ? 'Orange Money' : '—';
}

/** « +221 77 123 45 67 » à partir du format du backend. */
export function telephoneLisible(telephone: string): string {
  return `+221 ${formatMobile(telephone)}`;
}

/**
 * Somme des montants renvoyés par le backend, pour l'affichage seulement : chaque
 * montant est celui de la commande (montantTotalFcfa), aucun n'est recalculé ici.
 */
export function totalARembourser(commandes: ReadonlyArray<CommandeARembourser>): number {
  return commandes.reduce((total, c) => total + c.montantFcfa, 0);
}

/** Cellule CSV : entre guillemets, guillemets doublés ; neutralise une formule (= + - @). */
function cellule(valeur: string): string {
  const sure = /^[=+\-@\t\r]/.test(valeur) ? `'${valeur}` : valeur;
  return `"${sure.replace(/"/g, '""')}"`;
}

/**
 * Export CSV pour suivre les remboursements dans un tableur : séparateur « ; » et BOM
 * UTF-8 (Excel en français ouvre alors le fichier correctement). Contient des données
 * personnelles : à ne pas diffuser.
 */
export function versCsv(commandes: ReadonlyArray<CommandeARembourser>, titres: Record<string, string | null>): string {
  const entete = ['Commande', 'Événement', 'Téléphone (+221)', 'Montant (FCFA)', 'Moyen', 'Référence du paiement', 'Payée le (heure de Dakar)', 'Motif'];
  const lignes = commandes.map((c) =>
    [
      c.id,
      titres[c.eventId] ?? c.eventId,
      // Numéro national seul : un « + » en tête serait pris pour une formule par le tableur.
      formatMobile(c.telephone),
      String(c.montantFcfa),
      libelleMoyen(c.moyenPaiement),
      c.referencePaiement ?? '',
      c.payeeLe ? `${formatDateLongue(c.payeeLe)} ${formatHeure(c.payeeLe)}` : '',
      libelleMotif(c.motif),
    ]
      .map(cellule)
      .join(';'),
  );
  return `\uFEFF${[entete.map(cellule).join(';'), ...lignes].join('\r\n')}\r\n`;
}

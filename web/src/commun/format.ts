/*
 * Formats d'affichage. Aucun calcul métier : les montants viennent du backend.
 */

const FCFA = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/**
 * 15000 -> « 15 000 FCFA ». Lève une erreur sur un montant non entier : mieux vaut
 * un écran d'erreur qu'un prix faux affiché à l'acheteur.
 */
export function formatFcfa(montant: number): string {
  if (!Number.isInteger(montant)) {
    throw new RangeError(`Montant FCFA invalide : ${montant} (entier attendu)`);
  }
  return `${FCFA.format(montant)} FCFA`;
}

/** Toujours l'heure de Dakar (UTC+0, sans heure d'été), quel que soit le fuseau du téléphone. */
const FUSEAU = 'Africa/Dakar';

const DATE_COURTE = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});
const DATE_LONGUE = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});
const HEURE = new Intl.DateTimeFormat('fr-FR', { timeZone: FUSEAU, hour: '2-digit', minute: '2-digit' });

function enDate(valeur: string | Date): Date {
  return typeof valeur === 'string' ? new Date(valeur) : valeur;
}

/** « 21 h 00 » */
export function formatHeure(valeur: string | Date): string {
  return HEURE.format(enDate(valeur)).replace(':', ' h ');
}

/** « sam. 14 nov. · 21 h 00 » */
export function formatDateCourte(valeur: string | Date): string {
  return `${DATE_COURTE.format(enDate(valeur))} · ${formatHeure(valeur)}`;
}

/** « samedi 14 novembre 2026 » */
export function formatDateLongue(valeur: string | Date): string {
  return DATE_LONGUE.format(enDate(valeur));
}

/**
 * Valeur d'un <input type="datetime-local"> (« 2026-11-14T21:00 »), lue comme l'heure
 * DE DAKAR (UTC+0 toute l'année), quel que soit le fuseau de l'ordinateur de
 * l'organisateur -> Instant ISO envoyé au backend (« 2026-11-14T21:00:00Z »).
 * Renvoie null pour une saisie incomplète ou invalide.
 */
export function heureDakarVersInstant(valeurLocale: string): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(valeurLocale);
  if (!m) return null;
  const instant = `${valeurLocale}:00Z`;
  const date = new Date(instant);
  // Rejette les dates impossibles (31 février...) que Date corrigerait en silence.
  return Number.isNaN(date.getTime()) || date.toISOString().slice(0, 16) !== valeurLocale ? null : instant;
}

/** 582 -> « 09:42 » ; jamais négatif. */
export function formatCompteARebours(secondes: number): string {
  const s = Math.max(0, Math.floor(secondes));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/*
 * Numéros sénégalais. Le backend normalise et valide (+2217XXXXXXXX) : ce contrôle
 * ne sert qu'à prévenir l'acheteur avant l'envoi.
 */

/** Garde les 9 chiffres nationaux : « +221 77 123 45 67 », « 00221771234567 » -> « 771234567 ». */
export function chiffresNationaux(saisie: string): string {
  let chiffres = saisie.replace(/\D/g, '');
  if (chiffres.startsWith('00221')) chiffres = chiffres.slice(5);
  else if (chiffres.startsWith('221') && chiffres.length > 9) chiffres = chiffres.slice(3);
  return chiffres.slice(0, 9);
}

/** Mobile sénégalais : 9 chiffres commençant par 7. */
export function estMobileSenegalais(saisie: string): boolean {
  return /^7\d{8}$/.test(chiffresNationaux(saisie));
}

/** « 771234567 » -> « 77 123 45 67 » (saisie partielle acceptée). */
export function formatMobile(saisie: string): string {
  const c = chiffresNationaux(saisie);
  return [c.slice(0, 2), c.slice(2, 5), c.slice(5, 7), c.slice(7, 9)].filter(Boolean).join(' ');
}

/** Format envoyé au backend : « +221771234567 ». */
export function versE164(saisie: string): string {
  return `+221${chiffresNationaux(saisie)}`;
}

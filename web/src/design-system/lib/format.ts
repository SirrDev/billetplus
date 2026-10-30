/**
 * Formatage d'affichage uniquement.
 * Les montants sont des entiers en FCFA (XOF n'a pas de centimes). Le backend reste la
 * source de vérité pour tout calcul de prix, de frais ou de total : ces fonctions
 * n'embarquent aucune règle métier.
 */

const NBSP = ' ';
const DAKAR_TZ = 'Africa/Dakar';

/** 10500 -> « 10 500 FCFA » (espaces insécables : le montant ne se coupe jamais en fin de ligne). */
export function formatFcfa(amount: number): string {
  if (!Number.isInteger(amount)) {
    throw new RangeError(`Montant FCFA invalide : ${amount} (entier attendu)`);
  }
  const sign = amount < 0 ? '-' : '';
  const digits = Math.abs(amount)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, NBSP);
  return `${sign}${digits}${NBSP}FCFA`;
}

/** Garde uniquement les chiffres, au plus 9 (numéro mobile sénégalais sans indicatif). */
export function sanitizeSnPhone(input: string): string {
  return input.replace(/\D/g, '').slice(0, 9);
}

/** « 771234567 » -> « 77 123 45 67 » (saisie progressive acceptée). */
export function formatSnPhone(digits: string): string {
  const d = sanitizeSnPhone(digits);
  const groups = [d.slice(0, 2), d.slice(2, 5), d.slice(5, 7), d.slice(7, 9)];
  return groups.filter((g) => g.length > 0).join(' ');
}

/** Mobile sénégalais : 9 chiffres commençant par 70, 75, 76, 77 ou 78. */
export function isValidSnMobile(digits: string): boolean {
  return /^(70|75|76|77|78)\d{7}$/.test(digits);
}

/** 108 -> « 01:48 » */
export function formatCountdown(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const mm = String(Math.floor(safe / 60)).padStart(2, '0');
  const ss = String(safe % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

/**
 * Date d'événement affichée à l'heure de Dakar (UTC+0, sans changement d'heure),
 * quel que soit le fuseau de l'appareil : « sam. 14 nov. · 20 h » ou « sam. 14 nov. · 20 h 30 ».
 */
export function formatEventDate(date: Date, options: { withTime?: boolean } = {}): string {
  const { withTime = true } = options;
  const day = new Intl.DateTimeFormat('fr-FR', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: DAKAR_TZ,
  }).format(date);
  if (!withTime) return day;

  const parts = new Intl.DateTimeFormat('fr-FR', {
    hour: 'numeric',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone: DAKAR_TZ,
  }).formatToParts(date);
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '0';
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '00';
  const time = minute === '00' ? `${Number(hour)} h` : `${Number(hour)} h ${minute}`;
  return `${day} · ${time}`;
}

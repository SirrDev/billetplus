import {
  chiffresNationaux,
  estMobileSenegalais,
  formatCompteARebours,
  formatDateCourte,
  formatFcfa,
  formatHeure,
  formatMobile,
  versE164,
} from './format';

// Intl insère des espaces insécables (U+202F, U+00A0) : on les ramène à des espaces pour comparer.
const espaces = (texte: string) => texte.replace(/\s/g, ' ');

describe('formatFcfa', () => {
  it('sépare les milliers et ajoute FCFA', () => {
    expect(espaces(formatFcfa(15000))).toBe('15 000 FCFA');
    expect(espaces(formatFcfa(0))).toBe('0 FCFA');
  });

  it('refuse un montant non entier plutôt que d afficher un prix faux', () => {
    expect(() => formatFcfa(1500.5)).toThrow(RangeError);
    expect(() => formatFcfa(Number.NaN)).toThrow(RangeError);
  });
});

describe('dates', () => {
  it("affiche l'heure de Dakar quel que soit le fuseau de l'appareil", () => {
    // 21:00 UTC = 21 h 00 à Dakar (UTC+0 toute l'année).
    expect(formatHeure('2026-11-14T21:00:00Z')).toBe('21 h 00');
    expect(espaces(formatDateCourte('2026-11-14T21:00:00Z'))).toBe('sam. 14 nov. · 21 h 00');
  });

  it('formate le compte à rebours sans jamais passer sous zéro', () => {
    expect(formatCompteARebours(582)).toBe('09:42');
    expect(formatCompteARebours(-5)).toBe('00:00');
  });
});

describe('numéros sénégalais', () => {
  it.each([
    ['77 123 45 67', '771234567'],
    ['+221 77 123 45 67', '771234567'],
    ['00221771234567', '771234567'],
    ['221771234567', '771234567'],
  ])('garde les 9 chiffres nationaux de %s', (saisie, attendu) => {
    expect(chiffresNationaux(saisie)).toBe(attendu);
  });

  it('valide un mobile à 9 chiffres commençant par 7', () => {
    expect(estMobileSenegalais('77 123 45 67')).toBe(true);
    expect(estMobileSenegalais('33 123 45 67')).toBe(false);
    expect(estMobileSenegalais('77 123 45')).toBe(false);
  });

  it('met en forme pendant la saisie et produit le format du backend', () => {
    expect(formatMobile('7712')).toBe('77 12');
    expect(formatMobile('771234567')).toBe('77 123 45 67');
    expect(versE164('77 123 45 67')).toBe('+221771234567');
  });
});

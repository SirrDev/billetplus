import { describe, expect, it } from 'vitest';
import {
  formatCountdown,
  formatEventDate,
  formatFcfa,
  formatSnPhone,
  isValidSnMobile,
  sanitizeSnPhone,
} from './format';

describe('formatFcfa', () => {
  it('groupe les milliers avec des espaces insécables', () => {
    expect(formatFcfa(10500)).toBe('10 500 FCFA');
    expect(formatFcfa(3300000)).toBe('3 300 000 FCFA');
    expect(formatFcfa(0)).toBe('0 FCFA');
  });

  it('refuse les montants non entiers (le FCFA n’a pas de centimes)', () => {
    expect(() => formatFcfa(10.5)).toThrow(RangeError);
    expect(() => formatFcfa(Number.NaN)).toThrow(RangeError);
  });
});

describe('téléphone sénégalais', () => {
  it('ne garde que 9 chiffres', () => {
    expect(sanitizeSnPhone('77 123 45 67 89')).toBe('771234567');
    expect(sanitizeSnPhone('abc')).toBe('');
  });

  it('formate progressivement en 2-3-2-2', () => {
    expect(formatSnPhone('7')).toBe('7');
    expect(formatSnPhone('771')).toBe('77 1');
    expect(formatSnPhone('77123')).toBe('77 123');
    expect(formatSnPhone('771234567')).toBe('77 123 45 67');
  });

  it('valide les préfixes mobiles', () => {
    expect(isValidSnMobile('771234567')).toBe(true);
    expect(isValidSnMobile('701234567')).toBe(true);
    expect(isValidSnMobile('331234567')).toBe(false);
    expect(isValidSnMobile('77123456')).toBe(false);
  });
});

describe('formatCountdown', () => {
  it('affiche mm:ss', () => {
    expect(formatCountdown(108)).toBe('01:48');
    expect(formatCountdown(5)).toBe('00:05');
    expect(formatCountdown(0)).toBe('00:00');
  });

  it('ne descend jamais sous zéro', () => {
    expect(formatCountdown(-4)).toBe('00:00');
  });
});

describe('formatEventDate', () => {
  it("affiche l'heure de Dakar", () => {
    const date = new Date(Date.UTC(2026, 10, 14, 20, 0));
    expect(formatEventDate(date)).toBe('sam. 14 nov. · 20 h');
    expect(formatEventDate(date, { withTime: false })).toBe('sam. 14 nov.');
  });

  it('ajoute les minutes quand elles ne sont pas nulles', () => {
    const date = new Date(Date.UTC(2026, 10, 14, 20, 30));
    expect(formatEventDate(date)).toBe('sam. 14 nov. · 20 h 30');
  });
});

import { retourDansEspace } from './retourInterne';

describe('retourDansEspace', () => {
  it("garde une page de l'espace", () => {
    expect(retourDansEspace('/organisateur', '/organisateur')).toBe('/organisateur');
    expect(retourDansEspace('/organisateur', '/organisateur/evenements/11111111-1111-1111-1111-111111111111')).toBe(
      '/organisateur/evenements/11111111-1111-1111-1111-111111111111',
    );
    expect(retourDansEspace('/scanner', '/scanner/evenements/abc-123')).toBe('/scanner/evenements/abc-123');
    expect(retourDansEspace('/admin', '/admin/compte')).toBe('/admin/compte');
  });

  it("renvoie à l'accueil de l'espace sans adresse demandée", () => {
    expect(retourDansEspace('/organisateur', undefined)).toBe('/organisateur');
    expect(retourDansEspace('/admin', '')).toBe('/admin');
  });

  it.each([
    '/organisateur/../\\evil.com',
    '/organisateur/..',
    '/organisateur//evil.com',
    '/organisateur/%2e%2e/x',
    '/organisateur\\evil.com',
    '/organisateurs',
    '/organisateur-faux',
    '/organisateur?x=1',
    '/organisateur#x',
    '//evil.com/organisateur',
    'https://evil.com/organisateur',
    '/scanner',
    '/admin',
  ])('refuse %s', (depuis) => {
    expect(retourDansEspace('/organisateur', depuis)).toBe('/organisateur');
  });
});

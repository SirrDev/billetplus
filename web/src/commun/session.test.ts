import { effacerJetons, enregistrerJetons, lireJetons, surChangementDeSession } from './session';

describe('session', () => {
  beforeEach(() => effacerJetons());

  it('ne garde que les deux jetons, et ignore un contenu corrompu', () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' } as never);
    expect(JSON.parse(localStorage.getItem('billetplus.session') ?? '{}')).toEqual({ accessToken: 'a', refreshToken: 'r' });
    localStorage.setItem('billetplus.session', '{pas du json');
    expect(lireJetons()).toBeNull();
  });

  it("prévient d'une déconnexion faite dans un autre onglet", () => {
    const ecouteur = vi.fn();
    const arreter = surChangementDeSession(ecouteur);
    window.dispatchEvent(new StorageEvent('storage', { key: 'billetplus.session' }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'autre-cle' }));
    expect(ecouteur).toHaveBeenCalledTimes(1);
    arreter();
    window.dispatchEvent(new StorageEvent('storage', { key: 'billetplus.session' }));
    expect(ecouteur).toHaveBeenCalledTimes(1);
  });
});

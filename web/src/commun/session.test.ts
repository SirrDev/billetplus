import {
  CLE_LISTE_CONTROLE,
  effacerJetons,
  enregistrerJetons,
  enregistrerProfil,
  lireJetons,
  lireProfil,
  surChangementDeSession,
} from './session';

describe('session', () => {
  beforeEach(() => effacerJetons());

  it('ne garde que les deux jetons, et ignore un contenu corrompu', () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' } as never);
    expect(JSON.parse(localStorage.getItem('billetplus.session') ?? '{}')).toEqual({ accessToken: 'a', refreshToken: 'r' });
    localStorage.setItem('billetplus.session', '{pas du json');
    expect(lireJetons()).toBeNull();
  });

  it("efface avec la session tout ce que le compte a laissé sur l'appareil", () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    enregistrerProfil({ id: 'agent-1', role: 'AGENT' });
    localStorage.setItem(CLE_LISTE_CONTROLE, JSON.stringify({ compte: 'agent-1', evenements: [] }));
    effacerJetons();
    expect(lireJetons()).toBeNull();
    expect(lireProfil()).toBeNull();
    expect(localStorage.getItem(CLE_LISTE_CONTROLE)).toBeNull();
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

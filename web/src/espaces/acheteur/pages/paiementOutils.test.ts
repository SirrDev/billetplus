import { ErreurAffichable, messageErreur } from '@/commun/http';
import { allerPayer, nomDuMoyen } from './paiementOutils';

describe('allerPayer', () => {
  const assign = vi.fn();

  beforeEach(() => {
    assign.mockReset();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, assign } as Location);
  });

  it('ouvre un lien de paiement https', () => {
    allerPayer('https://pay.unitechpay.test/c1?x=1');
    expect(assign).toHaveBeenCalledWith('https://pay.unitechpay.test/c1?x=1');
  });

  it.each(['javascript:alert(document.domain)', 'http://pay.test/c1', 'data:text/html,<b>x</b>', 'pas une url'])(
    'refuse %s',
    (url) => {
      expect(() => allerPayer(url)).toThrow(ErreurAffichable);
      expect(() => allerPayer(url)).toThrow(/Lien de paiement refusé/);
      expect(assign).not.toHaveBeenCalled();
    },
  );
});

describe('message affiché après un refus', () => {
  it("n'est pas confondu avec une panne réseau", () => {
    let erreur: unknown;
    try {
      allerPayer('javascript:alert(1)');
    } catch (e) {
      erreur = e;
    }
    expect(messageErreur(erreur)).toMatch(/Lien de paiement refusé/);
    expect(messageErreur(erreur)).not.toMatch(/Connexion impossible/);
  });
});

describe('nomDuMoyen', () => {
  it('donne le nom lisible du moyen de paiement', () => {
    expect(nomDuMoyen('WAVE')).toBe('Wave');
    expect(nomDuMoyen('ORANGE_MONEY')).toBe('Orange Money');
  });
});

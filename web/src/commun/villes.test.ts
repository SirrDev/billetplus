import { libelleVille, optionsVilles, paysDeLaVille } from './villes';

describe('villes (ADR 0011)', () => {
  it('affiche le pays seulement hors du Sénégal', () => {
    expect(libelleVille({ villeNom: 'Thiès', pays: 'SN', paysNom: 'Sénégal' })).toBe('Thiès');
    expect(libelleVille({ villeNom: 'Abidjan', pays: 'CI', paysNom: "Côte d'Ivoire" })).toBe("Abidjan (Côte d'Ivoire)");
  });

  it('groupe les options par pays après une option vide', () => {
    const options = optionsVilles(
      [
        { code: 'SN', nom: 'Sénégal', villes: [{ code: 'SN-DAKAR', nom: 'Dakar' }] },
        { code: 'CI', nom: "Côte d'Ivoire", villes: [{ code: 'CI-ABIDJAN', nom: 'Abidjan' }] },
      ],
      'Toutes les villes',
    );
    expect(options).toEqual([
      { valeur: '', libelle: 'Toutes les villes' },
      { valeur: 'SN-DAKAR', libelle: 'Dakar', groupe: 'Sénégal' },
      { valeur: 'CI-ABIDJAN', libelle: 'Abidjan', groupe: "Côte d'Ivoire" },
    ]);
  });

  it('lit le pays dans le code de la ville', () => {
    expect(paysDeLaVille('BJ-COTONOU')).toBe('BJ');
  });
});

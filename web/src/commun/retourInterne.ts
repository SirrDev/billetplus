/** Espaces protégés vers lesquels une page de connexion peut renvoyer. */
export type EspaceProtege = '/organisateur' | '/scanner' | '/admin';

/**
 * Adresse où renvoyer après une connexion : la page demandée avant la connexion
 * (`state.depuis`, rempli avec `location.pathname` par ExigerRole), si elle appartient à
 * l'espace, sinon l'accueil de l'espace.
 *
 * Liste blanche plutôt que préfixe : segments faits de lettres, chiffres et tirets
 * seulement. Refuse « .. », « \ », « // », « % », « ? » et « # ». Aujourd'hui `depuis` ne
 * vient jamais d'une source extérieure ; si cela changeait, `/organisateur/../\x.com`
 * passerait un simple préfixe et mènerait hors du site (mécanisme de GHSA-wrjc-x8rr-h8h6).
 */
export function retourDansEspace(espace: EspaceProtege, depuis: string | undefined): string {
  const motif = new RegExp(`^${espace}(?:/[A-Za-z0-9-]+)*$`);
  return depuis !== undefined && motif.test(depuis) ? depuis : espace;
}

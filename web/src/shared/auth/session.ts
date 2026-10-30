/**
 * Jetons de la session (organisateur, agent, admin), gardés dans localStorage pour
 * survivre à la fermeture de l'application : un agent ouvre le scanner le jour J
 * sans se reconnecter (le jeton de rafraîchissement dure 7 jours côté backend).
 *
 * Limite assumée au MVP : un script injecté dans la page (XSS) pourrait les lire.
 * La politique de sécurité du contenu (CSP) du déploiement doit l'empêcher.
 */
export interface Jetons {
  accessToken: string;
  refreshToken: string;
}

const CLE = 'billetplus.session';
const ecouteurs = new Set<() => void>();

export function lireJetons(): Jetons | null {
  try {
    const brut = localStorage.getItem(CLE);
    if (!brut) return null;
    const jetons = JSON.parse(brut) as Partial<Jetons>;
    return jetons.accessToken && jetons.refreshToken ? (jetons as Jetons) : null;
  } catch {
    return null;
  }
}

export function enregistrerJetons(jetons: Jetons): void {
  localStorage.setItem(CLE, JSON.stringify(jetons));
  ecouteurs.forEach((f) => f());
}

export function effacerJetons(): void {
  localStorage.removeItem(CLE);
  ecouteurs.forEach((f) => f());
}

/** Prévient le contexte d'authentification d'un changement (connexion, déconnexion, jeton refusé). */
export function surChangementDeSession(ecouteur: () => void): () => void {
  ecouteurs.add(ecouteur);
  return () => {
    ecouteurs.delete(ecouteur);
  };
}

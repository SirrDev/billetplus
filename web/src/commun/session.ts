/**
 * Jetons de session (organisateur, puis agent de contrôle), gardés dans localStorage
 * pour survivre à la fermeture de l'application : le jeton de rafraîchissement dure
 * 7 jours côté backend (billetplus.jwt.refresh-expiration-days).
 *
 * Limite assumée au MVP : un script injecté dans la page (XSS) pourrait les lire. Le
 * rendu React échappe tout contenu de l'API, et la politique de sécurité du contenu (CSP)
 * du déploiement doit interdire les scripts tiers. Un cookie httpOnly demanderait un
 * changement du backend (ADR à écrire).
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
    return typeof jetons.accessToken === 'string' && typeof jetons.refreshToken === 'string'
      ? { accessToken: jetons.accessToken, refreshToken: jetons.refreshToken }
      : null;
  } catch {
    return null;
  }
}

export function enregistrerJetons(jetons: Jetons): void {
  try {
    localStorage.setItem(CLE, JSON.stringify({ accessToken: jetons.accessToken, refreshToken: jetons.refreshToken }));
  } finally {
    ecouteurs.forEach((f) => f());
  }
}

export function effacerJetons(): void {
  try {
    localStorage.removeItem(CLE);
  } finally {
    ecouteurs.forEach((f) => f());
  }
}

/**
 * Prévient d'un changement de session (connexion, déconnexion, jeton refusé), y compris
 * venu d'un AUTRE onglet (événement « storage ») : se déconnecter ferme tous les onglets.
 */
export function surChangementDeSession(ecouteur: () => void): () => void {
  const autreOnglet = (e: StorageEvent) => {
    if (e.key === CLE || e.key === null) ecouteur();
  };
  ecouteurs.add(ecouteur);
  window.addEventListener('storage', autreOnglet);
  return () => {
    ecouteurs.delete(ecouteur);
    window.removeEventListener('storage', autreOnglet);
  };
}

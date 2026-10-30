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
/** Clé de la liste gardée par le contrôle d'entrée (déclarée ici pour être effacée avec la session). */
export const CLE_LISTE_CONTROLE = 'billetplus.controle.evenements';
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
    localStorage.removeItem(CLE_PROFIL);
    // Dernière liste d'événements du contrôle d'entrée (espaces/controle) : effacée à toute
    // fin de session, volontaire ou forcée, pour ne rien laisser du compte sur l'appareil.
    localStorage.removeItem(CLE_LISTE_CONTROLE);
  } finally {
    ecouteurs.forEach((f) => f());
  }
}

/*
 * Profil de la dernière session (id, e-mail, nom, rôle : rien de secret), pour rouvrir
 * l'application SANS RÉSEAU (scanner relancé le jour J sans connexion). Il ne donne aucun
 * droit : le backend reste seul juge à chaque appel. Effacé avec les jetons.
 */
const CLE_PROFIL = 'billetplus.profil';

export function enregistrerProfil(profil: unknown): void {
  try {
    localStorage.setItem(CLE_PROFIL, JSON.stringify(profil));
  } catch {
    // Stockage indisponible : la session marche, seule la réouverture hors ligne est perdue.
  }
}

export function lireProfil(): unknown {
  try {
    const brut = localStorage.getItem(CLE_PROFIL);
    return brut ? (JSON.parse(brut) as unknown) : null;
  } catch {
    return null;
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

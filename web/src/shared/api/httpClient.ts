import { effacerJetons, enregistrerJetons, lireJetons, type Jetons } from '../auth/session';

const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

interface RequestOptions extends RequestInit {
  /** Requête publique : pas d'en-tête Authorization, pas de rafraîchissement. */
  skipAuth?: boolean;
}

/**
 * Erreur d'API : statut HTTP et message du backend. Le backend renvoie toujours
 * {timestamp, status, message} (GlobalExceptionHandler) : ce message est prévu pour
 * être lu (« Commande introuvable », « Places insuffisantes »...).
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Appel de l'API Billet+. Avec une session ouverte, ajoute le jeton d'accès ; s'il
 * a expiré (401, 15 minutes côté backend), le rafraîchit UNE fois puis rejoue la
 * requête. Si le rafraîchissement échoue, la session est fermée.
 */
export async function httpClient<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth = false, ...init } = options;
  const jetons = skipAuth ? null : lireJetons();
  let response = await appeler(path, init, jetons?.accessToken);

  if (response.status === 401 && jetons) {
    const nouveaux = await rafraichir(jetons);
    if (nouveaux) {
      response = await appeler(path, init, nouveaux.accessToken);
    } else {
      effacerJetons();
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, await messageDErreur(response));
  }
  // 201 ou 204 sans corps (inscription, affectation d'agent...) : rien à lire.
  const texte = await response.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
}

function appeler(path: string, init: RequestInit, accessToken?: string): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, {
    ...init,
    // Fusion APRÈS le reste des options : des en-têtes passés par l'appelant
    // (jeton de commande...) s'ajoutent à Content-Type au lieu de le remplacer.
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });
}

/** Un seul rafraîchissement à la fois, partagé par les requêtes parties en même temps. */
let rafraichissementEnCours: Promise<Jetons | null> | null = null;

function rafraichir(jetons: Jetons): Promise<Jetons | null> {
  rafraichissementEnCours ??= fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: jetons.refreshToken }),
  })
    .then(async (r) => {
      if (!r.ok) return null;
      const nouveaux = (await r.json()) as Jetons;
      const session = { accessToken: nouveaux.accessToken, refreshToken: nouveaux.refreshToken };
      enregistrerJetons(session);
      return session;
    })
    .catch(() => null)
    .finally(() => {
      rafraichissementEnCours = null;
    });
  return rafraichissementEnCours;
}

async function messageDErreur(response: Response): Promise<string> {
  try {
    const corps = (await response.json()) as { message?: unknown };
    if (typeof corps.message === 'string' && corps.message.trim()) {
      return corps.message;
    }
  } catch {
    // Corps absent ou non JSON : message générique ci-dessous.
  }
  return `Erreur ${response.status}`;
}

import { effacerJetons, enregistrerJetons, lireJetons, type Jetons } from './session';

const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

/**
 * Erreur d'API : statut HTTP et message du backend. GlobalExceptionHandler renvoie
 * toujours {timestamp, status, message}, un message prévu pour être lu.
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

export interface OptionsHttp extends RequestInit {
  /**
   * Ajoute le jeton d'accès de la session (espace organisateur). Les routes publiques et
   * celles de l'acheteur (jeton de commande en en-tête) n'en envoient jamais.
   */
  authentifie?: boolean;
  /**
   * Abandon après ce délai (ms) : un réseau qui ne répond plus (2G saturée, portail captif)
   * lève alors une erreur non-ApiError, traitée comme une absence de réseau.
   */
  delaiMs?: number;
}

/** Marge avant l'expiration du jeton d'accès à partir de laquelle on le renouvelle d'avance. */
const MARGE_EXPIRATION_MS = 30_000;

/**
 * Appel de l'API Billet+. Les en-têtes de l'appelant (jeton de commande...)
 * s'ajoutent à Content-Type. 201/204 sans corps : renvoie undefined.
 *
 * Requête authentifiée :
 * - jeton d'accès (15 min) expiré ou presque : renouvelé AVANT l'appel. Indispensable, car le
 *   backend ignore un jeton expiré (JwtAuthenticationFilter) : sur une route publique
 *   (GET /api/events/{id}), il répond la vue anonyme (404 pour un brouillon), jamais 401 ;
 * - 401 malgré tout : un seul rafraîchissement, puis la requête est rejouée ;
 * - rafraîchissement REFUSÉ par le backend : session fermée. Coupure réseau : session
 *   gardée (le jeton de rafraîchissement reste valable 7 jours), l'erreur remonte.
 */
export async function http<T>(chemin: string, options: OptionsHttp = {}): Promise<T> {
  const { authentifie = false, delaiMs, ...reste } = options;
  const init: RequestInit = delaiMs && !reste.signal ? { ...reste, signal: AbortSignal.timeout(delaiMs) } : reste;
  let jetons = authentifie ? lireJetons() : null;
  if (jetons && expireBientot(jetons.accessToken)) {
    jetons = await jetonsValides(jetons);
  }
  let reponse = await appeler(chemin, init, jetons?.accessToken);

  if (reponse.status === 401 && jetons) {
    const nouveaux = await jetonsValides(jetons);
    if (nouveaux) reponse = await appeler(chemin, init, nouveaux.accessToken);
  }

  if (!reponse.ok) {
    throw new ApiError(reponse.status, await messageDErreur(reponse));
  }
  const texte = await reponse.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
}

function appeler(chemin: string, init: RequestInit, accessToken?: string): Promise<Response> {
  return fetch(`${API_BASE_URL}${chemin}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });
}

/**
 * Échéance (champ « exp » du JWT) lue sans vérifier la signature : elle ne sert qu'à
 * décider QUAND renouveler, le backend reste seul juge de la validité. Illisible : on
 * laisse partir la requête (le 401 éventuel déclenchera le rafraîchissement).
 */
function expireBientot(accessToken: string): boolean {
  try {
    const charge = accessToken.split('.')[1];
    if (!charge) return false;
    const json = atob(charge.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: unknown }).exp;
    return typeof exp === 'number' && exp * 1000 - Date.now() < MARGE_EXPIRATION_MS;
  } catch {
    return false;
  }
}

/**
 * Jetons utilisables après un échec ou une expiration de `utilises`. Si une autre requête
 * a déjà renouvelé la session entre-temps, on reprend ses jetons sans nouvel appel : un
 * deuxième /auth/refresh casserait une future rotation avec détection de réutilisation.
 * null : session fermée (refus du backend).
 */
async function jetonsValides(utilises: Jetons): Promise<Jetons | null> {
  const actuels = lireJetons();
  if (!actuels) return null;
  if (actuels.accessToken !== utilises.accessToken) return actuels;
  return rafraichir(actuels);
}

/** Un seul rafraîchissement à la fois, partagé par les requêtes parties ensemble. */
let rafraichissementEnCours: Promise<Jetons | null> | null = null;

function rafraichir(jetons: Jetons): Promise<Jetons | null> {
  rafraichissementEnCours ??= fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: jetons.refreshToken }),
  })
    .then(async (r) => {
      if (!r.ok) {
        // Refus du backend (jeton de rafraîchissement expiré ou invalide) : fin de session.
        // 5xx : panne passagère, on garde la session et l'erreur remonte.
        if (r.status < 500) effacerJetons();
        return null;
      }
      const corps = (await r.json()) as Jetons;
      const session = { accessToken: corps.accessToken, refreshToken: corps.refreshToken };
      enregistrerJetons(session);
      return session;
    })
    // Coupure réseau : rejetée telle quelle, sans toucher à la session.
    .finally(() => {
      rafraichissementEnCours = null;
    });
  return rafraichissementEnCours;
}

async function messageDErreur(reponse: Response): Promise<string> {
  try {
    const corps = (await reponse.json()) as { message?: unknown };
    if (typeof corps.message === 'string' && corps.message.trim()) return corps.message;
  } catch {
    // Corps absent ou non JSON : message générique ci-dessous.
  }
  return `Erreur ${reponse.status}`;
}

/**
 * Erreur levée par le client lui-même, dont le message est écrit pour l'utilisateur
 * (lien de paiement refusé, stockage local en échec...) : affichée telle quelle.
 */
export class ErreurAffichable extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ErreurAffichable';
  }
}

/** Message lisible d'une erreur d'appel : celui du backend, ou un message de repli. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof ErreurAffichable) return erreur.message;
  if (erreur instanceof ApiError) {
    if (erreur.status === 429) return 'Trop de demandes : patientez une minute avant de réessayer.';
    if (erreur.status >= 500) return 'Le service est momentanément indisponible. Réessayez dans un instant.';
    return erreur.message;
  }
  return 'Connexion impossible. Vérifiez votre réseau et réessayez.';
}

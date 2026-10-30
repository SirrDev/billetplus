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

/**
 * Appel de l'API Billet+. Les en-têtes de l'appelant (jeton de commande...)
 * s'ajoutent à Content-Type. 201/204 sans corps : renvoie undefined.
 * L'authentification de l'espace organisateur s'ajoutera ici.
 */
export async function http<T>(chemin: string, options: RequestInit = {}): Promise<T> {
  const reponse = await fetch(`${API_BASE_URL}${chemin}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  if (!reponse.ok) {
    throw new ApiError(reponse.status, await messageDErreur(reponse));
  }
  const texte = await reponse.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
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

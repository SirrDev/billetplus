const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

interface RequestOptions extends RequestInit {
  skipAuth?: boolean;
}

/**
 * Wrapper fetch minimal. Le rafraîchissement automatique de JWT sera ajouté
 * dans feature/web-auth-context — ce squelette pose seulement la base
 * commune utilisée par tous les modules (organisateur, acheteur, scanner).
 */
export async function httpClient<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`Erreur API ${response.status} sur ${path}`);
  }

  return response.json() as Promise<T>;
}

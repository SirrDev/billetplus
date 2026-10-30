import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { httpClient } from '../api/httpClient';
import { effacerJetons, enregistrerJetons, lireJetons, surChangementDeSession, type Jetons } from './session';

export type Role = 'ORGANISATEUR' | 'AGENT' | 'ADMIN';

/** MeResponse (GET /api/auth/me). */
export interface Utilisateur {
  id: string;
  email: string;
  nomComplet: string;
  role: Role;
}

interface ContexteAuth {
  utilisateur: Utilisateur | null;
  /** Vrai tant que la session existante n'a pas été vérifiée auprès du backend. */
  chargement: boolean;
  connecter: (email: string, motDePasse: string) => Promise<Utilisateur>;
  deconnecter: () => void;
}

const Contexte = createContext<ContexteAuth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [utilisateur, setUtilisateur] = useState<Utilisateur | null>(null);
  const [chargement, setChargement] = useState(() => lireJetons() !== null);

  const recharger = useCallback(async () => {
    if (!lireJetons()) {
      setUtilisateur(null);
      setChargement(false);
      return;
    }
    try {
      setUtilisateur(await httpClient<Utilisateur>('/auth/me'));
    } catch {
      setUtilisateur(null);
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => {
    void recharger();
    // Session fermée ailleurs (jeton refusé par le serveur...).
    return surChangementDeSession(() => {
      if (!lireJetons()) setUtilisateur(null);
    });
  }, [recharger]);

  const connecter = useCallback(async (email: string, motDePasse: string) => {
    const jetons = await httpClient<Jetons>('/auth/login', {
      method: 'POST',
      skipAuth: true,
      body: JSON.stringify({ email, motDePasse }),
    });
    enregistrerJetons({ accessToken: jetons.accessToken, refreshToken: jetons.refreshToken });
    const moi = await httpClient<Utilisateur>('/auth/me');
    setUtilisateur(moi);
    return moi;
  }, []);

  const deconnecter = useCallback(() => {
    effacerJetons();
    setUtilisateur(null);
  }, []);

  const valeur = useMemo(
    () => ({ utilisateur, chargement, connecter, deconnecter }),
    [utilisateur, chargement, connecter, deconnecter],
  );
  return <Contexte.Provider value={valeur}>{children}</Contexte.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- hook indissociable de son fournisseur
export function useAuth(): ContexteAuth {
  const contexte = useContext(Contexte);
  if (!contexte) throw new Error('useAuth doit être utilisé sous <AuthProvider>');
  return contexte;
}

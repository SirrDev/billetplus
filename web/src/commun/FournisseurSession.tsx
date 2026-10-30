import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Cadre, Chargement } from '@/ui';
import { lireMoi, seConnecter, Session, useSession, type EtatSession, type Utilisateur } from './auth';
import { effacerJetons, enregistrerJetons, lireJetons, surChangementDeSession } from './session';

/**
 * Session courante, relue sur /auth/me au démarrage et à chaque changement de jetons
 * (connexion, déconnexion, rafraîchissement refusé par le backend).
 */
export function FournisseurSession({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<EtatSession>(() => (lireJetons() ? { etat: 'chargement' } : { etat: 'anonyme' }));

  const relire = useCallback(() => {
    if (!lireJetons()) {
      setSession({ etat: 'anonyme' });
      return;
    }
    lireMoi()
      .then((utilisateur) => setSession({ etat: 'connecte', utilisateur }))
      // Jeton refusé (http a déjà fermé la session) ou réseau absent : connexion à refaire.
      .catch(() => setSession({ etat: 'anonyme' }));
  }, []);

  useEffect(() => {
    relire();
    return surChangementDeSession(relire);
  }, [relire]);

  const connexion = useCallback(async (email: string, motDePasse: string): Promise<Utilisateur> => {
    const jetons = await seConnecter(email, motDePasse);
    enregistrerJetons(jetons);
    const utilisateur = await lireMoi();
    setSession({ etat: 'connecte', utilisateur });
    return utilisateur;
  }, []);

  const valeur = useMemo(() => ({ session, connexion, deconnexion: effacerJetons }), [session, connexion]);
  return <Session.Provider value={valeur}>{children}</Session.Provider>;
}

/**
 * Garde d'un espace réservé : sans session, renvoie vers la connexion en gardant
 * l'adresse demandée ; avec un autre rôle, renvoie vers l'accueil. Le backend reste
 * seul juge (@PreAuthorize) : cette garde n'évite qu'un écran inutile.
 */
export function ExigerRole({
  roles,
  connexion,
  children,
}: {
  roles: ReadonlyArray<Utilisateur['role']>;
  connexion: string;
  children: ReactNode;
}) {
  const { session } = useSession();
  const location = useLocation();
  if (session.etat === 'chargement') {
    return (
      <Cadre sansNav>
        <Chargement libelle="Ouverture de la session…" />
      </Cadre>
    );
  }
  if (session.etat === 'anonyme') {
    return <Navigate to={connexion} replace state={{ depuis: location.pathname }} />;
  }
  if (!roles.includes(session.utilisateur.role)) return <Navigate to="/" replace />;
  return <>{children}</>;
}

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Cadre, Chargement } from '@/ui';
import { lireMoi, seConnecter, Session, useSession, type EtatSession, type Utilisateur } from './auth';
import { ApiError } from './http';
import { effacerJetons, enregistrerJetons, enregistrerProfil, lireJetons, lireProfil, surChangementDeSession } from './session';

const ROLES: ReadonlyArray<string> = ['ORGANISATEUR', 'AGENT', 'ADMIN'];

/** Profil mémorisé, s'il a la forme d'un MeResponse ; sinon null. */
function profilMemorise(): Utilisateur | null {
  const p = lireProfil() as Partial<Utilisateur> | null;
  return p && typeof p.id === 'string' && typeof p.nomComplet === 'string' && typeof p.email === 'string' && ROLES.includes(p.role ?? '')
    ? (p as Utilisateur)
    : null;
}

/**
 * Session courante, relue sur /auth/me au démarrage et à chaque changement de jetons
 * (connexion, déconnexion, rafraîchissement refusé par le backend).
 *
 * Sans réseau, la session reste ouverte avec le profil mémorisé : le scanner doit se
 * rouvrir le jour J sans connexion (ADR 0004). Un jeton REFUSÉ par le backend ferme
 * toujours la session (http efface alors les jetons).
 */
export function FournisseurSession({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<EtatSession>(() => (lireJetons() ? { etat: 'chargement' } : { etat: 'anonyme' }));

  const relire = useCallback(() => {
    if (!lireJetons()) {
      setSession({ etat: 'anonyme' });
      return;
    }
    lireMoi()
      .then((utilisateur) => {
        enregistrerProfil(utilisateur);
        setSession({ etat: 'connecte', utilisateur });
      })
      .catch((e: unknown) => {
        const hors = profilMemorise();
        // Réseau absent (pas une réponse du backend) et session encore là : on garde le profil.
        if (!(e instanceof ApiError) && lireJetons() && hors) setSession({ etat: 'connecte', utilisateur: hors });
        else setSession({ etat: 'anonyme' });
      });
  }, []);

  useEffect(() => {
    relire();
    return surChangementDeSession(relire);
  }, [relire]);

  const connexion = useCallback(async (email: string, motDePasse: string): Promise<Utilisateur> => {
    const jetons = await seConnecter(email, motDePasse);
    enregistrerJetons(jetons);
    const utilisateur = await lireMoi();
    enregistrerProfil(utilisateur);
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

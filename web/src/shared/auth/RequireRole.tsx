import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, EmptyState, Notice, Screen, TopBar } from '@/design-system';
import { useAuth, type Role } from './AuthContext';

/**
 * Garde de route : renvoie vers la page de connexion de l'espace (en gardant la page
 * demandée), ou explique qu'un autre rôle est requis. Le backend contrôle de toute
 * façon chaque appel : cette garde ne fait que guider l'utilisateur.
 */
export function RequireRole({
  roles,
  connexion,
  children,
}: {
  roles: ReadonlyArray<Role>;
  connexion: string;
  children: ReactNode;
}) {
  const { utilisateur, chargement, deconnecter } = useAuth();
  const emplacement = useLocation();

  if (chargement) {
    return (
      <Screen>
        <Notice icon="clock">Vérification de la session…</Notice>
      </Screen>
    );
  }
  if (!utilisateur) {
    const retour = encodeURIComponent(emplacement.pathname + emplacement.search);
    return <Navigate to={`${connexion}?retour=${retour}`} replace />;
  }
  if (!roles.includes(utilisateur.role)) {
    return (
      <Screen>
        <TopBar title="Accès réservé" backTo="/" backLabel="Retour à l'accueil" />
        <EmptyState
          icon="lock"
          title="Accès réservé"
          description={`Cet espace est réservé aux comptes ${roles.join(' ou ').toLowerCase()}. Vous êtes connecté avec ${utilisateur.email}.`}
          actions={
            <Button variant="secondary" onClick={deconnecter}>
              Changer de compte
            </Button>
          }
        />
      </Screen>
    );
  }
  return <>{children}</>;
}

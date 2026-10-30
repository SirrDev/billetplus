import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { Bouton, Cadre, navOrganisateur } from '@/ui';

/** Cadre de l'espace organisateur : sa navigation, son accueil et la déconnexion. */
export function CadreOrganisateur({ children, etroit }: { children: ReactNode; etroit?: boolean }) {
  const { deconnexion } = useSession();
  const navigate = useNavigate();
  const quitter = () => {
    deconnexion();
    navigate('/organisateur/connexion', { replace: true });
  };
  return (
    <Cadre
      fondGris
      etroit={etroit}
      liens={navOrganisateur}
      accueil="/organisateur"
      actions={
        <Bouton variante="secondaire" onClick={quitter}>
          Se déconnecter
        </Bouton>
      }
    >
      {children}
    </Cadre>
  );
}

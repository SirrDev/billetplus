import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { Bouton, Cadre } from '@/ui';

/** Cadre de l'administration : un seul écran, donc pas de barre de navigation ; déconnexion visible. */
export function CadreAdmin({ children }: { children: ReactNode }) {
  const { deconnexion } = useSession();
  const navigate = useNavigate();
  return (
    <Cadre
      fondGris
      sansNav
      accueil="/admin"
      actions={
        <Bouton
          variante="secondaire"
          onClick={() => {
            deconnexion();
            navigate('/admin/connexion', { replace: true });
          }}
        >
          Se déconnecter
        </Bouton>
      }
    >
      {children}
    </Cadre>
  );
}

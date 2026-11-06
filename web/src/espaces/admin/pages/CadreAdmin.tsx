import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { Bouton, Cadre } from '@/ui';
import s from './admin.module.css';

/**
 * Cadre de l'administration : deux écrans (remboursements, reversements), reliés par une
 * barre sous l'en-tête qui passe à la ligne sur téléphone ; l'en-tête ne garde que la
 * déconnexion (trois boutons y débordaient à 375 px).
 */
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
      <nav className={s.navAdmin} aria-label="Administration">
        <NavLink to="/admin" end className={({ isActive }) => (isActive ? `${s.lienAdmin} ${s.lienActif}` : s.lienAdmin)}>
          Remboursements
        </NavLink>
        <NavLink
          to="/admin/reversements"
          className={({ isActive }) => (isActive ? `${s.lienAdmin} ${s.lienActif}` : s.lienAdmin)}
        >
          Reversements
        </NavLink>
      </nav>
      {children}
    </Cadre>
  );
}

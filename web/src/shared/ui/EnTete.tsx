import { Link, NavLink } from 'react-router-dom';
import { BottomNav, Icon, type NavItem } from '@/design-system';
import s from './enTete.module.css';

interface NavigationSiteProps {
  liens: ReadonlyArray<NavItem>;
  /** Nom de la navigation pour les lecteurs d'écran (« Espace organisateur »...). */
  libelle: string;
  /**
   * Barre basse sur téléphone. Faux pour les écrans de parcours (fiche, choix des
   * billets, commande), qui ont déjà un bouton retour et une barre d'action en bas.
   */
  barreMobile?: boolean;
}

/**
 * Navigation adaptée à l'écran :
 * - téléphone : la barre basse du design system (si barreMobile) ;
 * - ordinateur (1024 px et plus) : une en-tête de site sur toute la largeur, avec le
 *   logo et les liens, présente sur TOUS les écrans, y compris ceux du parcours.
 * Chacune est masquée là où l'autre s'affiche (CSS), jamais les deux à la fois.
 */
export function NavigationSite({ liens, libelle, barreMobile = true }: NavigationSiteProps) {
  return (
    <>
      <div className={s.reserve} aria-hidden="true" />
      <header className={s.entete}>
        <div className={s.interieur}>
          <Link to="/" className={s.logo} aria-label="Billet+, accueil">
            Billet<span className={s.plus}>+</span>
          </Link>
          <nav aria-label={libelle} className={s.liens}>
            {liens.map((lien) => (
              <NavLink
                key={lien.to}
                to={lien.to}
                end={lien.end}
                className={({ isActive }) => `${s.lien} ${isActive ? s.actif : ''}`}
              >
                <Icon name={lien.icon} size={20} />
                {lien.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      {barreMobile ? (
        <div className={s.mobileSeulement}>
          <BottomNav items={liens} label={libelle} />
        </div>
      ) : null}
    </>
  );
}

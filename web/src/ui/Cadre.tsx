import type { CSSProperties, ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Icone } from './Icone';
import { navAcheteur, type LienNav } from './outils';
import s from './Cadre.module.css';

interface CadreProps {
  children: ReactNode;
  /** Contenu resserré (1040 px) pour la saisie : paiement, commande. */
  etroit?: boolean;
  /** Sans navigation : écrans de parcours où l'on ne doit pas se perdre. */
  sansNav?: boolean;
  /** Fond gris des écrans à cartes blanches. */
  fondGris?: boolean;
  liens?: ReadonlyArray<LienNav>;
  /** Accueil de l'espace (cible du logo) : « / » pour l'acheteur, « /organisateur »... */
  accueil?: string;
  /** Actions de l'en-tête, à droite (déconnexion de l'organisateur). */
  actions?: ReactNode;
}

/**
 * Cadre commun à tous les écrans : en-tête, contenu centré, navigation.
 * La navigation est le même <nav> à toutes les tailles ; seul le CSS la déplace
 * (barre basse sur mobile, en-tête dès 720 px) : même ordre de tabulation partout.
 */
export function Cadre({ children, etroit, sansNav, fondGris, liens = navAcheteur, accueil = '/', actions }: CadreProps) {
  const style = {
    '--nb-liens': liens.length,
    '--fond': fondGris ? 'var(--bp-surface)' : undefined,
  } as CSSProperties;
  return (
    <div className={[s.site, sansNav && s.sansNav].filter(Boolean).join(' ')} style={style}>
      <a href="#contenu" className="visually-hidden">
        Aller au contenu
      </a>
      <header className={s.entete}>
        <div className={s.enteteInterieur}>
          <Link to={accueil} className={s.logo} aria-label="Billet+, accueil">
            <span className={s.logoMarque} aria-hidden="true">
              B+
            </span>
            <span className={s.logoNom} aria-hidden="true">
              Billet+
            </span>
          </Link>
          {!sansNav && (
            <nav aria-label="Navigation principale" className={s.nav}>
              {liens.map((l) => (
                <NavLink key={l.vers} to={l.vers} end={l.exact} className={s.lien}>
                  <Icone nom={l.icone} taille={22} />
                  {l.libelle}
                </NavLink>
              ))}
            </nav>
          )}
          {actions && <div className={s.actions}>{actions}</div>}
        </div>
      </header>
      <main id="contenu" tabIndex={-1} className={[s.principal, etroit && s.etroit].filter(Boolean).join(' ')}>
        {children}
      </main>
      <footer className={s.pied}>
        <div className={s.piedInterieur}>
          <span>Billet+ · Billetterie événementielle, Dakar</span>
          <span>Paiement Wave et Orange Money</span>
        </div>
      </footer>
    </div>
  );
}

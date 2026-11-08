import type { CSSProperties, ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Icone } from './Icone';
import { navAcheteur, useEnLigne, type LienNav } from './outils';
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
  /** Bandeau « hors connexion » sous l'en-tête (désactivé là où l'écran l'affiche lui-même : scanner). */
  bandeauHorsLigne?: boolean;
}

/**
 * Cadre commun à tous les écrans : en-tête, contenu centré, navigation.
 * La navigation est le même <nav> à toutes les tailles ; seul le CSS la déplace
 * (barre basse sur mobile, en-tête dès 720 px) : même ordre de tabulation partout.
 */
export function Cadre({
  children,
  etroit,
  sansNav,
  fondGris,
  liens = navAcheteur,
  accueil = '/',
  actions,
  bandeauHorsLigne = true,
}: CadreProps) {
  const enLigne = useEnLigne();
  // Espace public de l'acheteur : entrées vers l'espace organisateur et le contrôle d'entrée
  // (l'administration n'a pas de lien public). Jamais sur un écran sans navigation :
  // connexions des équipes, paiement en cours.
  const espacePublic = liens === navAcheteur && !sansNav;
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
                  {l.libelleCourt ? (
                    <>
                      {/* Nom accessible : toujours le libellé complet ; le court n'est qu'un affichage mobile. */}
                      <span className={s.libelleLong}>{l.libelle}</span>
                      <span className={s.libelleCourt} aria-hidden="true">
                        {l.libelleCourt}
                      </span>
                    </>
                  ) : (
                    l.libelle
                  )}
                </NavLink>
              ))}
            </nav>
          )}
          {actions ? (
            <div className={s.actions}>{actions}</div>
          ) : (
            espacePublic && (
              <div className={s.actions}>
                <Link to="/organisateur" className={s.lienOrganisateurs}>
                  Organisateurs
                </Link>
                <Link to="/scanner" className={s.lienOrganisateurs}>
                  {/* Nom accessible : toujours le libellé complet ; le court n'est qu'un affichage mobile. */}
                  <span className={s.libelleLong}>Contrôle d'entrée</span>
                  <span className={s.libelleCourt} aria-hidden="true">
                    Contrôle
                  </span>
                </Link>
              </div>
            )
          )}
        </div>
      </header>
      {/* Région live toujours présente : un role=status inséré à la volée est mal annoncé. */}
      {bandeauHorsLigne && (
        <div role="status" className={enLigne ? undefined : s.horsLigne}>
          {!enLigne && (
            <>
              <Icone nom="horsLigne" taille={16} />
              Hors connexion : vous voyez les dernières données reçues sur cet appareil.
            </>
          )}
        </div>
      )}
      <main id="contenu" tabIndex={-1} className={[s.principal, etroit && s.etroit].filter(Boolean).join(' ')}>
        {children}
      </main>
      {espacePublic ? (
        <PiedPublic />
      ) : (
        <footer className={s.pied}>
          <div className={s.piedInterieur}>
            <span>Billet+ · Billetterie événementielle, Dakar</span>
            <span>Paiement Wave et Orange Money</span>
          </div>
        </footer>
      )}
    </div>
  );
}

/**
 * Pied de page de l'espace public, à toutes les tailles : marque, liens utiles, moyens de
 * paiement. Les libellés diffèrent de ceux de l'en-tête (pas deux liens au même nom).
 */
function PiedPublic() {
  return (
    <footer className={s.piedPublic}>
      <div className={s.piedPublicInterieur}>
        <div className={s.piedMarque}>
          <span className={s.logoMarque} aria-hidden="true">
            B+
          </span>
          <div>
            <p className={s.piedNom}>Billet+</p>
            <p className={s.piedAccroche}>Billetterie événementielle, Dakar</p>
          </div>
        </div>
        <nav aria-label="Liens du pied de page" className={s.piedColonnes}>
          <div>
            <p className={s.piedTitre}>Acheter</p>
            <ul className={s.piedListe}>
              <li>
                <Link to="/">Tous les événements</Link>
              </li>
              <li>
                <Link to="/billets">Retrouver mes billets</Link>
              </li>
              <li>
                <Link to="/aide">Questions fréquentes</Link>
              </li>
            </ul>
          </div>
          <div>
            <p className={s.piedTitre}>Organiser</p>
            <ul className={s.piedListe}>
              <li>
                <Link to="/organisateur/inscription">Vendre ses billets avec Billet+</Link>
              </li>
              <li>
                <Link to="/organisateur">Espace organisateur</Link>
              </li>
              <li>
                <Link to="/scanner">Contrôler les entrées</Link>
              </li>
            </ul>
          </div>
        </nav>
        <div>
          <p className={s.piedTitre}>Paiement</p>
          <p className={s.piedMoyens}>
            <span className={s.piedMoyen}>Wave</span>
            <span className={s.piedMoyen}>Orange Money</span>
          </p>
        </div>
      </div>
      <p className={s.piedBas}>© {new Date().getFullYear()} Billet+ · Dakar, Sénégal</p>
    </footer>
  );
}

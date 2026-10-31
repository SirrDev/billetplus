import { useNavigate } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { Avis, Bouton, BoutonLien, Icone, Panneau } from '@/ui';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

/**
 * Compte de l'organisateur : identité lue dans /auth/me (jamais modifiable par le client,
 * le rôle est fixé par le backend). Aucune route ne permet encore de changer le mot de
 * passe ou le nom : l'écran le dit au lieu de proposer un formulaire sans effet.
 */
export function ComptePage() {
  const { session, deconnexion } = useSession();
  const navigate = useNavigate();
  const utilisateur = session.etat === 'connecte' ? session.utilisateur : null;

  const quitter = () => {
    deconnexion();
    navigate('/organisateur/connexion', { replace: true });
  };

  return (
    <CadreOrganisateur etroit>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Espace organisateur</p>
          <h1 className={s.titrePage}>Mon compte</h1>
        </div>

        <Panneau titre="Identité" id="identite">
          <dl className={s.fiche}>
            <div>
              <dt>Nom ou structure</dt>
              <dd>{utilisateur?.nomComplet}</dd>
            </div>
            <div>
              <dt>E-mail de connexion</dt>
              <dd>{utilisateur?.email}</dd>
            </div>
            <div>
              <dt>Rôle</dt>
              <dd>Organisateur</dd>
            </div>
          </dl>
          <Avis>
            <p>
              Le changement de nom, d'e-mail ou de mot de passe n'est pas encore disponible en ligne. Gardez votre mot de
              passe pour vous seul.
            </p>
          </Avis>
        </Panneau>

        <Panneau titre="Raccourcis" id="raccourcis">
          <div className={s.actions}>
            <BoutonLien to="/organisateur" variante="secondaire">
              <Icone nom="calendrier" />
              Mes événements
            </BoutonLien>
            <BoutonLien to="/organisateur/equipe" variante="secondaire">
              <Icone nom="bouclier" />
              Équipe de contrôle
            </BoutonLien>
            <BoutonLien to="/scanner" variante="secondaire">
              <Icone nom="valide" />
              Contrôler une entrée
            </BoutonLien>
          </div>
        </Panneau>

        <Panneau titre="Session" id="session">
          <p className={s.texte}>
            Sur un ordinateur partagé, déconnectez-vous en partant : votre session reste ouverte tant qu'elle sert, et jusqu'à 7 jours sans utilisation.
          </p>
          <div>
            <Bouton variante="secondaire" onClick={quitter}>
              Se déconnecter de cet appareil
            </Bouton>
          </div>
        </Panneau>
      </div>
    </CadreOrganisateur>
  );
}

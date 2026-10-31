import { useSession } from '@/commun/auth';
import { FormulaireMotDePasse } from '@/commun/FormulaireMotDePasse';
import { BoutonLien, Icone, Panneau } from '@/ui';
import { CadreControle } from './CadreControle';
import s from './controle.module.css';

/**
 * Compte de l'agent de contrôle : identité et changement du mot de passe que
 * l'organisateur lui a donné à la création du compte.
 */
export function CompteControlePage() {
  const { session } = useSession();
  const utilisateur = session.etat === 'connecte' ? session.utilisateur : null;
  return (
    <CadreControle>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>{utilisateur?.email}</p>
          <h1 className={s.titrePage}>Mon compte</h1>
        </div>
        <Panneau titre="Mot de passe" id="mot-de-passe">
          <p className={s.texte}>
            Votre organisateur vous a donné un mot de passe à la création du compte : remplacez-le par un mot de passe
            connu de vous seul.
          </p>
          <FormulaireMotDePasse />
        </Panneau>
        <div>
          <BoutonLien to="/scanner" variante="secondaire">
            <Icone nom="retour" />
            Événements à contrôler
          </BoutonLien>
        </div>
      </div>
    </CadreControle>
  );
}

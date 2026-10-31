import { useSession } from '@/commun/auth';
import { FormulaireMotDePasse } from '@/commun/FormulaireMotDePasse';
import { BoutonLien, Icone, Panneau } from '@/ui';
import { CadreAdmin } from './CadreAdmin';
import s from './admin.module.css';

/**
 * Compte administrateur : changement du mot de passe. Le compte est créé hors API au
 * premier démarrage ; le changer ici ne dépend plus des variables BILLETPLUS_ADMIN_*
 * (elles ne servent qu'à la création, tant qu'aucun ADMIN n'existe).
 */
export function CompteAdminPage() {
  const { session } = useSession();
  const utilisateur = session.etat === 'connecte' ? session.utilisateur : null;
  return (
    <CadreAdmin>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>{utilisateur?.email}</p>
          <h1 className={s.titrePage}>Mon compte</h1>
        </div>
        <Panneau titre="Mot de passe" id="mot-de-passe">
          <FormulaireMotDePasse />
        </Panneau>
        <div>
          <BoutonLien to="/admin" variante="secondaire">
            <Icone nom="retour" />
            Remboursements
          </BoutonLien>
        </div>
      </div>
    </CadreAdmin>
  );
}

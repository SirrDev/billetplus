import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { TEXTE_MAX, useSession } from '@/commun/auth';
import { ApiError, messageErreur } from '@/commun/http';
import { Avis, Bouton, BoutonLien, Cadre, ChampTexte, Icone, Panneau } from '@/ui';
import s from './controle.module.css';

/** Rôles autorisés à contrôler (ScanController : hasAnyRole AGENT, ORGANISATEUR). */
const ROLES_CONTROLE = ['AGENT', 'ORGANISATEUR'];

export function ConnexionControlePage() {
  const { session, connexion, deconnexion } = useSession();
  const navigate = useNavigate();
  const depuis = (useLocation().state as { depuis?: string } | null)?.depuis;
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  if (session.etat === 'connecte' && ROLES_CONTROLE.includes(session.utilisateur.role)) {
    return <Navigate to="/scanner" replace />;
  }

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    setEnvoi(true);
    try {
      const utilisateur = await connexion(email.trim(), motDePasse);
      if (!ROLES_CONTROLE.includes(utilisateur.role)) {
        deconnexion();
        setErreur("Ce compte ne peut pas contrôler les entrées.");
        setEnvoi(false);
        return;
      }
      // Adresse interne à l'espace de contrôle seulement.
      navigate(depuis?.startsWith('/scanner') ? depuis : '/scanner', { replace: true });
    } catch (e2) {
      setErreur(e2 instanceof ApiError && e2.status === 401 ? 'E-mail ou mot de passe incorrect.' : messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Cadre sansNav fondGris accueil="/scanner">
      <div className={s.connexion}>
        <Panneau>
          <form className={s.formulaire} onSubmit={valider}>
            <div>
              <BoutonLien to="/" variante="secondaire">
                <Icone nom="retour" />
                Retour à l'accueil
              </BoutonLien>
            </div>
            <div>
              <p className={s.surtitre}>Contrôle d'entrée</p>
              <h1 className={s.titrePage}>Connexion de l'agent</h1>
            </div>
            <ChampTexte
              libelle="E-mail"
              type="email"
              valeur={email}
              onChange={setEmail}
              requis
              maxLength={TEXTE_MAX}
              autoComplete="username"
              inputMode="email"
            />
            <ChampTexte
              libelle="Mot de passe"
              type="password"
              valeur={motDePasse}
              onChange={setMotDePasse}
              requis
              autoComplete="current-password"
            />
            {erreur && (
              <Avis ton="danger">
                <p>{erreur}</p>
              </Avis>
            )}
            <Bouton type="submit" grand pleineLargeur disabled={envoi}>
              {envoi ? 'Connexion…' : 'Se connecter'}
            </Bouton>
            <p className={s.texte}>
              Les comptes d'agent sont créés par l'organisateur, dans son espace « Équipe ». Connectez-vous avant
              l'événement, avec du réseau : le contrôle fonctionne ensuite sans connexion.
            </p>
          </form>
        </Panneau>
      </div>
    </Cadre>
  );
}

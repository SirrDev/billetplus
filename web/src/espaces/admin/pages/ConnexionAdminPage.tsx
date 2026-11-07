import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { TEXTE_MAX, useSession } from '@/commun/auth';
import { ApiError, messageErreur } from '@/commun/http';
import { retourDansEspace } from '@/commun/retourInterne';
import { Avis, Bouton, Cadre, ChampTexte, Panneau } from '@/ui';
import s from './admin.module.css';

/**
 * Connexion de l'administrateur. Le compte ADMIN est créé hors API (variables
 * d'environnement du backend) : aucune inscription ici. Un autre rôle est refusé et ses
 * jetons effacés de l'appareil.
 */
export function ConnexionAdminPage() {
  const { session, connexion, deconnexion } = useSession();
  const navigate = useNavigate();
  const depuis = (useLocation().state as { depuis?: string } | null)?.depuis;
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  if (session.etat === 'connecte' && session.utilisateur.role === 'ADMIN') {
    return <Navigate to="/admin" replace />;
  }

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    setEnvoi(true);
    try {
      const utilisateur = await connexion(email.trim(), motDePasse);
      if (utilisateur.role !== 'ADMIN') {
        deconnexion();
        setErreur("Ce compte n'est pas un compte administrateur.");
        setEnvoi(false);
        return;
      }
      navigate(retourDansEspace('/admin', depuis), { replace: true });
    } catch (e2) {
      setErreur(e2 instanceof ApiError && e2.status === 401 ? 'E-mail ou mot de passe incorrect.' : messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Cadre sansNav fondGris accueil="/admin">
      <div className={s.connexion}>
        <Panneau>
          <form className={s.formulaire} onSubmit={valider}>
            <div>
              <h1 className={s.titrePage}>Connexion</h1>
              <p className={s.sousTitre}>Administration Billet+</p>
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
          </form>
        </Panneau>
      </div>
    </Cadre>
  );
}

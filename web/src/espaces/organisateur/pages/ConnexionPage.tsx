import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { TEXTE_MAX, useSession } from '@/commun/auth';
import { ApiError, messageErreur } from '@/commun/http';
import { retourDansEspace } from '@/commun/retourInterne';
import { Avis, Bouton, BoutonLien, Cadre, ChampTexte, Icone, Panneau } from '@/ui';
import s from './organisateur.module.css';

export function ConnexionPage() {
  const { session, connexion, deconnexion } = useSession();
  const navigate = useNavigate();
  const depuis = (useLocation().state as { depuis?: string } | null)?.depuis;
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  if (session.etat === 'connecte' && session.utilisateur.role === 'ORGANISATEUR') {
    return <Navigate to="/organisateur" replace />;
  }

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    setEnvoi(true);
    try {
      const utilisateur = await connexion(email.trim(), motDePasse);
      if (utilisateur.role !== 'ORGANISATEUR') {
        // Ne pas laisser sur l'appareil les jetons d'un compte refusé ici (agent, admin).
        deconnexion();
        setErreur("Ce compte n'est pas un compte organisateur.");
        setEnvoi(false);
        return;
      }
      // Seule une adresse de l'espace organisateur est suivie : jamais une adresse arbitraire.
      navigate(retourDansEspace('/organisateur', depuis), { replace: true });
    } catch (e2) {
      // 401 : même message que le backend, sans dire si l'e-mail existe.
      setErreur(e2 instanceof ApiError && e2.status === 401 ? 'E-mail ou mot de passe incorrect.' : messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Cadre sansNav fondGris accueil="/organisateur">
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
              <h1 className={s.titrePage}>Connexion</h1>
              <p className={s.sousTitre}>Espace organisateur</p>
            </div>
            <ChampTexte
              libelle="E-mail"
              type="email"
              valeur={email}
              onChange={setEmail}
              requis
              maxLength={TEXTE_MAX}
              autoComplete="email"
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
              Pas encore de compte ?{' '}
              <Link to="/organisateur/inscription" className={s.lienDiscret}>
                Créer un compte organisateur
              </Link>
            </p>
          </form>
        </Panneau>
      </div>
    </Cadre>
  );
}

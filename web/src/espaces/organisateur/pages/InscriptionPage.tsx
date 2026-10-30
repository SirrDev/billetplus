import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN, sInscrire, TEXTE_MAX, useSession } from '@/commun/auth';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, Cadre, ChampTexte, Panneau } from '@/ui';
import s from './organisateur.module.css';

/** Le client ne choisit jamais son rôle : /register crée toujours un ORGANISATEUR. */
export function InscriptionPage() {
  const { session, connexion } = useSession();
  const navigate = useNavigate();
  const [nom, setNom] = useState('');
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreurMotDePasse, setErreurMotDePasse] = useState<string>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  if (session.etat === 'connecte' && session.utilisateur.role === 'ORGANISATEUR') {
    return <Navigate to="/organisateur" replace />;
  }

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    // Longueur en octets UTF-8 : BCrypt tronque au-delà de 72 octets.
    const octets = new TextEncoder().encode(motDePasse).length;
    if (motDePasse.length < MOT_DE_PASSE_MIN || octets > MOT_DE_PASSE_MAX) {
      setErreurMotDePasse(`Entre ${MOT_DE_PASSE_MIN} et ${MOT_DE_PASSE_MAX} caractères.`);
      return;
    }
    setErreurMotDePasse(undefined);
    setEnvoi(true);
    try {
      await sInscrire(email.trim(), motDePasse, nom.trim());
      await connexion(email.trim(), motDePasse);
      navigate('/organisateur', { replace: true });
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Cadre sansNav fondGris accueil="/organisateur">
      <div className={s.connexion}>
        <Panneau>
          <form className={s.formulaire} onSubmit={valider}>
            <div>
              <p className={s.surtitre}>Espace organisateur</p>
              <h1 className={s.titrePage}>Créer un compte</h1>
            </div>
            <ChampTexte libelle="Nom ou structure" valeur={nom} onChange={setNom} requis maxLength={TEXTE_MAX} autoComplete="organization" />
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
              minLength={MOT_DE_PASSE_MIN}
              maxLength={MOT_DE_PASSE_MAX}
              autoComplete="new-password"
              aide={`De ${MOT_DE_PASSE_MIN} à ${MOT_DE_PASSE_MAX} caractères.`}
              erreur={erreurMotDePasse}
            />
            {erreur && (
              <Avis ton="danger">
                <p>{erreur}</p>
              </Avis>
            )}
            <Bouton type="submit" grand pleineLargeur disabled={envoi}>
              {envoi ? 'Création du compte…' : 'Créer mon compte'}
            </Bouton>
            <p className={s.texte}>
              Déjà inscrit ?{' '}
              <Link to="/organisateur/connexion" className={s.lienDiscret}>
                Se connecter
              </Link>
            </p>
          </form>
        </Panneau>
      </div>
    </Cadre>
  );
}

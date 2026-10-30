import { useState, type FormEvent, type ReactNode } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Notice, Screen, TextField, TopBar } from '@/design-system';
import { ApiError } from '../api/httpClient';
import m from '../ui/miseEnPage.module.css';
import { useAuth, type Role } from './AuthContext';

interface ConnexionPageProps {
  titre: string;
  sousTitre: string;
  /** Page ouverte après la connexion, sauf si une page protégée était demandée (?retour=). */
  accueil: string;
  roles: ReadonlyArray<Role>;
  /** Liens complémentaires (inscription...). */
  pied?: ReactNode;
}

/** Connexion par email et mot de passe (POST /api/auth/login), commune à l'organisateur et au scanner. */
export function ConnexionPage({ titre, sousTitre, accueil, roles, pied }: ConnexionPageProps) {
  const { utilisateur, connecter } = useAuth();
  const naviguer = useNavigate();
  const [parametres] = useSearchParams();
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  // Le retour n'accepte qu'un chemin interne : jamais une adresse externe (redirection ouverte).
  const retourDemande = parametres.get('retour');
  const retour =
    retourDemande && retourDemande.startsWith('/') && !retourDemande.startsWith('//') ? retourDemande : accueil;

  if (utilisateur && roles.includes(utilisateur.role)) {
    return <Navigate to={retour} replace />;
  }

  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    setEnvoi(true);
    setErreur(null);
    try {
      const moi = await connecter(email.trim(), motDePasse);
      if (!roles.includes(moi.role)) {
        setErreur(`Ce compte (${moi.role.toLowerCase()}) n'a pas accès à cet espace.`);
        setEnvoi(false);
        return;
      }
      naviguer(retour, { replace: true });
    } catch (err) {
      setErreur(
        err instanceof ApiError && err.status === 401
          ? 'Email ou mot de passe incorrect.'
          : err instanceof ApiError && err.status === 429
            ? 'Trop de tentatives : patientez une minute.'
            : 'Connexion impossible. Vérifiez votre réseau et réessayez.',
      );
      setEnvoi(false);
    }
  };

  return (
    <Screen>
      <TopBar title={titre} subtitle={sousTitre} backTo="/" backLabel="Retour à l'accueil" />
      <form className={m.formulaire} onSubmit={soumettre} noValidate>
        <TextField
          label="Email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          maxLength={255}
        />
        <TextField
          label="Mot de passe"
          type="password"
          autoComplete="current-password"
          value={motDePasse}
          onChange={(e) => setMotDePasse(e.target.value)}
          required
          maxLength={72}
        />
        {erreur ? (
          <Notice tone="danger" role="alert" icon="warning">
            {erreur}
          </Notice>
        ) : null}
        <Button type="submit" fullWidth loading={envoi} disabled={!email || !motDePasse}>
          Se connecter
        </Button>
        {pied}
      </form>
    </Screen>
  );
}

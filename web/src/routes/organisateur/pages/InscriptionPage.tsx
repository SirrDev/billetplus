import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Notice, Screen, TextField, TopBar } from '@/design-system';
import { useAuth } from '@/shared/auth/AuthContext';
import m from '@/shared/ui/miseEnPage.module.css';
import { inscrire } from '../api';
import { messageErreur } from '../outils';

/** Création d'un compte organisateur (POST /api/auth/register), puis connexion directe. */
export function InscriptionPage() {
  const { connecter } = useAuth();
  const naviguer = useNavigate();
  const [nomComplet, setNomComplet] = useState('');
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  // Mêmes bornes que le backend (RegisterRequest) : 8 à 72 caractères (limite de BCrypt).
  const motDePasseValide = motDePasse.length >= 8 && motDePasse.length <= 72;

  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    if (!motDePasseValide) return;
    setEnvoi(true);
    setErreur(null);
    try {
      await inscrire(email.trim(), motDePasse, nomComplet.trim());
      await connecter(email.trim(), motDePasse);
      naviguer('/organisateur', { replace: true });
    } catch (err) {
      setErreur(messageErreur(err));
      setEnvoi(false);
    }
  };

  return (
    <Screen>
      <div className={m.carteCentree}>
        <TopBar
          title="Créer un compte organisateur"
          backTo="/organisateur/connexion"
          backLabel="Retour à la connexion"
        />
        <form className={`${m.formulaire} ${m.apresTitre}`} onSubmit={soumettre} noValidate>
          <TextField
            label="Nom complet ou structure"
            value={nomComplet}
            onChange={(e) => setNomComplet(e.target.value)}
            required
            maxLength={255}
            autoComplete="name"
          />
          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            maxLength={255}
            autoComplete="email"
          />
          <TextField
            label="Mot de passe"
            type="password"
            hint="8 caractères au minimum."
            error={motDePasse && !motDePasseValide ? 'Entre 8 et 72 caractères.' : undefined}
            value={motDePasse}
            onChange={(e) => setMotDePasse(e.target.value)}
            required
            maxLength={72}
            autoComplete="new-password"
          />
          {erreur ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreur}
            </Notice>
          ) : null}
          <Button
            type="submit"
            fullWidth
            loading={envoi}
            disabled={!nomComplet.trim() || !email.trim() || !motDePasseValide}
          >
            Créer mon compte
          </Button>
          <p className={`${m.texte} ${m.muet}`}>
            Déjà inscrit ? <Link to="/organisateur/connexion">Se connecter</Link>
          </p>
        </form>
      </div>
    </Screen>
  );
}

import { useState, type FormEvent } from 'react';
import { Avis, Bouton, ChampTexte } from '@/ui';
import { changerMotDePasse, MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN } from './auth';
import { ApiError, messageErreur } from './http';

interface Erreurs {
  nouveau?: string;
  confirmation?: string;
}

/**
 * Changement du mot de passe du compte connecté (organisateur, agent, administrateur).
 * Mêmes bornes que l'inscription. Le backend ne révoque pas les jetons déjà émis :
 * l'écran le dit, sans promettre une déconnexion des autres appareils.
 */
export function FormulaireMotDePasse() {
  const [actuel, setActuel] = useState('');
  const [nouveau, setNouveau] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [erreurs, setErreurs] = useState<Erreurs>({});
  const [retour, setRetour] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setRetour(null);
    const nouvelles: Erreurs = {};
    if (nouveau.length < MOT_DE_PASSE_MIN || nouveau.length > MOT_DE_PASSE_MAX) {
      nouvelles.nouveau = `Entre ${MOT_DE_PASSE_MIN} et ${MOT_DE_PASSE_MAX} caractères.`;
    } else if (nouveau === actuel) {
      nouvelles.nouveau = "Choisissez un mot de passe différent de l'actuel.";
    }
    if (confirmation !== nouveau) nouvelles.confirmation = 'Les deux saisies ne correspondent pas.';
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0) return;

    setEnvoi(true);
    try {
      await changerMotDePasse(actuel, nouveau);
      setActuel('');
      setNouveau('');
      setConfirmation('');
      setRetour({
        ton: 'succes',
        texte:
          "Mot de passe changé : utilisez-le à votre prochaine connexion. Les appareils déjà connectés le restent jusqu'à la fin de leur session.",
      });
    } catch (e2) {
      setRetour({
        ton: 'danger',
        texte:
          e2 instanceof ApiError && e2.status === 429
            ? "Trop d'essais : réessayez dans un quart d'heure."
            : messageErreur(e2),
      });
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <form onSubmit={valider} style={{ display: 'grid', gap: 16 }}>
      <ChampTexte
        libelle="Mot de passe actuel"
        type="password"
        valeur={actuel}
        onChange={setActuel}
        requis
        maxLength={255}
        autoComplete="current-password"
      />
      <ChampTexte
        libelle="Nouveau mot de passe"
        type="password"
        valeur={nouveau}
        onChange={setNouveau}
        requis
        maxLength={MOT_DE_PASSE_MAX}
        autoComplete="new-password"
        aide={`${MOT_DE_PASSE_MIN} caractères au moins.`}
        erreur={erreurs.nouveau}
      />
      <ChampTexte
        libelle="Confirmer le nouveau mot de passe"
        type="password"
        valeur={confirmation}
        onChange={setConfirmation}
        requis
        maxLength={MOT_DE_PASSE_MAX}
        autoComplete="new-password"
        erreur={erreurs.confirmation}
      />
      {retour && (
        <Avis ton={retour.ton}>
          <p>{retour.texte}</p>
        </Avis>
      )}
      <div>
        <Bouton type="submit" disabled={envoi}>
          {envoi ? 'Enregistrement…' : 'Changer le mot de passe'}
        </Bouton>
      </div>
    </form>
  );
}

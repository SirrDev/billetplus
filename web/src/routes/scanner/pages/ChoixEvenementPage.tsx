import { useEffect, useState } from 'react';
import { Button, EmptyState, ListRow, Notice, Screen, TextField, TopBar, formatEventDate } from '@/design-system';
import { useAuth } from '@/shared/auth/AuthContext';
import m from '@/shared/ui/miseEnPage.module.css';
import { evenementsAControler, type EvenementAControler } from '../api';

/**
 * Choix de l'événement à contrôler. Le backend n'expose pas les affectations d'un
 * agent : la liste montre les événements publics, et le serveur refuse le scan à un
 * agent qui n'est pas affecté (message clair sur l'écran de contrôle).
 */
export function ChoixEvenementPage() {
  const { utilisateur, deconnecter } = useAuth();
  const [recherche, setRecherche] = useState('');
  const [evenements, setEvenements] = useState<EvenementAControler[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    const minuterie = setTimeout(() => {
      setErreur(null);
      evenementsAControler(recherche)
        .then((page) => setEvenements(page.contenu))
        .catch(() => setErreur('Liste indisponible. Vérifiez la connexion.'));
    }, 300);
    return () => clearTimeout(minuterie);
  }, [recherche]);

  return (
    <Screen>
      <TopBar
        title="Contrôle d'entrée"
        subtitle={utilisateur ? `Connecté : ${utilisateur.nomComplet}` : undefined}
        backTo="/"
        backLabel="Retour à l'accueil"
        trailing={
          <Button variant="ghost" size="md" icon="logout" onClick={deconnecter}>
            Déconnexion
          </Button>
        }
      />
      <div className={m.pile}>
        <TextField
          label="Événement à contrôler"
          type="search"
          placeholder="Rechercher par titre"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
          maxLength={100}
        />
        {erreur ? (
          <Notice tone="danger" role="alert" icon="wifiOff">
            {erreur}
          </Notice>
        ) : null}
        {evenements === null && !erreur ? <Notice icon="clock">Chargement…</Notice> : null}
        {evenements?.length === 0 ? (
          <EmptyState icon="calendar" title="Aucun événement" description="Aucun événement publié ne correspond." />
        ) : null}
        <div className={m.grille}>
          {evenements?.map((evenement) => (
            <ListRow
              key={evenement.id}
              icon="scan"
              label={evenement.titre}
              value={`${formatEventDate(new Date(evenement.debuteLe))} · ${evenement.venueNom}`}
              to={`/scanner/evenements/${evenement.id}`}
            />
          ))}
        </div>
      </div>
    </Screen>
  );
}

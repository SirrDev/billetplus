import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ButtonLink, EmptyState, Notice, Screen, TopBar, formatEventDate } from '@/design-system';
import { useAuth } from '@/shared/auth/AuthContext';
import m from '@/shared/ui/miseEnPage.module.css';
import { mesEvenements, type EvenementOrganisateur } from '../api';
import { BadgeStatut, NavigationOrganisateur } from '../composants';
import { messageErreur } from '../outils';
import s from './organisateur.module.css';

/** Accueil de l'organisateur : ses événements, du plus proche au plus lointain (GET /api/events/mine). */
export function MesEvenementsPage() {
  const { utilisateur, deconnecter } = useAuth();
  const [evenements, setEvenements] = useState<EvenementOrganisateur[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    mesEvenements()
      .then((liste) => setEvenements([...liste].sort((a, b) => a.debuteLe.localeCompare(b.debuteLe))))
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, []);

  return (
    <Screen withNav>
      <NavigationOrganisateur />
      <TopBar
        title="Mes événements"
        large
        subtitle={utilisateur?.nomComplet}
        trailing={
          <Button variant="ghost" size="md" icon="logout" onClick={deconnecter}>
            Déconnexion
          </Button>
        }
      />
      <div className={`${m.ligneActions} ${m.apresTitre}`}>
        <ButtonLink to="/organisateur/evenements/nouveau" icon="plus" size="md">
          Créer un événement
        </ButtonLink>
        <ButtonLink to="/organisateur/agents" variant="secondary" icon="user" size="md">
          Mes agents
        </ButtonLink>
      </div>

      {erreur ? (
        <Notice tone="danger" role="alert" icon="warning">
          {erreur}
        </Notice>
      ) : null}
      {evenements === null && !erreur ? <Notice icon="clock">Chargement…</Notice> : null}
      {evenements?.length === 0 ? (
        <EmptyState
          icon="calendar"
          title="Aucun événement"
          description="Créez votre premier événement, ajoutez ses catégories de billets, puis publiez-le pour ouvrir la vente."
          actions={<ButtonLink to="/organisateur/evenements/nouveau">Créer un événement</ButtonLink>}
        />
      ) : null}

      <div className={m.apresTitre}>
        <ul className={`${m.grille} ${s.liste}`} aria-label="Mes événements">
          {evenements?.map((evenement) => (
            <li key={evenement.id}>
              <Link to={`/organisateur/evenements/${evenement.id}`} className={s.carte}>
                <BadgeStatut statut={evenement.status} />
                <span className={s.carteTitre}>{evenement.titre}</span>
                <span className={m.muet}>
                  {formatEventDate(new Date(evenement.debuteLe))} · {evenement.venueNom}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </Screen>
  );
}

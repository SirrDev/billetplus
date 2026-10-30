import { useEffect, useState } from 'react';
import {
  Button,
  EmptyState,
  EventFeaturedCard,
  EventRow,
  Notice,
  Screen,
  TextField,
  TopBar,
  buyerNavItems,
} from '@/design-system';
import { NavigationSite } from '@/shared/ui/EnTete';
import m from '@/shared/ui/miseEnPage.module.css';
import { rechercherEvenements, type EvenementPublic } from '../api';
import { messageErreur, versResume } from '../affichage';

const TAILLE_PAGE = 20;
/** Attente après la dernière frappe avant de chercher : une requête par mot, pas par lettre. */
const DELAI_RECHERCHE_MS = 300;

/** Écran « Découvrir » : événements publiés, à venir ou en cours, et recherche par titre (GET /api/events). */
export function DecouvrirPage() {
  const [saisie, setSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
  const [evenements, setEvenements] = useState<EvenementPublic[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    const minuterie = setTimeout(() => setRecherche(saisie), DELAI_RECHERCHE_MS);
    return () => clearTimeout(minuterie);
  }, [saisie]);

  // Nouvelle recherche : on repart de la première page.
  useEffect(() => {
    let annule = false;
    setChargement(true);
    setErreur(null);
    rechercherEvenements(recherche, 0, TAILLE_PAGE)
      .then((resultat) => {
        if (annule) return;
        setEvenements(resultat.contenu);
        setPage(0);
        setPages(resultat.pages);
      })
      .catch((e: unknown) => !annule && setErreur(messageErreur(e)))
      .finally(() => !annule && setChargement(false));
    return () => {
      annule = true;
    };
  }, [recherche, essai]);

  const voirPlus = () => {
    setChargement(true);
    rechercherEvenements(recherche, page + 1, TAILLE_PAGE)
      .then((resultat) => {
        setEvenements((deja) => [...deja, ...resultat.contenu]);
        setPage(resultat.page);
        setPages(resultat.pages);
      })
      .catch((e: unknown) => setErreur(messageErreur(e)))
      .finally(() => setChargement(false));
  };

  const [alaffiche, ...suivants] = evenements;
  const termine = !chargement && !erreur;

  return (
    <Screen withNav>
      <NavigationSite liens={buyerNavItems} libelle="Navigation principale" />
      <div className={m.pile}>
        <TopBar title="Découvrir" large subtitle="Concerts, matchs, festivals à Dakar" />
        <TextField
          label="Rechercher un événement"
          type="search"
          placeholder="Titre, artiste…"
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          maxLength={100}
        />

        {erreur ? (
          <Notice tone="danger" role="alert" icon="warning">
            {erreur}{' '}
            <Button variant="ghost" size="md" onClick={() => setEssai((n) => n + 1)}>
              Réessayer
            </Button>
          </Notice>
        ) : null}

        {termine && evenements.length === 0 ? (
          <EmptyState
            icon="search"
            title={recherche ? 'Aucun résultat' : 'Aucun événement pour le moment'}
            description={
              recherche
                ? `Aucun événement à venir ne correspond à « ${recherche} ».`
                : 'Les prochains événements apparaîtront ici dès leur publication.'
            }
          />
        ) : null}

        <div className={m.grille}>
          {alaffiche && !recherche ? (
            <div className={m.pleineLargeur}>
              <EventFeaturedCard event={versResume(alaffiche)} to={`/evenements/${alaffiche.id}`} />
            </div>
          ) : null}
          {(recherche ? evenements : suivants).map((evenement) => (
            <EventRow
              key={evenement.id}
              event={versResume(evenement)}
              to={`/evenements/${evenement.id}`}
              highlight={recherche}
            />
          ))}
        </div>

        {chargement ? <Notice icon="clock">Chargement des événements…</Notice> : null}
        {termine && page + 1 < pages ? (
          <Button variant="secondary" fullWidth onClick={voirPlus}>
            Voir plus d'événements
          </Button>
        ) : null}
      </div>
    </Screen>
  );
}

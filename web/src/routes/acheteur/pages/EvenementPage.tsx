import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Badge,
  ButtonLink,
  EmptyState,
  Notice,
  Poster,
  Screen,
  StickyActionBar,
  TopBar,
  formatEventDate,
  formatFcfa,
  buyerNavItems,
} from '@/design-system';
import { ApiError } from '@/shared/api/httpClient';
import { lireCategories, lireEvenement, type CategorieBillet, type Evenement } from '../api';
import { afficheDe, messageErreur } from '../affichage';
import { NavigationSite } from '@/shared/ui/EnTete';
import m from '@/shared/ui/miseEnPage.module.css';

/** Fiche d'un événement : date à l'heure de Dakar, lieu, description, catégories et prix (lecture). */
export function EvenementPage() {
  const { id = '' } = useParams();
  const [evenement, setEvenement] = useState<Evenement | null>(null);
  const [categories, setCategories] = useState<CategorieBillet[]>([]);
  const [erreur, setErreur] = useState<{ introuvable: boolean; message: string } | null>(null);

  useEffect(() => {
    let annule = false;
    Promise.all([lireEvenement(id), lireCategories(id)])
      .then(([e, c]) => {
        if (annule) return;
        setEvenement(e);
        setCategories(c);
      })
      .catch((e: unknown) => {
        if (annule) return;
        // Un brouillon répond 404 comme un identifiant inconnu : même message ici.
        setErreur({ introuvable: e instanceof ApiError && e.status === 404, message: messageErreur(e) });
      });
    return () => {
      annule = true;
    };
  }, [id]);

  if (erreur) {
    return (
      <Screen>
        <NavigationSite liens={buyerNavItems} libelle="Navigation principale" barreMobile={false} />
        <TopBar title="Événement" backTo="/" backLabel="Retour à la liste" />
        <EmptyState
          icon={erreur.introuvable ? 'search' : 'wifiOff'}
          title={erreur.introuvable ? 'Événement introuvable' : 'Chargement impossible'}
          description={erreur.introuvable ? "Cet événement n'existe pas ou n'est plus en ligne." : erreur.message}
          actions={<ButtonLink to="/">Voir les événements</ButtonLink>}
        />
      </Screen>
    );
  }
  if (!evenement) {
    return (
      <Screen>
        <NavigationSite liens={buyerNavItems} libelle="Navigation principale" barreMobile={false} />
        <TopBar title="Événement" backTo="/" backLabel="Retour à la liste" />
        <Notice icon="clock">Chargement…</Notice>
      </Screen>
    );
  }

  const enVente = evenement.status === 'PUBLIE' && categories.some((c) => c.disponible);
  return (
    <Screen withActionBar>
      <NavigationSite liens={buyerNavItems} libelle="Navigation principale" barreMobile={false} />
      <TopBar title={evenement.titre} backTo="/" backLabel="Retour à la liste" />
      <div className={`${m.deuxColonnes} ${m.apresTitre}`}>
        <div className={m.pile}>
          <div className={m.affiche}>
            <Poster variant={afficheDe(evenement.id)} />
          </div>
          <p className={m.texte}>
            <strong>{formatEventDate(new Date(evenement.debuteLe))}</strong>
            <br />
            {evenement.venueNom}
          </p>
          {evenement.status === 'ANNULE' ? (
            <Notice tone="danger" role="alert" icon="warning">
              Cet événement est annulé.
            </Notice>
          ) : null}
          {evenement.description ? <p className={m.texte}>{evenement.description}</p> : null}
        </div>

        <section className={`${m.pile} ${m.lateral}`} aria-labelledby="titre-billets">
          <h2 id="titre-billets" className={m.titreSection}>
            Billets
          </h2>
          {categories.length === 0 ? <Notice icon="ticket">La billetterie n'est pas encore ouverte.</Notice> : null}
          <ul aria-label="Catégories de billets" className={m.listeSimple}>
            {categories.map((categorie) => (
              <li key={categorie.id}>
                <span>{categorie.nom}</span>
                <span>
                  {categorie.disponible ? formatFcfa(categorie.prixFcfa) : <Badge tone="neutral">Épuisé</Badge>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <StickyActionBar>
        {enVente ? (
          <ButtonLink to={`/evenements/${evenement.id}/billets`} fullWidth>
            Choisir mes billets
          </ButtonLink>
        ) : (
          <Notice icon="lock">Aucun billet en vente pour cet événement.</Notice>
        )}
      </StickyActionBar>
    </Screen>
  );
}

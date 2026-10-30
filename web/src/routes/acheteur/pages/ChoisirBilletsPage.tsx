import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  ButtonLink,
  EmptyState,
  Notice,
  PhoneField,
  Screen,
  StickyActionBar,
  TicketCategoryRow,
  TopBar,
  isValidSnMobile,
} from '@/design-system';
import { creerCommande, lireCategories, lireEvenement, type CategorieBillet, type Evenement } from '../api';
import { enregistrerCommande } from '../commandesLocales';
import { messageErreur } from '../affichage';
import m from '@/shared/ui/miseEnPage.module.css';

/** Plafond du backend : 20 places par catégorie et par commande (Order.QUANTITE_MAX_PAR_CATEGORIE). */
const MAX_PAR_CATEGORIE = 20;

/**
 * Choix des billets et du numéro, puis création de la commande (POST /api/orders),
 * qui réserve les places pendant le délai de paiement. Aucun montant n'est calculé
 * ici : le total est celui que renvoie le serveur, affiché à l'étape suivante.
 */
export function ChoisirBilletsPage() {
  const { id = '' } = useParams();
  const naviguer = useNavigate();
  const [evenement, setEvenement] = useState<Evenement | null>(null);
  const [categories, setCategories] = useState<CategorieBillet[]>([]);
  const [quantites, setQuantites] = useState<Record<string, number>>({});
  const [telephone, setTelephone] = useState('');
  const [telephoneTouche, setTelephoneTouche] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [erreurChargement, setErreurChargement] = useState<string | null>(null);

  useEffect(() => {
    let annule = false;
    Promise.all([lireEvenement(id), lireCategories(id)])
      .then(([e, c]) => {
        if (annule) return;
        setEvenement(e);
        setCategories(c);
      })
      .catch((e: unknown) => !annule && setErreurChargement(messageErreur(e)));
    return () => {
      annule = true;
    };
  }, [id]);

  const lignes = Object.entries(quantites)
    .filter(([, quantite]) => quantite > 0)
    .map(([categoryId, quantite]) => ({ categoryId, quantite }));
  const nombreDeBillets = lignes.reduce((total, l) => total + l.quantite, 0);
  const telephoneValide = isValidSnMobile(telephone);

  const reserver = async (evenementSoumis: FormEvent) => {
    evenementSoumis.preventDefault();
    setTelephoneTouche(true);
    if (!evenement || nombreDeBillets === 0 || !telephoneValide) return;
    setEnvoi(true);
    setErreur(null);
    try {
      const creee = await creerCommande(evenement.id, telephone, lignes);
      // Le jeton n'est remis qu'une fois : il est enregistré AVANT de quitter la page.
      await enregistrerCommande({
        id: creee.commande.id,
        jeton: creee.jetonAcces,
        eventId: evenement.id,
        titre: evenement.titre,
        debuteLe: evenement.debuteLe,
        venueNom: evenement.venueNom,
        creeLe: creee.commande.creeLe,
      });
      naviguer(`/commandes/${creee.commande.id}`);
    } catch (e) {
      setErreur(messageErreur(e));
      setEnvoi(false);
    }
  };

  if (erreurChargement) {
    return (
      <Screen>
        <TopBar title="Vos billets" backTo={`/evenements/${id}`} backLabel="Retour à l'événement" />
        <EmptyState
          icon="wifiOff"
          title="Chargement impossible"
          description={erreurChargement}
          actions={<ButtonLink to={`/evenements/${id}`}>Retour à l'événement</ButtonLink>}
        />
      </Screen>
    );
  }

  return (
    <Screen withActionBar>
      <TopBar
        title="Vos billets"
        subtitle={evenement?.titre}
        backTo={`/evenements/${id}`}
        backLabel="Retour à l'événement"
      />
      <form onSubmit={reserver} noValidate className={m.deuxColonnes}>
        <div className={m.pile}>
          {categories.map((categorie) => (
            <TicketCategoryRow
              key={categorie.id}
              name={categorie.nom}
              priceFcfa={categorie.prixFcfa}
              remaining={categorie.disponible ? undefined : 0}
              quantity={quantites[categorie.id] ?? 0}
              maxPerOrder={MAX_PAR_CATEGORIE}
              onQuantityChange={(n) => setQuantites((q) => ({ ...q, [categorie.id]: n }))}
            />
          ))}
        </div>

        <div className={`${m.pile} ${m.lateral}`}>
          <PhoneField
            value={telephone}
            onChange={(chiffres) => setTelephone(chiffres)}
            label="Votre numéro de téléphone"
            hint="Le numéro Wave ou Orange Money qui paiera."
            error={
              telephoneTouche && telephone && !telephoneValide
                ? 'Numéro de mobile sénégalais invalide (77, 78, 76, 75, 70…)'
                : undefined
            }
          />
          <Notice icon="clock">Vos places sont réservées quelques minutes, le temps de payer.</Notice>
          {erreur ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreur}
            </Notice>
          ) : null}
        </div>

        <StickyActionBar>
          <Button type="submit" fullWidth loading={envoi} disabled={nombreDeBillets === 0 || !evenement}>
            {nombreDeBillets === 0
              ? 'Choisissez vos billets'
              : `Réserver ${nombreDeBillets} billet${nombreDeBillets > 1 ? 's' : ''}`}
          </Button>
        </StickyActionBar>
      </form>
    </Screen>
  );
}

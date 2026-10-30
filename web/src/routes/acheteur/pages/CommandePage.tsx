import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button,
  ButtonLink,
  CountdownNotice,
  EmptyState,
  Notice,
  PriceSummary,
  RadioCardGroup,
  Screen,
  StickyActionBar,
  SuccessHero,
  TicketPass,
  TopBar,
  formatEventDate,
  formatFcfa,
} from '@/design-system';
import {
  lireBillets,
  lireCategories,
  lireCommande,
  ouvrirPaiement,
  type Billet,
  type Commande,
  type MoyenPaiement,
} from '../api';
import { lireCommandeLocale, type CommandeLocale } from '../commandesLocales';
import { codeBillet, messageErreur } from '../affichage';
import m from '@/shared/ui/miseEnPage.module.css';

/** Pendant l'attente du paiement : relecture régulière, pour l'acheteur qui revient de Wave ou d'Orange Money. */
const INTERVALLE_RELECTURE_MS = 5000;

const MOYENS = [
  { value: 'WAVE', label: 'Wave', icon: 'card' },
  { value: 'ORANGE_MONEY', label: 'Orange Money', icon: 'card' },
] as const;

type Etat =
  | { type: 'chargement' }
  | { type: 'absente' }
  | { type: 'erreur'; message: string }
  | { type: 'prete'; locale: CommandeLocale; commande: Commande };

/**
 * Une commande de l'acheteur, lue avec le jeton gardé sur l'appareil (ADR 0006).
 * Le statut vient TOUJOURS du serveur, jamais d'une déduction côté client.
 */
export function CommandePage() {
  const { id = '' } = useParams();
  const [etat, setEtat] = useState<Etat>({ type: 'chargement' });
  const [nomsCategories, setNomsCategories] = useState<Record<string, string>>({});

  const relire = useCallback(async () => {
    const locale = await lireCommandeLocale(id);
    if (!locale) {
      setEtat({ type: 'absente' });
      return;
    }
    try {
      const commande = await lireCommande(id, locale.jeton);
      setEtat({ type: 'prete', locale, commande });
    } catch (e) {
      setEtat({ type: 'erreur', message: messageErreur(e) });
    }
  }, [id]);

  useEffect(() => {
    void relire();
  }, [relire]);

  const enAttente = etat.type === 'prete' && etat.commande.status === 'EN_ATTENTE_PAIEMENT';
  useEffect(() => {
    if (!enAttente) return;
    const minuterie = setInterval(() => void relire(), INTERVALLE_RELECTURE_MS);
    return () => clearInterval(minuterie);
  }, [enAttente, relire]);

  // Noms des catégories, pour le détail du montant (les lignes de la commande n'ont que leur identifiant).
  const eventId = etat.type === 'prete' ? etat.commande.eventId : null;
  useEffect(() => {
    if (!eventId) return;
    lireCategories(eventId)
      .then((categories) => setNomsCategories(Object.fromEntries(categories.map((c) => [c.id, c.nom]))))
      .catch(() => undefined); // Sans les noms, le détail affiche « Billets » : rien de bloquant.
  }, [eventId]);

  if (etat.type === 'chargement') {
    return (
      <Screen>
        <TopBar title="Ma commande" backTo="/billets" backLabel="Retour à mes billets" />
        <Notice icon="clock">Chargement de la commande…</Notice>
      </Screen>
    );
  }
  if (etat.type === 'absente') {
    return (
      <Screen>
        <TopBar title="Ma commande" backTo="/billets" backLabel="Retour à mes billets" />
        <EmptyState
          icon="lock"
          title="Commande introuvable sur cet appareil"
          description="Vos commandes sont enregistrées sur le téléphone qui les a passées. Ouvrez Billet+ sur cet appareil."
          actions={<ButtonLink to="/">Voir les événements</ButtonLink>}
        />
      </Screen>
    );
  }
  if (etat.type === 'erreur') {
    return (
      <Screen>
        <TopBar title="Ma commande" backTo="/billets" backLabel="Retour à mes billets" />
        <EmptyState
          icon="wifiOff"
          title="Chargement impossible"
          description={etat.message}
          actions={<Button onClick={() => void relire()}>Réessayer</Button>}
        />
      </Screen>
    );
  }

  const { locale, commande } = etat;
  const titre = (
    <TopBar title="Ma commande" subtitle={locale.titre} backTo="/billets" backLabel="Retour à mes billets" />
  );
  const lignes = commande.lignes.map((l) => ({
    label: `${l.quantite} × ${nomsCategories[l.categoryId] ?? 'Billets'}`,
    amountFcfa: l.sousTotalFcfa,
  }));

  switch (commande.status) {
    case 'EN_ATTENTE_PAIEMENT':
      if (new Date(commande.expireLe).getTime() <= Date.now()) {
        return <CommandeExpiree titre={titre} locale={locale} />;
      }
      return (
        <Paiement titre={titre} locale={locale} commande={commande} lignes={lignes} onExpire={() => void relire()} />
      );
    case 'PAYEE':
      return <BilletsPayes locale={locale} commande={commande} lignes={lignes} />;
    case 'PAYEE_SANS_BILLET':
      return (
        <Screen>
          {titre}
          <EmptyState
            icon="warning"
            tone="warning"
            title="Paiement reçu, billets indisponibles"
            description={`Nous avons bien reçu ${formatFcfa(commande.montantTotalFcfa)}, mais les billets n'ont pas pu être émis (vente fermée ou places épuisées). Vous serez remboursé sur le numéro qui a payé.`}
            actions={<ButtonLink to="/">Voir les événements</ButtonLink>}
          />
        </Screen>
      );
    case 'EXPIREE':
    case 'ANNULEE':
      return <CommandeExpiree titre={titre} locale={locale} />;
  }
}

function CommandeExpiree({ titre, locale }: { titre: JSX.Element; locale: CommandeLocale }) {
  return (
    <Screen>
      {titre}
      <EmptyState
        icon="clock"
        tone="warning"
        title="Commande expirée"
        description="Le délai de paiement est dépassé et vos places ont été remises en vente. Vous pouvez passer une nouvelle commande."
        actions={<ButtonLink to={`/evenements/${locale.eventId}`}>Revenir à l'événement</ButtonLink>}
      />
    </Screen>
  );
}

interface PaiementProps {
  titre: JSX.Element;
  locale: CommandeLocale;
  commande: Commande;
  lignes: ReadonlyArray<{ label: string; amountFcfa: number }>;
  onExpire: () => void;
}

function Paiement({ titre, locale, commande, lignes, onExpire }: PaiementProps) {
  const [moyen, setMoyen] = useState<MoyenPaiement>('WAVE');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const payer = async () => {
    setEnvoi(true);
    setErreur(null);
    try {
      const lien = await ouvrirPaiement(commande.id, locale.jeton, moyen);
      // Le paiement se fait chez l'opérateur ; la passerelle ramène ensuite l'acheteur ici.
      window.location.assign(lien.urlPaiement);
    } catch (e) {
      setErreur(messageErreur(e));
      setEnvoi(false);
    }
  };

  return (
    <Screen withActionBar>
      {titre}
      <div className={m.deuxColonnes}>
        <div className={m.pile}>
          <CountdownNotice deadline={new Date(commande.expireLe)} onExpire={onExpire} />
          <PriceSummary lines={lignes} totalFcfa={commande.montantTotalFcfa} />
        </div>
        <div className={`${m.pile} ${m.lateral}`}>
          <RadioCardGroup legend="Moyen de paiement" name="moyen" value={moyen} onChange={setMoyen} options={MOYENS} />
          <Notice icon="shield">
            Vous allez être redirigé vers {moyen === 'WAVE' ? 'Wave' : 'Orange Money'} pour confirmer le paiement.
            Revenez ensuite sur cette page : vos billets s'afficheront dès la confirmation.
          </Notice>
          {erreur ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreur}
            </Notice>
          ) : null}
        </div>
      </div>
      <StickyActionBar>
        <Button fullWidth loading={envoi} onClick={() => void payer()}>
          Payer {formatFcfa(commande.montantTotalFcfa)}
        </Button>
      </StickyActionBar>
    </Screen>
  );
}

interface BilletsPayesProps {
  locale: CommandeLocale;
  commande: Commande;
  lignes: ReadonlyArray<{ label: string; amountFcfa: number }>;
}

function BilletsPayes({ locale, commande, lignes }: BilletsPayesProps) {
  const [billets, setBillets] = useState<Billet[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    lireBillets(commande.id, locale.jeton)
      .then(setBillets)
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, [commande.id, locale.jeton]);

  const date = formatEventDate(new Date(locale.debuteLe));
  return (
    // Le bandeau ouvre l'écran (il déborde jusqu'au bord, comme dans le prototype) :
    // pas de barre de titre au-dessus, qu'il recouvrirait.
    <Screen>
      <SuccessHero
        title="Paiement confirmé !"
        description={`Présentez ces QR codes à l'entrée. Ils restent sur cet appareil.`}
      />
      <PriceSummary lines={lignes} totalFcfa={commande.montantTotalFcfa} totalLabel="Payé" />
      {erreur ? (
        <Notice tone="danger" role="alert" icon="warning">
          {erreur}
        </Notice>
      ) : null}
      {billets === null && !erreur ? <Notice icon="clock">Chargement des billets…</Notice> : null}
      <div className={m.grille}>
        {billets?.map((billet) =>
          billet.statut === 'EMIS' && billet.qr ? (
            <TicketPass
              key={billet.id}
              eventTitle={locale.titre}
              categoryName={billet.categorie}
              dateLabel={date}
              venue={locale.venueNom}
              qrPayload={billet.qr}
              code={codeBillet(billet.id)}
              status="valid"
            />
          ) : (
            // Aucun QR n'est fourni pour un billet inactif : on n'en dessine pas.
            <Notice key={billet.id} tone={billet.statut === 'SCANNE' ? 'warning' : 'danger'} icon="ticket">
              Billet {billet.categorie} {codeBillet(billet.id)} :{' '}
              {billet.statut === 'SCANNE' ? 'déjà utilisé à l’entrée.' : 'annulé.'}
            </Notice>
          ),
        )}
      </div>
      <ButtonLink to="/billets" variant="secondary" icon="ticket" fullWidth>
        Mes billets
      </ButtonLink>
    </Screen>
  );
}

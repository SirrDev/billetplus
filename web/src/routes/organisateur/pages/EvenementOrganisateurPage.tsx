import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button,
  ButtonLink,
  DataTable,
  EmptyState,
  MetricCard,
  Notice,
  ProgressBar,
  Screen,
  SegmentedTabs,
  TextField,
  TopBar,
  formatEventDate,
  formatFcfa,
  tabPanelProps,
} from '@/design-system';
import { ApiError } from '@/shared/api/httpClient';
import m from '@/shared/ui/miseEnPage.module.css';
import {
  affecterAgent,
  annuler,
  creerCategorie,
  lireCategories,
  lireEvenement,
  lireTableauDeBord,
  mesAgents,
  publier,
  retirerAgent,
  type Agent,
  type CategorieOrganisateur,
  type EvenementOrganisateur,
  type LigneTableau,
  type TableauDeBord,
} from '../api';
import { BadgeStatut, NavigationOrganisateur } from '../composants';
import { lireMontant, messageErreur } from '../outils';
import s from './organisateur.module.css';

type Onglet = 'ventes' | 'billets' | 'agents';
const ONGLETS = [
  { id: 'ventes', label: 'Ventes' },
  { id: 'billets', label: 'Billets' },
  { id: 'agents', label: 'Agents' },
] as const;

const nombre = (n: number) => new Intl.NumberFormat('fr-FR').format(n);

/** Fiche d'un événement pour son organisateur : statut, publication, annulation, ventes, billets, agents. */
export function EvenementOrganisateurPage() {
  const { id = '' } = useParams();
  const [evenement, setEvenement] = useState<EvenementOrganisateur | null>(null);
  const [erreur, setErreur] = useState<{ introuvable: boolean; message: string } | null>(null);
  const [onglet, setOnglet] = useState<Onglet>('ventes');
  const [action, setAction] = useState<string | null>(null);
  const [confirmerAnnulation, setConfirmerAnnulation] = useState(false);
  const [erreurAction, setErreurAction] = useState<string | null>(null);

  useEffect(() => {
    lireEvenement(id)
      .then(setEvenement)
      .catch((e: unknown) =>
        setErreur({ introuvable: e instanceof ApiError && e.status === 404, message: messageErreur(e) }),
      );
  }, [id]);

  const changerStatut = async (operation: 'publier' | 'annuler') => {
    setAction(operation);
    setErreurAction(null);
    try {
      setEvenement(operation === 'publier' ? await publier(id) : await annuler(id));
      setConfirmerAnnulation(false);
    } catch (e) {
      setErreurAction(messageErreur(e));
    } finally {
      setAction(null);
    }
  };

  if (erreur) {
    return (
      <Screen withNav>
        <NavigationOrganisateur />
        <TopBar title="Événement" backTo="/organisateur" backLabel="Retour à mes événements" />
        <EmptyState
          icon={erreur.introuvable ? 'search' : 'wifiOff'}
          title={erreur.introuvable ? 'Événement introuvable' : 'Chargement impossible'}
          description={erreur.introuvable ? "Cet événement n'existe pas ou ne vous appartient pas." : erreur.message}
          actions={<ButtonLink to="/organisateur">Mes événements</ButtonLink>}
        />
      </Screen>
    );
  }
  if (!evenement) {
    return (
      <Screen withNav>
        <NavigationOrganisateur />
        <TopBar title="Événement" backTo="/organisateur" backLabel="Retour à mes événements" />
        <Notice icon="clock">Chargement…</Notice>
      </Screen>
    );
  }

  const modifiable = evenement.status === 'BROUILLON' || evenement.status === 'PUBLIE';
  return (
    <Screen withNav>
      <NavigationOrganisateur />
      <TopBar title={evenement.titre} backTo="/organisateur" backLabel="Retour à mes événements" />
      <div className={`${m.pile} ${m.apresTitre}`}>
        <div className={m.ligneActions}>
          <BadgeStatut statut={evenement.status} />
          <span className={m.muet}>
            {formatEventDate(new Date(evenement.debuteLe))} · {evenement.venueNom}
          </span>
        </div>

        <div className={m.ligneActions}>
          {evenement.status === 'BROUILLON' ? (
            <Button size="md" icon="send" loading={action === 'publier'} onClick={() => void changerStatut('publier')}>
              Publier et ouvrir la vente
            </Button>
          ) : null}
          {evenement.status === 'PUBLIE' ? (
            <ButtonLink to={`/evenements/${evenement.id}`} variant="secondary" size="md" icon="globe">
              Voir la page publique
            </ButtonLink>
          ) : null}
          {modifiable && !confirmerAnnulation ? (
            <Button variant="ghost" size="md" icon="close" onClick={() => setConfirmerAnnulation(true)}>
              Annuler l'événement
            </Button>
          ) : null}
        </div>
        {confirmerAnnulation ? (
          <Notice tone="danger" role="alert" icon="warning">
            Annuler l'événement ferme la vente et annule tous les billets déjà vendus. Les acheteurs devront être
            remboursés. Cette action est définitive.
            <span className={m.ligneActions}>
              <Button
                variant="danger"
                size="md"
                loading={action === 'annuler'}
                onClick={() => void changerStatut('annuler')}
              >
                Confirmer l'annulation
              </Button>
              <Button variant="secondary" size="md" onClick={() => setConfirmerAnnulation(false)}>
                Garder l'événement
              </Button>
            </span>
          </Notice>
        ) : null}
        {erreurAction ? (
          <Notice tone="danger" role="alert" icon="warning">
            {erreurAction}
          </Notice>
        ) : null}

        <SegmentedTabs
          label="Sections de l'événement"
          tabs={ONGLETS}
          value={onglet}
          onChange={setOnglet}
          idPrefix="evenement"
        />
        <div {...tabPanelProps('evenement', onglet)} className={m.pile}>
          {onglet === 'ventes' ? <Ventes eventId={evenement.id} /> : null}
          {onglet === 'billets' ? <Billets eventId={evenement.id} modifiable={modifiable} /> : null}
          {onglet === 'agents' ? <Agents eventId={evenement.id} /> : null}
        </div>
      </div>
    </Screen>
  );
}

/* ---------------------------------------------------------------------- Ventes */

function Ventes({ eventId }: { eventId: string }) {
  const [tableau, setTableau] = useState<TableauDeBord | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  const charger = useCallback(() => {
    setErreur(null);
    lireTableauDeBord(eventId)
      .then(setTableau)
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, [eventId]);
  useEffect(charger, [charger]);

  if (erreur) {
    return (
      <Notice tone="danger" role="alert" icon="warning">
        {erreur}
      </Notice>
    );
  }
  if (!tableau) return <Notice icon="clock">Chargement des ventes…</Notice>;
  if (tableau.categories.length === 0) {
    return (
      <EmptyState
        icon="ticket"
        title="Aucune catégorie"
        description="Ajoutez des catégories de billets dans l'onglet Billets pour suivre vos ventes."
      />
    );
  }

  const quota = tableau.categories.reduce((total, c) => total + c.quotaTotal, 0);
  return (
    <>
      <div className={m.indicateurs}>
        <MetricCard
          tone="dark"
          label="Billets vendus"
          value={nombre(tableau.vendus)}
          unit={`/ ${nombre(quota)}`}
          progress={{ value: tableau.vendus, max: quota, label: `${tableau.vendus} billets vendus sur ${quota}` }}
        />
        <MetricCard
          tone="highlight"
          label="Chiffre d'affaires"
          value={nombre(tableau.chiffreAffairesFcfa)}
          unit="FCFA"
          hint="brut, avant commission"
        />
        <MetricCard label="En cours de paiement" value={nombre(tableau.enCoursDePaiement)} hint="places réservées" />
        <MetricCard label="Entrées scannées" value={nombre(tableau.scannes)} />
      </div>
      <h2 className={m.titreSection}>Par catégorie</h2>
      <div className={m.grille}>
        {tableau.categories.map((c) => (
          <div key={c.categoryId} className={s.barre}>
            {/* Visible : la barre seule n'affiche aucun texte (son nom n'est lu que par les lecteurs d'écran). */}
            <p className={s.barreLibelle} aria-hidden="true">
              <strong>{c.nom}</strong>
              <span>
                {nombre(c.vendus)} / {nombre(c.quotaTotal)} vendus
              </span>
            </p>
            <ProgressBar
              value={c.vendus}
              max={c.quotaTotal}
              label={`${c.nom} : ${c.vendus} billets vendus sur ${c.quotaTotal}`}
            />
          </div>
        ))}
      </div>
      <DataTable<LigneTableau>
        caption="Détail des ventes par catégorie"
        rows={tableau.categories}
        getRowKey={(c) => c.categoryId}
        columns={[
          { key: 'nom', header: 'Catégorie', render: (c) => c.nom, rowHeader: true },
          { key: 'prix', header: 'Prix', render: (c) => formatFcfa(c.prixFcfa), align: 'right' },
          { key: 'vendus', header: 'Vendus', render: (c) => `${c.vendus} / ${c.quotaTotal}`, align: 'right' },
          { key: 'dispo', header: 'Disponibles', render: (c) => nombre(c.disponibles), align: 'right' },
          { key: 'scannes', header: 'Scannés', render: (c) => nombre(c.scannes), align: 'right' },
          { key: 'ca', header: "Chiffre d'affaires", render: (c) => formatFcfa(c.chiffreAffairesFcfa), align: 'right' },
        ]}
      />
      <div>
        <Button variant="secondary" size="md" icon="refresh" onClick={charger}>
          Actualiser
        </Button>
      </div>
    </>
  );
}

/* --------------------------------------------------------------------- Billets */

function Billets({ eventId, modifiable }: { eventId: string; modifiable: boolean }) {
  const [categories, setCategories] = useState<CategorieOrganisateur[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [nom, setNom] = useState('');
  const [prix, setPrix] = useState('');
  const [quota, setQuota] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreurAjout, setErreurAjout] = useState<string | null>(null);

  useEffect(() => {
    lireCategories(eventId)
      .then(setCategories)
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, [eventId]);

  const prixLu = lireMontant(prix);
  const quotaLu = /^\d{1,7}$/.test(quota.trim()) ? Number(quota.trim()) : null;
  const valide = nom.trim() && prixLu !== null && quotaLu !== null && quotaLu >= 1;

  const ajouter = async (e: FormEvent) => {
    e.preventDefault();
    if (!valide) return;
    setEnvoi(true);
    setErreurAjout(null);
    try {
      const creee = await creerCategorie(eventId, nom.trim(), prixLu, quotaLu);
      setCategories((deja) => [...(deja ?? []), creee].sort((a, b) => a.prixFcfa - b.prixFcfa));
      setNom('');
      setPrix('');
      setQuota('');
    } catch (err) {
      setErreurAjout(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <>
      {erreur ? (
        <Notice tone="danger" role="alert" icon="warning">
          {erreur}
        </Notice>
      ) : null}
      {categories?.length === 0 ? <Notice icon="ticket">Aucune catégorie pour l'instant.</Notice> : null}
      {categories && categories.length > 0 ? (
        <DataTable<CategorieOrganisateur>
          caption="Catégories de billets"
          rows={categories}
          getRowKey={(c) => c.id}
          columns={[
            { key: 'nom', header: 'Catégorie', render: (c) => c.nom, rowHeader: true },
            { key: 'prix', header: 'Prix', render: (c) => formatFcfa(c.prixFcfa), align: 'right' },
            {
              key: 'quota',
              header: 'Vendus / quota',
              render: (c) => `${c.quotaVendu ?? 0} / ${c.quotaTotal ?? '—'}`,
              align: 'right',
            },
          ]}
        />
      ) : null}

      {modifiable ? (
        <form className={m.formulaire} onSubmit={ajouter} noValidate aria-labelledby="titre-ajout">
          <h2 id="titre-ajout" className={m.titreSection}>
            Ajouter une catégorie
          </h2>
          <TextField
            label="Nom"
            value={nom}
            onChange={(e) => setNom(e.target.value)}
            maxLength={255}
            placeholder="Ex. Tribune, VIP, Pelouse"
          />
          <div className={s.paire}>
            <TextField
              label="Prix (FCFA)"
              inputMode="numeric"
              value={prix}
              onChange={(e) => setPrix(e.target.value)}
              error={prix && prixLu === null ? 'Montant entier en FCFA, sans décimale.' : undefined}
              hint="0 pour une entrée gratuite."
            />
            <TextField
              label="Nombre de places"
              inputMode="numeric"
              value={quota}
              onChange={(e) => setQuota(e.target.value)}
              error={quota && (quotaLu === null || quotaLu < 1) ? 'Au moins 1 place.' : undefined}
            />
          </div>
          {erreurAjout ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreurAjout}
            </Notice>
          ) : null}
          <Button type="submit" loading={envoi} disabled={!valide} icon="plus">
            Ajouter la catégorie
          </Button>
        </form>
      ) : (
        <Notice icon="lock">Cet événement n'accepte plus de nouvelles catégories.</Notice>
      )}
    </>
  );
}

/* ---------------------------------------------------------------------- Agents */

function Agents({ eventId }: { eventId: string }) {
  const [agents, setAgents] = useState<Agent[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState<string | null>(null);
  const [annonce, setAnnonce] = useState<string | null>(null);

  useEffect(() => {
    mesAgents()
      .then(setAgents)
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, []);

  const agir = async (agent: Agent, operation: 'affecter' | 'retirer') => {
    setEnCours(`${agent.id}-${operation}`);
    setErreur(null);
    setAnnonce(null);
    try {
      await (operation === 'affecter' ? affecterAgent(eventId, agent.id) : retirerAgent(eventId, agent.id));
      setAnnonce(
        operation === 'affecter'
          ? `${agent.nomComplet} peut contrôler les entrées de cet événement.`
          : `${agent.nomComplet} ne peut plus contrôler cet événement.`,
      );
    } catch (e) {
      setErreur(messageErreur(e));
    } finally {
      setEnCours(null);
    }
  };

  return (
    <>
      <Notice icon="scan">
        Un agent affecté peut scanner les billets de cet événement depuis l'espace Contrôle, avec son propre compte.
      </Notice>
      {annonce ? (
        <Notice tone="success" icon="check">
          {annonce}
        </Notice>
      ) : null}
      {erreur ? (
        <Notice tone="danger" role="alert" icon="warning">
          {erreur}
        </Notice>
      ) : null}
      {agents?.length === 0 ? (
        <EmptyState
          icon="user"
          title="Aucun agent"
          description="Créez les comptes de vos agents de contrôle, puis affectez-les à cet événement."
          actions={<ButtonLink to="/organisateur/agents">Créer un agent</ButtonLink>}
        />
      ) : null}
      <ul className={s.liste} aria-label="Mes agents">
        {agents?.map((agent) => (
          <li key={agent.id} className={s.ligneAgent}>
            <span className={s.identite}>
              <strong>{agent.nomComplet}</strong>
              <span className={m.muet}>{agent.email}</span>
            </span>
            <span className={m.ligneActions}>
              <Button
                size="md"
                icon="check"
                loading={enCours === `${agent.id}-affecter`}
                onClick={() => void agir(agent, 'affecter')}
              >
                Affecter
              </Button>
              <Button
                size="md"
                variant="secondary"
                loading={enCours === `${agent.id}-retirer`}
                onClick={() => void agir(agent, 'retirer')}
              >
                Retirer
              </Button>
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

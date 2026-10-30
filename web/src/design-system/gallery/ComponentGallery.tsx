import { useState } from 'react';
import {
  BarChart,
  BottomNav,
  Badge,
  Button,
  ButtonLink,
  Chip,
  ChipGroup,
  CountdownNotice,
  DataTable,
  EmptyState,
  EntryCounter,
  EventFeaturedCard,
  EventRow,
  FilterTag,
  IconButton,
  ListRow,
  MetricCard,
  Notice,
  PhoneField,
  PriceSummary,
  ProgressBar,
  RadioCardGroup,
  ScanResultPanel,
  ScannerViewfinder,
  Screen,
  SegmentedTabs,
  SuccessHero,
  TextField,
  TicketCategoryRow,
  TicketPass,
  Toggle,
  TopBar,
  buyerNavItems,
  formatEventDate,
  formatFcfa,
  isValidSnMobile,
  tabPanelProps,
  type EventSummary,
  type ScanStatus,
} from '..';

const SUNSET: EventSummary = {
  id: 'dakar-sunset-sessions',
  title: 'Dakar Sunset Sessions',
  category: 'Musique',
  startsAt: new Date(Date.UTC(2026, 10, 14, 20, 0)),
  venue: 'Corniche Ouest',
  fromPriceFcfa: 5000,
  poster: 'sunset',
};

const JAZZ: EventSummary = {
  id: 'soiree-jazz-kora',
  title: 'Soirée Jazz & Kora',
  category: 'Musique',
  startsAt: new Date(Date.UTC(2026, 10, 20, 19, 0)),
  venue: 'Plateau',
  fromPriceFcfa: 15000,
  poster: 'rings',
};

const TECH: EventSummary = {
  id: 'conference-tech-dakar',
  title: 'Conférence Tech Dakar',
  category: 'Conférence',
  startsAt: new Date(Date.UTC(2026, 10, 28, 9, 0)),
  venue: 'Almadies',
  fromPriceFcfa: 10000,
  poster: 'bars',
};

interface OrderRow {
  id: string;
  buyer: string;
  tickets: string;
  status: 'paid' | 'pending';
  totalFcfa: number;
}

const ORDERS: ReadonlyArray<OrderRow> = [
  { id: 'BP-CMD-0042', buyer: '+221 77 ••• •• 13', tickets: '2 × Pelouse', status: 'paid', totalFcfa: 10500 },
  { id: 'BP-CMD-0041', buyer: '+221 78 ••• •• 52', tickets: '1 × VIP', status: 'pending', totalFcfa: 52500 },
];

const SALES = [
  { label: 'Ven', value: 24 },
  { label: 'Sam', value: 31 },
  { label: 'Dim', value: 28 },
  { label: 'Lun', value: 45 },
  { label: 'Mar', value: 52 },
  { label: 'Mer', value: 61 },
  { label: 'Jeu', value: 77 },
];

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginTop: 32, display: 'grid', gap: 14 }}>
      <h2 style={{ margin: 0, fontFamily: 'var(--bp-font-display)', fontSize: 20, fontWeight: 800 }}>{title}</h2>
      {children}
    </section>
  );
}

/**
 * Catalogue visuel de tous les composants, monté sur une route de développement (ex. /dev/composants).
 * Sert de contrôle visuel manuel et de test de fumée : si un composant casse, cette page ne se rend plus.
 */
export function ComponentGallery() {
  const [category, setCategory] = useState('all');
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming');
  const [qty, setQty] = useState({ pelouse: 2, tribune: 0, vip: 0 });
  const [phone, setPhone] = useState('');
  const [notif, setNotif] = useState(true);
  const [method, setMethod] = useState<'wave' | 'orange' | 'free'>('wave');
  const [scan, setScan] = useState<ScanStatus>('valid');
  const [deadline] = useState(() => Date.now() + 108_000);

  const phoneError = phone.length === 9 && !isValidSnMobile(phone) ? 'Numéro mobile sénégalais invalide.' : undefined;
  const subtotal = qty.pelouse * 5000 + qty.tribune * 15000 + qty.vip * 50000;
  const fee = Math.round(subtotal * 0.05);

  return (
    <Screen withNav>
      <TopBar title="Composants Billet+" backTo="/" backLabel="Retour à l'accueil" subtitle="Galerie de développement" />

      <Section title="Boutons">
        <div style={{ display: 'grid', gap: 8 }}>
          <Button fullWidth>Payer {formatFcfa(10500)}</Button>
          <Button fullWidth variant="secondary" icon="download">
            Télécharger le PDF
          </Button>
          <Button fullWidth variant="ghost">
            Changer de numéro
          </Button>
          <Button fullWidth variant="danger" icon="logout">
            Se déconnecter
          </Button>
          <Button fullWidth loading>
            Envoi en cours
          </Button>
          <Button fullWidth disabled>
            Sélectionnez au moins un billet
          </Button>
          <ButtonLink to="/billets" fullWidth size="md" variant="secondary">
            Lien stylé en bouton
          </ButtonLink>
          <div style={{ display: 'flex', gap: 8 }}>
            <IconButton label="Rechercher" icon="search" />
            <IconButton label="Filtres" icon="filter" tone="accent" />
            <IconButton label="Fermer" icon="close" tone="solid" />
          </div>
        </div>
      </Section>

      <Section title="Puces et onglets">
        <ChipGroup
          label="Catégorie"
          value={category}
          onChange={setCategory}
          scroll
          options={[
            { value: 'all', label: 'Tout' },
            { value: 'music', label: 'Musique' },
            { value: 'sport', label: 'Sport' },
            { value: 'conf', label: 'Conférence' },
            { value: 'fest', label: 'Festival' },
          ]}
        />
        <div style={{ display: 'flex', gap: 8 }}>
          <FilterTag label="Dakar" onRemove={() => undefined} />
          <FilterTag label="Ce week-end" onRemove={() => undefined} />
          <Chip selected={false}>Puce isolée</Chip>
        </div>
        <SegmentedTabs
          label="Mes billets"
          idPrefix="galerie"
          value={tab}
          onChange={setTab}
          tabs={[
            { id: 'upcoming', label: 'À venir (2)' },
            { id: 'past', label: 'Passés (1)' },
          ]}
        />
        <div {...tabPanelProps('galerie', tab)}>{tab === 'upcoming' ? 'Deux événements à venir.' : 'Un événement terminé.'}</div>
      </Section>

      <Section title="Formulaires">
        <TextField label="Nom de l'événement" placeholder="Ex. Festival Teranga Beach" />
        <PhoneField value={phone} onChange={setPhone} error={phoneError} hint="Nous vous envoyons un code par SMS." />
        <Toggle
          label="Rappels avant l'événement"
          description="Une notification la veille et le jour même."
          checked={notif}
          onChange={setNotif}
        />
        <RadioCardGroup
          legend="Moyen de paiement"
          name="galerie-paiement"
          value={method}
          onChange={setMethod}
          options={[
            { value: 'wave', label: 'Wave', icon: 'card' },
            { value: 'orange', label: 'Orange Money', badge: 'Bientôt', disabled: true },
            { value: 'free', label: 'Free Money', badge: 'Bientôt', disabled: true },
          ]}
        />
      </Section>

      <Section title="Retours d'état">
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Badge tone="success" icon="check">
            Payé
          </Badge>
          <Badge tone="warning">En attente</Badge>
          <Badge tone="danger" icon="close">
            Refusé
          </Badge>
          <Badge tone="accent">Plus que 12</Badge>
          <Badge tone="neutral">Bientôt</Badge>
        </div>
        <Notice tone="warning" icon="wifiOff">
          Hors ligne · 3 scans à synchroniser
        </Notice>
        <Notice tone="success" icon="check">
          Vos billets restent disponibles sans connexion.
        </Notice>
        <Notice tone="danger" icon="warning" role="alert">
          Le paiement n&apos;a pas abouti.
        </Notice>
        <ProgressBar value={210} max={300} label="Pelouse : 210 billets vendus sur 300" />
        <CountdownNotice deadline={deadline} />
      </Section>

      <Section title="Événements">
        <EventFeaturedCard event={SUNSET} to="/evenements/dakar-sunset-sessions" />
        <EventRow event={JAZZ} to="/evenements/soiree-jazz-kora" />
        <EventRow event={TECH} to="/evenements/conference-tech-dakar" highlight="dakar" />
        <p style={{ margin: 0, fontSize: 14 }}>Date formatée : {formatEventDate(SUNSET.startsAt)}</p>
      </Section>

      <Section title="Billetterie">
        <TicketCategoryRow
          name="Pelouse"
          description="Accès debout, devant les scènes"
          priceFcfa={5000}
          quantity={qty.pelouse}
          onQuantityChange={(n) => setQty((q) => ({ ...q, pelouse: n }))}
        />
        <TicketCategoryRow
          name="Tribune"
          description="Place assise, vue dégagée"
          priceFcfa={15000}
          quantity={qty.tribune}
          onQuantityChange={(n) => setQty((q) => ({ ...q, tribune: n }))}
        />
        <TicketCategoryRow
          name="VIP"
          description="Espace réservé avec bar dédié"
          priceFcfa={50000}
          remaining={12}
          quantity={qty.vip}
          onQuantityChange={(n) => setQty((q) => ({ ...q, vip: n }))}
        />
        <PriceSummary
          lines={[
            { label: 'Sous-total', amountFcfa: subtotal },
            { label: 'Frais de service (5 %)', amountFcfa: fee },
          ]}
          totalFcfa={subtotal + fee}
        />
        <TicketPass
          eventTitle="Dakar Sunset Sessions"
          categoryName="Pelouse"
          dateLabel="Sam. 14 nov. 2026 · 20 h"
          venue="Corniche Ouest, Dakar"
          qrPayload="demo.payload.non-signe"
          code="BP-7K2M-48QX"
          status="valid"
        />
        <TicketPass
          eventTitle="Dakar Sunset Sessions"
          categoryName="Pelouse"
          dateLabel="Sam. 14 nov. 2026 · 20 h"
          venue="Corniche Ouest, Dakar"
          qrPayload="demo.payload.non-signe"
          code="BP-7K2M-48QX"
          status="used"
        />
      </Section>

      <Section title="Organisateur">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <MetricCard
            tone="dark"
            label="Billets vendus"
            value="318"
            unit="/ 450"
            progress={{ value: 318, max: 450, label: '318 billets vendus sur 450' }}
          />
          <MetricCard tone="highlight" label="Revenus" value="3 300 000" unit="FCFA" hint="hors frais de service" />
        </div>
        <BarChart data={SALES} ariaLabel="Billets vendus par jour, du vendredi 23 au jeudi 29 octobre" />
        <DataTable<OrderRow>
          caption="Dernières commandes"
          rows={ORDERS}
          getRowKey={(row) => row.id}
          columns={[
            { key: 'id', header: 'Commande', rowHeader: true, render: (row) => row.id },
            { key: 'tickets', header: 'Billets', render: (row) => row.tickets },
            {
              key: 'status',
              header: 'Statut',
              render: (row) =>
                row.status === 'paid' ? (
                  <Badge tone="success" icon="check">
                    Payé
                  </Badge>
                ) : (
                  <Badge tone="warning">En attente</Badge>
                ),
            },
            { key: 'total', header: 'Total', align: 'right', render: (row) => formatFcfa(row.totalFcfa) },
          ]}
        />
      </Section>

      <Section title="Contrôle d'entrée">
        <div style={{ background: 'var(--bp-ink)', color: '#fff', padding: 16, borderRadius: 20 }}>
          <EntryCounter entered={128} total={318} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Chip selected={scan === 'valid'} onClick={() => setScan('valid')}>
            Valide
          </Chip>
          <Chip selected={scan === 'used'} onClick={() => setScan('used')}>
            Utilisé
          </Chip>
          <Chip selected={scan === 'invalid'} onClick={() => setScan('invalid')}>
            Invalide
          </Chip>
        </div>
        <ScannerViewfinder />
        <ScanResultPanel
          status={scan}
          ticketCode="BP-7K2M-48QX"
          categoryLabel="Pelouse · 1 personne"
          firstScanLabel="20 h 12 · Porte A"
          onNext={() => undefined}
        />
      </Section>

      <Section title="Écrans et listes">
        <SuccessHero title="Paiement confirmé !" description="Vos 2 billets sont prêts. À samedi sur la Corniche !" />
        <div>
          <ListRow icon="ticket" label="Mes commandes" to="/billets" />
          <ListRow icon="card" label="Moyen de paiement" value="Wave" />
          <ListRow icon="logout" label="Se déconnecter" to="/connexion" tone="danger" />
        </div>
        <EmptyState
          icon="ticket"
          title="Aucun billet pour l'instant"
          description="Vos billets achetés apparaissent ici et restent disponibles sans connexion Internet."
          actions={
            <ButtonLink to="/" fullWidth>
              Découvrir les événements
            </ButtonLink>
          }
        />
      </Section>

      <BottomNav items={buyerNavItems} />
    </Screen>
  );
}

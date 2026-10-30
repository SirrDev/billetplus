import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import {
  BarChart,
  BottomNav,
  Button,
  ButtonLink,
  ChipGroup,
  DataTable,
  EventRow,
  PhoneField,
  PriceSummary,
  ProgressBar,
  RadioCardGroup,
  ScanResultPanel,
  SegmentedTabs,
  TextField,
  TicketCategoryRow,
  TicketPass,
  Toggle,
  buyerNavItems,
  type EventSummary,
} from '..';

const wrap = (ui: React.ReactElement, route = '/') => render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>);

describe('Button', () => {
  it('est désactivé et signale aria-busy pendant le chargement', () => {
    render(<Button loading>Payer</Button>);
    const button = screen.getByRole('button', { name: 'Payer' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });

  it('ButtonLink est un vrai lien', () => {
    wrap(<ButtonLink to="/billets">Mes billets</ButtonLink>);
    expect(screen.getByRole('link', { name: 'Mes billets' })).toHaveAttribute('href', '/billets');
  });
});

describe('TicketCategoryRow', () => {
  function Harness({ remaining }: { remaining?: number }) {
    const [q, setQ] = useState(0);
    return <TicketCategoryRow name="VIP" priceFcfa={50000} remaining={remaining} quantity={q} onQuantityChange={setQ} />;
  }

  it('incrémente et décrémente avec des libellés accessibles', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'Ajouter un billet VIP' }));
    await user.click(screen.getByRole('button', { name: 'Ajouter un billet VIP' }));
    expect(screen.getByText('2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retirer un billet VIP' }));
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('plafonne à 10 par commande', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const plus = screen.getByRole('button', { name: 'Ajouter un billet VIP' });
    for (let i = 0; i < 12; i += 1) await user.click(plus);
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(plus).toBeDisabled();
  });

  it('plafonne au stock restant et affiche « Plus que N »', async () => {
    const user = userEvent.setup();
    render(<Harness remaining={3} />);
    expect(screen.getByText('Plus que 3')).toBeInTheDocument();
    const plus = screen.getByRole('button', { name: 'Ajouter un billet VIP' });
    for (let i = 0; i < 5; i += 1) await user.click(plus);
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(plus).toBeDisabled();
  });

  it('désactive tout quand c’est épuisé', () => {
    render(<Harness remaining={0} />);
    expect(screen.getByText('Épuisé')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajouter un billet VIP' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Retirer un billet VIP' })).toBeDisabled();
  });
});

describe('PriceSummary', () => {
  it('affiche les lignes et le total formatés', () => {
    render(
      <PriceSummary
        lines={[
          { label: 'Sous-total', amountFcfa: 10000 },
          { label: 'Frais de service (5 %)', amountFcfa: 500 },
        ]}
        totalFcfa={10500}
      />,
    );
    // Testing Library normalise les espaces (NBSP inclus) dans le texte du DOM : on compare donc avec
    // des espaces simples. Les espaces insécables sont vérifiés strictement dans lib/format.test.ts.
    expect(screen.getByText('Sous-total')).toBeInTheDocument();
    expect(screen.getAllByText('10 500 FCFA')).toHaveLength(1);
    expect(screen.getByText('500 FCFA')).toBeInTheDocument();
    expect(screen.getByText('10 000 FCFA')).toBeInTheDocument();
  });
});

describe('SegmentedTabs', () => {
  function Harness() {
    const [v, setV] = useState<'a' | 'b' | 'c'>('a');
    return (
      <SegmentedTabs
        label="Test"
        idPrefix="t"
        value={v}
        onChange={setV}
        tabs={[
          { id: 'a', label: 'A' },
          { id: 'b', label: 'B' },
          { id: 'c', label: 'C' },
        ]}
      />
    );
  }

  it('suit le motif ARIA tabs : tabIndex itinérant et flèches', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const [a, b, c] = screen.getAllByRole('tab');
    expect(a).toHaveAttribute('aria-selected', 'true');
    expect(a).toHaveAttribute('tabindex', '0');
    expect(b).toHaveAttribute('tabindex', '-1');

    a?.focus();
    await user.keyboard('{ArrowRight}');
    expect(b).toHaveAttribute('aria-selected', 'true');
    expect(b).toHaveFocus();

    await user.keyboard('{End}');
    expect(c).toHaveAttribute('aria-selected', 'true');

    await user.keyboard('{ArrowRight}');
    expect(a).toHaveAttribute('aria-selected', 'true');
  });
});

describe('ChipGroup', () => {
  it('expose aria-pressed et notifie le choix', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ChipGroup
        label="Catégorie"
        value="all"
        onChange={onChange}
        options={[
          { value: 'all', label: 'Tout' },
          { value: 'music', label: 'Musique' },
        ]}
      />,
    );
    expect(screen.getByRole('group', { name: 'Catégorie' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tout' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Musique' }));
    expect(onChange).toHaveBeenCalledWith('music');
  });
});

describe('Form', () => {
  it('TextField relie label, aide et erreur', () => {
    render(<TextField label="Nom" hint="Visible sur l'affiche" error="Champ requis" />);
    const input = screen.getByLabelText('Nom');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription("Visible sur l'affiche Champ requis");
    expect(screen.getByRole('alert')).toHaveTextContent('Champ requis');
  });

  it('PhoneField formate la saisie et renvoie uniquement des chiffres', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [v, setV] = useState('');
      return (
        <>
          <PhoneField value={v} onChange={setV} />
          <output data-testid="raw">{v}</output>
        </>
      );
    }
    render(<Harness />);
    const input = screen.getByLabelText('Numéro de téléphone');
    await user.type(input, '77a123-45 67 99');
    expect(input).toHaveValue('77 123 45 67');
    expect(screen.getByTestId('raw')).toHaveTextContent('771234567');
  });

  it('Toggle bascule et expose role=switch', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [on, setOn] = useState(false);
      return <Toggle label="Rappels" checked={on} onChange={setOn} />;
    }
    render(<Harness />);
    const sw = screen.getByRole('switch', { name: 'Rappels' });
    expect(sw).toHaveAttribute('aria-checked', 'false');
    await user.click(sw);
    expect(sw).toHaveAttribute('aria-checked', 'true');
  });

  it('RadioCardGroup : radios natifs, option désactivée non choisissable', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <RadioCardGroup
        legend="Moyen de paiement"
        name="pay"
        value="wave"
        onChange={onChange}
        options={[
          { value: 'wave', label: 'Wave' },
          { value: 'orange', label: 'Orange Money', badge: 'Bientôt', disabled: true },
        ]}
      />,
    );
    expect(screen.getByRole('group', { name: 'Moyen de paiement' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Wave' })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: /Orange Money/ }));
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe('ProgressBar', () => {
  it('expose les valeurs bornées', () => {
    render(<ProgressBar value={500} max={300} label="Pelouse" />);
    const bar = screen.getByRole('progressbar', { name: 'Pelouse' });
    expect(bar).toHaveAttribute('aria-valuenow', '300');
    expect(bar).toHaveAttribute('aria-valuemax', '300');
  });
});

describe('ScanResultPanel', () => {
  it.each([
    ['valid', 'Entrée autorisée', 'Laisser entrer'],
    ['used', 'Déjà utilisé', "Refuser l'entrée"],
    ['invalid', 'Billet invalide', "Refuser l'entrée"],
  ] as const)('statut %s : titre et consigne explicites', (status, title, decision) => {
    render(<ScanResultPanel status={status} ticketCode="BP-7K2M-48QX" />);
    expect(screen.getByRole('status')).toHaveTextContent(title);
    expect(screen.getByText(decision)).toBeInTheDocument();
    expect(screen.getByText('BP-7K2M-48QX')).toBeInTheDocument();
  });

  it('donne l’heure du premier scan pour un billet déjà utilisé', () => {
    render(<ScanResultPanel status="used" ticketCode="X" firstScanLabel="20 h 12 · Porte A" />);
    expect(screen.getByText('Scanné à 20 h 12 · Porte A')).toBeInTheDocument();
  });

  it('appelle onNext', async () => {
    const user = userEvent.setup();
    const onNext = vi.fn();
    render(<ScanResultPanel status="valid" ticketCode="X" onNext={onNext} />);
    await user.click(screen.getByRole('button', { name: 'Scanner le billet suivant' }));
    expect(onNext).toHaveBeenCalledTimes(1);
  });
});

describe('TicketPass', () => {
  const base = {
    eventTitle: 'Dakar Sunset Sessions',
    categoryName: 'Pelouse',
    dateLabel: 'Sam. 14 nov. 2026 · 20 h',
    venue: 'Corniche Ouest',
    qrPayload: 'payload',
    code: 'BP-7K2M-48QX',
  };

  it('billet valide : QR actif', () => {
    render(<TicketPass {...base} status="valid" />);
    expect(screen.getByRole('img', { name: 'QR code du billet BP-7K2M-48QX' })).toBeInTheDocument();
    expect(screen.getAllByText('Valide').length).toBeGreaterThan(0);
  });

  it('billet utilisé : QR désactivé et signalé en texte', () => {
    render(<TicketPass {...base} status="used" />);
    expect(screen.getByRole('img', { name: 'QR code désactivé (déjà utilisé)' })).toBeInTheDocument();
    expect(screen.getAllByText('Déjà utilisé').length).toBeGreaterThan(0);
  });
});

describe('Navigation', () => {
  it('BottomNav marque l’onglet actif avec aria-current', () => {
    wrap(<BottomNav items={buyerNavItems} />, '/billets');
    expect(screen.getByRole('link', { name: 'Mes billets' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Découvrir' })).not.toHaveAttribute('aria-current');
  });
});

describe('EventRow', () => {
  const event: EventSummary = {
    id: 'e1',
    title: 'Conférence Tech Dakar',
    category: 'Conférence',
    startsAt: new Date(Date.UTC(2026, 10, 28, 9, 0)),
    venue: 'Almadies',
    fromPriceFcfa: 10000,
    poster: 'bars',
  };

  it('surligne le terme recherché sans casser le titre', () => {
    wrap(<EventRow event={event} to="/evenements/e1" highlight="dakar" />);
    expect(screen.getByRole('link')).toHaveTextContent('Conférence Tech Dakar');
    expect(screen.getByText('Dakar').tagName).toBe('MARK');
  });

  it('échappe les caractères spéciaux du terme recherché', () => {
    wrap(<EventRow event={event} to="/evenements/e1" highlight="(" />);
    expect(screen.getByRole('link')).toHaveTextContent('Conférence Tech Dakar');
  });
});

describe('Organisateur', () => {
  it('BarChart expose un tableau de secours avec toutes les valeurs', () => {
    render(
      <BarChart
        ariaLabel="Ventes par jour"
        data={[
          { label: 'Ven', value: 24 },
          { label: 'Sam', value: 31 },
        ]}
      />,
    );
    expect(screen.getByRole('img', { name: 'Ventes par jour' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Ven' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '31' })).toBeInTheDocument();
  });

  it('DataTable utilise des en-têtes de colonnes et de lignes', () => {
    render(
      <DataTable
        caption="Commandes"
        rows={[{ id: 'A1', total: 10 }]}
        getRowKey={(r) => r.id}
        columns={[
          { key: 'id', header: 'Commande', rowHeader: true, render: (r) => r.id },
          { key: 'total', header: 'Total', align: 'right', render: (r) => String(r.total) },
        ]}
      />,
    );
    expect(screen.getByRole('table', { name: 'Commandes' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Total' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'A1' })).toBeInTheDocument();
  });
});

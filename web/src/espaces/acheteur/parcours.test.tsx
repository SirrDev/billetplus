import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { enregistrerCommande, type CommandeLocale } from './commandesLocales';

/*
 * Parcours d'achat de bout en bout, avec un backend simulé au niveau de fetch.
 * IndexedDB n'existe pas dans jsdom : le stockage local est remplacé par une Map.
 */
const stockage = new Map<string, CommandeLocale>();
const verifierStockage = vi.fn(async () => undefined);
vi.mock('./commandesLocales', () => ({
  verifierStockage: () => verifierStockage(),
  enregistrerCommande: vi.fn(async (c: CommandeLocale) => void stockage.set(c.id, c)),
  lireCommandeLocale: vi.fn(async (id: string) => stockage.get(id)),
  listerCommandesLocales: vi.fn(async () => [...stockage.values()]),
  enregistrerBillets: vi.fn(async (id: string, billets: CommandeLocale['billets']) => {
    const c = stockage.get(id);
    if (c) stockage.set(id, { ...c, billets });
  }),
  listerBilletsRecus: vi.fn(async () => []),
}));

/* Le générateur PDF est testé à part (billetPdf.test.ts) : ici, seul ce qu'on lui transmet compte. */
const telechargerBilletsPdf = vi.fn();
vi.mock('./billetPdf', () => ({ telechargerBilletsPdf: (c: unknown) => telechargerBilletsPdf(c) }));

const allerPayer = vi.fn();
vi.mock('./pages/paiementOutils', async (original) => ({
  ...(await original<typeof import('./pages/paiementOutils')>()),
  allerPayer: (url: string) => allerPayer(url),
}));

const EVENEMENT = {
  id: 'e1',
  titre: 'Nuit du Mbalax',
  description: 'Une nuit de mbalax live.',
  debuteLe: '2099-11-14T21:00:00Z',
  termineLe: null,
  venueId: 'v1',
  venueNom: 'Grand Théâtre',
  venueAdresse: 'Dakar',
  organizerId: 'o1',
  status: 'PUBLIE',
  creeLe: '2026-10-01T00:00:00Z',
};
const CATEGORIES = [
  { id: 'std', nom: 'Standard', prixFcfa: 5000, disponible: true },
  { id: 'vip', nom: 'VIP', prixFcfa: 15000, disponible: false },
];
const COMMANDE = {
  id: 'c1',
  eventId: 'e1',
  status: 'EN_ATTENTE_PAIEMENT',
  lignes: [{ categoryId: 'std', quantite: 2, prixUnitaireFcfa: 5000, sousTotalFcfa: 10000 }],
  montantTotalFcfa: 10000,
  creeLe: '2026-10-30T10:00:00Z',
  expireLe: '2099-01-01T00:00:00Z',
  payeeLe: null,
};

type Route = (url: string, init?: RequestInit) => unknown;

function backend(routes: Record<string, Route>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = String(entree);
    const methode = init?.method ?? 'GET';
    const cle = Object.keys(routes).find((motif) => `${methode} ${url}`.startsWith(motif));
    if (!cle) return new Response(JSON.stringify({ message: `Route inattendue ${methode} ${url}` }), { status: 500 });
    const corps = routes[cle]!(url, init);
    if (corps instanceof Response) return corps;
    return new Response(JSON.stringify(corps), { status: 200 });
  });
}

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

const espaces = (t: string | null) => (t ?? '').replace(/\s/g, ' ');

beforeEach(() => {
  stockage.clear();
  allerPayer.mockReset();
  verifierStockage.mockReset();
  verifierStockage.mockResolvedValue(undefined);
});

describe('Découvrir', () => {
  it("met le premier événement à l'affiche, sans prix ni genre inventés", async () => {
    backend({
      'GET /api/events?': () => ({
        contenu: [EVENEMENT, { ...EVENEMENT, id: 'e2', titre: 'Dakar Jazz' }],
        page: 0,
        taille: 12,
        total: 2,
        pages: 1,
      }),
    });
    ouvrir('/');
    expect(await screen.findByRole('heading', { name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Réserver mes places' })).toHaveAttribute('href', '/evenements/e1');
    expect(screen.getByRole('list', { name: 'Événements à venir' })).toHaveTextContent('Dakar Jazz');
    expect(document.body.textContent).not.toMatch(/dès|FCFA/);
  });
});

describe('Achat', () => {
  it("choisit des billets, crée la commande, garde le jeton sur l'appareil et part payer", async () => {
    const appels = backend({
      'GET /api/events/e1/categories': () => CATEGORIES,
      'GET /api/events/e1': () => EVENEMENT,
      'POST /api/orders/c1/paiement': () => ({ moyen: 'ORANGE_MONEY', urlPaiement: 'https://pay.test/c1', expireLe: COMMANDE.expireLe }),
      'POST /api/orders': () => ({ commande: COMMANDE, jetonAcces: 'jeton-secret' }),
    });
    ouvrir('/evenements/e1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText('Épuisé')).toBeInTheDocument();
    const continuer = screen.getByRole('button', { name: 'Continuer vers le paiement' });
    expect(continuer).toBeDisabled();

    const plus = screen.getByRole('button', { name: 'Ajouter un billet Standard' });
    await userEvent.click(plus);
    await userEvent.click(plus);
    expect(espaces(screen.getByText(/billets · total/).nextElementSibling?.textContent ?? '')).toBe('10 000 FCFA');
    await userEvent.click(continuer);

    // Écran de paiement
    expect(await screen.findByRole('heading', { name: 'Paiement' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: /Orange Money/ }));
    const payer = screen.getByRole('button', { name: 'Payer avec Orange Money' });
    // Avant création, le montant n'est qu'un aperçu : il est présenté comme tel.
    expect(espaces(screen.getByText('Total estimé').nextElementSibling?.textContent ?? '')).toBe('10 000 FCFA');

    await userEvent.type(screen.getByLabelText('Numéro de mobile'), '33 12');
    await userEvent.click(payer);
    expect(screen.getByRole('alert')).toHaveTextContent(/9 chiffres/);
    expect(appels.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);

    await userEvent.clear(screen.getByLabelText('Numéro de mobile'));
    await userEvent.type(screen.getByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(payer);

    await waitFor(() => expect(allerPayer).toHaveBeenCalledWith('https://pay.test/c1'));
    const creation = appels.mock.calls.find(([url, init]) => url === '/api/orders' && init?.method === 'POST');
    expect(JSON.parse(String(creation?.[1]?.body))).toEqual({
      eventId: 'e1',
      telephone: '+221771234567',
      lignes: [{ categoryId: 'std', quantite: 2 }],
    });
    expect(stockage.get('c1')).toMatchObject({ jeton: 'jeton-secret', titre: 'Nuit du Mbalax' });
    const paiement = appels.mock.calls.find(([url]) => url === '/api/orders/c1/paiement');
    expect(paiement?.[1]?.headers).toMatchObject({ 'X-Jeton-Commande': 'jeton-secret' });
    expect(JSON.parse(String(paiement?.[1]?.body))).toEqual({ moyen: 'ORANGE_MONEY' });
  });

  it('obtient des billets gratuits sans paiement et les affiche aussitôt (entrée libre)', async () => {
    const GRATUITE = [{ id: 'libre', nom: 'Entrée libre', prixFcfa: 0, disponible: true }];
    const CONFIRMEE = {
      ...COMMANDE,
      status: 'PAYEE',
      lignes: [{ categoryId: 'libre', quantite: 2, prixUnitaireFcfa: 0, sousTotalFcfa: 0 }],
      montantTotalFcfa: 0,
      payeeLe: '2026-10-30T10:00:01Z',
    };
    const appels = backend({
      'GET /api/events/e1/categories': () => GRATUITE,
      'GET /api/events/e1': () => EVENEMENT,
      'POST /api/orders/entree-libre': () => ({ commande: CONFIRMEE, jetonAcces: 'jeton-libre' }),
      'GET /api/orders/c1/billets': () => [
        { id: 'b1', categoryId: 'libre', categorie: 'Entrée libre', statut: 'EMIS', qr: 'charge.signature' },
        { id: 'b2', categoryId: 'libre', categorie: 'Entrée libre', statut: 'EMIS', qr: 'charge.signature2' },
      ],
      'GET /api/orders/c1': () => CONFIRMEE,
    });
    ouvrir('/evenements/e1');

    expect(await screen.findByText('Gratuit')).toBeInTheDocument();
    const plus = screen.getByRole('button', { name: 'Ajouter un billet Entrée libre' });
    await userEvent.click(plus);
    await userEvent.click(plus);
    await userEvent.click(screen.getByRole('button', { name: 'Obtenir mes billets' }));

    expect(await screen.findByRole('heading', { name: 'Vos billets gratuits' })).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: /Wave/ })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: 'Obtenir mes billets' }));

    expect(await screen.findAllByRole('article')).toHaveLength(2);
    expect(allerPayer).not.toHaveBeenCalled();
    expect(stockage.get('c1')).toMatchObject({ jeton: 'jeton-libre' });
    expect(screen.getByText('Billets gratuits confirmés')).toBeInTheDocument();
    expect(screen.getByText('2 · Coordonnées')).toBeInTheDocument();
    const creation = appels.mock.calls.find(([url]) => url === '/api/orders/entree-libre');
    expect(JSON.parse(String(creation?.[1]?.body))).toEqual({
      eventId: 'e1',
      telephone: '+221771234567',
      lignes: [{ categoryId: 'libre', quantite: 2 }],
    });
    expect(appels.mock.calls.some(([url, init]) => url === '/api/orders' && init?.method === 'POST')).toBe(false);
  });

  it('refuse de mélanger billets gratuits et payants dans une commande', async () => {
    backend({
      'GET /api/events/e1/categories': () => [
        { id: 'libre', nom: 'Entrée libre', prixFcfa: 0, disponible: true },
        { id: 'std', nom: 'Standard', prixFcfa: 5000, disponible: true },
      ],
      'GET /api/events/e1': () => EVENEMENT,
    });
    ouvrir('/evenements/e1');

    await userEvent.click(await screen.findByRole('button', { name: 'Ajouter un billet Entrée libre' }));
    await userEvent.click(screen.getByRole('button', { name: 'Ajouter un billet Standard' }));
    expect(screen.getByText(/se commandent séparément/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuer vers le paiement' })).toBeDisabled();
  });

  it("affiche l'erreur du backend quand les places viennent de partir", async () => {
    backend({
      'GET /api/events/e1/categories': () => CATEGORIES,
      'GET /api/events/e1': () => EVENEMENT,
      'POST /api/orders': () => new Response(JSON.stringify({ message: 'Places insuffisantes' }), { status: 409 }),
    });
    render(
      <MemoryRouter initialEntries={[{ pathname: '/evenements/e1/paiement', state: { quantites: { std: 1 } } }]}>
        <Routage />
      </MemoryRouter>,
    );
    await userEvent.type(await screen.findByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: /Payer/ }));
    expect(await screen.findByText('Places insuffisantes')).toBeInTheDocument();
    expect(stockage.size).toBe(0);
    expect(allerPayer).not.toHaveBeenCalled();
  });

  it("ne crée aucune commande si l'appareil ne peut pas garder le jeton", async () => {
    verifierStockage.mockRejectedValue(new Error('QuotaExceededError'));
    const appels = backend({ 'GET /api/events/e1/categories': () => CATEGORIES, 'GET /api/events/e1': () => EVENEMENT });
    render(
      <MemoryRouter initialEntries={[{ pathname: '/evenements/e1/paiement', state: { quantites: { std: 1 } } }]}>
        <Routage />
      </MemoryRouter>,
    );
    await userEvent.type(await screen.findByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: /Payer/ }));
    expect(await screen.findByText(/ne peut pas enregistrer vos billets/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it("n'ouvre aucun paiement si la commande créée n'a pas pu être enregistrée", async () => {
    vi.mocked(enregistrerCommande).mockRejectedValueOnce(new Error('QuotaExceededError'));
    const appels = backend({
      'GET /api/events/e1/categories': () => CATEGORIES,
      'GET /api/events/e1': () => EVENEMENT,
      'POST /api/orders': () => ({ commande: COMMANDE, jetonAcces: 'jeton-secret' }),
    });
    render(
      <MemoryRouter initialEntries={[{ pathname: '/evenements/e1/paiement', state: { quantites: { std: 1 } } }]}>
        <Routage />
      </MemoryRouter>,
    );
    await userEvent.type(await screen.findByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: /Payer/ }));
    expect(await screen.findByText(/aucun paiement n'a été demandé/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([url]) => String(url).endsWith('/paiement'))).toBe(false);
    expect(allerPayer).not.toHaveBeenCalled();
  });

  it("renvoie vers l'événement si l'on arrive au paiement sans billets choisis", async () => {
    backend({ 'GET /api/events/e1/categories': () => CATEGORIES, 'GET /api/events/e1': () => EVENEMENT });
    ouvrir('/evenements/e1/paiement');
    expect(await screen.findByRole('button', { name: 'Continuer vers le paiement' })).toBeInTheDocument();
  });

  it("répond « introuvable » pour un événement inconnu ou non publié", async () => {
    backend({
      'GET /api/events/zz': () => new Response(JSON.stringify({ message: 'Evenement introuvable' }), { status: 404 }),
    });
    ouvrir('/evenements/zz');
    expect(await screen.findByRole('heading', { level: 1, name: 'Événement introuvable' })).toBeInTheDocument();
  });
});

describe('Commande', () => {
  const locale: CommandeLocale = {
    id: 'c1',
    jeton: 'jeton-secret',
    eventId: 'e1',
    titre: 'Nuit du Mbalax',
    debuteLe: EVENEMENT.debuteLe,
    lieu: 'Grand Théâtre',
    creeLe: COMMANDE.creeLe,
  };

  it('télécharge en PDF les seuls billets valables, avec leur QR signé intact', async () => {
    stockage.set('c1', locale);
    telechargerBilletsPdf.mockReset();
    backend({
      'GET /api/orders/c1/billets': () => [
        { id: '4f2a91c0-0000-4000-8000-000000000001', categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'charge.signature' },
        { id: 'b2', categoryId: 'std', categorie: 'Standard', statut: 'SCANNE', qr: null },
      ],
      'GET /api/orders/c1': () => ({ ...COMMANDE, status: 'PAYEE', payeeLe: '2026-10-30T10:02:00Z' }),
      'GET /api/events/e1/categories': () => CATEGORIES,
    });
    ouvrir('/commandes/c1');
    // Un seul billet valable : libellé au singulier.
    await userEvent.click(await screen.findByRole('button', { name: 'Télécharger mon billet (PDF)' }));
    await waitFor(() => expect(telechargerBilletsPdf).toHaveBeenCalledTimes(1));
    expect(telechargerBilletsPdf).toHaveBeenCalledWith(
      expect.objectContaining({
        titre: 'Nuit du Mbalax',
        lieu: 'Grand Théâtre',
        commandeId: 'c1',
        billets: [{ code: 'BP-4F2A-91C0', categorie: 'Standard', qr: 'charge.signature' }],
      }),
    );
  });

  it("ne propose pas de PDF quand aucun billet n'est plus valable", async () => {
    stockage.set('c1', locale);
    backend({
      'GET /api/orders/c1/billets': () => [{ id: 'b2', categoryId: 'std', categorie: 'Standard', statut: 'SCANNE', qr: null }],
      'GET /api/orders/c1': () => ({ ...COMMANDE, status: 'PAYEE', payeeLe: '2026-10-30T10:02:00Z' }),
      'GET /api/events/e1/categories': () => CATEGORIES,
    });
    ouvrir('/commandes/c1');
    expect(await screen.findByText('Déjà utilisé')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Télécharger/ })).not.toBeInTheDocument();
  });

  it('affiche les billets payés avec leur QR et les garde pour le hors-ligne', async () => {
    stockage.set('c1', locale);
    backend({
      'GET /api/orders/c1/billets': () => [
        { id: 'b1', categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'charge.signature' },
        { id: 'b2', categoryId: 'std', categorie: 'Standard', statut: 'SCANNE', qr: null },
      ],
      'GET /api/orders/c1': () => ({ ...COMMANDE, status: 'PAYEE', payeeLe: '2026-10-30T10:02:00Z' }),
      'GET /api/events/e1/categories': () => CATEGORIES,
    });
    ouvrir('/commandes/c1');
    expect(await screen.findByRole('heading', { name: 'Vos billets' })).toBeInTheDocument();
    expect(await screen.findAllByRole('article')).toHaveLength(2);
    expect(screen.getAllByRole('img', { name: /QR code du billet/ })).toHaveLength(1);
    await waitFor(() => expect(stockage.get('c1')?.billets).toHaveLength(2));
  });

  it('propose de reprendre un paiement en attente, au montant de la commande', async () => {
    stockage.set('c1', locale);
    backend({
      'GET /api/orders/c1': () => COMMANDE,
      'GET /api/events/e1/categories': () => CATEGORIES,
      'POST /api/orders/c1/paiement': () => ({ moyen: 'WAVE', urlPaiement: 'https://pay.test/w', expireLe: COMMANDE.expireLe }),
    });
    ouvrir('/commandes/c1');
    expect(await screen.findByRole('heading', { name: 'Finaliser le paiement' })).toBeInTheDocument();
    expect(screen.getByRole('timer')).toBeInTheDocument();
    expect(await screen.findByText('2 × Standard')).toBeInTheDocument();
    // Après création, c'est le montant ferme du backend (montantTotalFcfa) qui s'affiche.
    await userEvent.click(screen.getByRole('button', { name: /Payer 10.000 FCFA avec Wave/ }));
    await waitFor(() => expect(allerPayer).toHaveBeenCalledWith('https://pay.test/w'));
  });

  it("n'affiche l'échec d'ouverture du paiement que tant que la commande l'attend", async () => {
    stockage.set('c1', locale);
    let statut = 'EN_ATTENTE_PAIEMENT';
    backend({
      'GET /api/orders/c1/billets': () => [],
      'GET /api/orders/c1': () => ({ ...COMMANDE, status: statut }),
      'GET /api/events/e1/categories': () => CATEGORIES,
    });
    const entree = { pathname: '/commandes/c1', state: { erreurPaiement: 'Service de paiement indisponible' } };
    const premier = render(
      <MemoryRouter initialEntries={[entree]}>
        <Routage />
      </MemoryRouter>,
    );
    expect(await screen.findByText('Service de paiement indisponible')).toBeInTheDocument();
    premier.unmount();

    // Même entrée d'historique (le state survit au rechargement), mais commande désormais payée.
    statut = 'PAYEE';
    render(
      <MemoryRouter initialEntries={[entree]}>
        <Routage />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { name: 'Vos billets' })).toBeInTheDocument();
    expect(screen.queryByText('Service de paiement indisponible')).not.toBeInTheDocument();
  });

  it('explique le remboursement manuel quand la commande est payée sans billet', async () => {
    stockage.set('c1', locale);
    backend({
      'GET /api/orders/c1': () => ({ ...COMMANDE, status: 'PAYEE_SANS_BILLET' }),
      'GET /api/events/e1/categories': () => CATEGORIES,
    });
    ouvrir('/commandes/c1');
    expect(await screen.findByText(/remboursement est traité manuellement/)).toBeInTheDocument();
  });

  it("dit clairement qu'une commande d'un autre appareil n'est pas lisible ici", async () => {
    ouvrir('/commandes/inconnue');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Commande introuvable sur cet appareil' }),
    ).toBeInTheDocument();
  });

  it('montre les billets enregistrés quand le réseau manque', async () => {
    stockage.set('c1', {
      ...locale,
      billets: [{ id: 'b1', categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'charge.signature' }],
    });
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    ouvrir('/commandes/c1');
    expect(await screen.findByText(/Pas de connexion/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /QR code du billet/ })).toBeInTheDocument();
  });
});

describe('Mes billets', () => {
  it("invite à découvrir les événements quand l'appareil n'a aucune commande", async () => {
    ouvrir('/billets');
    expect(await screen.findByRole('heading', { name: "Aucun billet pour l'instant" })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Découvrir les événements' })).toHaveAttribute('href', '/');
  });
});

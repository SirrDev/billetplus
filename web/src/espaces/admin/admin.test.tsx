import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons, lireJetons } from '@/commun/session';

/* Espace administrateur de bout en bout, backend simulé au niveau de fetch. */

const ADMIN = { id: 'ad1', email: 'admin@billetplus.sn', nomComplet: 'Admin', role: 'ADMIN' };

const COMMANDES = [
  {
    id: 'c1',
    eventId: 'e1',
    telephone: '+221771234567',
    montantFcfa: 25000,
    motif: 'EVENEMENT_ANNULE',
    payeeLe: '2099-11-14T21:00:00Z',
    moyenPaiement: 'WAVE',
    referencePaiement: 'UTP-123',
  },
  {
    id: 'c2',
    eventId: 'brouillon',
    telephone: '+221781112233',
    montantFcfa: 5000,
    motif: 'RESERVATION_EXPIREE',
    payeeLe: '2099-11-13T10:00:00Z',
    moyenPaiement: 'ORANGE_MONEY',
    referencePaiement: null,
  },
];

type Routes = Record<string, () => unknown>;

function backend(routes: Routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    const route = routes[`${init?.method ?? 'GET'} ${url.pathname}`];
    if (!route) return new Response(JSON.stringify({ message: 'Introuvable' }), { status: 404 });
    const corps = route();
    return corps instanceof Response ? corps : new Response(JSON.stringify(corps), { status: 200 });
  });
}

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

const espaces = (t: string | null | undefined) => (t ?? '').replace(/\s/g, ' ');

beforeEach(() => effacerJetons());

describe('Administration', () => {
  it("renvoie un anonyme vers la connexion de l'administrateur", async () => {
    backend({});
    ouvrir('/admin');
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument();
    expect(screen.getByText('Administration Billet+')).toBeInTheDocument();
  });

  it("refuse un organisateur, sans garder ses jetons sur l'appareil", async () => {
    backend({
      'POST /api/auth/login': () => ({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' }),
      'GET /api/auth/me': () => ({ ...ADMIN, role: 'ORGANISATEUR' }),
    });
    ouvrir('/admin/connexion');
    await userEvent.type(await screen.findByLabelText('E-mail'), 'orga@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText("Ce compte n'est pas un compte administrateur.")).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
  });

  it('liste les commandes à rembourser avec motif, téléphone, référence et total', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    const appels = backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/commandes-a-rembourser': () => COMMANDES,
      'GET /api/events/e1': () => ({ id: 'e1', titre: 'Nuit du Mbalax' }),
    });
    ouvrir('/admin');
    const tableau = await screen.findByRole('table', { name: 'Commandes à rembourser' });
    expect(tableau).toHaveTextContent('Nuit du Mbalax');
    expect(tableau).toHaveTextContent('Événement non visible');
    expect(tableau).toHaveTextContent('Événement annulé');
    expect(tableau).toHaveTextContent('Paiement reçu après la fin de la réservation');
    expect(espaces(tableau.textContent)).toContain('+221 77 123 45 67');
    expect(tableau).toHaveTextContent('UTP-123');
    expect(espaces(screen.getByText('Total à rembourser').nextElementSibling?.textContent)).toBe('30 000 FCFA');
    const liste = appels.mock.calls.find(([u]) => String(u) === '/api/admin/commandes-a-rembourser');
    expect((liste?.[1]?.headers as Record<string, string>).Authorization).toBe('Bearer a');
  });

  it('copie la référence du paiement en un geste', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    const writeText = vi.fn(async () => undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/commandes-a-rembourser': () => COMMANDES,
    });
    ouvrir('/admin');
    // Tableau (ordinateur) et cartes (téléphone) sont tous deux dans le DOM : jsdom n'applique pas les media queries.
    const [bouton] = await screen.findAllByRole('button', { name: 'Référence UTP-123 : copier' });
    await userEvent.click(bouton!);
    expect(writeText).toHaveBeenCalledWith('UTP-123');
    expect(await screen.findByText('Copié')).toBeInTheDocument();
  });

  it("dit qu'il n'y a rien à rembourser", async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    backend({ 'GET /api/auth/me': () => ADMIN, 'GET /api/admin/commandes-a-rembourser': () => [] });
    ouvrir('/admin');
    expect(await screen.findByRole('heading', { name: 'Aucune commande à rembourser' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Exporter/ })).not.toBeInTheDocument();
  });

  it('dit « Copie impossible » quand le presse-papiers est refusé', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn(async () => Promise.reject(new Error('refus'))) },
    });
    backend({ 'GET /api/auth/me': () => ADMIN, 'GET /api/admin/commandes-a-rembourser': () => COMMANDES });
    ouvrir('/admin');
    const [bouton] = await screen.findAllByRole('button', { name: 'Référence UTP-123 : copier' });
    await userEvent.click(bouton!);
    expect(await screen.findByText('Copie impossible')).toBeInTheDocument();
  });

  it('propose de réessayer quand le serveur échoue', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    let appels = 0;
    backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/commandes-a-rembourser': () =>
        ++appels === 1 ? new Response(JSON.stringify({ message: 'x' }), { status: 503 }) : [],
    });
    ouvrir('/admin');
    await userEvent.click(await screen.findByRole('button', { name: 'Réessayer' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Aucune commande à rembourser' })).toBeInTheDocument());
  });
});

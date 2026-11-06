import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';

/* Crédit prépayé de l'entrée libre (ADR 0010), backend simulé au niveau de fetch. */

const ORGANISATEUR = { id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga Test', role: 'ORGANISATEUR' };
const CREDIT = {
  soldeFcfa: 850,
  tarif: { seuilPlaces: 100, tarifPetitFcfa: 75, tarifGrandFcfa: 50 },
  rechargeMinFcfa: 1000,
  rechargeMaxFcfa: 1000000,
  mouvements: [
    { id: 'm2', type: 'ENTREE_LIBRE', montantFcfa: -150, orderId: 'c1', creeLe: '2026-11-06T10:05:00Z' },
    { id: 'm1', type: 'RECHARGE', montantFcfa: 1000, orderId: null, creeLe: '2026-11-06T10:00:00Z' },
  ],
  recharges: [
    { id: 'r2', montantFcfa: 2000, status: 'ANOMALIE', moyen: 'WAVE', creeLe: '2026-11-06T11:00:00Z', traiteeLe: '2026-11-06T11:01:00Z' },
    { id: 'r1', montantFcfa: 1000, status: 'PAYEE', moyen: 'WAVE', creeLe: '2026-11-06T10:00:00Z', traiteeLe: '2026-11-06T10:01:00Z' },
  ],
};

type Routes = Record<string, (init?: RequestInit) => unknown>;

function backend(routes: Routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    const route = routes[`${init?.method ?? 'GET'} ${url.pathname}`];
    if (!route) return new Response(JSON.stringify({ message: 'Introuvable' }), { status: 404 });
    const corps = route(init);
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

beforeEach(() => {
  effacerJetons();
  enregistrerJetons({ accessToken: 'acces', refreshToken: 'rafraichi' });
});

describe('Crédit entrée libre', () => {
  it('affiche le solde, les tarifs reçus du backend et l’historique', async () => {
    backend({ 'GET /api/auth/me': () => ORGANISATEUR, 'GET /api/credit': () => CREDIT });
    ouvrir('/organisateur/credit');

    expect(await screen.findByRole('heading', { level: 1, name: 'Crédit entrée libre' })).toBeInTheDocument();
    const solde = await screen.findByText(/^850/);
    expect(espaces(solde.textContent)).toBe('850 FCFA');
    expect(espaces(screen.getByText(/Chaque billet gratuit émis coûte/).textContent)).toMatch(
      /75 FCFA si l'événement a 100 places ou moins .* 50 FCFA au-delà/,
    );
    const mouvements = screen.getByRole('table', { name: 'Mouvements du crédit' });
    expect(espaces(mouvements.textContent)).toContain('− 150 FCFA');
    expect(espaces(mouvements.textContent)).toContain('+ 1 000 FCFA');
    expect(screen.getByText('À régulariser')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Crédit/ })).toHaveAttribute('href', '/organisateur/credit');
  });

  it('refuse un montant hors bornes sans rien envoyer, puis ouvre la recharge', async () => {
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/credit': () => CREDIT,
      'POST /api/credit/recharges': () => ({ rechargeId: 'r3', moyen: 'ORANGE_MONEY', url: 'https://pay.test/r3' }),
    });
    ouvrir('/organisateur/credit');

    const montant = await screen.findByLabelText(/^Montant \(FCFA\)/);
    await userEvent.type(montant, '500');
    await userEvent.type(screen.getByLabelText('Numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: 'Payer la recharge' }));
    expect(await screen.findByText(/Montant entier entre 1 000 FCFA et 1 000 000 FCFA/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);

    await userEvent.clear(montant);
    await userEvent.type(montant, '5000');
    await userEvent.click(screen.getByRole('radio', { name: /Orange Money/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Payer la recharge' }));

    await waitFor(() => expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(true));
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'POST');
    expect(JSON.parse(String(envoi?.[1]?.body))).toEqual({
      montantFcfa: 5000,
      moyen: 'ORANGE_MONEY',
      telephone: '+221771234567',
    });
  });
});

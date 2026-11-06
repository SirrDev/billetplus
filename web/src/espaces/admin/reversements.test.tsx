import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';

/* Reversements aux organisateurs (ADR 0009), backend simulé au niveau de fetch. */

const ADMIN = { id: 'ad1', email: 'admin@billetplus.sn', nomComplet: 'Admin', role: 'ADMIN' };
const recettes = (brut: number, commission: number, reverse: number) => ({
  commandes: 2,
  brutFcfa: brut,
  commissionFcfa: commission,
  netFcfa: brut - commission,
  reverseFcfa: reverse,
  resteAReverserFcfa: brut - commission - reverse,
});
const LIGNES = [
  { eventId: 'e1', evenementAnnule: false, recettes: recettes(20_000, 1_000, 15_000) },
  { eventId: 'e2', evenementAnnule: true, recettes: { ...recettes(0, 0, 9_500), commandes: 0 } },
];

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
  enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
});

describe('Reversements', () => {
  it('liste la part de chaque organisateur et signale un trop reversé', async () => {
    backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/reversements': () => LIGNES,
      'GET /api/events/e1': () => ({ id: 'e1', titre: 'Nuit du Mbalax' }),
      'GET /api/events/e2': () => ({ id: 'e2', titre: 'Festival annulé' }),
    });
    ouvrir('/admin/reversements');

    const tableau = await screen.findByRole('table', { name: 'Reversements par événement' });
    expect(await screen.findByText('Nuit du Mbalax')).toBeInTheDocument();
    expect(espaces(tableau.textContent)).toContain('19 000 FCFA');
    expect(espaces(tableau.textContent)).toContain('4 000 FCFA');
    expect(espaces(tableau.textContent)).toContain('Trop reversé : 9 500 FCFA');
    expect(tableau).toHaveTextContent('Annulé');
    expect(screen.getByRole('link', { name: 'Remboursements' })).toHaveAttribute('href', '/admin');
  });

  it('enregistre un virement prérempli avec le reste à reverser', async () => {
    const appels = backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/reversements': () => LIGNES,
      'GET /api/events/e1': () => ({ id: 'e1', titre: 'Nuit du Mbalax' }),
      'POST /api/admin/reversements': () => ({ id: 'r1' }),
    });
    ouvrir('/admin/reversements');

    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer un virement pour Nuit du Mbalax' }));
    expect(screen.getByLabelText('Montant viré (FCFA)')).toHaveValue('4000');
    await userEvent.type(screen.getByLabelText('Référence du virement'), 'WAVE-SOLDE');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le virement' }));

    expect(await screen.findByText(/Virement de 4\s000 FCFA enregistré pour Nuit du Mbalax/)).toBeInTheDocument();
    const envoi = appels.mock.calls.find(([u, i]) => String(u) === '/api/admin/reversements' && i?.method === 'POST');
    const corps = JSON.parse(String(envoi?.[1]?.body)) as Record<string, unknown>;
    expect(corps).toMatchObject({ eventId: 'e1', montantFcfa: 4000, reference: 'WAVE-SOLDE', motif: null });
    expect(corps.reverseLe).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(corps).not.toHaveProperty('enregistrePar');
  });

  it("refuse une correction négative sans motif, sans rien envoyer", async () => {
    const appels = backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/reversements': () => LIGNES,
      'GET /api/events/e1': () => ({ id: 'e1', titre: 'Nuit du Mbalax' }),
    });
    ouvrir('/admin/reversements');

    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer un virement pour Nuit du Mbalax' }));
    const montant = screen.getByLabelText('Montant viré (FCFA)');
    await userEvent.clear(montant);
    await userEvent.type(montant, '-500');
    await userEvent.type(screen.getByLabelText('Référence du virement'), 'CORR');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le virement' }));

    expect(await screen.findByText('Une correction (montant négatif) doit être motivée.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);
  });
});

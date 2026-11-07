import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';

/* Vérification des organisateurs et conditions du virement (ADR 0012), côté administrateur. */

const ADMIN = { id: 'ad1', email: 'admin@billetplus.sn', nomComplet: 'Admin', role: 'ADMIN' };
const DEMANDE = {
  organisateurId: 'o1',
  nomComplet: 'Awa Diop',
  email: 'awa@test.sn',
  statut: 'EN_REVUE',
  typeTitulaire: 'PERSONNE',
  nomStructure: null,
  ninea: null,
  numeroReversement: '+221771234567',
  titulaireReversement: 'Awa Diop',
  motif: null,
  soumisLe: '2026-11-06T10:00:00Z',
  decideLe: null,
  piecesExigees: ['IDENTITE', 'JUSTIFICATIF_REVERSEMENT'],
  pieces: [{ type: 'IDENTITE', typeMime: 'application/pdf', taille: 10, deposeLe: '2026-11-06T10:00:00Z' }],
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

beforeEach(() => {
  effacerJetons();
  enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
});

describe('Vérification des organisateurs', () => {
  it('signale la pièce manquante, exige un motif pour refuser et accepte une demande', async () => {
    const appels = backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/verifications': () => [DEMANDE],
      'POST /api/admin/verifications/o1/acceptation': () => ({ ...DEMANDE, statut: 'VERIFIE' }),
    });
    ouvrir('/admin/verifications');

    expect(await screen.findByRole('heading', { name: 'Awa Diop' })).toBeInTheDocument();
    expect(screen.getByText('+221771234567')).toBeInTheDocument();
    expect(screen.getByText('Manque : Justificatif du numéro')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: "Télécharger : Pièce d'identité" })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Vérifications' })).toHaveAttribute('href', '/admin/verifications');

    await userEvent.click(screen.getByRole('button', { name: 'Refuser' }));
    expect(screen.getByText(/Indiquez le motif/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Accepter' }));
    expect(await screen.findByText('Awa Diop est vérifié.')).toBeInTheDocument();
  });
});

describe('Conditions du virement', () => {
  const ligne = {
    eventId: 'e1',
    evenementAnnule: false,
    recettes: { commandes: 1, brutFcfa: 10000, commissionFcfa: 500, netFcfa: 9500, reverseFcfa: 0, resteAReverserFcfa: 9500 },
    reversableLe: '2026-11-01',
    billetsScannes: 0,
    numeroReversement: '+221771234567',
    titulaireReversement: 'Awa Diop',
  };

  it('affiche le numéro vérifié et exige une dérogation quand aucun billet n’a été scanné', async () => {
    const appels = backend({
      'GET /api/auth/me': () => ADMIN,
      'GET /api/admin/reversements': () => [ligne],
      'GET /api/events/e1': () => ({ id: 'e1', titre: 'Nuit du Mbalax' }),
      'POST /api/admin/reversements': () => ({ id: 'r1' }),
    });
    ouvrir('/admin/reversements');

    expect(await screen.findByText('Aucun billet scanné')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer un virement pour Nuit du Mbalax' }));
    expect(screen.getByText('+221771234567', { selector: 'strong' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Référence du virement'), 'WAVE-1');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le virement' }));
    expect(await screen.findByText(/motivez la dérogation/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);

    await userEvent.type(screen.getByLabelText(/^Dérogation/), 'Entrée libre sans contrôle');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le virement' }));
    expect(await screen.findByText(/Virement de 9\s500 FCFA enregistré/)).toBeInTheDocument();
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'POST');
    expect(JSON.parse(String(envoi?.[1]?.body))).toMatchObject({ derogationScans: 'Entrée libre sans contrôle' });
  });
});

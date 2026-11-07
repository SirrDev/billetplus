import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';

/* Vérification du compte organisateur (ADR 0012), backend simulé au niveau de fetch. */

const ORGANISATEUR = { id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga Test', role: 'ORGANISATEUR' };
const A_VERIFIER = {
  organisateurId: 'o1',
  statut: 'A_VERIFIER',
  typeTitulaire: null,
  nomStructure: null,
  ninea: null,
  numeroReversement: null,
  titulaireReversement: null,
  motif: null,
  soumisLe: null,
  decideLe: null,
  piecesExigees: ['IDENTITE', 'JUSTIFICATIF_REVERSEMENT'],
  pieces: [] as Array<{ type: string; typeMime: string; taille: number; deposeLe: string }>,
};
const DECLAREE = { ...A_VERIFIER, typeTitulaire: 'PERSONNE', numeroReversement: '+221771234567', titulaireReversement: 'Awa Diop' };
const piece = (type: string) => ({ type, typeMime: 'application/pdf', taille: 10, deposeLe: '2026-11-06T10:00:00Z' });

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
  enregistrerJetons({ accessToken: 'acces', refreshToken: 'rafraichi' });
});

describe('Vérification du compte organisateur', () => {
  it('déclare le titulaire, dépose les pièces puis demande la vérification', async () => {
    let etat: typeof A_VERIFIER | typeof DECLAREE = A_VERIFIER;
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/verification': () => etat,
      'PUT /api/verification': () => (etat = DECLAREE),
      'POST /api/verification/pieces/IDENTITE': () => (etat = { ...etat, pieces: [piece('IDENTITE')] }),
      'POST /api/verification/pieces/JUSTIFICATIF_REVERSEMENT': () =>
        (etat = { ...etat, pieces: [...etat.pieces, piece('JUSTIFICATIF_REVERSEMENT')] }),
      'POST /api/verification/soumission': () => (etat = { ...etat, statut: 'EN_REVUE' }),
    });
    ouvrir('/organisateur/verification');

    expect(await screen.findByText('À vérifier')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Numéro de reversement'), '771234567');
    await userEvent.type(screen.getByLabelText(/^Titulaire du numéro/), 'Awa Diop');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText('Informations enregistrées.')).toBeInTheDocument();
    const declaration = appels.mock.calls.find(([, i]) => i?.method === 'PUT');
    expect(JSON.parse(String(declaration?.[1]?.body))).toEqual({
      typeTitulaire: 'PERSONNE',
      nomStructure: null,
      ninea: null,
      numeroReversement: '+221771234567',
      titulaireReversement: 'Awa Diop',
    });

    const demander = screen.getByRole('button', { name: 'Demander la vérification' });
    expect(demander).toBeDisabled();
    const pdf = new File(['%PDF-1.4'], 'cni.pdf', { type: 'application/pdf' });
    await userEvent.upload(screen.getByLabelText("Pièce d'identité : choisir le fichier"), pdf);
    await userEvent.upload(screen.getByLabelText('Justificatif du numéro de reversement : choisir le fichier'), pdf);
    await waitFor(() => expect(demander).toBeEnabled());
    const depot = appels.mock.calls.find(([u]) => String(u).endsWith('/verification/pieces/IDENTITE'));
    expect(depot?.[1]?.body).toBeInstanceOf(FormData);
    // Le navigateur fixe lui-même le Content-Type multipart (avec sa délimitation).
    expect((depot?.[1]?.headers as Record<string, string>)['Content-Type']).toBeUndefined();

    await userEvent.click(demander);
    expect(await screen.findByText('En cours de vérification')).toBeInTheDocument();
  });

  it('refuse un fichier de plus de 5 Mo sans l’envoyer', async () => {
    const appels = backend({ 'GET /api/auth/me': () => ORGANISATEUR, 'GET /api/verification': () => DECLAREE });
    ouvrir('/organisateur/verification');
    const gros = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'gros.pdf', { type: 'application/pdf' });
    await userEvent.upload(await screen.findByLabelText("Pièce d'identité : choisir le fichier"), gros);
    expect(await screen.findByText('Le fichier doit faire 5 Mo au plus.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);
  });

  it('sur un brouillon, remplace « Publier » par un lien vers la vérification', async () => {
    backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/verification': () => A_VERIFIER,
      'GET /api/events/e1': () => ({
        id: 'e1',
        titre: 'Nuit du Mbalax',
        description: null,
        debuteLe: '2099-11-14T21:00:00Z',
        termineLe: null,
        venueId: 'v1',
        venueNom: 'Sorano',
        organizerId: 'o1',
        status: 'BROUILLON',
        creeLe: '2026-11-01T00:00:00Z',
        categorie: 'MUSIQUE',
        villeCode: 'SN-DAKAR',
        villeNom: 'Dakar',
        pays: 'SN',
        paysNom: 'Sénégal',
        suspenduMotif: null,
      }),
      'GET /api/events/e1/categories': () => [{ id: 'c1', nom: 'Standard', prixFcfa: 5000, quotaTotal: 50, quotaVendu: 0 }],
      'GET /api/events/e1/tableau-de-bord': () => ({ eventId: 'e1', categories: [], vendus: 0, enCoursDePaiement: 0, scannes: 0, chiffreAffairesFcfa: 0 }),
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/e1');
    expect(await screen.findByRole('link', { name: 'Faire vérifier mon compte' })).toHaveAttribute(
      'href',
      '/organisateur/verification',
    );
    expect(screen.queryByRole('button', { name: 'Publier' })).not.toBeInTheDocument();
  });

  it('montre le motif d’un refus', async () => {
    backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/verification': () => ({ ...DECLAREE, statut: 'REFUSE', motif: 'Pièce illisible' }),
    });
    ouvrir('/organisateur/verification');
    expect(await screen.findByText('Refusé')).toBeInTheDocument();
    expect(screen.getByText('Pièce illisible')).toBeInTheDocument();
  });
});

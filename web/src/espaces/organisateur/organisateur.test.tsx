import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons, lireJetons } from '@/commun/session';

/* Espace organisateur de bout en bout, backend simulé au niveau de fetch. */

/*
 * Fausse carte : jsdom ne dessine rien. Elle garde l'écouteur de clic et les positions du
 * repère, pour vérifier le lien carte <-> champ. Le vrai Leaflet se vérifie dans un navigateur.
 */
const carte = vi.hoisted(() => ({
  clic: null as null | ((e: unknown) => void),
  repere: [] as unknown[],
}));
vi.mock('./pages/carteLeaflet', () => {
  const map = {
    setView: () => map,
    on: (_: string, f: (e: unknown) => void) => {
      carte.clic = f;
      return map;
    },
    remove: () => undefined,
    getBounds: () => ({ contains: () => true }),
    getZoom: () => 12,
  };
  const marker = (position: unknown) => {
    carte.repere.push(position);
    const m = {
      addTo: () => m,
      on: () => m,
      remove: () => undefined,
      setLatLng: (p: unknown) => {
        carte.repere.push(p);
        return m;
      },
    };
    return m;
  };
  return { L: { map: () => map, tileLayer: () => ({ addTo: () => undefined }), divIcon: () => ({}), marker } };
});
const cliquerCarte = (lat: number, lng: number) => {
  act(() => carte.clic?.({ latlng: { wrap: () => ({ lat, lng }) } }));
};

const ORGANISATEUR = { id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga Test', role: 'ORGANISATEUR' };
/* Liste des villes (ADR 0011), telle que GET /api/villes la rend (extrait). */
const VILLES = [
  { code: 'SN', nom: 'Sénégal', villes: [{ code: 'SN-DAKAR', nom: 'Dakar' }, { code: 'SN-THIES', nom: 'Thiès' }] },
  { code: 'CI', nom: "Côte d'Ivoire", villes: [{ code: 'CI-ABIDJAN', nom: 'Abidjan' }] },
];

const EVENEMENT = {
  id: '11111111-1111-1111-1111-111111111111',
  titre: 'Nuit du Mbalax',
  description: 'Une nuit de mbalax live.',
  debuteLe: '2099-11-14T21:00:00Z',
  termineLe: null,
  venueId: 'v1',
  venueNom: 'Grand Théâtre',
  organizerId: 'o1',
  status: 'BROUILLON',
  creeLe: '2026-10-30T10:00:00Z',
  categorie: 'MUSIQUE',
};
const TABLEAU_VIDE = { eventId: EVENEMENT.id, categories: [], vendus: 0, enCoursDePaiement: 0, scannes: 0, chiffreAffairesFcfa: 0 };

type Reponse = unknown | Response;
type Routes = Record<string, (init?: RequestInit) => Reponse>;

/** Clé « METHODE /chemin » exacte (sans la chaîne de requête). */
function backend(routes: Routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    const cle = `${init?.method ?? 'GET'} ${url.pathname}`;
    const route = routes[cle];
    if (!route) return new Response(JSON.stringify({ message: `Route inattendue ${cle}` }), { status: 500 });
    const corps = route(init);
    if (corps instanceof Response) return corps;
    return corps === undefined ? new Response(null, { status: 204 }) : new Response(JSON.stringify(corps), { status: 200 });
  });
}

const erreur = (status: number, message: string) => new Response(JSON.stringify({ status, message }), { status });

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

function connecte() {
  enregistrerJetons({ accessToken: 'acces', refreshToken: 'rafraichi' });
}

const jetonDe = (init?: RequestInit) => (init?.headers as Record<string, string> | undefined)?.Authorization;
const corpsDe = (init?: RequestInit) => JSON.parse(String(init?.body)) as unknown;

beforeEach(() => {
  effacerJetons();
  carte.clic = null;
  carte.repere = [];
});

describe('Connexion', () => {
  it("renvoie un anonyme vers la connexion, puis vers l'espace après connexion", async () => {
    const appels = backend({
      'POST /api/auth/login': () => ({ accessToken: 'acces', refreshToken: 'rafraichi', tokenType: 'Bearer' }),
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/events/mine': () => [],
    });
    ouvrir('/organisateur');
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('E-mail'), 'orga@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('heading', { name: "Aucun événement pour l'instant" })).toBeInTheDocument();
    expect(lireJetons()).toEqual({ accessToken: 'acces', refreshToken: 'rafraichi' });
    const liste = appels.mock.calls.find(([u]) => String(u) === '/api/events/mine');
    expect(jetonDe(liste?.[1])).toBe('Bearer acces');
  });

  it("n'indique pas si l'e-mail existe quand les identifiants sont faux", async () => {
    backend({ 'POST /api/auth/login': () => erreur(401, 'Identifiants invalides') });
    ouvrir('/organisateur/connexion');
    await userEvent.type(screen.getByLabelText('E-mail'), 'x@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'mauvais');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText('E-mail ou mot de passe incorrect.')).toBeInTheDocument();
  });

  it("refuse l'espace à un compte qui n'est pas organisateur", async () => {
    backend({
      'POST /api/auth/login': () => ({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' }),
      'GET /api/auth/me': () => ({ ...ORGANISATEUR, role: 'AGENT' }),
    });
    ouvrir('/organisateur/connexion');
    await userEvent.type(screen.getByLabelText('E-mail'), 'agent@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText("Ce compte n'est pas un compte organisateur.")).toBeInTheDocument();
    // Les jetons du compte refusé ne restent pas sur l'appareil.
    expect(lireJetons()).toBeNull();
  });

  it("vérifie la longueur du mot de passe avant l'inscription", async () => {
    const appels = backend({});
    ouvrir('/organisateur/inscription');
    await userEvent.type(screen.getByLabelText('Nom ou structure'), 'Orga');
    await userEvent.type(screen.getByLabelText('E-mail'), 'orga@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'court');
    await userEvent.click(screen.getByRole('button', { name: 'Créer mon compte' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Entre 8 et 72 caractères.');
    expect(appels).not.toHaveBeenCalled();
  });

  it.each(['/organisateur/connexion', '/organisateur/inscription'])("propose un retour à l'accueil sur %s", async (adresse) => {
    backend({});
    ouvrir(adresse);
    expect(await screen.findByRole('link', { name: "Retour à l'accueil" })).toHaveAttribute('href', '/');
  });
});

describe('Création et gestion d’un événement', () => {
  it("crée le lieu puis l'événement, à l'heure de Dakar, sans organisateur dans le corps", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/villes': () => VILLES,
      'POST /api/venues': () => ({ id: 'v1', nom: 'Grand Théâtre', adresse: 'Dakar', latitude: null, longitude: null, seatingPlan: null }),
      'POST /api/events': () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}`]: () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/nouveau');
    await userEvent.selectOptions(await screen.findByLabelText('Ville'), 'SN-DAKAR');

    await userEvent.type(await screen.findByLabelText('Titre'), 'Nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'Grand Théâtre');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Dakar');
    await userEvent.selectOptions(screen.getByLabelText('Catégorie'), 'SPECTACLE');
    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText(/Brouillon créé/)).toBeInTheDocument();
    const creation = appels.mock.calls.find(([u, i]) => String(u) === '/api/events' && i?.method === 'POST');
    expect(corpsDe(creation?.[1])).toEqual({
      titre: 'Nuit du Mbalax',
      description: null,
      debuteLe: '2099-11-14T21:00:00Z',
      termineLe: null,
      venueId: 'v1',
      categorie: 'SPECTACLE',
    });
    expect(jetonDe(creation?.[1])).toBe('Bearer acces');
  });

  it('refuse une fin avant le début sans rien envoyer', async () => {
    connecte();
    const appels = backend({ 'GET /api/auth/me': () => ORGANISATEUR, 'GET /api/villes': () => VILLES });
    ouvrir('/organisateur/evenements/nouveau');
    await userEvent.selectOptions(await screen.findByLabelText('Ville'), 'SN-DAKAR');
    await userEvent.type(await screen.findByLabelText('Titre'), 'X');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    fireEvent.change(screen.getByLabelText(/^Fin/), { target: { value: '2099-11-14T20:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'L');
    await userEvent.type(screen.getByLabelText('Adresse'), 'A');
    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));
    expect(screen.getByRole('alert')).toHaveTextContent('La fin ne peut pas précéder le début.');
    expect(appels.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);
  });

  it('ajoute une catégorie, publie, et affiche les ventes', async () => {
    connecte();
    let categories: unknown[] = [];
    let evenement = { ...EVENEMENT };
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      [`GET /api/events/${EVENEMENT.id}`]: () => evenement,
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => categories,
      [`POST /api/events/${EVENEMENT.id}/categories`]: (init) => {
        const c = { id: 'c1', disponible: true, quotaVendu: 0, ...(corpsDe(init) as object) };
        categories = [c];
        return c;
      },
      [`POST /api/events/${EVENEMENT.id}/publier`]: () => (evenement = { ...evenement, status: 'PUBLIE' }),
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => ({
        ...TABLEAU_VIDE,
        vendus: 30,
        chiffreAffairesFcfa: 150000,
        categories: categories.length
          ? [{ categoryId: 'c1', nom: 'Standard', prixFcfa: 5000, quotaTotal: 100, vendus: 30, enCoursDePaiement: 2, disponibles: 68, scannes: 0, annules: 0, chiffreAffairesFcfa: 150000 }]
          : [],
      }),
      'GET /api/agents': () => [],
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText('Brouillon')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publier' })).toBeDisabled();

    const formulaire = screen.getByRole('region', { name: 'Ajouter une catégorie' });
    await userEvent.type(within(formulaire).getByLabelText('Nom'), 'Standard');
    await userEvent.type(within(formulaire).getByLabelText('Prix (FCFA)'), '5000');
    await userEvent.type(within(formulaire).getByLabelText('Places'), '100');
    await userEvent.click(within(formulaire).getByRole('button', { name: 'Ajouter la catégorie' }));

    const ajout = appels.mock.calls.find(([u, i]) => String(u).endsWith('/categories') && i?.method === 'POST');
    expect(corpsDe(ajout?.[1])).toEqual({ nom: 'Standard', prixFcfa: 5000, quotaTotal: 100 });

    expect(await screen.findByRole('table', { name: 'Par catégorie' })).toHaveTextContent('30 / 100');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Publier' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Publier' }));
    expect(await screen.findByText('Événement publié : il est en vente.')).toBeInTheDocument();
    expect(screen.getByText('En vente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir la page publique' })).toHaveAttribute('href', `/evenements/${EVENEMENT.id}`);
    expect(screen.getByRole('progressbar', { name: /Billets vendus/ })).toHaveAttribute('aria-valuenow', '30');
  });

  it("demande confirmation avant d'annuler un événement", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      [`GET /api/events/${EVENEMENT.id}`]: () => ({ ...EVENEMENT, status: 'PUBLIE' }),
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      [`POST /api/events/${EVENEMENT.id}/annuler`]: () => ({ ...EVENEMENT, status: 'ANNULE' }),
      'GET /api/agents': () => [],
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    await userEvent.click(await screen.findByRole('button', { name: "Annuler l'événement" }));
    expect(appels.mock.calls.some(([u]) => String(u).endsWith('/annuler'))).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: "Oui, annuler l'événement" }));
    expect(await screen.findByText('Événement annulé.')).toBeInTheDocument();
    expect(screen.getByText('Annulé')).toBeInTheDocument();
  });

  it("répond « introuvable » pour l'événement d'un autre organisateur", async () => {
    connecte();
    backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      [`GET /api/events/${EVENEMENT.id}`]: () => erreur(404, 'Evenement introuvable'),
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => erreur(404, 'Evenement introuvable'),
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    expect(await screen.findByRole('heading', { level: 1, name: 'Événement introuvable' })).toBeInTheDocument();
  });

  it("n'affiche pas la gestion d'un événement publié d'un autre organisateur", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      // Route publique : un événement publié répond 200 à tout le monde.
      [`GET /api/events/${EVENEMENT.id}`]: () => ({ ...EVENEMENT, status: 'PUBLIE', organizerId: 'quelqu-un-d-autre' }),
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    expect(await screen.findByRole('heading', { level: 1, name: 'Événement introuvable' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: "Annuler l'événement" })).not.toBeInTheDocument();
    expect(appels.mock.calls.some(([u]) => String(u).endsWith('/tableau-de-bord'))).toBe(false);
  });

  it('affecte un agent au contrôle et le dit', async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      [`GET /api/events/${EVENEMENT.id}`]: () => ({ ...EVENEMENT, status: 'PUBLIE' }),
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      'GET /api/agents': () => [{ id: 'a1', email: 'agent@test.sn', nomComplet: 'Awa Agent', creeLe: '2026-10-30T10:00:00Z' }],
      [`GET /api/events/${EVENEMENT.id}/agents`]: () => [],
      [`PUT /api/events/${EVENEMENT.id}/agents/a1`]: () => undefined,
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    await userEvent.selectOptions(await screen.findByLabelText('Agent'), 'a1');
    await userEvent.click(screen.getByRole('button', { name: 'Affecter' }));
    expect(await screen.findByText('Awa Agent peut contrôler les entrées de cet événement.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([u, i]) => String(u).endsWith('/agents/a1') && i?.method === 'PUT')).toBe(true);
  });
});

describe('Recettes et commission', () => {
  const routesEvenement = (recettes: object, evenement: object = EVENEMENT) => ({
    'GET /api/auth/me': () => ORGANISATEUR,
    [`GET /api/events/${EVENEMENT.id}`]: () => evenement,
    [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
    [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
    [`GET /api/events/${EVENEMENT.id}/recettes`]: () => recettes,
    [`GET /api/events/${EVENEMENT.id}/agents`]: () => [],
    'GET /api/agents': () => [],
  });

  it("affiche la part de l'organisateur, commission déduite, et le reste à recevoir", async () => {
    connecte();
    backend(
      routesEvenement(
        { commandes: 2, brutFcfa: 13333, commissionFcfa: 466, netFcfa: 12867, reverseFcfa: 10000, resteAReverserFcfa: 2867 },
        { ...EVENEMENT, status: 'PUBLIE' },
      ),
    );
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);

    const panneau = await screen.findByRole('region', { name: 'Vos recettes' });
    const texte = (panneau.textContent ?? '').replace(/\s/g, ' ');
    expect(texte).toContain('Ventes encaissées (2 commandes)');
    expect(texte).toContain('13 333 FCFA');
    expect(texte).toContain('− 466 FCFA');
    expect(texte).toContain('12 867 FCFA');
    expect(texte).toContain('Reste à recevoir');
    expect(texte).toContain('2 867 FCFA');
  });

  it("annonce un trop perçu après l'annulation", async () => {
    connecte();
    backend(
      routesEvenement(
        { commandes: 0, brutFcfa: 0, commissionFcfa: 0, netFcfa: 0, reverseFcfa: 9500, resteAReverserFcfa: -9500 },
        { ...EVENEMENT, status: 'ANNULE' },
      ),
    );
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);

    const panneau = await screen.findByRole('region', { name: 'Vos recettes' });
    const texte = (panneau.textContent ?? '').replace(/\s/g, ' ');
    expect(texte).toContain('Trop perçu');
    expect(texte).toContain('9 500 FCFA');
    expect(texte).toContain('Événement annulé');
  });

  it("n'affiche rien tant qu'il n'y a ni vente ni reversement", async () => {
    connecte();
    backend(routesEvenement({ commandes: 0, brutFcfa: 0, commissionFcfa: 0, netFcfa: 0, reverseFcfa: 0, resteAReverserFcfa: 0 }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' });
    expect(screen.queryByRole('region', { name: 'Vos recettes' })).not.toBeInTheDocument();
  });
});

describe('Agents affectés à un événement', () => {
  const AWA = { id: 'a1', email: 'awa@test.sn', nomComplet: 'Awa Agent', creeLe: '2026-10-30T10:00:00Z' };
  const MOUSSA = { id: 'a2', email: 'moussa@test.sn', nomComplet: 'Moussa Diop', creeLe: '2026-10-30T10:00:00Z' };

  it("liste les affectés, retire en un clic et ne propose que les autres à l'affectation", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      [`GET /api/events/${EVENEMENT.id}`]: () => ({ ...EVENEMENT, status: 'PUBLIE' }),
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      'GET /api/agents': () => [AWA, MOUSSA],
      [`GET /api/events/${EVENEMENT.id}/agents`]: () => ['a1'],
      [`DELETE /api/events/${EVENEMENT.id}/agents/a1`]: () => undefined,
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);

    const liste = await screen.findByRole('list', { name: 'Agents affectés' });
    expect(within(liste).getByText('Awa Agent')).toBeInTheDocument();
    expect(within(liste).queryByText('Moussa Diop')).not.toBeInTheDocument();
    const choix = screen.getByLabelText('Agent');
    expect(within(choix).queryByRole('option', { name: /Awa Agent/ })).not.toBeInTheDocument();
    expect(within(choix).getByRole('option', { name: /Moussa Diop/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Retirer Awa Agent de cet événement' }));
    expect(await screen.findByText('Awa Agent ne contrôle plus cet événement.')).toBeInTheDocument();
    expect(screen.getByText('Aucun agent affecté à cet événement.')).toBeInTheDocument();
    expect(within(screen.getByLabelText('Agent')).getByRole('option', { name: /Awa Agent/ })).toBeInTheDocument();
    expect(appels.mock.calls.some(([u, i]) => String(u).endsWith('/agents/a1') && i?.method === 'DELETE')).toBe(true);
  });
});

describe('Position GPS sur la carte', () => {
  const LIEU_SANS_POSITION = { id: 'v1', nom: 'Grand Théâtre', adresse: 'Dakar', latitude: null, longitude: null, seatingPlan: null };

  it('envoie à la création la position choisie en touchant la carte', async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/villes': () => VILLES,
      'POST /api/venues': () => LIEU_SANS_POSITION,
      'POST /api/events': () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}`]: () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/nouveau');
    await userEvent.selectOptions(await screen.findByLabelText('Ville'), 'SN-DAKAR');
    await userEvent.type(await screen.findByLabelText('Titre'), 'Nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'Grand Théâtre');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Dakar');

    await waitFor(() => expect(carte.clic).not.toBeNull());
    cliquerCarte(14.7, -17.45);
    expect(screen.getByLabelText(/^Coordonnées GPS/)).toHaveValue('14.700000, -17.450000');
    await waitFor(() => expect(carte.repere).toHaveLength(1));

    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));
    await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' });
    const lieu = appels.mock.calls.find(([u, i]) => String(u) === '/api/venues' && i?.method === 'POST');
    expect(corpsDe(lieu?.[1])).toMatchObject({ latitude: 14.7, longitude: -17.45 });
  });

  const routesModification = (lieu: object, evenement: object = EVENEMENT) => ({
    'GET /api/auth/me': () => ORGANISATEUR,
    [`GET /api/events/${EVENEMENT.id}`]: () => evenement,
    [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
    [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
    'GET /api/agents': () => [],
    'GET /api/venues/v1': () => lieu,
    [`PUT /api/events/${EVENEMENT.id}`]: () => evenement,
    [`PUT /api/events/${EVENEMENT.id}/position`]: () => evenement,
  });
  const appelPosition = (appels: ReturnType<typeof backend>) =>
    appels.mock.calls.find(([u, i]) => String(u).endsWith('/position') && i?.method === 'PUT');

  it('fige la position une fois l’événement publié : pas de carte, rien envoyé', async () => {
    connecte();
    const appels = backend(
      routesModification({ ...LIEU_SANS_POSITION, latitude: 14.69, longitude: -17.44 }, { ...EVENEMENT, status: 'PUBLIE' }),
    );
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    expect(await screen.findByText(/La position du lieu est fixée depuis la publication/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Coordonnées GPS/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await screen.findByText('Modifications enregistrées.');
    expect(appelPosition(appels)).toBeUndefined();
  });

  it('le dit clairement quand un événement publié n a jamais eu de position', async () => {
    connecte();
    backend(routesModification(LIEU_SANS_POSITION, { ...EVENEMENT, status: 'PUBLIE' }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    expect(await screen.findByText(/Aucune position n.a été définie avant la publication/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Coordonnées GPS/)).not.toBeInTheDocument();
  });

  it('ajoute une position à un brouillon créé sans elle', async () => {
    connecte();
    const appels = backend(routesModification(LIEU_SANS_POSITION));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    await screen.findByLabelText(/^Coordonnées GPS/);
    await waitFor(() => expect(carte.clic).not.toBeNull());
    cliquerCarte(14.7, -17.45);
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await screen.findByText('Modifications enregistrées.');
    expect(corpsDe(appelPosition(appels)?.[1])).toEqual({ latitude: 14.7, longitude: -17.45 });
  });

  it("préremplit la position et ne l'envoie pas si elle n'a pas changé", async () => {
    connecte();
    const appels = backend(routesModification({ ...LIEU_SANS_POSITION, latitude: 14.6928123, longitude: -17.4467 }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    expect(await screen.findByLabelText(/^Coordonnées GPS/)).toHaveValue('14.6928123, -17.4467');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await screen.findByText('Modifications enregistrées.');
    expect(appelPosition(appels)).toBeUndefined();
  });

  it('refuse de retirer une position existante, sans rien envoyer', async () => {
    connecte();
    const appels = backend(routesModification({ ...LIEU_SANS_POSITION, latitude: 14.69, longitude: -17.44 }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    await userEvent.clear(await screen.findByLabelText(/^Coordonnées GPS/));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText('La position peut être déplacée, mais pas retirée.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'PUT')).toBe(false);
  });
});

describe('Modification d’un événement', () => {
  const routesPage = (evenement: object) => ({
    'GET /api/auth/me': () => ORGANISATEUR,
    [`GET /api/events/${EVENEMENT.id}`]: () => evenement,
    [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
    [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
    'GET /api/agents': () => [],
  });

  it('modifie un brouillon, dates comprises, puis revient à la page de l’événement', async () => {
    connecte();
    let actuel: Record<string, unknown> = { ...EVENEMENT, termineLe: '2099-11-15T02:00:00Z' };
    const appels = backend({
      ...routesPage(actuel),
      [`GET /api/events/${EVENEMENT.id}`]: () => actuel,
      [`PUT /api/events/${EVENEMENT.id}`]: (init) => {
        actuel = { ...actuel, ...(corpsDe(init) as object) };
        return actuel;
      },
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    await userEvent.click(await screen.findByRole('link', { name: 'Modifier' }));

    const titre = await screen.findByLabelText('Titre');
    expect(titre).toHaveValue('Nuit du Mbalax');
    expect(screen.getByLabelText('Début')).toHaveValue('2099-11-14T21:00');
    expect(screen.getByLabelText(/^Fin/)).toHaveValue('2099-11-15T02:00');
    await userEvent.clear(titre);
    await userEvent.type(titre, 'Grande nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-21T21:00' } });
    fireEvent.change(screen.getByLabelText(/^Fin/), { target: { value: '' } });
    // Préremplie avec la catégorie de l'événement, puis changée.
    expect(screen.getByLabelText('Catégorie')).toHaveValue('MUSIQUE');
    await userEvent.selectOptions(screen.getByLabelText('Catégorie'), 'FESTIVAL');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Grande nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText('Modifications enregistrées.')).toBeInTheDocument();
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'PUT');
    expect(corpsDe(envoi?.[1])).toEqual({
      titre: 'Grande nuit du Mbalax',
      description: 'Une nuit de mbalax live.',
      debuteLe: '2099-11-21T21:00:00Z',
      termineLe: null,
      categorie: 'FESTIVAL',
    });
    expect(jetonDe(envoi?.[1])).toBe('Bearer acces');
  });

  it('refuse une fin avant le début sans rien envoyer', async () => {
    connecte();
    const appels = backend(routesPage(EVENEMENT));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    fireEvent.change(await screen.findByLabelText(/^Fin/), { target: { value: '2099-11-14T20:00' } });
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText('La fin ne peut pas précéder le début.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([, i]) => i?.method === 'PUT')).toBe(false);
  });

  it('pour un événement publié, affiche les dates sans champ et les renvoie telles que reçues', async () => {
    connecte();
    const publie = { ...EVENEMENT, status: 'PUBLIE', debuteLe: '2099-11-14T21:00:00.123456Z' };
    const appels = backend({
      ...routesPage(publie),
      [`PUT /api/events/${EVENEMENT.id}`]: (init) => ({ ...publie, ...(corpsDe(init) as object) }),
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    expect(await screen.findByText(/ses dates ne changent plus/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Début')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^Description/), ' Complet à 22 h.');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(appels.mock.calls.some(([, i]) => i?.method === 'PUT')).toBe(true));
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'PUT');
    expect(corpsDe(envoi?.[1])).toMatchObject({
      description: 'Une nuit de mbalax live. Complet à 22 h.',
      debuteLe: '2099-11-14T21:00:00.123456Z',
      termineLe: null,
    });
  });

  it('affiche le refus du serveur', async () => {
    connecte();
    backend({
      ...routesPage({ ...EVENEMENT, status: 'PUBLIE' }),
      [`PUT /api/events/${EVENEMENT.id}`]: () => erreur(409, "Les dates d'un événement publié ne peuvent plus changer"),
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent("Les dates d'un événement publié ne peuvent plus changer");
  });

  it('ne propose aucun formulaire pour un événement annulé', async () => {
    connecte();
    backend(routesPage({ ...EVENEMENT, status: 'ANNULE' }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}/modifier`);
    expect(await screen.findByText(/annulé : il ne peut plus être modifié/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enregistrer' })).not.toBeInTheDocument();
  });

  it('ne montre pas le lien Modifier sur un événement annulé', async () => {
    connecte();
    backend(routesPage({ ...EVENEMENT, status: 'ANNULE' }));
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
  });
});

describe('Équipe', () => {
  it("crée un compte d'agent et l'ajoute à la liste", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/agents': () => [],
      'POST /api/agents': (init) => ({ id: 'a2', creeLe: '2026-10-30T10:00:00Z', ...(corpsDe(init) as object), motDePasse: undefined }),
    });
    ouvrir('/organisateur/equipe');
    expect(await screen.findByText("Aucun agent pour l'instant.")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Nom complet'), 'Moussa Diop');
    await userEvent.type(screen.getByLabelText('E-mail'), 'moussa@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe provisoire'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: "Créer le compte d'agent" }));
    expect(await screen.findByRole('table', { name: 'Agents de contrôle' })).toHaveTextContent('Moussa Diop');
    const creation = appels.mock.calls.find(([u, i]) => String(u) === '/api/agents' && i?.method === 'POST');
    expect(corpsDe(creation?.[1])).toEqual({ email: 'moussa@test.sn', motDePasse: 'MotDePasse123', nomComplet: 'Moussa Diop' });
  });

  const AWA = { id: 'a1', email: 'awa@test.sn', nomComplet: 'Awa Agent', creeLe: '2026-10-30T10:00:00Z' };

  it("modifie le nom et l'e-mail d'un agent", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/agents': () => [AWA],
      'PUT /api/agents/a1': (init) => ({ ...AWA, ...(corpsDe(init) as object) }),
    });
    ouvrir('/organisateur/equipe');
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier Awa Agent' }));
    const nom = screen.getByLabelText("Nom de l'agent");
    expect(nom).toHaveValue('Awa Agent');
    await userEvent.clear(nom);
    await userEvent.type(nom, 'Awa Ndiaye');
    await userEvent.clear(screen.getByLabelText("E-mail de l'agent"));
    await userEvent.type(screen.getByLabelText("E-mail de l'agent"), 'awa.ndiaye@test.sn');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Compte de Awa Ndiaye modifié.')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Agents de contrôle' })).toHaveTextContent('awa.ndiaye@test.sn');
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'PUT');
    expect(corpsDe(envoi?.[1])).toEqual({ email: 'awa.ndiaye@test.sn', nomComplet: 'Awa Ndiaye' });
  });

  it("affiche le refus d'un e-mail déjà utilisé et garde le formulaire ouvert", async () => {
    connecte();
    backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/agents': () => [AWA],
      'PUT /api/agents/a1': () => erreur(400, 'Un compte existe déjà avec cet email'),
    });
    ouvrir('/organisateur/equipe');
    await userEvent.click(await screen.findByRole('button', { name: 'Modifier Awa Agent' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByText('Un compte existe déjà avec cet email')).toBeInTheDocument();
    expect(screen.getByLabelText("Nom de l'agent")).toBeInTheDocument();
  });

  it('demande confirmation avant de retirer un agent', async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'GET /api/agents': () => [AWA],
      'DELETE /api/agents/a1': () => undefined,
    });
    ouvrir('/organisateur/equipe');
    await userEvent.click(await screen.findByRole('button', { name: 'Retirer Awa Agent' }));
    expect(appels.mock.calls.some(([, i]) => i?.method === 'DELETE')).toBe(false);
    // Les entrées hors ligne en attente seraient refusées après le retrait : on prévient avant.
    expect(screen.getByText(/Faites d'abord synchroniser son téléphone/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: "Garder l'agent" }));
    expect(appels.mock.calls.some(([, i]) => i?.method === 'DELETE')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Retirer Awa Agent' }));
    await userEvent.click(screen.getByRole('button', { name: "Oui, retirer l'agent" }));
    expect(await screen.findByText("Awa Agent ne fait plus partie de l'équipe.")).toBeInTheDocument();
    expect(screen.getByText("Aucun agent pour l'instant.")).toBeInTheDocument();
    expect(appels.mock.calls.some(([u, i]) => String(u) === '/api/agents/a1' && i?.method === 'DELETE')).toBe(true);
  });

  it('ferme la session et renvoie à la connexion', async () => {
    connecte();
    backend({ 'GET /api/auth/me': () => ORGANISATEUR, 'GET /api/agents': () => [] });
    ouvrir('/organisateur/equipe');
    await userEvent.click(await screen.findByRole('button', { name: 'Se déconnecter' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
  });
});

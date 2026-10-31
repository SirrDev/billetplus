import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons, lireJetons } from '@/commun/session';

/* Espace organisateur de bout en bout, backend simulé au niveau de fetch. */

const ORGANISATEUR = { id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga Test', role: 'ORGANISATEUR' };
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

beforeEach(() => effacerJetons());

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
});

describe('Création et gestion d’un événement', () => {
  it("crée le lieu puis l'événement, à l'heure de Dakar, sans organisateur dans le corps", async () => {
    connecte();
    const appels = backend({
      'GET /api/auth/me': () => ORGANISATEUR,
      'POST /api/venues': () => ({ id: 'v1', nom: 'Grand Théâtre', adresse: 'Dakar', latitude: null, longitude: null, seatingPlan: null }),
      'POST /api/events': () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}`]: () => EVENEMENT,
      [`GET /api/events/${EVENEMENT.id}/categories`]: () => [],
      [`GET /api/events/${EVENEMENT.id}/tableau-de-bord`]: () => TABLEAU_VIDE,
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/nouveau');

    await userEvent.type(await screen.findByLabelText('Titre'), 'Nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'Grand Théâtre');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Dakar');
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
    });
    expect(jetonDe(creation?.[1])).toBe('Bearer acces');
  });

  it('refuse une fin avant le début sans rien envoyer', async () => {
    connecte();
    const appels = backend({ 'GET /api/auth/me': () => ORGANISATEUR });
    ouvrir('/organisateur/evenements/nouveau');
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
      [`PUT /api/events/${EVENEMENT.id}/agents/a1`]: () => undefined,
    });
    ouvrir(`/organisateur/evenements/${EVENEMENT.id}`);
    await userEvent.selectOptions(await screen.findByLabelText('Agent'), 'a1');
    await userEvent.click(screen.getByRole('button', { name: 'Affecter' }));
    expect(await screen.findByText('Awa Agent peut contrôler les entrées de cet événement.')).toBeInTheDocument();
    expect(appels.mock.calls.some(([u, i]) => String(u).endsWith('/agents/a1') && i?.method === 'PUT')).toBe(true);
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
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Grande nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText('Modifications enregistrées.')).toBeInTheDocument();
    const envoi = appels.mock.calls.find(([, i]) => i?.method === 'PUT');
    expect(corpsDe(envoi?.[1])).toEqual({
      titre: 'Grande nuit du Mbalax',
      description: 'Une nuit de mbalax live.',
      debuteLe: '2099-11-21T21:00:00Z',
      termineLe: null,
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

  it('ferme la session et renvoie à la connexion', async () => {
    connecte();
    backend({ 'GET /api/auth/me': () => ORGANISATEUR, 'GET /api/agents': () => [] });
    ouvrir('/organisateur/equipe');
    await userEvent.click(await screen.findByRole('button', { name: 'Se déconnecter' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
  });
});

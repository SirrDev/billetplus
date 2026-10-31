import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons, lireJetons } from '@/commun/session';

/* Changement de mot de passe : organisateur, agent de contrôle et administrateur. */

// Trois mots de passe tapés caractère par caractère : près de 5 s sous charge (Node 20,
// suite complète). Même marge que les autres parcours longs (HANDOFF, section 10, n° 53).
vi.setConfig({ testTimeout: 15_000 });

const COMPTES = {
  ORGANISATEUR: { id: 'o1', email: 'orga@test.sn', nomComplet: 'Sunu Events', role: 'ORGANISATEUR' },
  AGENT: { id: 'a1', email: 'agent@test.sn', nomComplet: 'Awa Agent', role: 'AGENT' },
  ADMIN: { id: 'ad', email: 'admin@test.sn', nomComplet: 'Administrateur', role: 'ADMIN' },
} as const;

type Reponse = (init?: RequestInit) => Response | unknown;

function backend(role: keyof typeof COMPTES, routes: Record<string, Reponse> = {}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    const cle = `${init?.method ?? 'GET'} ${url.pathname}`;
    const toutes: Record<string, Reponse> = {
      'GET /api/auth/me': () => COMPTES[role],
      'GET /api/events/affectes': () => [],
      'GET /api/admin/commandes-a-rembourser': () => [],
      ...routes,
    };
    const route = toutes[cle];
    if (!route) return new Response(JSON.stringify({ message: `Route inattendue ${cle}` }), { status: 500 });
    const corps = route(init);
    if (corps instanceof Response) return corps;
    return corps === undefined ? new Response(null, { status: 204 }) : new Response(JSON.stringify(corps));
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

async function remplir(actuel: string, nouveau: string, confirmation = nouveau) {
  await userEvent.type(await screen.findByLabelText('Mot de passe actuel'), actuel);
  await userEvent.type(screen.getByLabelText('Nouveau mot de passe'), nouveau);
  await userEvent.type(screen.getByLabelText('Confirmer le nouveau mot de passe'), confirmation);
  await userEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }));
}

const envoisMotDePasse = (appels: ReturnType<typeof backend>) =>
  appels.mock.calls.filter(([u, i]) => String(u) === '/api/auth/mot-de-passe' && i?.method === 'POST');

beforeEach(() => {
  effacerJetons();
  enregistrerJetons({ accessToken: 'acces', refreshToken: 'rafraichi' });
});

describe("Mot de passe de l'organisateur", () => {
  it('envoie l’actuel et le nouveau avec le jeton, puis vide le formulaire', async () => {
    const appels = backend('ORGANISATEUR', { 'POST /api/auth/mot-de-passe': () => undefined });
    ouvrir('/organisateur/compte');
    await remplir('ancienMotDePasse', 'nouveauMotDePasse');

    expect(await screen.findByText(/Mot de passe changé/)).toBeInTheDocument();
    const [envoi] = envoisMotDePasse(appels);
    expect(JSON.parse(String(envoi?.[1]?.body))).toEqual({
      motDePasseActuel: 'ancienMotDePasse',
      nouveauMotDePasse: 'nouveauMotDePasse',
    });
    expect((envoi?.[1]?.headers as Record<string, string>).Authorization).toBe('Bearer acces');
    expect(screen.getByLabelText('Mot de passe actuel')).toHaveValue('');
    // La session reste ouverte : le changement ne déconnecte pas cet appareil.
    expect(lireJetons()).not.toBeNull();
  });

  it("affiche le refus d'un mot de passe actuel faux sans fermer la session", async () => {
    backend('ORGANISATEUR', {
      'POST /api/auth/mot-de-passe': () => erreur(400, 'Le mot de passe actuel est incorrect'),
    });
    ouvrir('/organisateur/compte');
    await remplir('mauvaisMotDePasse', 'nouveauMotDePasse');

    expect(await screen.findByRole('alert')).toHaveTextContent('Le mot de passe actuel est incorrect');
    expect(lireJetons()).not.toBeNull();
  });

  it('vérifie la confirmation et la longueur avant tout envoi', async () => {
    const appels = backend('ORGANISATEUR');
    ouvrir('/organisateur/compte');
    await remplir('ancienMotDePasse', 'nouveauMotDePasse', 'autreMotDePasse');
    expect(await screen.findByText('Les deux saisies ne correspondent pas.')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Nouveau mot de passe'));
    await userEvent.type(screen.getByLabelText('Nouveau mot de passe'), 'court');
    await userEvent.clear(screen.getByLabelText('Confirmer le nouveau mot de passe'));
    await userEvent.type(screen.getByLabelText('Confirmer le nouveau mot de passe'), 'court');
    await userEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }));
    expect(await screen.findByText('Entre 8 et 72 caractères.')).toBeInTheDocument();
    expect(envoisMotDePasse(appels)).toHaveLength(0);
  });

  it('explique la limite de débit en quart d’heure', async () => {
    backend('ORGANISATEUR', { 'POST /api/auth/mot-de-passe': () => erreur(429, 'Trop de requetes') });
    ouvrir('/organisateur/compte');
    await remplir('ancienMotDePasse', 'nouveauMotDePasse');
    expect(await screen.findByRole('alert')).toHaveTextContent("Trop d'essais : réessayez dans un quart d'heure.");
  });
});

describe("Mot de passe de l'agent de contrôle", () => {
  it('est accessible depuis le choix de l’événement', async () => {
    const appels = backend('AGENT', { 'POST /api/auth/mot-de-passe': () => undefined });
    ouvrir('/scanner');
    await userEvent.click(await screen.findByRole('link', { name: 'Mon compte et mot de passe' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Mon compte' })).toBeInTheDocument();
    await remplir('provisoire123', 'personnel123');
    expect(await screen.findByText(/Mot de passe changé/)).toBeInTheDocument();
    expect(envoisMotDePasse(appels)).toHaveLength(1);
  });
});

describe("Mot de passe de l'administrateur", () => {
  it('est accessible depuis les remboursements', async () => {
    backend('ADMIN', { 'POST /api/auth/mot-de-passe': () => undefined });
    ouvrir('/admin');
    await userEvent.click(await screen.findByRole('link', { name: 'Mon compte' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Mon compte' })).toBeInTheDocument();
    await remplir('ancienMotDePasse', 'nouveauMotDePasse');
    await waitFor(() => expect(screen.getByText(/Mot de passe changé/)).toBeInTheDocument());
  });
});

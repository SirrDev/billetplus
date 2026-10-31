import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';

/* Écrans transverses : aide, page introuvable, compte de l'organisateur, bandeau hors ligne. */

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

function enLigne(valeur: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => valeur });
}

beforeEach(() => {
  effacerJetons();
  enLigne(true);
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
    const chemin = new URL(String(entree), 'http://localhost').pathname;
    if (chemin === '/api/auth/me') {
      return new Response(JSON.stringify({ id: 'o1', email: 'orga@test.sn', nomComplet: 'Sunu Events', role: 'ORGANISATEUR' }));
    }
    if (chemin === '/api/events') return new Response(JSON.stringify({ contenu: [], page: 0, taille: 12, total: 0, pages: 0 }));
    return new Response(JSON.stringify({ message: 'Introuvable' }), { status: 404 });
  });
});

describe('Aide', () => {
  it('est dans la navigation et répond aux questions en les dépliant', async () => {
    ouvrir('/aide');
    expect(await screen.findByRole('heading', { level: 1, name: 'Aide' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Aide' })).toHaveAttribute('aria-current', 'page');
    const question = screen.getByText('Faut-il créer un compte ?');
    const details = question.closest('details');
    expect(details).not.toHaveAttribute('open');
    await userEvent.click(question);
    expect(details).toHaveAttribute('open');
    expect(within(details as HTMLElement).getByText(/numéro de mobile sénégalais suffit/)).toBeVisible();
  });
});

describe('Page introuvable', () => {
  it('le dit clairement au lieu de rediriger en silence', async () => {
    ouvrir('/adresse-qui-n-existe-pas');
    expect(await screen.findByRole('heading', { level: 1, name: 'Page introuvable' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Découvrir les événements' })).toHaveAttribute('href', '/');
  });

  it("ramène vers l'espace d'où l'on venait", async () => {
    ouvrir('/organisateur/page-inconnue');
    // Navigation de l'espace et bouton de retour : deux liens vers « Mes événements ».
    expect(await screen.findAllByRole('link', { name: 'Mes événements' })).toHaveLength(2);
    expect(screen.getByRole('heading', { level: 1, name: 'Page introuvable' })).toBeInTheDocument();
  });
});

describe("Compte de l'organisateur", () => {
  it('affiche son identité, sans formulaire sans effet', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    ouvrir('/organisateur/compte');
    expect(await screen.findByRole('heading', { level: 1, name: 'Mon compte' })).toBeInTheDocument();
    expect(screen.getByText('Sunu Events')).toBeInTheDocument();
    expect(screen.getByText('orga@test.sn')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mon compte' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByLabelText(/mot de passe/i)).not.toBeInTheDocument();
  });
});

describe('Bandeau hors connexion', () => {
  it('apparaît quand le réseau se coupe et disparaît à son retour', async () => {
    ouvrir('/');
    await screen.findByRole('heading', { name: 'Découvrir' });
    expect(screen.queryByText(/Hors connexion/)).not.toBeInTheDocument();
    act(() => window.dispatchEvent(new Event('offline')));
    expect(screen.getByText(/Hors connexion : vous voyez les dernières données reçues/)).toBeInTheDocument();
    act(() => window.dispatchEvent(new Event('online')));
    expect(screen.queryByText(/Hors connexion/)).not.toBeInTheDocument();
  });
});

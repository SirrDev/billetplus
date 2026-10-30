import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthContext';
import { ConnexionPage } from './ConnexionPage';

const json = (status: number, corps?: unknown) =>
  new Response(corps === undefined ? null : JSON.stringify(corps), { status });

function afficher(chemin: string) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/connexion"
            element={<ConnexionPage titre="Espace" sousTitre="" accueil="/accueil" roles={['ORGANISATEUR']} />}
          />
          <Route path="/accueil" element={<div>Accueil de l'espace</div>} />
          <Route path="/demandee" element={<div>Page demandée</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function seConnecter() {
  await userEvent.type(screen.getByLabelText('Email'), 'orga@billetplus.sn');
  await userEvent.type(screen.getByLabelText('Mot de passe'), 'motdepasse');
  await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
}

beforeEach(() => localStorage.clear());
afterEach(() => vi.unstubAllGlobals());

describe('Connexion', () => {
  function backend(role: string) {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.endsWith('/auth/login')
          ? json(200, { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' })
          : json(200, { id: 'u1', email: 'orga@billetplus.sn', nomComplet: 'Orga', role }),
      ),
    );
  }

  it('ouvre la page demandée après la connexion', async () => {
    backend('ORGANISATEUR');
    afficher('/connexion?retour=%2Fdemandee');
    await seConnecter();
    expect(await screen.findByText('Page demandée')).toBeInTheDocument();
  });

  it('ignore une adresse de retour externe (pas de redirection ouverte)', async () => {
    backend('ORGANISATEUR');
    afficher('/connexion?retour=%2F%2Fsite-malveillant.example');
    await seConnecter();
    expect(await screen.findByText("Accueil de l'espace")).toBeInTheDocument();
  });

  it("refuse un compte d'un autre rôle", async () => {
    backend('AGENT');
    afficher('/connexion');
    await seConnecter();
    expect(await screen.findByRole('alert')).toHaveTextContent("n'a pas accès à cet espace");
  });

  it('annonce des identifiants incorrects', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(401, { status: 401, message: 'Identifiants invalides' })),
    );
    afficher('/connexion');
    await seConnecter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Email ou mot de passe incorrect.');
  });
});

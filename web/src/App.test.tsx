import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

// Test de fumée minimal : vérifie que le routing racine par rôle
// (organisateur/acheteur/scanner) fonctionne — voir ADR 0004.
describe('App', () => {
  // L'accueil charge la liste des événements : réponse vide simulée, sans réseau.
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ contenu: [], page: 0, taille: 20, total: 0, pages: 0 }))));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("affiche l'écran Découvrir de l'acheteur sur la route racine", async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: 'Découvrir' })).toBeInTheDocument();
  });

  it('renvoie /organisateur vers la connexion sans session', async () => {
    render(
      <MemoryRouter initialEntries={['/organisateur']}>
        <App />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: 'Espace organisateur' })).toBeInTheDocument();
  });

  it('redirige une route inconnue vers la racine', async () => {
    render(
      <MemoryRouter initialEntries={['/route-inexistante']}>
        <App />
      </MemoryRouter>
    );

    expect(await screen.findByRole('heading', { name: 'Découvrir' })).toBeInTheDocument();
  });
});

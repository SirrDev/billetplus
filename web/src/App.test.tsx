import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from './App';

// Test de fumée minimal : vérifie que le routing racine par rôle
// (organisateur/acheteur/scanner) fonctionne — voir ADR 0004.
describe('App', () => {
  it("affiche la page d'accueil acheteur sur la route racine", () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/accueil acheteur/i)).toBeInTheDocument();
  });

  it('affiche le dashboard organisateur sur /organisateur', () => {
    render(
      <MemoryRouter initialEntries={['/organisateur']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/dashboard organisateur/i)).toBeInTheDocument();
  });

  it('redirige une route inconnue vers la racine', () => {
    render(
      <MemoryRouter initialEntries={['/route-inexistante']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/accueil acheteur/i)).toBeInTheDocument();
  });
});

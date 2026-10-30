import { render, screen } from '@testing-library/react';
import axe from 'axe-core';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ComponentGallery } from './ComponentGallery';

describe('ComponentGallery', () => {
  it('se rend entièrement (test de fumée de tous les composants)', () => {
    render(
      <MemoryRouter>
        <ComponentGallery />
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Composants Billet+' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navigation principale' })).toBeInTheDocument();
  });

  it("n'a aucune violation d'accessibilité détectable automatiquement", async () => {
    const { container } = render(
      <MemoryRouter>
        <ComponentGallery />
      </MemoryRouter>,
    );
    // jsdom ne calcule ni mise en page ni couleurs : le contraste est vérifié à part (voir docs).
    const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });
    const summary = results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
    expect(summary).toEqual([]);
  });
});

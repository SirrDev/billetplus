import { render, screen } from '@testing-library/react';
import { CartePosition } from './CartePosition';

/* Le chargement de la carte échoue (morceau hors précache, sans réseau) : la saisie reste possible. */
vi.mock('./carteLeaflet', () => {
  throw new Error('chargement impossible');
});

it("annonce une carte indisponible et garde la saisie à la main", async () => {
  render(<CartePosition valeur="14.7, -17.45" onChange={() => undefined} />);
  expect(await screen.findByText(/La carte n'a pas pu se charger/)).toBeInTheDocument();
  expect(screen.getByLabelText(/^Coordonnées GPS/)).toHaveValue('14.7, -17.45');
});

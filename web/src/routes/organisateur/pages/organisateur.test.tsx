import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/shared/api/httpClient';
import * as api from '../api';
import { EvenementOrganisateurPage } from './EvenementOrganisateurPage';
import { NouvelEvenementPage } from './NouvelEvenementPage';

vi.mock('../api');

const EVENEMENT: api.EvenementOrganisateur = {
  id: 'evt-1',
  titre: 'Finale de lutte',
  description: null,
  debuteLe: '2026-12-01T18:00:00Z',
  termineLe: null,
  venueId: 'lieu-1',
  venueNom: 'Arène nationale',
  organizerId: 'org-1',
  status: 'BROUILLON',
  creeLe: '2026-10-30T10:00:00Z',
};

function afficherFiche() {
  return render(
    <MemoryRouter initialEntries={['/organisateur/evenements/evt-1']}>
      <Routes>
        <Route path="/organisateur/evenements/:id" element={<EvenementOrganisateurPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(api.lireEvenement).mockResolvedValue(EVENEMENT);
  vi.mocked(api.lireTableauDeBord).mockResolvedValue({
    eventId: 'evt-1',
    categories: [],
    vendus: 0,
    enCoursDePaiement: 0,
    scannes: 0,
    chiffreAffairesFcfa: 0,
  });
  vi.mocked(api.lireCategories).mockResolvedValue([]);
  vi.mocked(api.mesAgents).mockResolvedValue([]);
});
afterEach(() => vi.clearAllMocks());

describe('Fiche organisateur', () => {
  it('publie un brouillon', async () => {
    vi.mocked(api.publier).mockResolvedValue({ ...EVENEMENT, status: 'PUBLIE' });
    afficherFiche();

    await userEvent.click(await screen.findByRole('button', { name: 'Publier et ouvrir la vente' }));
    expect(await screen.findByText('En vente')).toBeInTheDocument();
    expect(api.publier).toHaveBeenCalledWith('evt-1');
    expect(screen.getByRole('link', { name: 'Voir la page publique' })).toHaveAttribute('href', '/evenements/evt-1');
  });

  it('demande une confirmation avant d’annuler, et affiche le refus du serveur', async () => {
    vi.mocked(api.annuler).mockRejectedValue(new ApiError(409, "L'événement est terminé"));
    afficherFiche();

    await userEvent.click(await screen.findByRole('button', { name: "Annuler l'événement" }));
    expect(api.annuler).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: "Confirmer l'annulation" }));
    expect(await screen.findByText("L'événement est terminé")).toBeInTheDocument();
  });

  it('ajoute une catégorie avec un prix et un quota valides', async () => {
    vi.mocked(api.creerCategorie).mockResolvedValue({
      id: 'cat-1',
      nom: 'VIP',
      prixFcfa: 15000,
      disponible: true,
      quotaTotal: 50,
      quotaVendu: 0,
    });
    afficherFiche();

    await userEvent.click(await screen.findByRole('tab', { name: 'Billets' }));
    await userEvent.type(await screen.findByLabelText('Nom'), 'VIP');
    await userEvent.type(screen.getByLabelText('Prix (FCFA)'), '15 000');
    await userEvent.type(screen.getByLabelText('Nombre de places'), '50');
    await userEvent.click(screen.getByRole('button', { name: 'Ajouter la catégorie' }));

    await waitFor(() => expect(api.creerCategorie).toHaveBeenCalledWith('evt-1', 'VIP', 15000, 50));
    expect(await screen.findByRole('rowheader', { name: 'VIP' })).toBeInTheDocument();
  });

  it('refuse un prix décimal avant tout appel', async () => {
    afficherFiche();
    await userEvent.click(await screen.findByRole('tab', { name: 'Billets' }));
    await userEvent.type(await screen.findByLabelText('Nom'), 'VIP');
    await userEvent.type(screen.getByLabelText('Prix (FCFA)'), '15,5');
    await userEvent.type(screen.getByLabelText('Nombre de places'), '50');

    expect(screen.getByText('Montant entier en FCFA, sans décimale.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajouter la catégorie' })).toBeDisabled();
  });
});

describe('Nouvel événement', () => {
  it('crée le lieu puis l’événement, avec l’heure de Dakar en UTC', async () => {
    vi.mocked(api.creerLieu).mockResolvedValue({ id: 'lieu-9' });
    vi.mocked(api.creerEvenement).mockResolvedValue({ ...EVENEMENT, id: 'evt-9' });
    render(
      <MemoryRouter initialEntries={['/organisateur/evenements/nouveau']}>
        <Routes>
          <Route path="/organisateur/evenements/nouveau" element={<NouvelEvenementPage />} />
          <Route path="/organisateur/evenements/:id" element={<div>Fiche créée</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await userEvent.type(screen.getByLabelText('Titre'), 'Finale');
    await userEvent.type(screen.getByLabelText('Début (heure de Dakar)'), '2026-12-01T18:30');
    await userEvent.type(screen.getByLabelText('Lieu'), 'Arène');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Pikine');
    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));

    expect(await screen.findByText('Fiche créée')).toBeInTheDocument();
    expect(api.creerLieu).toHaveBeenCalledWith('Arène', 'Pikine');
    expect(api.creerEvenement).toHaveBeenCalledWith({
      titre: 'Finale',
      description: null,
      debuteLe: '2026-12-01T18:30:00.000Z',
      termineLe: null,
      venueId: 'lieu-9',
    });
  });
});

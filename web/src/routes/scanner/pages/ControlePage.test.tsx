import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/shared/api/httpClient';
import * as api from '../api';
import * as file from '../fileHorsLigne';
import * as verification from '../verificationHorsLigne';
import { ControlePage } from './ControlePage';

vi.mock('../api');
vi.mock('../fileHorsLigne');
vi.mock('../verificationHorsLigne');

function afficher() {
  return render(
    <MemoryRouter initialEntries={['/scanner/evenements/evt-1']}>
      <Routes>
        <Route path="/scanner/evenements/:id" element={<ControlePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function controler(code: string) {
  await userEvent.type(screen.getByLabelText('Douchette ou saisie manuelle'), code);
  await userEvent.click(screen.getByRole('button', { name: 'Contrôler ce code' }));
}

beforeEach(() => {
  vi.mocked(api.lireEvenement).mockResolvedValue({
    id: 'evt-1',
    titre: 'Finale',
    debuteLe: '',
    venueNom: '',
    status: 'PUBLIE',
  });
  vi.mocked(file.synchroniser).mockResolvedValue(0);
  vi.mocked(file.nombreEnAttente).mockResolvedValue(0);
});
afterEach(() => vi.clearAllMocks());

describe('Contrôle d’entrée', () => {
  it('billet accepté par le serveur : laisser entrer, compteur à 1', async () => {
    vi.mocked(api.scanner).mockResolvedValue({
      issue: 'ACCEPTE',
      ticketId: 'aaaabbbb-0000',
      premierScanLe: null,
      statut: 'SCANNE',
    });
    afficher();

    await controler('qr-1');
    expect(await screen.findByText('BP-AAAA-BBBB')).toBeInTheDocument();
    expect(api.scanner).toHaveBeenCalledWith('evt-1', 'qr-1');
    expect(screen.getByText(/entrée acceptée sur cet appareil/)).toHaveTextContent('1 entrée acceptée');
  });

  it('billet déjà scanné : verdict « déjà utilisé » avec l’heure du premier passage', async () => {
    vi.mocked(api.scanner).mockResolvedValue({
      issue: 'DEJA_SCANNE',
      ticketId: 'aaaabbbb-0000',
      premierScanLe: '2026-11-14T20:12:00Z',
      statut: 'SCANNE',
    });
    afficher();

    await controler('qr-1');
    expect(await screen.findByText(/Premier passage à 20:12/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('déjà servi');
  });

  it('agent non affecté (403) : message clair, aucun verdict', async () => {
    vi.mocked(api.scanner).mockRejectedValue(new ApiError(403, 'Accès refusé'));
    afficher();

    await controler('qr-1');
    expect(await screen.findByText(/pas affecté au contrôle de cet événement/)).toBeInTheDocument();
    expect(verification.verifierHorsLigne).not.toHaveBeenCalled();
  });

  it('réseau coupé : vérification de la signature sur l’appareil et mise en file', async () => {
    vi.mocked(api.scanner).mockRejectedValue(new TypeError('Failed to fetch'));
    vi.mocked(verification.verifierHorsLigne).mockResolvedValue({
      valide: true,
      ticketId: 'ccccdddd-0000',
      eventId: 'evt-1',
    });
    vi.mocked(file.nombreEnAttente).mockResolvedValue(1);
    afficher();

    await controler('qr-1');
    expect(await screen.findByText(/Contrôle hors ligne/)).toBeInTheDocument();
    expect(file.mettreEnAttente).toHaveBeenCalledWith(expect.objectContaining({ eventId: 'evt-1', qr: 'qr-1' }));
    await waitFor(() => expect(screen.getByText(/à synchroniser/)).toBeInTheDocument());
  });

  it('hors ligne, un billet d’un autre événement est refusé et n’est pas mis en file', async () => {
    vi.mocked(api.scanner).mockRejectedValue(new TypeError('Failed to fetch'));
    vi.mocked(verification.verifierHorsLigne).mockResolvedValue({ valide: true, ticketId: 'x', eventId: 'autre' });
    afficher();

    await controler('qr-1');
    expect(await screen.findByRole('alert')).toHaveTextContent('autre événement');
    expect(file.mettreEnAttente).not.toHaveBeenCalled();
  });
});

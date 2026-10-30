import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/shared/api/httpClient';
import * as api from '../api';
import * as locales from '../commandesLocales';
import { ChoisirBilletsPage } from './ChoisirBilletsPage';
import { CommandePage } from './CommandePage';
import { DecouvrirPage } from './DecouvrirPage';

vi.mock('../api');
vi.mock('../commandesLocales');

const EVENEMENT: api.Evenement = {
  id: 'evt-1',
  titre: 'Dakar Sunset Sessions',
  description: 'Concert au coucher du soleil',
  debuteLe: '2026-11-14T20:00:00Z',
  termineLe: null,
  venueId: 'lieu-1',
  venueNom: 'Corniche Ouest',
  status: 'PUBLIE',
};

const CATEGORIES: api.CategorieBillet[] = [
  { id: 'cat-tribune', nom: 'Tribune', prixFcfa: 5000, disponible: true },
  { id: 'cat-vip', nom: 'VIP', prixFcfa: 15000, disponible: false },
];

const LOCALE: locales.CommandeLocale = {
  id: 'cmd-1',
  jeton: 'jeton-secret',
  eventId: 'evt-1',
  titre: 'Dakar Sunset Sessions',
  debuteLe: '2026-11-14T20:00:00Z',
  venueNom: 'Corniche Ouest',
  creeLe: '2026-11-01T10:00:00Z',
};

function commande(status: api.StatutCommande, expireDansMs = 240_000): api.Commande {
  return {
    id: 'cmd-1',
    eventId: 'evt-1',
    status,
    lignes: [{ categoryId: 'cat-tribune', quantite: 2, prixUnitaireFcfa: 5000, sousTotalFcfa: 10000 }],
    montantTotalFcfa: 10000,
    creeLe: '2026-11-01T10:00:00Z',
    expireLe: new Date(Date.now() + expireDansMs).toISOString(),
    payeeLe: status === 'PAYEE' ? '2026-11-01T10:01:00Z' : null,
  };
}

function afficher(chemin: string, page: JSX.Element, motif: string) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route path={motif} element={page} />
        <Route
          path="/commandes/:id"
          element={
            <div>
              Page commande {'{'}id{'}'}
            </div>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(api.lireEvenement).mockResolvedValue(EVENEMENT);
  vi.mocked(api.lireCategories).mockResolvedValue(CATEGORIES);
  vi.mocked(locales.lireCommandeLocale).mockResolvedValue(LOCALE);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('Découvrir', () => {
  it('affiche les événements du backend, sans prix inventé, et cherche par titre', async () => {
    vi.mocked(api.rechercherEvenements).mockResolvedValue({
      contenu: [
        {
          id: 'a',
          titre: 'Dakar Sunset Sessions',
          description: null,
          debuteLe: '2026-11-14T20:00:00Z',
          termineLe: null,
          venueId: 'v',
          venueNom: 'Corniche Ouest',
          venueAdresse: 'Dakar',
        },
        {
          id: 'b',
          titre: 'Soirée Jazz',
          description: null,
          debuteLe: '2026-11-20T19:00:00Z',
          termineLe: null,
          venueId: 'v',
          venueNom: 'Plateau',
          venueAdresse: 'Dakar',
        },
      ],
      page: 0,
      taille: 20,
      total: 2,
      pages: 1,
    });
    afficher('/', <DecouvrirPage />, '/');

    expect(await screen.findByRole('link', { name: /Dakar Sunset Sessions/ })).toHaveAttribute('href', '/evenements/a');
    expect(screen.getByRole('link', { name: /Soirée Jazz/ })).toHaveAttribute('href', '/evenements/b');
    // Le backend ne donne pas de prix « dès » dans la liste : rien n'est affiché.
    expect(screen.queryByText(/dès/)).toBeNull();

    await userEvent.type(screen.getByLabelText('Rechercher un événement'), 'jazz');
    await waitFor(() => expect(api.rechercherEvenements).toHaveBeenLastCalledWith('jazz', 0, 20));
  });

  it('signale une erreur réseau et permet de réessayer', async () => {
    vi.mocked(api.rechercherEvenements).mockRejectedValueOnce(new TypeError('Failed to fetch'));
    vi.mocked(api.rechercherEvenements).mockResolvedValue({ contenu: [], page: 0, taille: 20, total: 0, pages: 0 });
    afficher('/', <DecouvrirPage />, '/');

    expect(await screen.findByRole('alert')).toHaveTextContent(/connexion impossible/i);
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Aucun événement pour le moment')).toBeInTheDocument();
  });
});

describe('Choisir ses billets', () => {
  it('crée la commande, enregistre son jeton sur l’appareil puis ouvre la commande', async () => {
    vi.mocked(api.creerCommande).mockResolvedValue({
      commande: commande('EN_ATTENTE_PAIEMENT'),
      jetonAcces: 'jeton-secret',
    });
    afficher('/evenements/evt-1/billets', <ChoisirBilletsPage />, '/evenements/:id/billets');

    const ajouter = await screen.findByRole('button', { name: 'Ajouter un billet Tribune' });
    await userEvent.click(ajouter);
    await userEvent.click(ajouter);
    // Catégorie épuisée : impossible d'en ajouter.
    expect(screen.getByRole('button', { name: 'Ajouter un billet VIP' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Votre numéro de téléphone'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: 'Réserver 2 billets' }));

    await waitFor(() =>
      expect(api.creerCommande).toHaveBeenCalledWith('evt-1', '771234567', [
        { categoryId: 'cat-tribune', quantite: 2 },
      ]),
    );
    expect(locales.enregistrerCommande).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'cmd-1', jeton: 'jeton-secret', titre: 'Dakar Sunset Sessions' }),
    );
    expect(await screen.findByText(/Page commande/)).toBeInTheDocument();
  });

  it('ne commande rien avec un numéro invalide et affiche le refus du serveur', async () => {
    vi.mocked(api.creerCommande).mockRejectedValue(new ApiError(409, 'Places insuffisantes'));
    afficher('/evenements/evt-1/billets', <ChoisirBilletsPage />, '/evenements/:id/billets');

    await userEvent.click(await screen.findByRole('button', { name: 'Ajouter un billet Tribune' }));
    await userEvent.type(screen.getByLabelText('Votre numéro de téléphone'), '338234567');
    await userEvent.click(screen.getByRole('button', { name: 'Réserver 1 billet' }));
    expect(api.creerCommande).not.toHaveBeenCalled();
    expect(screen.getByText(/Numéro de mobile sénégalais invalide/)).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Votre numéro de téléphone'));
    await userEvent.type(screen.getByLabelText('Votre numéro de téléphone'), '781234567');
    await userEvent.click(screen.getByRole('button', { name: 'Réserver 1 billet' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Places insuffisantes');
    expect(locales.enregistrerCommande).not.toHaveBeenCalled();
  });
});

describe('Commande', () => {
  it('en attente : montant du serveur, choix du moyen, puis redirection vers le paiement', async () => {
    vi.mocked(api.lireCommande).mockResolvedValue(commande('EN_ATTENTE_PAIEMENT'));
    vi.mocked(api.ouvrirPaiement).mockResolvedValue({
      moyen: 'ORANGE_MONEY',
      urlPaiement: 'https://om.example/p/1',
      expireLe: '',
    });
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    afficher('/commandes/cmd-1', <CommandePage />, '/commandes/:id');

    expect(await screen.findByRole('button', { name: /Payer 10\s000\sFCFA/ })).toBeInTheDocument();
    expect(api.lireCommande).toHaveBeenCalledWith('cmd-1', 'jeton-secret');
    expect(await screen.findByText(/2 × Tribune/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: /Orange Money/ }));
    await userEvent.click(screen.getByRole('button', { name: /Payer/ }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://om.example/p/1'));
    expect(api.ouvrirPaiement).toHaveBeenCalledWith('cmd-1', 'jeton-secret', 'ORANGE_MONEY');
    vi.unstubAllGlobals();
  });

  it('payée : affiche un QR par billet valable, et aucun QR pour un billet déjà utilisé', async () => {
    vi.mocked(api.lireCommande).mockResolvedValue(commande('PAYEE'));
    vi.mocked(api.lireBillets).mockResolvedValue([
      {
        id: 'aaaabbbb-0000-0000-0000-000000000001',
        categoryId: 'cat-tribune',
        categorie: 'Tribune',
        statut: 'EMIS',
        qr: '{"t":"1"}.signature',
      },
      {
        id: 'ccccdddd-0000-0000-0000-000000000002',
        categoryId: 'cat-tribune',
        categorie: 'Tribune',
        statut: 'SCANNE',
        qr: null,
      },
    ]);
    afficher('/commandes/cmd-1', <CommandePage />, '/commandes/:id');

    expect(await screen.findByRole('heading', { name: 'Paiement confirmé !' })).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'QR code du billet BP-AAAA-BBBB' })).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: /QR code/ })).toHaveLength(1);
    expect(screen.getByText(/BP-CCCC-DDDD : déjà utilisé/)).toBeInTheDocument();
    expect(api.lireBillets).toHaveBeenCalledWith('cmd-1', 'jeton-secret');
  });

  it('payée sans billet : annonce le remboursement', async () => {
    vi.mocked(api.lireCommande).mockResolvedValue(commande('PAYEE_SANS_BILLET'));
    afficher('/commandes/cmd-1', <CommandePage />, '/commandes/:id');
    expect(await screen.findByText('Paiement reçu, billets indisponibles')).toBeInTheDocument();
    expect(api.lireBillets).not.toHaveBeenCalled();
  });

  it('expirée : propose une nouvelle commande', async () => {
    vi.mocked(api.lireCommande).mockResolvedValue(commande('EXPIREE'));
    afficher('/commandes/cmd-1', <CommandePage />, '/commandes/:id');
    expect(await screen.findByText('Commande expirée')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: "Revenir à l'événement" })).toHaveAttribute('href', '/evenements/evt-1');
  });

  it('sans jeton sur cet appareil : explique où sont les billets, sans appeler le serveur', async () => {
    vi.mocked(locales.lireCommandeLocale).mockResolvedValue(undefined);
    afficher('/commandes/cmd-1', <CommandePage />, '/commandes/:id');
    expect(await screen.findByText('Commande introuvable sur cet appareil')).toBeInTheDocument();
    expect(api.lireCommande).not.toHaveBeenCalled();
  });
});

import 'fake-indexeddb/auto';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons } from '@/commun/session';
import { enregistrerCommande } from '@/espaces/acheteur/commandesLocales';
import { lireCoordonnees } from '@/espaces/organisateur/api';

/*
 * Fonctionnalités du backend rendues visibles : événement annulé ou terminé vu par
 * l'acheteur, adresse et carte du lieu, remboursement d'une commande d'un événement
 * annulé, billets annulés au tableau de bord, coordonnées GPS d'un lieu.
 */

const EVENEMENT = {
  id: 'e1',
  titre: 'Nuit du Mbalax',
  description: null,
  debuteLe: '2099-11-14T21:00:00Z',
  termineLe: null,
  venueId: 'v1',
  venueNom: 'Grand Théâtre',
  organizerId: 'o1',
  status: 'PUBLIE',
  creeLe: '2026-10-01T00:00:00Z',
};
const LIEU = { id: 'v1', nom: 'Grand Théâtre', adresse: 'Boulevard de la République, Dakar', latitude: 14.6697, longitude: -17.4381, seatingPlan: null };

type Route = (init?: RequestInit) => unknown;

function backend(routes: Record<string, Route>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    const cle = `${init?.method ?? 'GET'} ${url.pathname}`;
    const route = routes[cle];
    if (!route) return new Response(JSON.stringify({ message: `Route inattendue ${cle}` }), { status: 500 });
    const corps = route(init);
    if (corps instanceof Response) return corps;
    return new Response(JSON.stringify(corps), { status: 200 });
  });
}

const introuvable = () => new Response(JSON.stringify({ status: 404, message: 'Introuvable' }), { status: 404 });

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

beforeEach(() => effacerJetons());

describe("Événement vu par l'acheteur", () => {
  it("affiche l'adresse du lieu et un lien vers la carte par ses coordonnées", async () => {
    backend({
      'GET /api/events/e1': () => EVENEMENT,
      'GET /api/events/e1/categories': () => [{ id: 'std', nom: 'Standard', prixFcfa: 5000, disponible: true }],
      'GET /api/venues/v1': () => LIEU,
    });
    ouvrir('/evenements/e1');
    expect(await screen.findByText('Boulevard de la République, Dakar')).toBeInTheDocument();
    const carte = screen.getByRole('link', { name: 'Voir sur la carte' });
    expect(carte.getAttribute('href')).toContain(encodeURIComponent('14.6697,-17.4381'));
    expect(carte).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it("dit qu'un événement annulé l'est, au lieu de le dire introuvable, sans demander ses catégories", async () => {
    const appels = backend({
      'GET /api/events/e1': () => ({ ...EVENEMENT, status: 'ANNULE' }),
      'GET /api/events/e1/categories': introuvable,
      'GET /api/venues/v1': () => LIEU,
    });
    ouvrir('/evenements/e1');
    expect(await screen.findByText(/Événement annulé\./)).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText('La billetterie de cet événement est fermée.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Continuer vers le paiement' })).not.toBeInTheDocument();
    expect(appels.mock.calls.some(([u]) => String(u).endsWith('/categories'))).toBe(false);
  });

  it('dit qu’un événement terminé est fermé', async () => {
    backend({
      'GET /api/events/e1': () => ({ ...EVENEMENT, status: 'TERMINE' }),
      'GET /api/venues/v1': () => LIEU,
    });
    ouvrir('/evenements/e1');
    expect(await screen.findByText(/Cet événement est terminé/)).toBeInTheDocument();
  });

  it("s'affiche même si le lieu ne répond pas", async () => {
    backend({
      'GET /api/events/e1': () => EVENEMENT,
      'GET /api/events/e1/categories': () => [],
      'GET /api/venues/v1': introuvable,
    });
    ouvrir('/evenements/e1');
    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Voir sur la carte' })).not.toBeInTheDocument();
  });
});

describe("Commande d'un événement annulé", () => {
  it('annonce le remboursement du montant payé', async () => {
    await enregistrerCommande({
      id: 'k1',
      jeton: 'jk',
      eventId: 'e1',
      titre: 'Nuit du Mbalax',
      debuteLe: '2099-11-14T21:00:00Z',
      lieu: 'Grand Théâtre',
      creeLe: '2026-10-30T10:00:00Z',
    });
    backend({
      'GET /api/orders/k1': () => ({
        id: 'k1',
        eventId: 'e1',
        status: 'PAYEE',
        lignes: [{ categoryId: 'std', quantite: 2, prixUnitaireFcfa: 5000, sousTotalFcfa: 10000 }],
        montantTotalFcfa: 10000,
        creeLe: '2026-10-30T10:00:00Z',
        expireLe: '2026-10-30T10:05:00Z',
        payeeLe: '2026-10-30T10:02:00Z',
      }),
      'GET /api/events/e1/categories': introuvable,
      'GET /api/orders/k1/billets': () => [
        { id: 'b1', categoryId: 'std', categorie: 'Standard', statut: 'ANNULE', qr: null, transfereA: null },
        { id: 'b2', categoryId: 'std', categorie: 'Standard', statut: 'ANNULE', qr: null, transfereA: null },
      ],
    });
    ouvrir('/commandes/k1');
    const avis = await screen.findByText(/Vos billets ne sont plus valables/);
    expect(avis.textContent?.replace(/\s/g, ' ')).toContain('10 000 FCFA');
    expect(screen.queryByRole('link', { name: 'Transférer ce billet' })).not.toBeInTheDocument();
  });
});

describe('Organisateur', () => {
  it('voit les billets annulés par catégorie au tableau de bord', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    backend({
      'GET /api/auth/me': () => ({ id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga', role: 'ORGANISATEUR' }),
      'GET /api/events/e1': () => ({ ...EVENEMENT, status: 'ANNULE' }),
      'GET /api/events/e1/categories': () => [{ id: 'std', nom: 'Standard', prixFcfa: 5000, disponible: false, quotaTotal: 50, quotaVendu: 12 }],
      'GET /api/events/e1/tableau-de-bord': () => ({
        eventId: 'e1',
        categories: [
          { categoryId: 'std', nom: 'Standard', prixFcfa: 5000, quotaTotal: 50, vendus: 0, enCoursDePaiement: 0, disponibles: 50, scannes: 0, annules: 12, chiffreAffairesFcfa: 0 },
        ],
        vendus: 0,
        enCoursDePaiement: 0,
        scannes: 0,
        chiffreAffairesFcfa: 0,
      }),
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/e1');
    const entete = await screen.findByRole('columnheader', { name: 'Annulés' });
    const tableau = entete.closest('table') as HTMLElement;
    expect(within(tableau).getByRole('cell', { name: '12' })).toBeInTheDocument();
  });

  it('envoie les coordonnées GPS du lieu, copiées depuis une carte', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    const appels = backend({
      'GET /api/auth/me': () => ({ id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga', role: 'ORGANISATEUR' }),
      'POST /api/venues': () => LIEU,
      'POST /api/events': () => EVENEMENT,
      'GET /api/events/e1': () => ({ ...EVENEMENT, status: 'BROUILLON' }),
      'GET /api/events/e1/categories': () => [],
      'GET /api/events/e1/tableau-de-bord': () => ({ eventId: 'e1', categories: [], vendus: 0, enCoursDePaiement: 0, scannes: 0, chiffreAffairesFcfa: 0 }),
      'GET /api/agents': () => [],
    });
    ouvrir('/organisateur/evenements/nouveau');
    await userEvent.type(await screen.findByLabelText('Titre'), 'Nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'Grand Théâtre');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Dakar');
    fireEvent.change(screen.getByLabelText(/^Coordonnées GPS/), { target: { value: '14.6697, -17.4381' } });
    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    const lieu = appels.mock.calls.find(([u, i]) => String(u) === '/api/venues' && i?.method === 'POST');
    expect(JSON.parse(String(lieu?.[1]?.body))).toEqual({
      nom: 'Grand Théâtre',
      adresse: 'Dakar',
      latitude: 14.6697,
      longitude: -17.4381,
    });
  });

  it('refuse des coordonnées illisibles sans rien créer', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    const appels = backend({
      'GET /api/auth/me': () => ({ id: 'o1', email: 'orga@test.sn', nomComplet: 'Orga', role: 'ORGANISATEUR' }),
    });
    ouvrir('/organisateur/evenements/nouveau');
    await userEvent.type(await screen.findByLabelText('Titre'), 'Nuit du Mbalax');
    fireEvent.change(screen.getByLabelText('Début'), { target: { value: '2099-11-14T21:00' } });
    await userEvent.type(screen.getByLabelText('Nom du lieu'), 'Grand Théâtre');
    await userEvent.type(screen.getByLabelText('Adresse'), 'Dakar');
    fireEvent.change(screen.getByLabelText(/^Coordonnées GPS/), { target: { value: 'Dakar centre' } });
    await userEvent.click(screen.getByRole('button', { name: 'Créer le brouillon' }));
    expect(await screen.findByText(/Format attendu : latitude, longitude/)).toBeInTheDocument();
    expect(appels.mock.calls.some(([u]) => String(u) === '/api/venues')).toBe(false);
  });
});

describe('Filtre par catégorie', () => {
  const page = (contenu: object[]) => ({ contenu, page: 0, taille: 12, total: contenu.length, pages: 1 });
  const ev = (id: string, titre: string, categorie: string) => ({
    id,
    titre,
    description: null,
    debuteLe: '2099-11-14T21:00:00Z',
    termineLe: null,
    venueId: 'v1',
    venueNom: 'Grand Théâtre',
    venueAdresse: 'Dakar',
    categorie,
  });

  it('interroge le backend avec la catégorie choisie et retire « À l’affiche »', async () => {
    const appels = vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      const url = new URL(String(entree), 'http://localhost');
      const categorie = url.searchParams.get('categorie');
      const contenu =
        categorie === 'SPORT'
          ? [ev('s1', 'Derby de la Médina', 'SPORT')]
          : [ev('m1', 'Nuit du Mbalax', 'MUSIQUE'), ev('s1', 'Derby de la Médina', 'SPORT')];
      return new Response(JSON.stringify(page(contenu)));
    });
    ouvrir('/');
    expect(await screen.findByText("À l'affiche")).toBeInTheDocument();
    const filtres = screen.getByRole('group', { name: "Catégories d'événements" });
    expect(within(filtres).getByRole('button', { name: 'Tout' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(within(filtres).getByRole('button', { name: 'Sport' }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Sport' })).toBeInTheDocument();
    expect(within(filtres).getByRole('button', { name: 'Sport' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText("À l'affiche")).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Derby de la Médina/ })).toHaveTextContent('Sport');
    expect(appels.mock.calls.some(([u]) => String(u).includes('categorie=SPORT'))).toBe(true);
  });

  it('propose de tout revoir quand une catégorie est vide', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree) => {
      const vide = new URL(String(entree), 'http://localhost').searchParams.has('categorie');
      return new Response(JSON.stringify(page(vide ? [] : [ev('m1', 'Nuit du Mbalax', 'MUSIQUE')])));
    });
    ouvrir('/');
    await userEvent.click(await screen.findByRole('button', { name: 'Conférence' }));
    expect(await screen.findByText(/Aucun événement à venir en conférence/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Voir tous les événements' }));
    expect(await screen.findByText("À l'affiche")).toBeInTheDocument();
  });
});

describe('En-tête public', () => {
  it('propose le contrôle d’entrée à côté des organisateurs', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ contenu: [], page: 0, taille: 12, total: 0, pages: 0 })),
    );
    ouvrir('/');
    const lien = await screen.findByRole('link', { name: "Contrôle d'entrée" });
    expect(lien).toHaveAttribute('href', '/scanner');
    expect(screen.getByRole('link', { name: 'Organisateurs' })).toHaveAttribute('href', '/organisateur');
  });
});

describe('lireCoordonnees', () => {
  it('lit les formats courants et refuse le reste', () => {
    expect(lireCoordonnees('14.6697, -17.4381')).toEqual({ latitude: 14.6697, longitude: -17.4381 });
    expect(lireCoordonnees('14.6697 -17.4381')).toEqual({ latitude: 14.6697, longitude: -17.4381 });
    expect(lireCoordonnees('14,6697 -17,4381')).toEqual({ latitude: 14.6697, longitude: -17.4381 });
    expect(lireCoordonnees('14,6697; -17,4381')).toEqual({ latitude: 14.6697, longitude: -17.4381 });
    expect(lireCoordonnees('91, 10')).toBeNull();
    expect(lireCoordonnees('10, 181')).toBeNull();
    expect(lireCoordonnees('Dakar')).toBeNull();
    expect(lireCoordonnees('14.6697')).toBeNull();
  });
});

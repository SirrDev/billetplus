import 'fake-indexeddb/auto';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import {
  enregistrerBilletRecu,
  enregistrerCommande,
  lireBilletRecu,
  lireCommandeLocale,
  type CommandeLocale,
} from './commandesLocales';

/*
 * Transfert d'un billet (ADR 0008) : l'acheteur transfère, le destinataire ouvre son
 * billet avec le lien et son numéro. Backend simulé au niveau de fetch ; stockage réel
 * sur IndexedDB en mémoire (fake-indexeddb), base en version 2.
 */

const EVENEMENT = {
  id: 'e1',
  titre: 'Nuit du Mbalax',
  description: null,
  debuteLe: '2099-11-14T21:00:00Z',
  termineLe: null,
  venueId: 'v1',
  venueNom: 'Grand Théâtre',
  venueAdresse: 'Dakar',
};
const JETON_DESTINATAIRE = 'jeton-du-destinataire-0123456789abcdefghijklmn';

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

const erreur = (status: number, message: string) => new Response(JSON.stringify({ status, message }), { status });
const entete = (init: RequestInit | undefined, nom: string) => (init?.headers as Record<string, string>)[nom];

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

async function commandePayee(id: string): Promise<CommandeLocale> {
  const commande: CommandeLocale = {
    id,
    jeton: 'jeton-commande',
    eventId: 'e1',
    titre: 'Nuit du Mbalax',
    debuteLe: '2099-11-14T21:00:00Z',
    lieu: 'Grand Théâtre',
    creeLe: '2026-10-30T10:00:00Z',
    billets: [
      { id: `${id}-b1`, categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'qr-1' },
      { id: `${id}-b2`, categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'qr-2' },
    ],
  };
  await enregistrerCommande(commande);
  return commande;
}

describe('Transfert par l’acheteur', () => {
  it('transfère après double saisie du numéro, garde le lien et retire l’ancien QR', async () => {
    const commande = await commandePayee('t1');
    const appels = backend({
      [`POST /api/orders/t1/billets/t1-b1/transfert`]: () => ({ ticketId: 't1-b1', jeton: JETON_DESTINATAIRE }),
    });
    ouvrir(`/commandes/${commande.id}/billets/t1-b1/transfert`);

    expect(await screen.findByText(/Le transfert est définitif/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Son numéro de mobile'), '781234567');
    await userEvent.type(screen.getByLabelText('Confirmez son numéro'), '781234567');
    await userEvent.click(screen.getByRole('button', { name: 'Transférer définitivement' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Billet transféré' })).toBeInTheDocument();
    expect(screen.getByText(/Billet transféré au 78 ••• •• 67/)).toBeInTheDocument();
    const lien = `${window.location.origin}/billets/recu/t1-b1#${JETON_DESTINATAIRE}`;
    expect(screen.getByText(lien)).toBeInTheDocument();

    const [, init] = appels.mock.calls[0]!;
    expect(JSON.parse(String(init?.body))).toEqual({ telephone: '+221781234567' });
    expect(entete(init, 'X-Jeton-Commande')).toBe('jeton-commande');

    const gardee = await lireCommandeLocale('t1');
    expect(gardee?.liensTransfert?.['t1-b1']).toBe(lien);
    expect(gardee?.billets?.[0]).toMatchObject({ statut: 'TRANSFERE', qr: null, transfereA: '78 ••• •• 67' });
    // L'autre billet est intact.
    expect(gardee?.billets?.[1]).toMatchObject({ statut: 'EMIS', qr: 'qr-2' });
  });

  it('refuse deux numéros différents sans rien envoyer', async () => {
    await commandePayee('t2');
    const appels = backend({});
    ouvrir('/commandes/t2/billets/t2-b1/transfert');
    await userEvent.type(await screen.findByLabelText('Son numéro de mobile'), '781234567');
    await userEvent.type(screen.getByLabelText('Confirmez son numéro'), '781234568');
    await userEvent.click(screen.getByRole('button', { name: 'Transférer définitivement' }));
    expect(await screen.findByText('Les deux numéros ne correspondent pas.')).toBeInTheDocument();
    expect(appels).not.toHaveBeenCalled();
  });

  it('affiche le refus du serveur', async () => {
    await commandePayee('t3');
    backend({ 'POST /api/orders/t3/billets/t3-b1/transfert': () => erreur(409, 'Ce billet a déjà été transféré') });
    ouvrir('/commandes/t3/billets/t3-b1/transfert');
    await userEvent.type(await screen.findByLabelText('Son numéro de mobile'), '781234567');
    await userEvent.type(screen.getByLabelText('Confirmez son numéro'), '781234567');
    await userEvent.click(screen.getByRole('button', { name: 'Transférer définitivement' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ce billet a déjà été transféré');
    expect((await lireCommandeLocale('t3'))?.liensTransfert).toBeUndefined();
  });

  it('montre le lien gardé pour le renvoyer, sans nouveau transfert', async () => {
    const commande = await commandePayee('t4');
    await enregistrerCommande({
      ...commande,
      billets: commande.billets?.map((b, i) => (i === 0 ? { ...b, statut: 'TRANSFERE', qr: null, transfereA: '78 ••• •• 67' } : b)),
      liensTransfert: { 't4-b1': 'https://billetplus.sn/billets/recu/t4-b1#abc' },
    });
    const appels = backend({});
    ouvrir('/commandes/t4/billets/t4-b1/transfert');
    expect(await screen.findByText('https://billetplus.sn/billets/recu/t4-b1#abc')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Transférer définitivement' })).not.toBeInTheDocument();
    expect(appels).not.toHaveBeenCalled();
  });
});

describe('Commande payée', () => {
  it('propose de transférer un billet valable, et le lien d’un billet transféré', async () => {
    await commandePayee('t6');
    backend({
      'GET /api/orders/t6': () => ({
        id: 't6',
        eventId: 'e1',
        status: 'PAYEE',
        lignes: [{ categoryId: 'std', quantite: 2, prixUnitaireFcfa: 5000, sousTotalFcfa: 10000 }],
        montantTotalFcfa: 10000,
        creeLe: '2026-10-30T10:00:00Z',
        expireLe: '2099-01-01T00:00:00Z',
        payeeLe: '2026-10-30T10:02:00Z',
      }),
      'GET /api/events/e1/categories': () => [{ id: 'std', nom: 'Standard', prixFcfa: 5000, disponible: true }],
      'GET /api/orders/t6/billets': () => [
        { id: 't6-b1', categoryId: 'std', categorie: 'Standard', statut: 'TRANSFERE', qr: null, transfereA: '78 ••• •• 67' },
        { id: 't6-b2', categoryId: 'std', categorie: 'Standard', statut: 'EMIS', qr: 'qr-2', transfereA: null },
      ],
    });
    ouvrir('/commandes/t6');
    expect(await screen.findByText(/Transféré au 78 ••• •• 67/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Transférer ce billet' })).toHaveAttribute(
      'href',
      '/commandes/t6/billets/t6-b2/transfert',
    );
    // Aucun lien gardé sur cet appareil pour le billet transféré : rien à renvoyer.
    expect(screen.queryByRole('link', { name: 'Renvoyer le lien' })).not.toBeInTheDocument();
  });
});

describe('Billet reçu', () => {
  it('s’ouvre avec le lien et le numéro, puis reste sur l’appareil', async () => {
    const appels = backend({
      'POST /api/billets/r1/consultation': () => ({
        id: 'r1',
        eventId: 'e1',
        categorie: 'VIP',
        statut: 'EMIS',
        qr: 'qr-nouveau',
      }),
      'GET /api/events/e1': () => EVENEMENT,
    });
    ouvrir(`/billets/recu/r1#${JETON_DESTINATAIRE}`);

    await userEvent.type(await screen.findByLabelText('Votre numéro de mobile'), '781234567');
    await userEvent.click(screen.getByRole('button', { name: 'Ouvrir mon billet' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /QR code du billet/ })).toBeInTheDocument();
    const consultation = appels.mock.calls.find(([u]) => String(u).endsWith('/consultation'));
    expect(entete(consultation?.[1], 'X-Jeton-Billet')).toBe(JETON_DESTINATAIRE);
    expect(JSON.parse(String(consultation?.[1]?.body))).toEqual({ telephone: '+221781234567' });
    // Le jeton ne voyage jamais dans l'URL d'une requête.
    expect(appels.mock.calls.every(([u]) => !String(u).includes(JETON_DESTINATAIRE))).toBe(true);
    await waitFor(async () => expect((await lireBilletRecu('r1'))?.telephone).toBe('+221781234567'));
  });

  it('dit la même chose pour un mauvais numéro ou un mauvais lien', async () => {
    backend({ 'POST /api/billets/r2/consultation': () => erreur(404, 'Billet introuvable') });
    ouvrir(`/billets/recu/r2#${JETON_DESTINATAIRE}`);
    await userEvent.type(await screen.findByLabelText('Votre numéro de mobile'), '771234567');
    await userEvent.click(screen.getByRole('button', { name: 'Ouvrir mon billet' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Lien ou numéro incorrect');
    expect(await lireBilletRecu('r2')).toBeUndefined();
  });

  it('signale un lien sans clé', async () => {
    backend({});
    ouvrir('/billets/recu/r3');
    expect(await screen.findByRole('heading', { name: 'Lien incomplet' })).toBeInTheDocument();
  });

  it('s’affiche sans réseau une fois gardé, et figure dans « Mes billets »', async () => {
    await enregistrerBilletRecu({
      id: 'r4',
      jeton: JETON_DESTINATAIRE,
      telephone: '+221781234567',
      titre: 'Nuit du Mbalax',
      debuteLe: '2099-11-14T21:00:00Z',
      lieu: 'Grand Théâtre',
      recuLe: '2026-10-31T10:00:00Z',
      billet: { id: 'r4', eventId: 'e1', categorie: 'VIP', statut: 'EMIS', qr: 'qr-garde' },
    });
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    ouvrir('/billets/recu/r4');
    expect(await screen.findByRole('img', { name: /QR code du billet/ })).toBeInTheDocument();
  });

  it('apparaît dans « Mes billets »', async () => {
    await enregistrerBilletRecu({
      id: 'r5',
      jeton: JETON_DESTINATAIRE,
      telephone: '+221781234567',
      titre: 'Dakar Jazz',
      debuteLe: '2099-12-01T20:00:00Z',
      lieu: 'Sorano',
      recuLe: '2026-10-31T11:00:00Z',
      billet: { id: 'r5', eventId: 'e2', categorie: 'Standard', statut: 'EMIS', qr: 'qr-5' },
    });
    backend({});
    ouvrir('/billets');
    expect(await screen.findByRole('heading', { name: 'Reçus par transfert' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Dakar Jazz/ })).toHaveAttribute('href', '/billets/recu/r5');
  });
});

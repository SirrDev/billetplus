import 'fake-indexeddb/auto';
import { getPublicKeyAsync, signAsync, utils } from '@noble/ed25519';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Routage } from '@/App';
import { effacerJetons, enregistrerJetons, enregistrerProfil, lireJetons } from '@/commun/session';
import { mettreEnAttente, totalEnAttente, viderAppareil } from './fileHorsLigne';

/*
 * Contrôle d'entrée de bout en bout : backend simulé au niveau de fetch, IndexedDB en
 * mémoire, et une VRAIE paire Ed25519 générée pour le test (la clé publique est servie à
 * la place de /keys/ed25519_public.pem). jsdom n'a pas de caméra : on passe par la saisie
 * « douchette », qui emprunte le même chemin de décision que la caméra.
 */
let n = 0;
const nouvelId = () => `0000000${++n}`.slice(-8) + '-2197-4702-bc26-9723a3a585b0';

const AGENT = { id: 'a1', email: 'agent@test.sn', nomComplet: 'Awa Agent', role: 'AGENT' };

const hex = (o: Uint8Array) => Array.from(o, (b) => b.toString(16).padStart(2, '0')).join('');
const PREFIXE_SPKI = [0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00];

let prive: Uint8Array;
let pem: string;

beforeAll(async () => {
  prive = utils.randomPrivateKey();
  const publique = await getPublicKeyAsync(prive);
  pem = `-----BEGIN PUBLIC KEY-----\n${btoa(String.fromCharCode(...PREFIXE_SPKI, ...publique))}\n-----END PUBLIC KEY-----\n`;
});

async function qrSigne(eventId: string, ticketId = `t-${++n}`) {
  const json = JSON.stringify({ tid: ticketId, eid: eventId, iat: 1793000000000 });
  return `${json}.${hex(await signAsync(new TextEncoder().encode(json), prive))}`;
}

type Routes = Record<string, (init?: RequestInit) => unknown>;

/** `reseau.coupe` à true : toute requête API échoue comme une connexion absente. */
const reseau = { coupe: false };

function backend(routes: Routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entree, init) => {
    const url = new URL(String(entree), 'http://localhost');
    if (url.pathname === '/keys/ed25519_public.pem') return new Response(pem, { status: 200 });
    if (reseau.coupe) throw new TypeError('Failed to fetch');
    const cle = `${init?.method ?? 'GET'} ${url.pathname}`;
    const route = routes[cle];
    if (!route) return new Response(JSON.stringify({ message: `Route inattendue ${cle}` }), { status: 500 });
    const corps = route(init);
    return corps instanceof Response ? corps : new Response(JSON.stringify(corps), { status: 200 });
  });
}

function ouvrir(adresse: string) {
  return render(
    <MemoryRouter initialEntries={[adresse]}>
      <Routage />
    </MemoryRouter>,
  );
}

async function presenter(qr: string) {
  const champ = screen.getByLabelText('Contenu du QR (douchette ou saisie)');
  await userEvent.clear(champ);
  // Collage : un QR contient des accolades, que userEvent.type interpréterait.
  champ.focus();
  await userEvent.paste(qr);
  await userEvent.click(screen.getByRole('button', { name: 'Contrôler' }));
}

const evenement = (id: string) => ({ id, titre: 'Nuit du Mbalax', debuteLe: '2099-11-14T21:00:00Z', venueNom: 'Grand Théâtre', status: 'PUBLIE' });

beforeEach(async () => {
  effacerJetons();
  await viderAppareil();
  reseau.coupe = false;
  Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => !reseau.coupe });
});

describe('Connexion et choix de l’événement', () => {
  it('connecte un agent et lui propose les événements auxquels il est affecté', async () => {
    const id = nouvelId();
    const appels = backend({
      'POST /api/auth/login': () => ({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' }),
      'GET /api/auth/me': () => AGENT,
      'GET /api/events/affectes': () => [evenement(id)],
    });
    ouvrir('/scanner');
    expect(await screen.findByRole('heading', { name: "Connexion de l'agent" })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('E-mail'), 'agent@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByRole('heading', { name: 'Événement à contrôler' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Nuit du Mbalax/ })).toHaveAttribute('href', `/scanner/evenements/${id}`);
    const liste = appels.mock.calls.find(([u]) => String(u) === '/api/events/affectes');
    expect((liste?.[1]?.headers as Record<string, string>).Authorization).toBe('Bearer a');
  });

  it('rouvre la dernière liste sans réseau, pour le même compte seulement', async () => {
    const id = nouvelId();
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    enregistrerProfil(AGENT);
    backend({ 'GET /api/auth/me': () => AGENT, 'GET /api/events/affectes': () => [evenement(id)] });
    const premier = ouvrir('/scanner');
    expect(await screen.findByRole('link', { name: /Nuit du Mbalax/ })).toBeInTheDocument();
    premier.unmount();

    reseau.coupe = true;
    ouvrir('/scanner');
    expect(await screen.findByText(/dernière liste reçue sur cet appareil/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Nuit du Mbalax/ })).toBeInTheDocument();
  });

  it("dit à l'agent sans affectation de s'adresser à l'organisateur", async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    backend({ 'GET /api/auth/me': () => ({ ...AGENT, id: 'agent-sans-affectation' }), 'GET /api/events/affectes': () => [] });
    ouvrir('/scanner');
    expect(await screen.findByText(/ne vous a affecté à aucun événement/)).toBeInTheDocument();
  });

  it("refuse un compte qui ne peut pas contrôler, sans garder ses jetons", async () => {
    backend({
      'POST /api/auth/login': () => ({ accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' }),
      'GET /api/auth/me': () => ({ ...AGENT, role: 'ADMIN' }),
    });
    ouvrir('/scanner/connexion');
    await userEvent.type(screen.getByLabelText('E-mail'), 'admin@test.sn');
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }));
    expect(await screen.findByText('Ce compte ne peut pas contrôler les entrées.')).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
  });
});

describe('Réouverture sans réseau', () => {
  it("rouvre le scanner hors ligne avec le profil mémorisé, sans repasser par la connexion", async () => {
    const id = nouvelId();
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    enregistrerProfil(AGENT);
    reseau.coupe = true; // application relancée sans connexion
    backend({});
    ouvrir(`/scanner/evenements/${id}`);
    expect(await screen.findByText('Présentez le QR code')).toBeInTheDocument();
    expect(screen.getByText('Sans réseau')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: "Connexion de l'agent" })).not.toBeInTheDocument();
    // Et le contrôle marche : billet signé accepté sur l'appareil.
    await presenter(await qrSigne(id));
    expect(await screen.findByText('Laisser entrer')).toBeInTheDocument();
    expect(screen.getByText('Contrôle sans réseau')).toBeInTheDocument();
  });

  it('ferme toujours la session quand le backend refuse le jeton', async () => {
    enregistrerJetons({ accessToken: 'a', refreshToken: 'r' });
    enregistrerProfil(AGENT);
    backend({
      'GET /api/auth/me': () => new Response(JSON.stringify({ message: 'Non authentifie' }), { status: 401 }),
      'POST /api/auth/refresh': () => new Response(JSON.stringify({ message: 'Refus' }), { status: 401 }),
    });
    ouvrir('/scanner');
    expect(await screen.findByRole('heading', { name: "Connexion de l'agent" })).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
  });
});

describe('Contrôle', () => {
  beforeEach(() => enregistrerJetons({ accessToken: 'a', refreshToken: 'r' }));

  it("prévient avant d'effacer des entrées non synchronisées à la déconnexion", async () => {
    const id = nouvelId();
    backend({ 'GET /api/auth/me': () => AGENT, [`GET /api/events/${id}`]: () => evenement(id) });
    ouvrir(`/scanner/evenements/${id}`);
    await screen.findByRole('heading', { name: 'Nuit du Mbalax' });
    reseau.coupe = true;
    window.dispatchEvent(new Event('offline'));
    await presenter(await qrSigne(id));
    await screen.findByText('Laisser entrer');

    await userEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));
    expect(await screen.findByText(/1 entrée\(s\) faite\(s\) sans réseau ne sont pas encore envoyées/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Rester connecté' }));
    expect(lireJetons()).not.toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Me déconnecter quand même' }));
    expect(await screen.findByRole('heading', { name: "Connexion de l'agent" })).toBeInTheDocument();
    expect(lireJetons()).toBeNull();
    expect(await totalEnAttente()).toBe(0);
  });

  it('laisse entrer un billet accepté par le serveur, puis refuse sa seconde présentation', async () => {
    const id = nouvelId();
    let passages = 0;
    const appels = backend({
      'GET /api/auth/me': () => AGENT,
      [`GET /api/events/${id}`]: () => evenement(id),
      [`POST /api/events/${id}/scans`]: () =>
        ++passages === 1 ? { issue: 'ACCEPTE', ticketId: 't1' } : { issue: 'DEJA_SCANNE', ticketId: 't1', premierScanLe: '2099-11-14T21:02:00Z' },
    });
    ouvrir(`/scanner/evenements/${id}`);
    expect(await screen.findByRole('heading', { name: 'Nuit du Mbalax' })).toBeInTheDocument();
    expect(screen.getByText(/Caméra indisponible/)).toBeInTheDocument();

    const qr = await qrSigne(id, 't1');
    await presenter(qr);
    expect(await screen.findByText('Laisser entrer')).toBeInTheDocument();
    const scan = appels.mock.calls.find(([u]) => String(u).endsWith('/scans'));
    expect(JSON.parse(String(scan?.[1]?.body))).toEqual({ qrContent: qr });
    expect((scan?.[1]?.headers as Record<string, string>).Authorization).toBe('Bearer a');
    await waitFor(() => expect(screen.getByText('Entrées sur cet appareil').nextElementSibling).toHaveTextContent('1'));

    // Après le verdict, la lecture reprend ; le même billet est refusé avec l'heure du 1er passage.
    await waitFor(() => expect(screen.getByText('Présentez le QR code')).toBeInTheDocument(), { timeout: 4000 });
    // Relu aussitôt (billet resté devant la caméra) : ignoré par l'anti-rebond, aucun appel.
    await presenter(qr);
    expect(passages).toBe(1);
    // Présenté de nouveau plus tard : le serveur le refuse, avec l'heure du premier passage.
    const reel = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(reel + 5000);
    await presenter(qr);
    expect(await screen.findByText(/premier passage à 21 h 02/)).toBeInTheDocument();
    expect(screen.getByText("Refuser l'entrée")).toBeInTheDocument();
    // Attend l'écoulement réel du verdict (2,5 s) : la limite Vitest de 5 s par test était
    // dépassée par moments dans la suite complète sous Node 20 (CI).
  }, 15000);

  it("arrête le contrôle si l'agent n'est pas affecté à l'événement", async () => {
    const id = nouvelId();
    backend({
      'GET /api/auth/me': () => AGENT,
      [`GET /api/events/${id}`]: () => evenement(id),
      [`POST /api/events/${id}/scans`]: () => new Response(JSON.stringify({ message: 'Acces refuse' }), { status: 403 }),
    });
    ouvrir(`/scanner/evenements/${id}`);
    await screen.findByRole('heading', { name: 'Nuit du Mbalax' });
    await presenter(await qrSigne(id));
    expect(await screen.findByText(/pas affecté au contrôle de cet événement/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Choisir un autre événement' })).toHaveAttribute('href', '/scanner');
    // Le poste de contrôle est démonté : plus de lecteur (la caméra est libérée avec lui).
    expect(screen.queryByLabelText('Caméra de lecture des QR codes')).not.toBeInTheDocument();
  });

  it('après un 403 de synchronisation, permet d’effacer les entrées qui ne seront jamais acceptées', async () => {
    const id = nouvelId();
    await mettreEnAttente({ eventId: id, qr: 'q', scanneLe: '2099-11-14T21:00:00Z' });
    backend({
      'GET /api/auth/me': () => AGENT,
      [`GET /api/events/${id}`]: () => evenement(id),
      [`POST /api/events/${id}/scans`]: () => new Response(JSON.stringify({ message: 'Acces refuse' }), { status: 403 }),
    });
    ouvrir(`/scanner/evenements/${id}`);
    expect(await screen.findByText(/1 entrée\(s\) faite\(s\) sans réseau ne seront pas acceptées/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: "Effacer ces entrées de l'appareil" }));
    await waitFor(() => expect(screen.queryByText(/ne seront pas acceptées/)).not.toBeInTheDocument());
    expect(await totalEnAttente()).toBe(0);
  });

  it('sans réseau : accepte un billet signé, refuse un faux, puis synchronise au retour du réseau', async () => {
    const id = nouvelId();
    const recus: string[] = [];
    backend({
      'GET /api/auth/me': () => AGENT,
      [`GET /api/events/${id}`]: () => evenement(id),
      [`POST /api/events/${id}/scans`]: (init) => {
        recus.push((JSON.parse(String(init?.body)) as { qrContent: string }).qrContent);
        return { issue: 'DEJA_SCANNE', ticketId: 'tx', premierScanLe: '2099-11-14T21:00:00Z' };
      },
    });
    ouvrir(`/scanner/evenements/${id}`);
    await screen.findByRole('heading', { name: 'Nuit du Mbalax' });

    reseau.coupe = true;
    window.dispatchEvent(new Event('offline'));
    expect(await screen.findByText('Sans réseau')).toBeInTheDocument();

    const bon = await qrSigne(id);
    await presenter(bon);
    expect(await screen.findByText('Laisser entrer')).toBeInTheDocument();
    expect(screen.getByText(/contrôle sans réseau/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('À synchroniser').nextElementSibling).toHaveTextContent('1'));

    await waitFor(() => expect(screen.getByText('Présentez le QR code')).toBeInTheDocument(), { timeout: 4000 });
    const faux = bon.replace(/.$/, bon.endsWith('0') ? '1' : '0'); // signature altérée
    await presenter(faux);
    expect(await screen.findByText(/invalide ou falsifié/)).toBeInTheDocument();

    // Retour du réseau : la file part au serveur, qui signale un doublon (autre porte).
    reseau.coupe = false;
    window.dispatchEvent(new Event('online'));
    expect(await screen.findByText(/déjà entré\(s\) par une autre porte/)).toBeInTheDocument();
    expect(recus).toEqual([bon]);
    await waitFor(() => expect(screen.getByText('À synchroniser').nextElementSibling).toHaveTextContent('0'));
  }, 15000);
});

import { getPublicKeyAsync, signAsync, utils } from '@noble/ed25519';
import clePubliqueLivree from '../../../public/keys/ed25519_public.pem?raw';
import { clePemVersOctets, verifierHorsLigne } from './verificationHorsLigne';

/*
 * Paire de clés générée pour le test : on signe EXACTEMENT comme Ed25519QRSigner
 * (JSON + "." + signature hexadécimale). Aucune clé réelle n'est utilisée.
 */
const hex = (o: Uint8Array) => Array.from(o, (b) => b.toString(16).padStart(2, '0')).join('');

async function paire() {
  const prive = utils.randomPrivateKey();
  return { prive, publique: await getPublicKeyAsync(prive) };
}

async function qrSigne(prive: Uint8Array, contenu: object) {
  const json = JSON.stringify(contenu);
  return `${json}.${hex(await signAsync(new TextEncoder().encode(json), prive))}`;
}

const CONTENU = { tid: '4f2a91c0-0000-4000-8000-000000000001', eid: '65ef836a-2197-4702-bc26-9723a3a585b0', iat: 1793000000000 };

describe('verifierHorsLigne', () => {
  it('accepte un QR signé par la bonne clé et rend billet et événement', async () => {
    const { prive, publique } = await paire();
    expect(await verifierHorsLigne(await qrSigne(prive, CONTENU), publique)).toEqual({
      valide: true,
      ticketId: CONTENU.tid,
      eventId: CONTENU.eid,
    });
  });

  it('refuse un QR signé par une autre clé', async () => {
    const vraie = await paire();
    const fausse = await paire();
    expect(await verifierHorsLigne(await qrSigne(fausse.prive, CONTENU), vraie.publique)).toEqual({ valide: false });
  });

  it("refuse un QR dont le contenu a été modifié après signature (changement d'événement)", async () => {
    const { prive, publique } = await paire();
    const qr = await qrSigne(prive, CONTENU);
    const trafique = qr.replace(CONTENU.eid, '00000000-0000-4000-8000-000000000000');
    expect(await verifierHorsLigne(trafique, publique)).toEqual({ valide: false });
  });

  it.each([
    ['vide', ''],
    ['sans signature', '{"tid":"a","eid":"b"}'],
    ['signature trop courte', '{"tid":"a","eid":"b"}.abcd'],
    ['signature non hexadécimale', `{"tid":"a","eid":"b"}.${'z'.repeat(128)}`],
    ['JSON cassé', `{"tid":.${'0'.repeat(128)}`],
    ['trop long (> 1024)', `${'x'.repeat(1025)}.${'0'.repeat(128)}`],
  ])('refuse sans détail un QR %s', async (_cas, qr) => {
    const { publique } = await paire();
    expect(await verifierHorsLigne(qr, publique)).toEqual({ valide: false });
  });

  it('coupe au premier point, comme le backend', async () => {
    const { prive, publique } = await paire();
    const qr = await qrSigne(prive, CONTENU);
    // Un point de plus à la fin rend la signature invalide (le backend fait de même).
    expect(await verifierHorsLigne(`${qr}.00`, publique)).toEqual({ valide: false });
  });
});

/*
 * Interopérabilité Java -> navigateur : QR signé par la JCA du JDK 21
 * (Signature.getInstance("Ed25519"), comme Ed25519QRSigner) avec une paire JETABLE générée
 * le 2026-10-30 pour ce test. La clé privée n'a pas été conservée ; seule la clé publique
 * (X.509, comme ed25519_public.pem) et le QR produit sont figés ici.
 */
const PEM_JCA = '-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA4QqQFl31TWnWElSrf/mKY/AXaujbG+BnpHylo4ZXolM=\n-----END PUBLIC KEY-----\n';
const QR_JCA =
  '{"tid":"4f2a91c0-0000-4000-8000-000000000001","eid":"65ef836a-2197-4702-bc26-9723a3a585b0","iat":1793000000000}' +
  '.74726447d36aeda913a9b431bc187c2818ed1d03eef08573665b217875ec6896e2a9bfe1f0859abb145dc572d8089a0d92e47da87e66fb1007247f2f62a9f900';

describe('interopérabilité avec la signature du backend (JCA Java)', () => {
  it('accepte un QR signé par le JDK, avec une clé publique au format X.509', async () => {
    expect(await verifierHorsLigne(QR_JCA, clePemVersOctets(PEM_JCA))).toEqual({
      valide: true,
      ticketId: '4f2a91c0-0000-4000-8000-000000000001',
      eventId: '65ef836a-2197-4702-bc26-9723a3a585b0',
    });
  });

  it('refuse ce même QR au moindre caractère modifié', async () => {
    const cle = clePemVersOctets(PEM_JCA);
    expect(await verifierHorsLigne(QR_JCA.replace('"iat":1793000000000', '"iat":1793000000001'), cle)).toEqual({ valide: false });
    expect(await verifierHorsLigne(QR_JCA.replace(/.$/, '1'), cle)).toEqual({ valide: false });
  });

  it('refuse ce QR face à la clé livrée par la PWA (autre paire)', async () => {
    expect(await verifierHorsLigne(QR_JCA, clePemVersOctets(clePubliqueLivree))).toEqual({ valide: false });
  });
});

describe('clePemVersOctets', () => {
  it('lit la clé publique livrée par la PWA', () => {
    const pem = clePubliqueLivree;
    expect(pem).toContain('BEGIN PUBLIC KEY');
    expect(pem).not.toContain('PRIVATE');
    expect(clePemVersOctets(pem)).toHaveLength(32);
  });

  it("refuse une clé qui n'est pas Ed25519", () => {
    // En-tête d'une clé EC P-256 (OID différent), tronquée : jamais acceptée.
    const ec = btoa(String.fromCharCode(0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01));
    expect(() => clePemVersOctets(`-----BEGIN PUBLIC KEY-----\n${ec}\n-----END PUBLIC KEY-----`)).toThrow(/Ed25519/);
  });
});

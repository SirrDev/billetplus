import { getPublicKeyAsync, signAsync, utils } from '@noble/ed25519';
import { describe, expect, it } from 'vitest';
import { clePemVersOctets, verifierHorsLigne } from './verificationHorsLigne';

const hex = (octets: Uint8Array) => Array.from(octets, (o) => o.toString(16).padStart(2, '0')).join('');

/** QR au format du backend (Ed25519QRSigner) : {json}.{signature hexadécimale}. */
async function qr(clePrivee: Uint8Array, contenu: object): Promise<string> {
  const json = JSON.stringify(contenu);
  return `${json}.${hex(await signAsync(new TextEncoder().encode(json), clePrivee))}`;
}

describe('vérification hors ligne', () => {
  it('accepte un QR signé par la clé privée correspondante, et donne billet et événement', async () => {
    const privee = utils.randomPrivateKey();
    const publique = await getPublicKeyAsync(privee);
    const code = await qr(privee, { tid: 'billet-1', eid: 'evt-1', iat: 1_700_000_000_000 });

    await expect(verifierHorsLigne(code, publique)).resolves.toEqual({
      valide: true,
      ticketId: 'billet-1',
      eventId: 'evt-1',
    });
  });

  it('refuse un QR modifié, signé par une autre clé, ou mal formé', async () => {
    const privee = utils.randomPrivateKey();
    const publique = await getPublicKeyAsync(privee);
    const code = await qr(privee, { tid: 'billet-1', eid: 'evt-1', iat: 1 });

    // Changer le billet casse la signature.
    await expect(verifierHorsLigne(code.replace('billet-1', 'billet-2'), publique)).resolves.toEqual({ valide: false });
    const autre = await qr(utils.randomPrivateKey(), { tid: 'billet-1', eid: 'evt-1', iat: 1 });
    await expect(verifierHorsLigne(autre, publique)).resolves.toEqual({ valide: false });
    for (const casse of ['', 'pas un qr', '{"tid":"a"}.zz', `{"x":1}.${'00'.repeat(64)}`]) {
      await expect(verifierHorsLigne(casse, publique)).resolves.toEqual({ valide: false });
    }
  });

  it('lit les 32 octets de la clé dans un PEM « PUBLIC KEY » (SubjectPublicKeyInfo)', async () => {
    const publique = await getPublicKeyAsync(utils.randomPrivateKey());
    const entete = Uint8Array.from([0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00]);
    const der = new Uint8Array([...entete, ...publique]);
    const pem = `-----BEGIN PUBLIC KEY-----\n${btoa(String.fromCharCode(...der))}\n-----END PUBLIC KEY-----\n`;

    expect(clePemVersOctets(pem)).toEqual(publique);
  });
});

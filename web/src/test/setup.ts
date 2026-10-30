import '@testing-library/jest-dom/vitest';
import { etc } from '@noble/ed25519';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

/*
 * Sous jsdom et Node 20 (version de la CI), WebCrypto refuse un ArrayBuffer créé dans le
 * royaume de jsdom (« 2nd argument is not instance of ArrayBuffer »), alors qu'il accepte
 * un Uint8Array. @noble/ed25519 passe un ArrayBuffer à crypto.subtle.digest : on lui
 * fournit un SHA-512 qui transmet un Uint8Array. Tests seulement : les navigateurs, et
 * Node 22 et plus, n'ont pas ce défaut (HANDOFF, section 10, n° 42).
 */
etc.sha512Async = async (...messages: Uint8Array[]) =>
  // concatBytes alloue toujours un ArrayBuffer ordinaire (jamais un SharedArrayBuffer).
  new Uint8Array(await globalThis.crypto.subtle.digest('SHA-512', etc.concatBytes(...messages) as Uint8Array<ArrayBuffer>));

/*
 * Délai des findBy / waitFor : 1 s par défaut. Les parcours de bout en bout (connexion puis
 * liste, signature Ed25519) le dépassaient parfois dans la suite complète sous Node 20 (CI),
 * alors qu'ils passaient seuls : 3 s évitent un échec au hasard sans masquer un vrai défaut.
 */
configure({ asyncUtilTimeout: 3000 });

beforeEach(() => {
  // jsdom n'implémente pas le défilement (App remonte en haut à chaque page).
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

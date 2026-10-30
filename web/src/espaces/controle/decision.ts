import { ApiError } from '@/commun/http';
import { formatHeure } from '@/commun/format';
import type { ReponseScan } from './api';
import { TAILLE_MAX_QR, type ResultatHorsLigne } from './verificationHorsLigne';

/*
 * Décision à l'entrée, à partir du contenu lu dans un QR. Le serveur fait foi quand il
 * répond ; sans réseau (ou serveur en panne), la signature Ed25519 est vérifiée sur
 * l'appareil (ADR 0004) et l'entrée est mise en file pour être confirmée plus tard.
 * Toute issue autre qu'ACCEPTE est un refus d'entrée (contrat de ScanResponse).
 */

export type Verdict =
  | { type: 'entrer'; titre: string; detail: string; horsLigne: boolean }
  | { type: 'refuser'; titre: string; detail: string; horsLigne: boolean }
  /** 403 : l'utilisateur ne contrôle pas cet événement. Jamais contourné par le hors-ligne. */
  | { type: 'non-affecte' }
  /** 401 après rafraîchissement : la session est fermée, il faut se reconnecter. */
  | { type: 'session' };

export interface Dependances {
  scanner: (qr: string) => Promise<ReponseScan>;
  verifierHorsLigne: (qr: string) => Promise<ResultatHorsLigne>;
  dejaEntreIci: (ticketId: string) => Promise<string | null>;
  noterEntree: (ticketId: string, entreLe: string) => Promise<void>;
  mettreEnAttente: (qr: string, scanneLe: string) => Promise<void>;
  maintenant: () => Date;
}

const refus = (titre: string, detail: string, horsLigne = false): Verdict => ({ type: 'refuser', titre, detail, horsLigne });

const INVALIDE = (horsLigne = false) =>
  refus("Refuser l'entrée", "QR code invalide ou falsifié : ce billet n'est pas reconnu.", horsLigne);

export async function decider(qr: string, eventId: string, d: Dependances): Promise<Verdict> {
  const contenu = qr.trim();
  if (!contenu || contenu.length > TAILLE_MAX_QR) return INVALIDE();

  let reponse: ReponseScan | null = null;
  try {
    reponse = await d.scanner(contenu);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 403) return { type: 'non-affecte' };
      if (e.status === 401) return { type: 'session' };
      if (e.status === 400) return INVALIDE();
      if (e.status < 500 && e.status !== 429) return refus("Refuser l'entrée", e.message);
      // 429 et 5xx : serveur indisponible, on contrôle sur l'appareil.
    }
    // Réseau absent : contrôle hors ligne ci-dessous.
  }

  if (reponse) return verdictDuServeur(reponse, d);
  return verdictHorsLigne(contenu, eventId, d);
}

async function verdictDuServeur(r: ReponseScan, d: Dependances): Promise<Verdict> {
  switch (r.issue) {
    case 'ACCEPTE':
      // Le serveur a déjà enregistré l'entrée (billet SCANNE) : la mémoire locale n'est
      // qu'un confort hors ligne. Son échec ne doit pas faire refuser un billet valide.
      if (r.ticketId) await d.noterEntree(r.ticketId, d.maintenant().toISOString()).catch(() => undefined);
      return { type: 'entrer', titre: 'Laisser entrer', detail: 'Billet valide, entrée enregistrée.', horsLigne: false };
    case 'DEJA_SCANNE':
      return refus(
        "Refuser l'entrée",
        r.premierScanLe ? `Billet déjà utilisé : premier passage à ${formatHeure(r.premierScanLe)}.` : 'Billet déjà utilisé.',
      );
    case 'NON_VALIDE':
      return refus("Refuser l'entrée", r.statut === 'REMBOURSE' ? 'Billet remboursé.' : 'Billet annulé.');
    case 'AUTRE_EVENEMENT':
      return refus("Refuser l'entrée", "Ce billet est valable pour un autre événement.");
    default:
      return INVALIDE();
  }
}

async function verdictHorsLigne(qr: string, eventId: string, d: Dependances): Promise<Verdict> {
  const v = await d.verifierHorsLigne(qr);
  if (!v.valide) return INVALIDE(true);
  if (v.eventId !== eventId) return refus("Refuser l'entrée", 'Ce billet est valable pour un autre événement.', true);
  const dejaEntre = await d.dejaEntreIci(v.ticketId);
  if (dejaEntre) {
    return refus("Refuser l'entrée", `Billet déjà entré par cet appareil à ${formatHeure(dejaEntre)}.`, true);
  }
  const instant = d.maintenant().toISOString();
  // Mise en file AVANT de noter l'entrée : si la file échoue, aucune entrée n'est comptée.
  await d.mettreEnAttente(qr, instant);
  await d.noterEntree(v.ticketId, instant);
  return {
    type: 'entrer',
    titre: 'Laisser entrer',
    detail: 'Signature valide (contrôle sans réseau) : entrée confirmée au retour de la connexion.',
    horsLigne: true,
  };
}

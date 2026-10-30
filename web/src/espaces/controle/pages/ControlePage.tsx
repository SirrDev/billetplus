import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { formatDateCourte } from '@/commun/format';
import { ApiError } from '@/commun/http';
import { Avis, Bouton, BoutonLien, CompteursControle, EcranControle, Icone, PanneauVerdict, Viseur } from '@/ui';
import { lireEvenement, scanner, type EvenementControle } from '../api';
import { decider, type Verdict } from '../decision';
import {
  abandonner,
  dejaEntreIci,
  entreesIci,
  mettreEnAttente,
  noterEntree,
  scansEnAttente,
  synchroniser,
} from '../fileHorsLigne';
import { useLecteurQr } from '../useLecteurQr';
import { verifierHorsLigne } from '../verificationHorsLigne';
import { CadreControle } from './CadreControle';
import s from './controle.module.css';

/** Durée d'affichage d'un verdict avant de reprendre la lecture. */
const DUREE_VERDICT_MS = 2500;
/** Un même QR relu dans ce délai (le billet reste devant la caméra) est ignoré. */
const ANTI_REBOND_MS = 4000;
/** Envoi périodique des scans faits hors ligne, quand le réseau est là. */
const INTERVALLE_SYNCHRO_MS = 30_000;

const MESSAGES_CAMERA = {
  demarrage: 'Ouverture de la caméra…',
  refusee: "Caméra refusée : autorisez-la dans les réglages du navigateur, ou utilisez la saisie ci-dessous.",
  indisponible: 'Caméra indisponible sur cet appareil. Utilisez une douchette ou la saisie ci-dessous.',
} as const;

/**
 * Contrôle d'un événement. Sur un 403 (utilisateur non affecté), le poste de contrôle est
 * démonté : la caméra s'arrête avec lui (useLecteurQr libère ses pistes au démontage).
 */
export function ControlePage() {
  const { id = '' } = useParams();
  const [nonAffecte, setNonAffecte] = useState(false);
  const signalerNonAffecte = useCallback(() => setNonAffecte(true), []);
  return (
    <CadreControle>
      {nonAffecte ? <NonAffecte eventId={id} /> : <PosteControle eventId={id} onNonAffecte={signalerNonAffecte} />}
    </CadreControle>
  );
}

function NonAffecte({ eventId }: { eventId: string }) {
  const [enAttente, setEnAttente] = useState(0);
  useEffect(() => {
    scansEnAttente(eventId)
      .then(setEnAttente)
      .catch(() => undefined);
  }, [eventId]);
  const toutAbandonner = async () => {
    await abandonner(eventId).catch(() => undefined);
    setEnAttente(await scansEnAttente(eventId).catch(() => 0));
  };
  return (
    <Avis ton="danger">
      <p>
        <strong>Vous n'êtes pas affecté au contrôle de cet événement.</strong> Demandez à l'organisateur de vous affecter
        depuis son espace, puis réessayez.
      </p>
      {enAttente > 0 && (
        <p>
          {enAttente} entrée(s) faite(s) sans réseau ne seront pas acceptées par le serveur. Signalez-le au responsable,
          puis effacez-les de cet appareil.
        </p>
      )}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        <BoutonLien to="/scanner" variante="secondaire">
          Choisir un autre événement
        </BoutonLien>
        {enAttente > 0 && (
          <Bouton variante="secondaire" onClick={() => void toutAbandonner()}>
            Effacer ces entrées de l'appareil
          </Bouton>
        )}
      </div>
    </Avis>
  );
}

function PosteControle({ eventId: id, onNonAffecte }: { eventId: string; onNonAffecte: () => void }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [evenement, setEvenement] = useState<EvenementControle | null>(null);
  const [verdict, setVerdict] = useState<Verdict | null>(null);
  const [occupe, setOccupe] = useState(false);
  const [entrees, setEntrees] = useState(0);
  const [enAttente, setEnAttente] = useState(0);
  const [enLigne, setEnLigne] = useState(() => navigator.onLine);
  const [bilan, setBilan] = useState<{ texte: string; ton: 'succes' | 'attention' } | null>(null);
  const [saisie, setSaisie] = useState('');
  const dernier = useRef<{ qr: string; a: number } | null>(null);
  const minuterie = useRef<number | undefined>(undefined);

  const rafraichirCompteurs = useCallback(async () => {
    const [n, attente] = await Promise.all([entreesIci(id), scansEnAttente(id)]);
    setEntrees(n);
    setEnAttente(attente);
  }, [id]);

  // L'événement est lu sur une route publique, gardée par le service worker : affichable hors ligne.
  useEffect(() => {
    lireEvenement(id)
      .then(setEvenement)
      .catch(() => setEvenement(null));
    void rafraichirCompteurs().catch(() => undefined);
  }, [id, rafraichirCompteurs]);

  const synchroniserFile = useCallback(async () => {
    if (!navigator.onLine) return;
    try {
      const r = await synchroniser(id, (qr) => scanner(id, qr));
      if (r.envoyes > 0) {
        const alertes = [
          r.doublons > 0 && `${r.doublons} billet(s) déjà entré(s) par une autre porte`,
          r.refuses > 0 && `${r.refuses} billet(s) refusé(s) par le serveur`,
        ].filter(Boolean);
        setBilan(
          alertes.length > 0
            ? {
                ton: 'attention',
                texte: `Synchronisation : ${r.envoyes} entrée(s) envoyée(s). À signaler au responsable : ${alertes.join(', ')}.`,
              }
            : { ton: 'succes', texte: `Synchronisation : ${r.envoyes} entrée(s) confirmée(s) par le serveur.` },
        );
      }
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) onNonAffecte();
    } finally {
      await rafraichirCompteurs().catch(() => undefined);
    }
  }, [id, rafraichirCompteurs, onNonAffecte]);

  useEffect(() => {
    const passerEnLigne = () => {
      setEnLigne(true);
      void synchroniserFile();
    };
    const passerHorsLigne = () => setEnLigne(false);
    window.addEventListener('online', passerEnLigne);
    window.addEventListener('offline', passerHorsLigne);
    void synchroniserFile();
    const periodique = window.setInterval(() => void synchroniserFile(), INTERVALLE_SYNCHRO_MS);
    return () => {
      window.removeEventListener('online', passerEnLigne);
      window.removeEventListener('offline', passerHorsLigne);
      window.clearInterval(periodique);
      window.clearTimeout(minuterie.current);
    };
  }, [synchroniserFile]);

  const controler = useCallback(
    async (contenu: string) => {
      const qr = contenu.trim();
      if (!qr || occupe) return;
      const maintenant = Date.now();
      if (dernier.current && dernier.current.qr === qr && maintenant - dernier.current.a < ANTI_REBOND_MS) return;
      dernier.current = { qr, a: maintenant };
      setOccupe(true);
      let resultat: Verdict;
      try {
        resultat = await decider(qr, id, {
          scanner: (q) => scanner(id, q),
          verifierHorsLigne,
          dejaEntreIci: (t) => dejaEntreIci(id, t),
          noterEntree: (t, quand) => noterEntree(id, t, quand),
          mettreEnAttente: (q, quand) => mettreEnAttente({ eventId: id, qr: q, scanneLe: quand }),
          maintenant: () => new Date(),
        });
      } catch {
        // Stockage local en échec pendant un contrôle hors ligne : pas d'entrée sans trace.
        resultat = {
          type: 'refuser',
          titre: 'Contrôle impossible',
          detail: "L'appareil n'a pas pu enregistrer l'entrée. Réessayez ou appelez le responsable.",
          horsLigne: true,
        };
      }
      if (resultat.type === 'session') {
        navigate('/scanner/connexion', { replace: true, state: { depuis: location.pathname } });
        return;
      }
      if (resultat.type === 'non-affecte') {
        onNonAffecte();
        return;
      }
      setVerdict(resultat);
      navigator.vibrate?.(resultat.type === 'entrer' ? 120 : [120, 80, 120]);
      void rafraichirCompteurs().catch(() => undefined);
      minuterie.current = window.setTimeout(() => {
        setVerdict(null);
        setOccupe(false);
      }, DUREE_VERDICT_MS);
    },
    [id, occupe, navigate, location.pathname, rafraichirCompteurs, onNonAffecte],
  );

  const { video, etat } = useLecteurQr((qr) => void controler(qr), !occupe);

  const viaDouchette = (e: FormEvent) => {
    e.preventDefault();
    const contenu = saisie;
    setSaisie('');
    void controler(contenu);
  };

  const affiche = verdict && (verdict.type === 'entrer' || verdict.type === 'refuser') ? verdict : null;

  return (
    <EcranControle>
      <div className={s.enTeteSombre}>
        <div>
          <p>{evenement ? `${formatDateCourte(evenement.debuteLe)} · ${evenement.venueNom}` : "Contrôle d'entrée"}</p>
          <h1>{evenement?.titre ?? 'Événement'}</h1>
        </div>
        <span className={`${s.etatReseau} ${enLigne ? s.enLigne : s.horsLigne}`}>
          <Icone nom={enLigne ? 'valide' : 'horsLigne'} taille={14} />
          {enLigne ? 'En ligne' : 'Sans réseau'}
        </span>
      </div>

      <div className={s.controle}>
        <Viseur message={etat === 'active' ? undefined : MESSAGES_CAMERA[etat]}>
          <video ref={video} muted playsInline aria-label="Caméra de lecture des QR codes" />
        </Viseur>

        <div className={s.pile} style={{ gap: 16 }}>
          {affiche ? (
            <PanneauVerdict
              type={affiche.type}
              titre={affiche.titre}
              detail={affiche.detail}
              mention={affiche.horsLigne ? 'Contrôle sans réseau' : undefined}
            />
          ) : (
            <PanneauVerdict
              type="attente"
              titre={occupe ? 'Vérification…' : 'Présentez le QR code'}
              detail="Billet à l'écran ou imprimé, luminosité au maximum."
            />
          )}
          <CompteursControle entrees={entrees} enAttente={enAttente} />
          {!enLigne && (
            <Avis ton="attention" icone="horsLigne">
              <p>
                Sans réseau, seule la signature du billet est vérifiée : un billet annulé ou déjà passé à une autre porte
                ne peut pas être détecté. Le serveur le signalera à la synchronisation.
              </p>
            </Avis>
          )}
          {bilan && (
            <Avis ton={bilan.ton}>
              <p>{bilan.texte}</p>
            </Avis>
          )}
          <form className={s.douchette} onSubmit={viaDouchette}>
            <label htmlFor="saisie-qr">Contenu du QR (douchette ou saisie)</label>
            <input
              id="saisie-qr"
              value={saisie}
              onChange={(e) => setSaisie(e.target.value)}
              placeholder="Douchette : visez le QR"
              autoComplete="off"
              maxLength={1024}
            />
            <button type="submit" disabled={occupe}>
              Contrôler
            </button>
          </form>
        </div>
      </div>
    </EcranControle>
  );
}

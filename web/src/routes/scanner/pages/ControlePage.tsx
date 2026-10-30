import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button,
  Notice,
  ScanResultPanel,
  ScannerViewfinder,
  Screen,
  TextField,
  TopBar,
  type ScanStatus,
} from '@/design-system';
import { ApiError } from '@/shared/api/httpClient';
import m from '@/shared/ui/miseEnPage.module.css';
import { lireEvenement, scanner, type ResultatScan } from '../api';
import { mettreEnAttente, nombreEnAttente, synchroniser } from '../fileHorsLigne';
import { useLecteurQr } from '../useLecteurQr';
import { verifierHorsLigne } from '../verificationHorsLigne';
import s from './scanner.module.css';

interface Verdict {
  statut: ScanStatus;
  code: string;
  premierPassage?: string;
  /** Raison d'un refus, affichée sous le verdict (le panneau ne dit que « Refuser l'entrée »). */
  motif?: string;
  horsLigne?: boolean;
}

/** Code lisible du billet (le même que sur le billet de l'acheteur) : BP-XXXX-XXXX. */
function codeBillet(id: string | null | undefined): string {
  if (!id) return '—';
  const hex = id.replace(/-/g, '').toUpperCase();
  return `BP-${hex.slice(0, 4)}-${hex.slice(4, 8)}`;
}

function heure(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Dakar' }).format(
    new Date(iso),
  );
}

function verdictEnLigne(r: ResultatScan): Verdict {
  const code = codeBillet(r.ticketId);
  switch (r.issue) {
    case 'ACCEPTE':
      return { statut: 'valid', code };
    case 'DEJA_SCANNE':
      return {
        statut: 'used',
        code,
        premierPassage: r.premierScanLe ? `Premier passage à ${heure(r.premierScanLe)}` : undefined,
        motif: 'Ce billet a déjà servi : il peut s’agir d’une copie.',
      };
    case 'NON_VALIDE':
      return { statut: 'invalid', code, motif: 'Billet annulé ou remboursé.' };
    case 'AUTRE_EVENEMENT':
      return { statut: 'invalid', code, motif: 'Billet valable, mais pour un autre événement.' };
    case 'INVALIDE':
      return { statut: 'invalid', code: '—', motif: 'QR code inconnu ou falsifié.' };
  }
}

/**
 * Contrôle d'entrée d'un événement : caméra (ou douchette, ou saisie), verdict du
 * serveur, et repli hors ligne par vérification de la signature sur l'appareil.
 */
export function ControlePage() {
  const { id = '' } = useParams();
  const [titre, setTitre] = useState('Contrôle');
  const [verdict, setVerdict] = useState<Verdict | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [alerte, setAlerte] = useState<string | null>(null);
  const [acceptes, setAcceptes] = useState(0);
  const [enAttente, setEnAttente] = useState(0);
  const [saisie, setSaisie] = useState('');

  useEffect(() => {
    lireEvenement(id)
      .then((e) => setTitre(e.titre))
      .catch(() => undefined); // Le titre est un confort : le contrôle marche sans.
  }, [id]);

  // Envoi des scans faits hors ligne, au chargement puis à chaque retour du réseau.
  const envoyerLaFile = useCallback(async () => {
    try {
      await synchroniser();
    } finally {
      setEnAttente(await nombreEnAttente().catch(() => 0));
    }
  }, []);
  useEffect(() => {
    void envoyerLaFile();
    const surRetour = () => void envoyerLaFile();
    window.addEventListener('online', surRetour);
    return () => window.removeEventListener('online', surRetour);
  }, [envoyerLaFile]);

  const horsLigne = useCallback(
    async (qr: string): Promise<Verdict> => {
      const resultat = await verifierHorsLigne(qr);
      if (!resultat.valide)
        return { statut: 'invalid', code: '—', motif: 'QR code inconnu ou falsifié.', horsLigne: true };
      if (resultat.eventId !== id) {
        return {
          statut: 'invalid',
          code: codeBillet(resultat.ticketId),
          motif: 'Billet pour un autre événement.',
          horsLigne: true,
        };
      }
      await mettreEnAttente({ eventId: id, qr, scanneLe: new Date().toISOString() });
      setEnAttente(await nombreEnAttente());
      return { statut: 'valid', code: codeBillet(resultat.ticketId), horsLigne: true };
    },
    [id],
  );

  const traiter = useCallback(
    async (qr: string) => {
      const contenu = qr.trim();
      if (!contenu || enCours) return;
      setEnCours(true);
      setAlerte(null);
      try {
        let resultat: Verdict;
        if (!navigator.onLine) {
          resultat = await horsLigne(contenu);
        } else {
          try {
            resultat = verdictEnLigne(await scanner(id, contenu));
          } catch (e) {
            if (e instanceof ApiError && e.status === 403) {
              setAlerte(
                "Vous n'êtes pas affecté au contrôle de cet événement. Demandez à l'organisateur de vous affecter.",
              );
              return;
            }
            if (e instanceof ApiError && e.status === 404) {
              setAlerte('Événement introuvable ou fermé.');
              return;
            }
            if (e instanceof ApiError && e.status === 429) {
              setAlerte('Trop de scans en une minute : patientez un instant.');
              return;
            }
            if (e instanceof ApiError && e.status < 500) {
              setAlerte(e.message);
              return;
            }
            // Réseau coupé ou serveur indisponible : vérification sur l'appareil.
            resultat = await horsLigne(contenu);
          }
        }
        if (resultat.statut === 'valid') setAcceptes((n) => n + 1);
        setVerdict(resultat);
      } finally {
        setEnCours(false);
      }
    },
    [enCours, horsLigne, id],
  );

  const { video, etat } = useLecteurQr(verdict === null && !enCours, (contenu) => void traiter(contenu));

  const soumettreSaisie = (e: FormEvent) => {
    e.preventDefault();
    void traiter(saisie);
    setSaisie('');
  };

  return (
    <Screen dark>
      <TopBar title={titre} subtitle="Contrôle d'entrée" backTo="/scanner" backLabel="Changer d'événement" onDark />
      <div className={`${m.deuxColonnes} ${m.apresTitre}`}>
        <div className={m.pile}>
          {verdict ? (
            <div className={s.verdict}>
              <ScanResultPanel
                status={verdict.statut}
                ticketCode={verdict.code}
                firstScanLabel={verdict.premierPassage}
                onNext={() => setVerdict(null)}
              />
            </div>
          ) : (
            <ScannerViewfinder hint={etat === 'active' ? 'Placez le QR code dans le cadre' : 'Caméra en attente'}>
              {etat === 'active' || etat === 'demarrage' ? (
                <video ref={video} className={s.video} playsInline muted aria-label="Image de la caméra" />
              ) : null}
            </ScannerViewfinder>
          )}
          {verdict?.motif ? (
            <Notice tone={verdict.statut === 'used' ? 'warning' : 'danger'} role="alert" icon="warning">
              {verdict.motif}
            </Notice>
          ) : null}
          {verdict?.horsLigne && verdict.statut === 'valid' ? (
            <Notice tone="warning" icon="wifiOff">
              Contrôle hors ligne : signature authentique, mais une double entrée à une autre porte ne peut pas être
              détectée. Le scan sera envoyé au serveur au retour du réseau.
            </Notice>
          ) : null}
          {etat === 'refusee' ? (
            <Notice tone="warning" role="alert" icon="warning">
              Caméra refusée : autorisez-la dans les réglages du navigateur, ou utilisez la saisie ci-dessous.
            </Notice>
          ) : null}
          {etat === 'indisponible' ? (
            <Notice icon="scan">Pas de caméra disponible : utilisez une douchette ou la saisie ci-dessous.</Notice>
          ) : null}
        </div>

        <div className={`${m.pile} ${m.lateral}`}>
          {alerte ? (
            <Notice tone="danger" role="alert" icon="lock">
              {alerte}
            </Notice>
          ) : null}
          <p className={s.compteur} aria-live="polite">
            <strong>{acceptes}</strong> entrée{acceptes > 1 ? 's' : ''} acceptée{acceptes > 1 ? 's' : ''} sur cet
            appareil
            {enAttente > 0 ? ` · ${enAttente} à synchroniser` : ''}
          </p>
          {/* Carte claire : les champs du design system sont prévus pour un fond clair. */}
          <form className={`${m.pile} ${s.carteClaire}`} onSubmit={soumettreSaisie}>
            <TextField
              label="Douchette ou saisie manuelle"
              hint="Une douchette USB remplit ce champ et valide seule."
              value={saisie}
              onChange={(e) => setSaisie(e.target.value)}
              autoComplete="off"
              maxLength={1024}
            />
            <Button type="submit" variant="secondary" loading={enCours} disabled={!saisie.trim()}>
              Contrôler ce code
            </Button>
          </form>
          {enAttente > 0 ? (
            <Button variant="ghost" icon="refresh" onClick={() => void envoyerLaFile()}>
              Synchroniser maintenant
            </Button>
          ) : null}
        </div>
      </div>
    </Screen>
  );
}

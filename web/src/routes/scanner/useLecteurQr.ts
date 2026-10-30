import jsQR from 'jsqr';
import { useEffect, useRef, useState, type RefObject } from 'react';

/** API native de détection (Chrome sur Android) : absente des types DOM de TypeScript. */
interface DetecteurNatif {
  detect(source: CanvasImageSource): Promise<Array<{ rawValue: string }>>;
}
declare global {
  interface Window {
    BarcodeDetector?: new (options: { formats: string[] }) => DetecteurNatif;
  }
}

export type EtatCamera = 'arretee' | 'demarrage' | 'active' | 'refusee' | 'indisponible';

/** Après une lecture, le même QR n'est pas relu pendant ce délai (le billet reste devant l'objectif). */
const DELAI_ENTRE_LECTURES_MS = 2500;

/**
 * Caméra arrière et lecture des QR. Utilise BarcodeDetector quand le navigateur l'a
 * (rapide, natif), sinon jsQR sur une image extraite de la vidéo (iPhone, ordinateurs).
 *
 * @param actif  caméra allumée (éteinte pendant l'affichage d'un résultat, pour économiser la batterie)
 * @param surLecture appelé avec le contenu du QR lu
 */
export function useLecteurQr(
  actif: boolean,
  surLecture: (contenu: string) => void,
): {
  video: RefObject<HTMLVideoElement>;
  etat: EtatCamera;
} {
  const video = useRef<HTMLVideoElement>(null);
  const [etat, setEtat] = useState<EtatCamera>('arretee');
  const rappel = useRef(surLecture);
  rappel.current = surLecture;

  useEffect(() => {
    if (!actif) {
      setEtat('arretee');
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setEtat('indisponible');
      return;
    }
    let flux: MediaStream | undefined;
    let arrete = false;
    let image: number | undefined;
    let derniere = { contenu: '', le: 0 };
    const toile = document.createElement('canvas');
    const contexte = toile.getContext('2d', { willReadFrequently: true });
    const natif = window.BarcodeDetector ? new window.BarcodeDetector({ formats: ['qr_code'] }) : null;

    const signaler = (contenu: string) => {
      const maintenant = Date.now();
      if (contenu === derniere.contenu && maintenant - derniere.le < DELAI_ENTRE_LECTURES_MS) return;
      derniere = { contenu, le: maintenant };
      rappel.current(contenu);
    };

    const analyser = async () => {
      const element = video.current;
      if (arrete || !element) return;
      if (element.readyState >= element.HAVE_ENOUGH_DATA) {
        try {
          if (natif) {
            const codes = await natif.detect(element);
            if (codes[0]?.rawValue) signaler(codes[0].rawValue);
          } else if (contexte) {
            toile.width = element.videoWidth;
            toile.height = element.videoHeight;
            contexte.drawImage(element, 0, 0, toile.width, toile.height);
            const pixels = contexte.getImageData(0, 0, toile.width, toile.height);
            const code = jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'dontInvert' });
            if (code?.data) signaler(code.data);
          }
        } catch {
          // Image illisible : on passe à la suivante.
        }
      }
      image = requestAnimationFrame(() => void analyser());
    };

    setEtat('demarrage');
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then(async (f) => {
        if (arrete) {
          f.getTracks().forEach((t) => t.stop());
          return;
        }
        flux = f;
        if (video.current) {
          video.current.srcObject = f;
          await video.current.play().catch(() => undefined);
        }
        setEtat('active');
        void analyser();
      })
      .catch((e: unknown) => {
        setEtat(e instanceof DOMException && e.name === 'NotAllowedError' ? 'refusee' : 'indisponible');
      });

    return () => {
      arrete = true;
      if (image !== undefined) cancelAnimationFrame(image);
      flux?.getTracks().forEach((t) => t.stop());
    };
  }, [actif]);

  return { video, etat };
}

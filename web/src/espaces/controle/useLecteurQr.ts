import { useEffect, useRef, useState } from 'react';
import jsQR from 'jsqr';

/** Intervalle d'analyse : assez rapide pour l'entrée, sans vider la batterie. */
const INTERVALLE_MS = 200;

export type EtatCamera = 'demarrage' | 'active' | 'refusee' | 'indisponible';

/**
 * Lit les QR codes depuis la caméra arrière. Décodage par jsQR (tous navigateurs, y compris
 * Safari iOS sans BarcodeDetector). `actif` à false met la caméra en pause (verdict affiché).
 * La caméra est arrêtée au démontage : aucune piste ne reste ouverte.
 */
export function useLecteurQr(onCode: (contenu: string) => void, actif: boolean) {
  const video = useRef<HTMLVideoElement | null>(null);
  const [etat, setEtat] = useState<EtatCamera>('demarrage');
  const rappel = useRef(onCode);
  rappel.current = onCode;
  const enCours = useRef(actif);
  enCours.current = actif;

  useEffect(() => {
    if (!navigator.mediaDevices?.getUserMedia) {
      // Pas de caméra, ou page servie sans HTTPS (getUserMedia exige un contexte sécurisé).
      setEtat('indisponible');
      return;
    }
    let flux: MediaStream | null = null;
    let minuterie: number | undefined;
    let arrete = false;
    const toile = document.createElement('canvas');
    const contexte = toile.getContext('2d', { willReadFrequently: true });

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then(async (f) => {
        if (arrete) return f.getTracks().forEach((t) => t.stop());
        flux = f;
        const v = video.current;
        if (!v) return;
        v.srcObject = f;
        v.setAttribute('playsinline', 'true'); // iOS : pas de plein écran forcé
        await v.play().catch(() => undefined);
        setEtat('active');
        minuterie = window.setInterval(() => {
          if (!enCours.current || !contexte || v.readyState < v.HAVE_ENOUGH_DATA) return;
          toile.width = v.videoWidth;
          toile.height = v.videoHeight;
          contexte.drawImage(v, 0, 0, toile.width, toile.height);
          const image = contexte.getImageData(0, 0, toile.width, toile.height);
          const code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
          if (code?.data) rappel.current(code.data);
        }, INTERVALLE_MS);
      })
      .catch((e: unknown) => {
        if (arrete) return;
        const nom = e instanceof DOMException ? e.name : '';
        setEtat(nom === 'NotAllowedError' || nom === 'SecurityError' ? 'refusee' : 'indisponible');
      });

    return () => {
      arrete = true;
      window.clearInterval(minuterie);
      flux?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  return { video, etat };
}

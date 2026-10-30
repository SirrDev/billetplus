import { useState } from 'react';
import { formatDateLongue, formatHeure } from '@/commun/format';
import { Avis, Bouton, codeBillet, Icone } from '@/ui';
import type { Billet } from '../api';
import s from './pages.module.css';

/**
 * Télécharge les billets valables (EMIS, avec QR) en PDF. jsPDF et qrcode ne sont chargés
 * qu'au clic : l'acheteur qui ne veut pas de PDF ne les télécharge pas. Sans billet
 * valable (tous déjà utilisés ou annulés), le bouton n'est pas affiché : chaque billet
 * montre déjà son statut.
 */
export function BoutonBilletsPdf({
  billets,
  titre,
  debuteLe,
  lieu,
  commandeId,
}: {
  billets: ReadonlyArray<Billet>;
  titre: string;
  debuteLe: string;
  lieu: string;
  commandeId: string;
}) {
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState(false);
  const valables = billets.filter((b) => b.statut === 'EMIS' && b.qr !== null);
  if (valables.length === 0) return null;

  const telecharger = async () => {
    setErreur(false);
    setEnCours(true);
    try {
      const { telechargerBilletsPdf } = await import('../billetPdf');
      telechargerBilletsPdf({
        titre,
        date: `${formatDateLongue(debuteLe)} à ${formatHeure(debuteLe)}`,
        lieu,
        commandeId,
        billets: valables.map((b) => ({ code: codeBillet(b.id), categorie: b.categorie, qr: b.qr ?? '' })),
      });
    } catch {
      setErreur(true);
    } finally {
      setEnCours(false);
    }
  };

  return (
    <div className={s.actionPdf}>
      <Bouton variante="festif" onClick={() => void telecharger()} disabled={enCours} aria-busy={enCours}>
        <Icone nom="billet" />
        {valables.length > 1 ? 'Télécharger mes billets (PDF)' : 'Télécharger mon billet (PDF)'}
      </Bouton>
      <p className={s.note} role="status">
        {enCours ? 'Préparation du PDF…' : ''}
      </p>
      {erreur && (
        <Avis ton="danger">
          <p>Le PDF n'a pas pu être préparé. Vos billets restent affichés ci-dessous : présentez-les à l'écran.</p>
        </Avis>
      )}
    </div>
  );
}

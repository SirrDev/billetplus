import type { ReactNode } from 'react';
import { Icone } from './Icone';
import s from './Controle.module.css';

/** Fond sombre du contrôle d'entrée. */
export function EcranControle({ children }: { children: ReactNode }) {
  return <div className={s.ecran}>{children}</div>;
}

/**
 * Cadre de visée de la caméra. La vidéo (et la lecture du QR) est fournie par la page :
 * ce composant ne fait que la mettre en forme. `message` remplace la vidéo (caméra refusée...).
 */
export function Viseur({ children, message }: { children?: ReactNode; message?: string }) {
  return (
    <div className={s.viseur}>
      {children}
      {message ? <p className={s.messageViseur}>{message}</p> : <span className={s.coins} aria-hidden="true" />}
    </div>
  );
}

/**
 * Verdict du contrôle : icône ET texte (« Laisser entrer » / « Refuser l'entrée »), jamais
 * la couleur seule. Annoncé immédiatement aux lecteurs d'écran (aria-live="assertive").
 */
export function PanneauVerdict({
  type,
  titre,
  detail,
  mention,
}: {
  type: 'entrer' | 'refuser' | 'attente';
  titre: string;
  detail?: string;
  /** Précision visible à part (« Contrôle sans réseau »). */
  mention?: string;
}) {
  const icone = type === 'entrer' ? 'coche' : type === 'refuser' ? 'croix' : 'info';
  return (
    <div className={`${s.verdict} ${s[type]}`} role="status" aria-live="assertive" aria-atomic="true">
      <span className={s.verdictIcone}>
        <Icone nom={icone} taille={30} />
      </span>
      <div>
        {mention && <p className={s.mention}>{mention}</p>}
        <p className={s.verdictTitre}>{titre}</p>
        {detail && <p className={s.verdictDetail}>{detail}</p>}
      </div>
    </div>
  );
}

export function CompteursControle({ entrees, enAttente }: { entrees: number; enAttente: number }) {
  return (
    <dl className={s.compteurs} aria-label="Compteurs de ce poste">
      <div className={s.compteur}>
        <dt>Entrées sur cet appareil</dt>
        <dd>{entrees}</dd>
      </div>
      <div className={s.compteur}>
        <dt>À synchroniser</dt>
        <dd>{enAttente}</dd>
      </div>
    </dl>
  );
}

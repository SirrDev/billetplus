import type { ReactNode } from 'react';
import { cx } from '../lib/cx';
import { Button } from './Button';
import { Icon, type IconName } from './Icon';
import s from './Scanner.module.css';

/* ---------------------------------------------------------- ScannerViewfinder */

export interface ScannerViewfinderProps {
  /** Le flux caméra (<video>) est fourni par l'appelant (getUserMedia) ; ce composant ne dessine que le cadre. */
  children?: ReactNode;
  hint?: string;
}

export function ScannerViewfinder({ children, hint = 'Placez le QR code dans le cadre' }: ScannerViewfinderProps) {
  return (
    <div className={s.viewfinder}>
      {children ? <div className={s.video}>{children}</div> : null}
      <svg
        className={s.frame}
        viewBox="0 0 230 230"
        fill="none"
        stroke="currentColor"
        strokeWidth="6"
        strokeLinecap="round"
        strokeLinejoin="round"
        role="img"
        aria-label="Cadre de visée : placez le QR code du billet dans le cadre"
      >
        <path d="M4 54V24a20 20 0 0 1 20-20h30" />
        <path d="M176 4h30a20 20 0 0 1 20 20v30" />
        <path d="M226 176v30a20 20 0 0 1-20 20h-30" />
        <path d="M54 226H24a20 20 0 0 1-20-20v-30" />
        <path className={s.laser} d="M30 115h170" strokeWidth="3" />
      </svg>
      <div className={s.hint}>{hint}</div>
    </div>
  );
}

/* ------------------------------------------------------------- EntryCounter */

export interface EntryCounterProps {
  entered: number;
  total: number;
}

export function EntryCounter({ entered, total }: EntryCounterProps) {
  return (
    <div className={s.counter}>
      <div className={s.counterValue}>
        {entered}
        <span className={s.counterTotal}> / {total}</span>
      </div>
      <div className={s.counterLabel}>entrées</div>
    </div>
  );
}

/* ------------------------------------------------------------ ScanResultPanel */

export type ScanStatus = 'valid' | 'used' | 'invalid';

export interface ScanResultPanelProps {
  status: ScanStatus;
  ticketCode: string;
  /** Catégorie du billet (ex. « Pelouse · 1 personne »), affichée si le billet est valide. */
  categoryLabel?: string;
  /** Moment et lieu du premier scan (ex. « 20 h 12 · Porte A »), affiché si le billet est déjà utilisé. */
  firstScanLabel?: string;
  onNext?: () => void;
  nextLabel?: string;
}

const COPY: Record<ScanStatus, { title: string; decision: string; icon: IconName; className: string }> = {
  valid: { title: 'Entrée autorisée', decision: 'Laisser entrer', icon: 'check', className: s.resultValid ?? '' },
  used: { title: 'Déjà utilisé', decision: "Refuser l'entrée", icon: 'exclamation', className: s.resultUsed ?? '' },
  invalid: { title: 'Billet invalide', decision: "Refuser l'entrée", icon: 'close', className: s.resultInvalid ?? '' },
};

/**
 * Résultat d'un scan. Le verdict est annoncé (role="status") et repose sur trois signaux
 * redondants — icône, titre, consigne (« Laisser entrer » / « Refuser l'entrée ») —
 * pour rester lisible en plein soleil ou pour une personne daltonienne.
 */
export function ScanResultPanel({
  status,
  ticketCode,
  categoryLabel,
  firstScanLabel,
  onNext,
  nextLabel = 'Scanner le billet suivant',
}: ScanResultPanelProps) {
  const copy = COPY[status];
  const detail =
    status === 'valid'
      ? (categoryLabel ?? 'Billet reconnu')
      : status === 'used'
        ? firstScanLabel
          ? `Scanné à ${firstScanLabel}`
          : 'Billet déjà scanné'
        : 'Signature non reconnue';
  return (
    <section className={s.panel} aria-label="Résultat du scan">
      <div className={cx(s.result, copy.className)} role="status">
        <div className={s.resultIcon}>
          <Icon name={copy.icon} size={30} strokeWidth={2.6} />
        </div>
        <div>
          <h2 className={s.resultTitle}>{copy.title}</h2>
          <div className={s.resultDetail}>{detail}</div>
        </div>
      </div>
      <dl className={s.facts}>
        <dt>Billet</dt>
        <dd className={s.code}>{ticketCode}</dd>
        <dt>Décision</dt>
        <dd>{copy.decision}</dd>
      </dl>
      {onNext ? (
        <Button fullWidth className={s.next} onClick={onNext}>
          {nextLabel}
        </Button>
      ) : null}
    </section>
  );
}

import { QRCodeSVG } from 'qrcode.react';
import { useCountdown } from '../hooks/useCountdown';
import { cx } from '../lib/cx';
import { formatCountdown, formatFcfa } from '../lib/format';
import { Badge, Notice } from './Feedback';
import { Icon } from './Icon';
import s from './Ticketing.module.css';

/* ------------------------------------------------------------ QuantityStepper */

export interface QuantityStepperProps {
  value: number;
  onChange: (next: number) => void;
  /** Nom de la catégorie, repris dans les libellés accessibles (« Ajouter un billet Pelouse »). */
  label: string;
  min?: number;
  max?: number;
  disabled?: boolean;
}

export function QuantityStepper({ value, onChange, label, min = 0, max = 10, disabled = false }: QuantityStepperProps) {
  return (
    <div className={s.stepper}>
      <button
        type="button"
        className={s.stepBtn}
        aria-label={`Retirer un billet ${label}`}
        disabled={disabled || value <= min}
        onClick={() => onChange(Math.max(min, value - 1))}
      >
        <Icon name="minus" size={20} strokeWidth={2.2} />
      </button>
      <output className={s.qty} aria-live="polite" aria-label={`${label} : ${value} billet${value > 1 ? 's' : ''}`}>
        {value}
      </output>
      <button
        type="button"
        className={cx(s.stepBtn, s.stepBtnPlus)}
        aria-label={`Ajouter un billet ${label}`}
        disabled={disabled || value >= max}
        onClick={() => onChange(Math.min(max, value + 1))}
      >
        <Icon name="plus" size={20} strokeWidth={2.2} />
      </button>
    </div>
  );
}

/* ---------------------------------------------------------- TicketCategoryRow */

export interface TicketCategoryRowProps {
  name: string;
  description?: string;
  priceFcfa: number;
  /** Places restantes (renvoyées par l'API). Absent = pas d'information de stock. */
  remaining?: number;
  quantity: number;
  onQuantityChange: (next: number) => void;
  /** Plafond par commande (10 par défaut, aligné sur le prototype). */
  maxPerOrder?: number;
  /** En dessous de ce stock, affiche « Plus que N ». */
  lowStockThreshold?: number;
}

export function TicketCategoryRow({
  name,
  description,
  priceFcfa,
  remaining,
  quantity,
  onQuantityChange,
  maxPerOrder = 10,
  lowStockThreshold = 20,
}: TicketCategoryRowProps) {
  const soldOut = remaining !== undefined && remaining <= 0;
  const max = remaining === undefined ? maxPerOrder : Math.min(maxPerOrder, Math.max(0, remaining));
  const low = remaining !== undefined && remaining > 0 && remaining <= lowStockThreshold;
  return (
    <div className={s.category}>
      <div className={s.categoryInfo}>
        <div className={s.categoryHead}>
          <span className={s.categoryName}>{name}</span>
          {soldOut ? <Badge tone="neutral">Épuisé</Badge> : null}
          {low ? <Badge tone="accent">Plus que {remaining}</Badge> : null}
        </div>
        {description ? <div className={s.categoryDesc}>{description}</div> : null}
        <div className={s.categoryPrice}>{formatFcfa(priceFcfa)}</div>
      </div>
      <QuantityStepper value={quantity} onChange={onQuantityChange} label={name} max={max} disabled={soldOut} />
    </div>
  );
}

/* --------------------------------------------------------------- PriceSummary */

export interface PriceLine {
  label: string;
  amountFcfa: number;
}

export interface PriceSummaryProps {
  lines: ReadonlyArray<PriceLine>;
  totalFcfa: number;
  totalLabel?: string;
  className?: string;
}

/**
 * Récapitulatif de prix. Les montants viennent du backend (source de vérité) :
 * ce composant ne calcule rien, il affiche.
 */
export function PriceSummary({ lines, totalFcfa, totalLabel = 'Total', className }: PriceSummaryProps) {
  return (
    <section className={cx(s.summary, className)} aria-label="Récapitulatif du prix">
      <dl className={s.summaryLines}>
        {lines.map((line) => (
          <div key={line.label} style={{ display: 'contents' }}>
            <dt>{line.label}</dt>
            <dd>{formatFcfa(line.amountFcfa)}</dd>
          </div>
        ))}
      </dl>
      <div className={s.summaryTotal}>
        <span className={s.summaryTotalLabel}>{totalLabel}</span>
        <span className={s.summaryTotalValue}>{formatFcfa(totalFcfa)}</span>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------ CountdownNotice */

export interface CountdownNoticeProps {
  /** Instant d'expiration de la réservation (fourni par le serveur : l'horloge de l'appareil n'est pas fiable). */
  deadline: Date | number;
  onExpire?: () => void;
}

/** Bandeau « Vos places sont réservées : 01:48 ». Non annoncé à chaque seconde (aria-live désactivé). */
export function CountdownNotice({ deadline, onExpire }: CountdownNoticeProps) {
  const { secondsLeft, expired } = useCountdown(deadline, onExpire);
  return expired ? (
    <Notice tone="danger" icon="clock" role="status">
      Réservation expirée : vos places ont été remises en vente.
    </Notice>
  ) : (
    <Notice tone="warning" icon="clock" role="timer">
      Vos places sont réservées : <strong>{formatCountdown(secondsLeft)}</strong>
    </Notice>
  );
}

/* ----------------------------------------------------------------- TicketPass */

export type TicketPassStatus = 'valid' | 'used' | 'cancelled';

export interface TicketPassProps {
  eventTitle: string;
  categoryName: string;
  /** Déjà formaté (ex. « Sam. 14 nov. 2026 · 20 h »). */
  dateLabel: string;
  venue: string;
  /** Contenu exact du QR : payload signé Ed25519 fourni par le backend. Jamais fabriqué côté client. */
  qrPayload: string;
  /** Code lisible à l'œil, utile en secours (ex. « BP-7K2M-48QX »). */
  code: string;
  status: TicketPassStatus;
}

const PASS_STATUS = {
  valid: { tone: 'success', icon: 'check', label: 'Valide' },
  used: { tone: 'warning', icon: 'exclamation', label: 'Déjà utilisé' },
  cancelled: { tone: 'danger', icon: 'close', label: 'Annulé' },
} as const;

/**
 * Billet affiché à l'entrée. Un billet utilisé ou annulé voit son QR grisé et recouvert d'un texte :
 * il ne peut plus être présenté par erreur, et l'état ne repose pas sur la couleur seule.
 */
export function TicketPass({ eventTitle, categoryName, dateLabel, venue, qrPayload, code, status }: TicketPassProps) {
  const meta = PASS_STATUS[status];
  const inactive = status !== 'valid';
  return (
    <article className={s.pass} aria-label={`Billet ${categoryName} pour ${eventTitle}`}>
      <div className={s.passTop}>
        <Badge tone="accent" size="lg">
          {categoryName}
        </Badge>
        <h2 className={s.passTitle}>{eventTitle}</h2>
        <p className={s.passMeta}>
          {dateLabel}
          <br />
          {venue}
        </p>
      </div>
      <div className={s.tear} aria-hidden="true" />
      <div className={s.passBottom}>
        <div className={cx(s.qr, inactive && s.qrDim)}>
          <QRCodeSVG
            value={qrPayload}
            size={192}
            level="M"
            marginSize={0}
            bgColor="#ffffff"
            fgColor="#1a1033"
            role="img"
            aria-label={inactive ? `QR code désactivé (${meta.label.toLowerCase()})` : `QR code du billet ${code}`}
          />
          {inactive ? <div className={s.qrOverlay}>{meta.label}</div> : null}
        </div>
        <div className={s.code}>{code}</div>
        <div className={s.passStatus}>
          <Badge tone={meta.tone} icon={meta.icon} size="lg">
            {meta.label}
          </Badge>
        </div>
      </div>
    </article>
  );
}

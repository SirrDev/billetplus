import type { ReactNode } from 'react';
import { cx } from '../lib/cx';
import { Icon, type IconName } from './Icon';
import s from './Feedback.module.css';

export type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger';

/* ---------------------------------------------------------------------- Badge */

export interface BadgeProps {
  tone?: Tone;
  icon?: IconName;
  size?: 'md' | 'lg';
  children: ReactNode;
  className?: string;
}

/** Pastille de statut. Toujours accompagnée d'un texte (et idéalement d'une icône) : la couleur ne suffit pas. */
export function Badge({ tone = 'neutral', icon, size = 'md', className, children }: BadgeProps) {
  return (
    <span className={cx(s.badge, s[tone], size === 'lg' && s.badgeLg, className)}>
      {icon ? <Icon name={icon} size={size === 'lg' ? 16 : 14} strokeWidth={2.4} /> : null}
      {children}
    </span>
  );
}

/* --------------------------------------------------------------------- Notice */

const NOTICE_CLASS = {
  neutral: s.noticeNeutral,
  accent: s.noticeWarning,
  success: s.noticeSuccess,
  warning: s.noticeWarning,
  danger: s.noticeDanger,
} as const;

export interface NoticeProps {
  tone?: Tone;
  icon?: IconName;
  /** « alert » pour une erreur à annoncer immédiatement, « status » (défaut) pour une information. */
  role?: 'status' | 'alert' | 'timer';
  children: ReactNode;
  className?: string;
}

export function Notice({ tone = 'neutral', icon, role = 'status', className, children }: NoticeProps) {
  return (
    <div
      className={cx(s.notice, NOTICE_CLASS[tone], className)}
      role={role}
      aria-live={role === 'timer' ? 'off' : undefined}
    >
      {icon ? (
        <span className={s.noticeIcon}>
          <Icon name={icon} size={20} strokeWidth={2} />
        </span>
      ) : null}
      <div>{children}</div>
    </div>
  );
}

/* ---------------------------------------------------------------- ProgressBar */

export interface ProgressBarProps {
  value: number;
  max: number;
  /** Nom accessible, ex. « Pelouse : 210 billets vendus sur 300 ». */
  label: string;
  color?: 'violet' | 'pink' | 'orange' | 'yellow';
  onDark?: boolean;
  className?: string;
}

export function ProgressBar({ value, max, label, color = 'violet', onDark = false, className }: ProgressBarProps) {
  const safeMax = Math.max(0, max);
  const clamped = Math.min(Math.max(0, value), safeMax);
  const pct = safeMax > 0 ? (clamped / safeMax) * 100 : 0;
  const fillClass = cx(
    s.fill,
    color === 'pink' && s.fillPink,
    color === 'orange' && s.fillOrange,
    color === 'yellow' && s.fillYellow,
  );
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={safeMax}
      aria-valuenow={clamped}
      className={cx(onDark ? s.trackOnDark : s.track, className)}
    >
      <div className={fillClass} style={{ width: `${pct}%` }} />
    </div>
  );
}

/* ------------------------------------------------------------------ EmptyState */

export interface EmptyStateProps {
  icon: IconName;
  title: string;
  description: string;
  tone?: 'neutral' | 'warning' | 'danger';
  /** Boutons d'action (ButtonLink / Button), empilés. */
  actions?: ReactNode;
  className?: string;
}

/** Liste vide, recherche sans résultat, événement complet, erreur réseau… */
export function EmptyState({ icon, title, description, tone = 'neutral', actions, className }: EmptyStateProps) {
  return (
    <section className={cx(s.empty, className)} role="status">
      <div className={cx(s.emptyIcon, tone === 'warning' && s.emptyIconWarning, tone === 'danger' && s.emptyIconDanger)}>
        <Icon name={icon} size={64} strokeWidth={1.6} />
      </div>
      <h2 className={s.emptyTitle}>{title}</h2>
      <p className={s.emptyText}>{description}</p>
      {actions ? <div className={s.emptyActions}>{actions}</div> : null}
    </section>
  );
}

/* ----------------------------------------------------------------- SuccessHero */

export interface SuccessHeroProps {
  title: string;
  description: string;
}

/** Bandeau de confirmation (paiement réussi) : dégradé signature + coche. */
export function SuccessHero({ title, description }: SuccessHeroProps) {
  return (
    <header className={s.hero}>
      <svg className={s.confetti} viewBox="0 0 390 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
        <circle cx="60" cy="50" r="7" fill="#ffc93c" />
        <circle cx="330" cy="40" r="5" fill="#fff" />
        <circle cx="352" cy="140" r="8" fill="#ffc93c" />
        <circle cx="34" cy="170" r="5" fill="#fff" />
        <circle cx="96" cy="250" r="6" fill="#ffc93c" />
        <circle cx="318" cy="262" r="6" fill="#fff" />
        <rect x="110" y="28" width="14" height="6" rx="3" fill="#fff" transform="rotate(-25 117 31)" />
        <rect x="270" y="86" width="14" height="6" rx="3" fill="#ffc93c" transform="rotate(30 277 89)" />
        <rect x="70" y="120" width="14" height="6" rx="3" fill="#ffc93c" transform="rotate(20 77 123)" />
        <rect x="300" y="200" width="14" height="6" rx="3" fill="#fff" transform="rotate(-35 307 203)" />
      </svg>
      <div className={s.heroBadge}>
        <div className={s.heroCheck}>
          <Icon name="check" size={46} strokeWidth={2.6} />
        </div>
      </div>
      <h1 className={s.heroTitle}>{title}</h1>
      <p className={s.heroText}>{description}</p>
    </header>
  );
}

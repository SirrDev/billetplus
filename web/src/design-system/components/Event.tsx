import { useId } from 'react';
import { Link } from 'react-router-dom';
import { formatEventDate, formatFcfa } from '../lib/format';
import { cx } from '../lib/cx';
import { Badge } from './Feedback';
import s from './Event.module.css';

export type PosterVariant = 'sunset' | 'rings' | 'bars';

export interface EventSummary {
  id: string;
  title: string;
  /** Genre (Musique, Sport…). Facultatif : absent, aucun badge n'est affiché. */
  category?: string;
  startsAt: Date;
  venue: string;
  /** Prix d'appel. Facultatif : absent, « dès … » n'est pas affiché (jamais de prix inventé). */
  fromPriceFcfa?: number;
  poster: PosterVariant;
}

/* ------------------------------------------------------------------- Poster */

export interface PosterProps {
  variant: PosterVariant;
  className?: string;
}

/**
 * Visuel d'affiche généré en SVG (aucune image à charger : fonctionne hors ligne).
 * Décoratif : le titre de l'événement est toujours rendu en texte à côté.
 */
export function Poster({ variant, className }: PosterProps) {
  const uid = useId().replace(/:/g, '');
  const gradient = `bp-sky-${uid}`;
  return (
    <svg
      className={cx(s.poster, className)}
      viewBox="0 0 100 100"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      focusable="false"
    >
      {variant === 'sunset' ? (
        <>
          <defs>
            <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#5b2be0" />
              <stop offset="0.55" stopColor="#e8327c" />
              <stop offset="1" stopColor="#ff5a1f" />
            </linearGradient>
          </defs>
          <rect width="100" height="100" fill={`url(#${gradient})`} />
          <circle cx="70" cy="42" r="20" fill="#ffc93c" />
          <path d="M0 60 Q18 50 36 60 T72 60 T108 60 V100 H0Z" fill="#1a1033" opacity="0.35" />
          <path d="M0 74 Q20 64 40 74 T80 74 T120 74 V100 H0Z" fill="#1a1033" opacity="0.55" />
        </>
      ) : null}
      {variant === 'rings' ? (
        <>
          <defs>
            <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="#5b2be0" />
              <stop offset="1" stopColor="#1a1033" />
            </linearGradient>
          </defs>
          <rect width="100" height="100" fill={`url(#${gradient})`} />
          <circle cx="30" cy="36" r="42" fill="none" stroke="#ffc93c" strokeWidth="7" />
          <circle cx="30" cy="36" r="24" fill="none" stroke="#ff5a1f" strokeWidth="7" />
          <circle cx="30" cy="36" r="8" fill="#ffc93c" />
        </>
      ) : null}
      {variant === 'bars' ? (
        <>
          <rect width="100" height="100" fill="#1a1033" />
          <rect x="16" y="52" width="17" height="30" rx="4" fill="#ff5a1f" />
          <rect x="42" y="36" width="17" height="46" rx="4" fill="#ffc93c" />
          <rect x="68" y="20" width="17" height="62" rx="4" fill="#8f6bff" />
        </>
      ) : null}
    </svg>
  );
}

/* ------------------------------------------------------------ EventFeaturedCard */

export interface EventCardProps {
  event: EventSummary;
  /** Route de la fiche événement. */
  to: string;
}

/** Grande carte « À l'affiche ». Tout le bloc est un seul lien (un seul arrêt de tabulation). */
export function EventFeaturedCard({ event, to }: EventCardProps) {
  return (
    <Link to={to} className={s.featured}>
      <div className={s.featuredArt}>
        <Poster variant={event.poster} />
      </div>
      <div className={s.featuredShade} />
      {event.category ? (
        <div className={s.featuredBadge}>
          <Badge tone="accent" size="lg">
            {event.category}
          </Badge>
        </div>
      ) : null}
      <div className={s.featuredBody}>
        <div className={s.featuredDate}>{formatEventDate(event.startsAt)}</div>
        <h3 className={s.featuredTitle}>{event.title}</h3>
        <div className={s.featuredFoot}>
          <span className={s.featuredVenue}>{event.venue}</span>
          {event.fromPriceFcfa !== undefined ? (
            <span className={s.price}>dès {formatFcfa(event.fromPriceFcfa)}</span>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

/* ------------------------------------------------------------------ EventRow */

export interface EventRowProps extends EventCardProps {
  /** Terme recherché : les occurrences sont surlignées avec <mark>. */
  highlight?: string;
}

function Highlighted({ text, term }: { text: string; term?: string }) {
  const needle = term?.trim();
  if (!needle) return <>{text}</>;
  const escaped = needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'));
  return (
    <>
      {parts.map((part, index) =>
        part.toLowerCase() === needle.toLowerCase() ? (
          <mark key={index} className={s.mark}>
            {part}
          </mark>
        ) : (
          <span key={index}>{part}</span>
        ),
      )}
    </>
  );
}

/** Ligne compacte d'événement (listes, résultats de recherche). */
export function EventRow({ event, to, highlight }: EventRowProps) {
  return (
    <Link to={to} className={s.row}>
      <div className={s.rowArt}>
        <Poster variant={event.poster} />
      </div>
      <div className={s.rowBody}>
        <div className={s.rowDate}>{formatEventDate(event.startsAt)}</div>
        <div className={s.rowTitle}>
          <Highlighted text={event.title} term={highlight} />
        </div>
        <div className={s.rowMeta}>
          {event.venue}
          {event.fromPriceFcfa !== undefined ? ` · dès ${formatFcfa(event.fromPriceFcfa)}` : null}
        </div>
      </div>
    </Link>
  );
}

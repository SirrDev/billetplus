import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cx } from '../lib/cx';
import { Icon, type IconName } from './Icon';
import b from '../styles/base.module.css';

export interface ScreenProps {
  children: ReactNode;
  /** Réserve la place de la barre de navigation basse. */
  withNav?: boolean;
  /** Réserve la place de la barre d'action collante (bouton principal). */
  withActionBar?: boolean;
  /** Fond sombre (écran du scanner). */
  dark?: boolean;
  className?: string;
}

/**
 * Racine d'un écran : largeur mobile (480 px max, centrée sur grand écran), typographie de base,
 * anneau de focus. C'est le seul composant qui pose des styles « globaux », limités à son sous-arbre.
 */
export function Screen({ children, withNav = false, withActionBar = false, dark = false, className }: ScreenProps) {
  return (
    <div className={cx(b.screen, withNav && b.withNav, withActionBar && b.withBar, dark && b.dark, className)}>
      {children}
    </div>
  );
}

export function StickyActionBar({ children }: { children: ReactNode }) {
  return <div className={b.actionBar}>{children}</div>;
}

/** Texte réservé aux lecteurs d'écran. */
export function VisuallyHidden({ children }: { children: ReactNode }) {
  return <span className={b.srOnly}>{children}</span>;
}

export interface ListRowProps {
  icon: IconName;
  label: string;
  /** Si fourni : la ligne est un lien avec chevron. */
  to?: string;
  /** Valeur affichée à droite (ex. « Français »). */
  value?: string;
  /** Contrôle à droite (ex. <Toggle/>). Prioritaire sur `value`. */
  trailing?: ReactNode;
  tone?: 'default' | 'danger';
}

/** Ligne de réglages / menu. */
export function ListRow({ icon, label, to, value, trailing, tone = 'default' }: ListRowProps) {
  const content = (
    <>
      <span className={b.listIcon}>
        <Icon name={icon} size={24} strokeWidth={1.8} />
      </span>
      <span className={b.listLabel}>{label}</span>
      {trailing ? (
        trailing
      ) : value ? (
        <span className={b.listTrailing}>{value}</span>
      ) : to ? (
        <span className={b.listTrailing}>
          <Icon name="chevronRight" size={20} strokeWidth={2} />
        </span>
      ) : null}
    </>
  );
  const className = cx(b.listRow, tone === 'danger' && b.danger, to && b.listRowLink);
  return to ? (
    <Link to={to} className={className}>
      {content}
    </Link>
  ) : (
    <div className={className}>{content}</div>
  );
}

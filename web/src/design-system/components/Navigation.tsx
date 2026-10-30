import { useRef, type KeyboardEvent, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { cx } from '../lib/cx';
import { Icon } from './Icon';
import type { NavItem } from './navigationItems';
import { IconLink } from './Button';
import s from './Navigation.module.css';

/* --------------------------------------------------------------------- TopBar */

export interface TopBarProps {
  title: string;
  /** Si fourni, affiche une flèche retour vers cette route. */
  backTo?: string;
  backLabel?: string;
  /** Titre en grand (onglets racines : « Mes billets », « Mon compte »). */
  large?: boolean;
  subtitle?: string;
  trailing?: ReactNode;
  onDark?: boolean;
}

export function TopBar({ title, backTo, backLabel = 'Retour', large = false, subtitle, trailing, onDark = false }: TopBarProps) {
  return (
    <div className={s.topBar}>
      <div className={s.topLeft}>
        {backTo ? <IconLink to={backTo} icon="back" label={backLabel} tone={onDark ? 'onDark' : 'outline'} /> : null}
        <div>
          <h1 className={cx(s.title, large && s.titleLg)}>{title}</h1>
          {subtitle ? <div className={s.subtitle}>{subtitle}</div> : null}
        </div>
      </div>
      {trailing}
    </div>
  );
}

/* ------------------------------------------------------------------ BottomNav */

export interface BottomNavProps {
  items: ReadonlyArray<NavItem>;
  label?: string;
}

/** Barre de navigation basse. NavLink pose lui-même aria-current="page" sur l'onglet actif. */
export function BottomNav({ items, label = 'Navigation principale' }: BottomNavProps) {
  return (
    <nav aria-label={label} className={s.bottomNav}>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) => cx(s.navItem, isActive && s.navActive)}
        >
          <Icon name={item.icon} size={24} />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

/* -------------------------------------------------------------- SegmentedTabs */

export interface TabOption<T extends string> {
  id: T;
  label: string;
}

export interface SegmentedTabsProps<T extends string> {
  label: string;
  tabs: ReadonlyArray<TabOption<T>>;
  value: T;
  onChange: (next: T) => void;
  /** Préfixe des identifiants ; à réutiliser avec tabPanelProps pour relier onglet et panneau. */
  idPrefix: string;
  className?: string;
}

/** Onglets accessibles : un seul tabIndex=0, flèches gauche/droite, Début/Fin (motif WAI-ARIA « tabs »). */
export function SegmentedTabs<T extends string>({ label, tabs, value, onChange, idPrefix, className }: SegmentedTabsProps<T>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  const focusTab = (index: number) => {
    const tab = tabs[index];
    if (!tab) return;
    onChange(tab.id);
    refs.current[index]?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const last = tabs.length - 1;
    switch (event.key) {
      case 'ArrowRight':
        event.preventDefault();
        focusTab(index === last ? 0 : index + 1);
        break;
      case 'ArrowLeft':
        event.preventDefault();
        focusTab(index === 0 ? last : index - 1);
        break;
      case 'Home':
        event.preventDefault();
        focusTab(0);
        break;
      case 'End':
        event.preventDefault();
        focusTab(last);
        break;
      default:
    }
  };

  return (
    <div role="tablist" aria-label={label} className={cx(s.segmented, className)}>
      {tabs.map((tab, index) => {
        const selected = tab.id === value;
        return (
          <button
            key={tab.id}
            ref={(node) => {
              refs.current[index] = node;
            }}
            type="button"
            role="tab"
            id={`${idPrefix}-tab-${tab.id}`}
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel-${tab.id}`}
            tabIndex={selected ? 0 : -1}
            className={s.tab}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => onKeyDown(event, index)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

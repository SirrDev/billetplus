import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cx } from '../lib/cx';
import { Icon } from './Icon';
import s from './Chip.module.css';

export interface ChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-pressed'> {
  selected: boolean;
  children: ReactNode;
}

/** Puce de filtre : bouton à bascule (aria-pressed), cible tactile de 44 px. */
export function Chip({ selected, className, type = 'button', children, ...rest }: ChipProps) {
  return (
    <button type={type} aria-pressed={selected} className={cx(s.chip, className)} {...rest}>
      {children}
    </button>
  );
}

export interface ChipOption<T extends string> {
  value: T;
  label: string;
}

export interface ChipGroupProps<T extends string> {
  /** Nom accessible du groupe (« Catégorie », « Ville »…). */
  label: string;
  options: ReadonlyArray<ChipOption<T>>;
  value: T;
  onChange: (next: T) => void;
  /** Une seule ligne défilable horizontalement (barre de catégories). */
  scroll?: boolean;
  className?: string;
}

/** Groupe de puces à choix unique. */
export function ChipGroup<T extends string>({ label, options, value, onChange, scroll = false, className }: ChipGroupProps<T>) {
  return (
    <div role="group" aria-label={label} className={cx(s.group, scroll && s.scroll, className)}>
      {options.map((option) => (
        <Chip key={option.value} selected={option.value === value} onClick={() => onChange(option.value)}>
          {option.label}
        </Chip>
      ))}
    </div>
  );
}

export interface FilterTagProps {
  label: string;
  onRemove: () => void;
}

/** Filtre actif affiché au-dessus des résultats, retirable d'un geste. */
export function FilterTag({ label, onRemove }: FilterTagProps) {
  return (
    <button type="button" className={s.tag} onClick={onRemove} aria-label={`Retirer le filtre ${label}`}>
      {label}
      <Icon name="close" size={14} strokeWidth={2.4} />
    </button>
  );
}

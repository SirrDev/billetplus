import { forwardRef, useId, type ChangeEvent, type InputHTMLAttributes } from 'react';
import { cx } from '../lib/cx';
import { formatSnPhone, sanitizeSnPhone } from '../lib/format';
import { Badge } from './Feedback';
import { Icon, type IconName } from './Icon';
import s from './Form.module.css';

/* ------------------------------------------------------------------ TextField */

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  hint?: string;
  error?: string;
  id?: string;
}

/** Champ texte : label toujours visible (jamais un simple placeholder), erreur annoncée (role="alert"). */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, id, className, ...rest },
  ref,
) {
  const auto = useId();
  const inputId = id ?? auto;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  return (
    <div className={cx(s.field, className)}>
      <label htmlFor={inputId} className={s.label}>
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        className={cx(s.input, error && s.invalid)}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hintId, errorId].filter(Boolean).join(' ') || undefined}
        {...rest}
      />
      {hint ? (
        <p id={hintId} className={s.hint}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className={s.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
});

/* ----------------------------------------------------------------- PhoneField */

export interface PhoneFieldProps {
  /** Valeur « brute » : 9 chiffres maximum, sans indicatif ni espaces. */
  value: string;
  onChange: (digits: string) => void;
  label?: string;
  hint?: string;
  error?: string;
  id?: string;
  name?: string;
  disabled?: boolean;
  className?: string;
}

/** Numéro de téléphone sénégalais : préfixe +221 fixe, saisie formatée « 77 123 45 67 ». */
export function PhoneField({
  value,
  onChange,
  label = 'Numéro de téléphone',
  hint,
  error,
  id,
  name,
  disabled,
  className,
}: PhoneFieldProps) {
  const auto = useId();
  const inputId = id ?? auto;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const handle = (event: ChangeEvent<HTMLInputElement>) => onChange(sanitizeSnPhone(event.target.value));
  return (
    <div className={cx(s.field, className)}>
      <label htmlFor={inputId} className={s.label}>
        {label}
      </label>
      <div className={s.row}>
        <span className={s.prefix} aria-hidden="true">
          +221
        </span>
        <input
          id={inputId}
          name={name}
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          placeholder="7X XXX XX XX"
          maxLength={12}
          disabled={disabled}
          value={formatSnPhone(value)}
          onChange={handle}
          className={cx(s.input, error && s.invalid)}
          aria-invalid={error ? true : undefined}
          aria-describedby={[hintId, errorId].filter(Boolean).join(' ') || undefined}
        />
      </div>
      {hint ? (
        <p id={hintId} className={s.hint}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className={s.error} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/* --------------------------------------------------------------------- Toggle */

export interface ToggleProps {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  className?: string;
}

/** Interrupteur (role="switch") : l'état est exposé via aria-checked, le libellé reste visible. */
export function Toggle({ label, description, checked, onChange, className }: ToggleProps) {
  const base = useId();
  const labelId = `${base}-label`;
  const descId = description ? `${base}-desc` : undefined;
  return (
    <div className={cx(s.toggleRow, className)}>
      <div className={s.toggleText}>
        <div id={labelId} className={s.toggleLabel}>
          {label}
        </div>
        {description ? (
          <div id={descId} className={s.toggleDesc}>
            {description}
          </div>
        ) : null}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        aria-describedby={descId}
        className={s.switch}
        onClick={() => onChange(!checked)}
      >
        <span className={s.knob} />
      </button>
    </div>
  );
}

/* ------------------------------------------------------------ RadioCardGroup */

export interface RadioCardOption<T extends string> {
  value: T;
  label: string;
  description?: string;
  icon?: IconName;
  /** Ex. « Bientôt » : pastille affichée à droite. */
  badge?: string;
  disabled?: boolean;
}

export interface RadioCardGroupProps<T extends string> {
  /** Titre du groupe, lu par les lecteurs d'écran (fieldset/legend natifs). */
  legend: string;
  name: string;
  value: T;
  onChange: (next: T) => void;
  options: ReadonlyArray<RadioCardOption<T>>;
  className?: string;
}

/** Choix unique sous forme de cartes (moyen de paiement, billet à transférer…). Radios natifs, navigables au clavier. */
export function RadioCardGroup<T extends string>({
  legend,
  name,
  value,
  onChange,
  options,
  className,
}: RadioCardGroupProps<T>) {
  return (
    <fieldset className={cx(s.fieldset, className)}>
      <legend className={s.legend}>{legend}</legend>
      <div className={s.cards}>
        {options.map((option) => {
          const selected = option.value === value;
          return (
            <label
              key={option.value}
              className={cx(s.card, selected && s.cardSelected, option.disabled && s.cardDisabled)}
            >
              <input
                type="radio"
                className={s.radio}
                name={name}
                value={option.value}
                checked={selected}
                disabled={option.disabled}
                onChange={() => onChange(option.value)}
              />
              {option.icon ? (
                <span className={s.cardIcon}>
                  <Icon name={option.icon} size={24} strokeWidth={1.8} />
                </span>
              ) : null}
              <span className={s.cardText}>
                <span className={s.cardLabel}>{option.label}</span>
                {option.description ? <span className={s.cardDesc}>{option.description}</span> : null}
              </span>
              {option.badge ? <Badge tone="neutral">{option.badge}</Badge> : null}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

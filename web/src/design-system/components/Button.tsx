import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { cx } from '../lib/cx';
import { Icon, type IconName } from './Icon';
import s from './Button.module.css';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'lg' | 'md';

interface Shared {
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  icon?: IconName;
  children: ReactNode;
}

function buttonClass(variant: ButtonVariant, size: ButtonSize, fullWidth: boolean) {
  return cx(s.base, s[variant], s[size], fullWidth && s.full);
}

export interface ButtonProps extends Shared, Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  /** Désactive le bouton et signale l'attente aux lecteurs d'écran (aria-busy). */
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'lg',
    fullWidth = false,
    icon,
    loading = false,
    disabled,
    className,
    type = 'button',
    children,
    ...rest
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cx(buttonClass(variant, size, fullWidth), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <span className={s.spinner} aria-hidden="true" /> : icon ? <Icon name={icon} size={20} /> : null}
      {children}
    </button>
  );
});

export interface ButtonLinkProps extends Shared, Omit<LinkProps, 'children'> {}

/** Même rendu que Button, mais c'est un vrai lien de navigation (react-router). */
export function ButtonLink({
  variant = 'primary',
  size = 'lg',
  fullWidth = false,
  icon,
  className,
  children,
  ...rest
}: ButtonLinkProps) {
  return (
    <Link className={cx(buttonClass(variant, size, fullWidth), className)} {...rest}>
      {icon ? <Icon name={icon} size={20} /> : null}
      {children}
    </Link>
  );
}

export type IconButtonTone = 'outline' | 'solid' | 'accent' | 'onDark';

interface IconShared {
  /** Obligatoire : un bouton sans texte visible doit avoir un nom accessible. */
  label: string;
  icon: IconName;
  tone?: IconButtonTone;
}

export interface IconButtonProps extends IconShared, Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-label'> {}

export function IconButton({ label, icon, tone = 'outline', className, type = 'button', ...rest }: IconButtonProps) {
  return (
    <button type={type} aria-label={label} className={cx(s.iconBtn, s[tone], className)} {...rest}>
      <Icon name={icon} size={22} strokeWidth={2} />
    </button>
  );
}

export interface IconLinkProps extends IconShared, Omit<LinkProps, 'children' | 'aria-label'> {}

export function IconLink({ label, icon, tone = 'outline', className, ...rest }: IconLinkProps) {
  return (
    <Link aria-label={label} className={cx(s.iconBtn, s[tone], className)} {...rest}>
      <Icon name={icon} size={22} strokeWidth={2} />
    </Link>
  );
}

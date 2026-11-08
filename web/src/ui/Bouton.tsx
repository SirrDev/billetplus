import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { Icone } from './Icone';
import s from './Bouton.module.css';

type Variante = 'principal' | 'secondaire' | 'festif';

interface Apparence {
  variante?: Variante;
  grand?: boolean;
  pleineLargeur?: boolean;
}

function classes({ variante = 'principal', grand, pleineLargeur }: Apparence, extra?: string): string {
  return [s.bouton, s[variante], grand && s.grand, pleineLargeur && s.pleineLargeur, extra].filter(Boolean).join(' ');
}

export function Bouton({
  variante,
  grand,
  pleineLargeur,
  className,
  type = 'button',
  ...reste
}: Apparence & ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button type={type} className={classes({ variante, grand, pleineLargeur }, className)} {...reste} />;
}

/** Lien qui ressemble à un bouton : une navigation reste un <a>, pas un <button>. */
export function BoutonLien({
  variante,
  grand,
  pleineLargeur,
  className,
  children,
  ...reste
}: Apparence & LinkProps & { children: ReactNode }) {
  return (
    <Link className={classes({ variante, grand, pleineLargeur }, className)} {...reste}>
      {children}
    </Link>
  );
}

/** Retour à l'écran précédent du parcours : lien secondaire avec flèche, seul sur sa ligne. */
export function LienRetour({ children, ...reste }: LinkProps & { children: ReactNode }) {
  return (
    <div>
      <BoutonLien variante="secondaire" {...reste}>
        <Icone nom="retour" />
        {children}
      </BoutonLien>
    </div>
  );
}

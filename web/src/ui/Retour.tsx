import type { ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';
import s from './Retour.module.css';

type Ton = 'info' | 'attention' | 'danger' | 'succes';

const ICONES: Record<Ton, NomIcone> = { info: 'info', attention: 'alerte', danger: 'croix', succes: 'valide' };

/**
 * Message dans la page. Icône + texte : le sens ne repose jamais sur la couleur seule.
 * « danger » est annoncé immédiatement (role="alert"), les autres poliment.
 */
export function Avis({ ton = 'info', icone, children }: { ton?: Ton; icone?: NomIcone; children: ReactNode }) {
  return (
    <div className={`${s.avis} ${s[ton]}`} role={ton === 'danger' ? 'alert' : 'status'}>
      <Icone nom={icone ?? ICONES[ton]} taille={22} />
      <div className={s.avisCorps}>{children}</div>
    </div>
  );
}

export function EtatVide({
  titre,
  texte,
  icone = 'billet',
  ton = 'info',
  action,
  niveau = 2,
}: {
  titre: string;
  texte?: ReactNode;
  icone?: NomIcone;
  ton?: 'info' | 'attention' | 'danger';
  action?: ReactNode;
  /** 1 quand l'état vide EST la page (événement introuvable...). */
  niveau?: 1 | 2;
}) {
  const Titre = niveau === 1 ? 'h1' : 'h2';
  return (
    <div className={s.vide}>
      <span className={`${s.videIcone} ${ton !== 'info' ? s[ton] : ''}`}>
        <Icone nom={icone} taille={30} />
      </span>
      <Titre className={s.videTitre}>{titre}</Titre>
      {texte && <p className={s.videTexte}>{texte}</p>}
      {action}
    </div>
  );
}

export function Chargement({ libelle = 'Chargement…' }: { libelle?: string }) {
  return (
    <div className={s.chargement} role="status">
      <span className={s.roue} aria-hidden="true" />
      {libelle}
    </div>
  );
}

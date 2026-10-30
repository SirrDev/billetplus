import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { formatDateCourte } from '@/commun/format';
import { Affiche } from './Affiche';
import { BoutonLien } from './Bouton';
import s from './CarteEvenement.module.css';

/** Ce que le backend donne d'un événement dans sa liste publique : ni genre ni prix « dès ». */
export interface ResumeEvenement {
  id: string;
  titre: string;
  debuteLe: string;
  lieu: string;
}

export function GrilleEvenements({ children, libelle }: { children: ReactNode; libelle: string }) {
  return (
    <ul className={s.grille} aria-label={libelle}>
      {children}
    </ul>
  );
}

/** Une carte de la grille. À placer dans GrilleEvenements (elle rend un <li>). */
export function CarteEvenement({ evenement, vers }: { evenement: ResumeEvenement; vers: string }) {
  return (
    <li>
      <Link to={vers} className={s.carte}>
        <div className={s.carteAffiche}>
          <Affiche id={evenement.id} />
        </div>
        <div className={s.carteCorps}>
          <span className={s.date}>{formatDateCourte(evenement.debuteLe)}</span>
          <h3 className={s.titre}>{evenement.titre}</h3>
          <span className={s.lieu}>{evenement.lieu}</span>
        </div>
      </Link>
    </li>
  );
}

/** L'événement mis en avant en haut de « Découvrir ». */
export function EvenementALaUne({ evenement, vers }: { evenement: ResumeEvenement; vers: string }) {
  const idTitre = `une-${evenement.id}`;
  return (
    <section className={s.une} aria-labelledby={idTitre}>
      <div className={s.uneAffiche}>
        <Affiche id={evenement.id} />
        <span className={s.pastille}>À l'affiche</span>
      </div>
      <div className={s.uneCorps}>
        <p className={s.uneDate}>{formatDateCourte(evenement.debuteLe)}</p>
        <h2 id={idTitre} className={s.uneTitre}>
          {evenement.titre}
        </h2>
        <p className={s.uneLieu}>{evenement.lieu}</p>
        <div>
          <BoutonLien to={vers} grand>
            Réserver mes places
          </BoutonLien>
        </div>
      </div>
    </section>
  );
}

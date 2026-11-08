import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { formatDateCourte, formatHeure, majusculeInitiale, partiesDate } from '@/commun/format';
import { Affiche } from './Affiche';
import { BoutonLien } from './Bouton';
import s from './CarteEvenement.module.css';

/** Ce que le backend donne d'un événement dans sa liste publique : ni genre ni prix « dès ». */
export interface ResumeEvenement {
  id: string;
  titre: string;
  debuteLe: string;
  lieu: string;
  /** Libellé de la catégorie (déjà traduit par l'appelant), affiché en étiquette. */
  categorie?: string;
}

export function GrilleEvenements({ children, libelle }: { children: ReactNode; libelle: string }) {
  return (
    <ul className={s.grille} aria-label={libelle}>
      {children}
    </ul>
  );
}

/**
 * Talon daté d'un billet (jour en grand, mois dessous). Décoratif pour les lecteurs d'écran :
 * la date complète est lue dans le texte masqué qui l'accompagne.
 */
function Talon({ debuteLe, className }: { debuteLe: string; className: string | undefined }) {
  const { jour, mois } = partiesDate(debuteLe);
  return (
    <span className={className} aria-hidden="true">
      <span className={s.talonJour}>{jour}</span>
      <span className={s.talonMois}>{mois.replace('.', '')}</span>
    </span>
  );
}

/** Une carte de la grille, dessinée comme un billet. À placer dans GrilleEvenements (elle rend un <li>). */
export function CarteEvenement({ evenement, vers }: { evenement: ResumeEvenement; vers: string }) {
  const { jourSemaine } = partiesDate(evenement.debuteLe);
  return (
    <li>
      <Link to={vers} className={s.carte}>
        <div className={s.carteAffiche}>
          <Affiche id={evenement.id} />
          {evenement.categorie && <span className={s.etiquette}>{evenement.categorie}</span>}
        </div>
        <div className={s.carteCorps}>
          <Talon debuteLe={evenement.debuteLe} className={s.talon} />
          <div className={s.carteInfos}>
            <span className="visually-hidden">{formatDateCourte(evenement.debuteLe)}</span>
            <h3 className={s.titre}>{evenement.titre}</h3>
            <span className={s.lieu}>{evenement.lieu}</span>
            <span className={s.heure} aria-hidden="true">
              {majusculeInitiale(jourSemaine)} · {formatHeure(evenement.debuteLe)}
            </span>
          </div>
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
        <Talon debuteLe={evenement.debuteLe} className={s.uneTalon} />
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

import type { ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';
import s from './Pilotage.module.css';

/** Grille d'indicateurs : 2 colonnes sur mobile, 4 sur ordinateur. Un <dl> : chaque valeur a son libellé. */
export function Indicateurs({ children, libelle }: { children: ReactNode; libelle: string }) {
  return (
    <dl className={s.indicateurs} aria-label={libelle}>
      {children}
    </dl>
  );
}

export function Indicateur({
  libelle,
  valeur,
  complement,
  ton,
  progression,
}: {
  libelle: string;
  valeur: string;
  complement?: string;
  ton?: 'sombre' | 'attention';
  /** 0 à 100 : barre de remplissage, avec son nom accessible. */
  progression?: number;
}) {
  return (
    <div className={[s.indicateur, ton && s[ton]].filter(Boolean).join(' ')}>
      <dt>{libelle}</dt>
      <dd>
        {valeur}
        {complement && <span className={s.complement}>{complement}</span>}
        {progression !== undefined && (
          <span
            className={s.progression}
            style={{ display: 'block' }}
            role="progressbar"
            aria-label={`${libelle} : taux de remplissage`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(progression)}
          >
            <span className={s.progressionValeur} style={{ display: 'block', width: `${Math.min(100, Math.max(0, progression))}%` }} />
          </span>
        )}
      </dd>
    </div>
  );
}

export type TonPastille = 'neutre' | 'succes' | 'attention' | 'danger' | 'info';

/** État court : toujours une icône ET un texte, jamais la couleur seule. */
export function Pastille({ ton = 'neutre', icone, children }: { ton?: TonPastille; icone?: NomIcone; children: ReactNode }) {
  return (
    <span className={`${s.pastille} ${s[`ton-${ton}`]}`}>
      {icone && <Icone nom={icone} taille={14} />}
      {children}
    </span>
  );
}

export interface ColonneTableau<L> {
  titre: string;
  cellule: (ligne: L) => ReactNode;
  /** Aligné à droite, chiffres à chasse fixe. */
  nombre?: boolean;
}

/** Tableau de données accessible (caption, th scope) qui défile horizontalement sur mobile. */
export function Tableau<L>({
  legende,
  colonnes,
  lignes,
  cle,
}: {
  legende: string;
  colonnes: ReadonlyArray<ColonneTableau<L>>;
  lignes: ReadonlyArray<L>;
  cle: (ligne: L) => string;
}) {
  return (
    <div className={s.defilement}>
      <table className={s.tableau}>
        <caption>{legende}</caption>
        <thead>
          <tr>
            {colonnes.map((c) => (
              <th key={c.titre} scope="col" className={c.nombre ? s.nombre : undefined}>
                {c.titre}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {lignes.map((l) => (
            <tr key={cle(l)}>
              {colonnes.map((c) => (
                <td key={c.titre} className={c.nombre ? s.nombre : undefined}>
                  {c.cellule(l)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

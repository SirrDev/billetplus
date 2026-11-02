import s from './Filtres.module.css';

/**
 * Filtres en pastilles (une seule active). Boutons à bascule (aria-pressed) dans un groupe
 * nommé ; sur mobile, la rangée défile horizontalement sans faire déborder la page.
 */
export function Filtres<V extends string>({
  libelle,
  options,
  valeur,
  onChange,
}: {
  libelle: string;
  options: ReadonlyArray<{ valeur: V; libelle: string }>;
  valeur: V;
  onChange: (valeur: V) => void;
}) {
  return (
    <div role="group" aria-label={libelle} className={s.filtres}>
      {options.map((o) => (
        <button
          key={o.valeur}
          type="button"
          className={s.filtre}
          aria-pressed={valeur === o.valeur}
          onClick={() => onChange(o.valeur)}
        >
          {o.libelle}
        </button>
      ))}
    </div>
  );
}

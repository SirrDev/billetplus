import { useId, type ReactNode } from 'react';
import { formatMobile } from '@/commun/format';
import { Icone } from './Icone';
import s from './Formulaire.module.css';

export function ChampRecherche({
  valeur,
  onChange,
  libelle,
  placeholder,
}: {
  valeur: string;
  onChange: (valeur: string) => void;
  libelle: string;
  placeholder?: string;
}) {
  return (
    <label className={`${s.saisie} ${s.recherche}`}>
      <Icone nom="recherche" taille={18} />
      <span className="visually-hidden">{libelle}</span>
      <input
        type="search"
        className={s.input}
        value={valeur}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

/**
 * Numéro de mobile sénégalais : l'indicatif +221 est affiché, l'acheteur ne tape
 * que ses 9 chiffres (mis en forme pendant la saisie). Erreur annoncée par role="alert".
 */
export function ChampTelephone({
  valeur,
  onChange,
  libelle = 'Numéro de mobile',
  aide,
  erreur,
}: {
  valeur: string;
  onChange: (valeur: string) => void;
  libelle?: string;
  aide?: string;
  erreur?: string;
}) {
  const id = useId();
  const decrit = [aide && `${id}-aide`, erreur && `${id}-erreur`].filter(Boolean).join(' ') || undefined;
  return (
    <div className={s.champ}>
      <label htmlFor={id} className={s.libelle}>
        {libelle}
      </label>
      <div className={[s.saisie, erreur && s.saisieInvalide].filter(Boolean).join(' ')}>
        <span className={s.prefixe} aria-hidden="true">
          +221
        </span>
        <input
          id={id}
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          placeholder="77 123 45 67"
          className={s.input}
          value={valeur}
          aria-invalid={erreur ? true : undefined}
          aria-describedby={decrit}
          onChange={(e) => onChange(formatMobile(e.target.value))}
        />
      </div>
      {aide && (
        <p id={`${id}-aide`} className={s.aide}>
          {aide}
        </p>
      )}
      {erreur && (
        <p id={`${id}-erreur`} className={s.erreur} role="alert">
          {erreur}
        </p>
      )}
    </div>
  );
}

/**
 * Champ de saisie avec libellé visible, aide et erreur reliées (aria-describedby).
 * `maxLength` doit reprendre le @Size(max) du DTO backend : sinon PostgreSQL refuse et
 * l'API répond 500 au lieu de 400.
 */
export function ChampTexte({
  libelle,
  valeur,
  onChange,
  type = 'text',
  multiligne,
  aide,
  erreur,
  requis,
  maxLength,
  minLength,
  autoComplete,
  min,
  inputMode,
}: {
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  type?: 'text' | 'email' | 'password' | 'number' | 'date' | 'datetime-local';
  multiligne?: boolean;
  aide?: string;
  erreur?: string;
  requis?: boolean;
  maxLength?: number;
  minLength?: number;
  autoComplete?: string;
  min?: string;
  inputMode?: 'numeric' | 'text' | 'email';
}) {
  const id = useId();
  const decrit = [aide && `${id}-aide`, erreur && `${id}-erreur`].filter(Boolean).join(' ') || undefined;
  const commun = {
    id,
    className: s.input,
    value: valeur,
    required: requis,
    maxLength,
    minLength,
    'aria-invalid': erreur ? true : undefined,
    'aria-describedby': decrit,
  } as const;
  return (
    <div className={s.champ}>
      <label htmlFor={id} className={s.libelle}>
        {libelle}
        {!requis && <span className={s.facultatif}> (facultatif)</span>}
      </label>
      <div className={[s.saisie, multiligne && s.saisieMultiligne, erreur && s.saisieInvalide].filter(Boolean).join(' ')}>
        {multiligne ? (
          <textarea {...commun} rows={4} onChange={(e) => onChange(e.target.value)} />
        ) : (
          <input
            {...commun}
            type={type}
            min={min}
            inputMode={inputMode}
            autoComplete={autoComplete}
            onChange={(e) => onChange(e.target.value)}
          />
        )}
      </div>
      {aide && (
        <p id={`${id}-aide`} className={s.aide}>
          {aide}
        </p>
      )}
      {erreur && (
        <p id={`${id}-erreur`} className={s.erreur} role="alert">
          {erreur}
        </p>
      )}
    </div>
  );
}

export interface OptionCarte<V extends string> {
  valeur: V;
  titre: string;
  detail?: string;
  visuel?: ReactNode;
}

/** Choix exclusif présenté en cartes : de vrais boutons radio dans un fieldset. */
export function ChoixCartes<V extends string>({
  legende,
  nom,
  options,
  valeur,
  onChange,
}: {
  legende: string;
  nom: string;
  options: ReadonlyArray<OptionCarte<V>>;
  valeur: V;
  onChange: (valeur: V) => void;
}) {
  return (
    <fieldset className={s.groupe}>
      <legend className={s.legende}>{legende}</legend>
      {options.map((o) => (
        <label key={o.valeur} className={s.option}>
          <input
            type="radio"
            className={s.radio}
            name={nom}
            value={o.valeur}
            checked={valeur === o.valeur}
            onChange={() => onChange(o.valeur)}
          />
          {o.visuel}
          <span>
            <span className={s.optionTitre}>{o.titre}</span>
            {o.detail && <span className={s.optionDetail}>{o.detail}</span>}
          </span>
        </label>
      ))}
    </fieldset>
  );
}

/**
 * Liste de choix (<select> natif : clavier, lecteurs d'écran et sélecteur du téléphone
 * fonctionnent sans effort). Même habillage que ChampTexte.
 */
export function ChampChoix<V extends string>({
  libelle,
  options,
  valeur,
  onChange,
  aide,
  libelleMasque = false,
}: {
  libelle: string;
  /** Libellé lu par les lecteurs d'écran seulement, quand la première option le dit déjà (filtre). */
  libelleMasque?: boolean;
  /** groupe : options consécutives du même groupe réunies dans un <optgroup> (villes par pays). */
  options: ReadonlyArray<{ valeur: V; libelle: string; groupe?: string }>;
  valeur: V;
  onChange: (valeur: V) => void;
  aide?: string;
}) {
  const id = useId();
  const groupes: Array<{ nom: string | undefined; options: Array<{ valeur: V; libelle: string }> }> = [];
  for (const o of options) {
    const dernier = groupes[groupes.length - 1];
    if (dernier && dernier.nom === o.groupe) dernier.options.push(o);
    else groupes.push({ nom: o.groupe, options: [o] });
  }
  const rendre = (o: { valeur: V; libelle: string }) => (
    <option key={o.valeur} value={o.valeur}>
      {o.libelle}
    </option>
  );
  return (
    <div className={s.champ}>
      <label htmlFor={id} className={libelleMasque ? 'visually-hidden' : s.libelle}>
        {libelle}
      </label>
      <div className={s.saisie}>
        <select
          id={id}
          className={`${s.input} ${s.choix}`}
          value={valeur}
          aria-describedby={aide ? `${id}-aide` : undefined}
          onChange={(e) => onChange(e.target.value as V)}
        >
          {groupes.map((g, i) =>
            g.nom === undefined ? (
              g.options.map(rendre)
            ) : (
              <optgroup key={`${g.nom}-${i}`} label={g.nom}>
                {g.options.map(rendre)}
              </optgroup>
            ),
          )}
        </select>
      </div>
      {aide && (
        <p id={`${id}-aide`} className={s.aide}>
          {aide}
        </p>
      )}
    </div>
  );
}

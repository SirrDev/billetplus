import type { ReactNode } from 'react';
import { cx } from '../lib/cx';
import { ProgressBar } from './Feedback';
import s from './Organizer.module.css';

/* ------------------------------------------------------------------ MetricCard */

export interface MetricCardProps {
  label: string;
  /** Valeur déjà formatée (ex. « 318 », « 3 300 000 »). */
  value: string;
  /** Unité ou total affiché à côté (ex. « / 450 », « FCFA »). */
  unit?: string;
  hint?: string;
  tone?: 'dark' | 'highlight' | 'plain';
  progress?: { value: number; max: number; label: string };
}

export function MetricCard({ label, value, unit, hint, tone = 'plain', progress }: MetricCardProps) {
  const toneClass = tone === 'dark' ? s.metricDark : tone === 'highlight' ? s.metricHighlight : s.metricPlain;
  return (
    <div className={cx(s.metric, toneClass)}>
      <div className={s.metricLabel}>{label}</div>
      <div className={s.metricValue}>
        {value}
        {unit ? <span className={s.metricUnit}>{unit}</span> : null}
      </div>
      {progress ? (
        <ProgressBar
          className={s.metricProgress}
          value={progress.value}
          max={progress.max}
          label={progress.label}
          color="yellow"
          onDark={tone === 'dark'}
        />
      ) : null}
      {hint ? <div className={s.metricHint}>{hint}</div> : null}
    </div>
  );
}

/* -------------------------------------------------------------------- BarChart */

export interface BarDatum {
  label: string;
  value: number;
}

export interface BarChartProps {
  data: ReadonlyArray<BarDatum>;
  /** Résumé lu par les lecteurs d'écran ; les valeurs détaillées sont exposées dans un tableau masqué. */
  ariaLabel: string;
  /** Met en avant la dernière barre (aujourd'hui). */
  highlightLast?: boolean;
  className?: string;
}

const STEP = 88;
const BAR_W = 62;
const PAD = 12;
const BASELINE = 200;
const MAX_BAR_H = 170;

/**
 * Histogramme SVG responsive (viewBox) sans dépendance. Chaque barre porte sa valeur en texte :
 * l'information ne dépend ni de la couleur ni de la survol.
 */
export function BarChart({ data, ariaLabel, highlightLast = true, className }: BarChartProps) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const width = PAD * 2 + Math.max(0, data.length - 1) * STEP + BAR_W;
  const height = 236;
  return (
    <figure style={{ margin: 0 }} className={className}>
      <svg className={s.chart} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={ariaLabel} focusable="false">
        <line className={s.axis} x1="0" y1={BASELINE} x2={width} y2={BASELINE} />
        {data.map((datum, index) => {
          const barH = Math.round((datum.value / max) * MAX_BAR_H);
          const x = PAD + index * STEP;
          const isLast = highlightLast && index === data.length - 1;
          return (
            <g key={`${datum.label}-${index}`}>
              <rect
                className={isLast ? s.barHighlight : s.barBase}
                x={x}
                y={BASELINE - barH}
                width={BAR_W}
                height={barH}
                rx={8}
              />
              <text className={s.chartValue} x={x + BAR_W / 2} y={BASELINE - barH - 8} textAnchor="middle">
                {datum.value}
              </text>
              <text className={s.chartLabel} x={x + BAR_W / 2} y={224} textAnchor="middle">
                {datum.label}
              </text>
            </g>
          );
        })}
      </svg>
      {/* srOnly sur une div ENVELOPPANTE, pas sur la table : une <table> ne se réduit pas à
          width: 1px (elle prend la largeur de son contenu) et débordait de l'écran, ce qui
          rendait toute la page défilable de côté sur un téléphone. La table reste intacte
          pour les lecteurs d'écran. */}
      <div className={s.srOnly}>
        <table>
          <caption>{ariaLabel}</caption>
          <thead>
            <tr>
              <th scope="col">Période</th>
              <th scope="col">Valeur</th>
            </tr>
          </thead>
          <tbody>
            {data.map((datum, index) => (
              <tr key={`${datum.label}-${index}`}>
                <th scope="row">{datum.label}</th>
                <td>{datum.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}

/* ------------------------------------------------------------------ DataTable */

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  align?: 'left' | 'right';
  /** La cellule devient un en-tête de ligne (<th scope="row">) : meilleure lecture au lecteur d'écran. */
  rowHeader?: boolean;
}

export interface DataTableProps<T> {
  caption: string;
  columns: ReadonlyArray<Column<T>>;
  rows: ReadonlyArray<T>;
  getRowKey: (row: T) => string;
  className?: string;
}

export function DataTable<T>({ caption, columns, rows, getRowKey, className }: DataTableProps<T>) {
  return (
    <div className={cx(s.tableWrap, className)}>
      <table className={s.table}>
        <caption className={s.caption}>{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" className={cx(s.th, column.align === 'right' && s.right)}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={getRowKey(row)}>
              {columns.map((column) =>
                column.rowHeader ? (
                  <th key={column.key} scope="row" className={cx(s.td, s.rowHeader, column.align === 'right' && s.right)}>
                    {column.render(row)}
                  </th>
                ) : (
                  <td key={column.key} className={cx(s.td, column.align === 'right' && s.right)}>
                    {column.render(row)}
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

import { motifDe, type Motif } from './outils';

/*
 * Affiches géométriques générées : aucune image distante, donc rien à télécharger
 * hors ligne. Le motif dépend de l'identifiant : un événement garde la même affiche.
 */

const DESSINS: Record<Motif, JSX.Element> = {
  soleil: (
    <>
      <rect width="600" height="400" fill="#1B1640" />
      <circle cx="420" cy="160" r="140" fill="#FFB020" />
      <circle cx="420" cy="160" r="196" fill="none" stroke="#5B3DF5" strokeWidth="26" />
      <path d="M0 290 L80 245 L150 285 L230 225 L320 280 L400 235 L480 275 L600 215 L600 400 L0 400 Z" fill="#0F1115" />
    </>
  ),
  barres: (
    <>
      <rect width="600" height="400" fill="#5B3DF5" />
      <circle cx="470" cy="80" r="190" fill="#7C66FF" />
      <rect x="60" y="200" width="38" height="200" fill="#FFB020" />
      <rect x="125" y="130" width="38" height="270" fill="#FFB020" />
      <rect x="190" y="240" width="38" height="160" fill="#FFB020" />
      <rect x="255" y="165" width="38" height="235" fill="#FFB020" />
    </>
  ),
  formes: (
    <>
      <rect width="600" height="400" fill="#0F1115" />
      <rect x="90" y="90" width="200" height="200" rx="40" fill="#FFB020" />
      <rect x="320" y="90" width="200" height="200" rx="100" fill="#5B3DF5" />
    </>
  ),
  vague: (
    <>
      <rect width="600" height="400" fill="#FFB020" />
      <path d="M0 400 Q150 130 300 260 T600 170 L600 400 Z" fill="#E8590C" />
      <circle cx="140" cy="120" r="56" fill="#0F1115" />
    </>
  ),
  orbite: (
    <>
      <rect width="600" height="400" fill="#1B1640" />
      <circle cx="160" cy="400" r="260" fill="#5B3DF5" />
      <circle cx="480" cy="110" r="46" fill="#FFB020" />
      <circle cx="400" cy="240" r="24" fill="#FFCF70" />
    </>
  ),
};

/** Remplit son conteneur (qui fixe la hauteur) ; décorative, le titre est à côté. */
export function Affiche({ id }: { id: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 600 400"
      preserveAspectRatio="xMidYMid slice"
      style={{ width: '100%', height: '100%', display: 'block' }}
    >
      {DESSINS[motifDe(id)]}
    </svg>
  );
}

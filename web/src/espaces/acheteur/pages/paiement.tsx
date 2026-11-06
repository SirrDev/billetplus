import { ChoixCartes, type OptionCarte } from '@/ui';
import type { MoyenPaiement } from '../api';
import s from './pages.module.css';

/* Pastilles neutres : pas de logos de marque tant que les chartes Wave / Orange ne sont pas fournies. */
const MOYENS: ReadonlyArray<OptionCarte<MoyenPaiement>> = [
  {
    valeur: 'WAVE',
    titre: 'Wave',
    detail: "Validation dans l'application Wave",
    visuel: (
      <span className={`${s.moyen} ${s.moyenWave}`} aria-hidden="true">
        W
      </span>
    ),
  },
  {
    valeur: 'ORANGE_MONEY',
    titre: 'Orange Money',
    detail: 'Confirmation sur votre téléphone',
    visuel: (
      <span className={`${s.moyen} ${s.moyenOm}`} aria-hidden="true">
        OM
      </span>
    ),
  },
];

export function ChoixMoyen({ valeur, onChange }: { valeur: MoyenPaiement; onChange: (m: MoyenPaiement) => void }) {
  return <ChoixCartes legende="Moyen de paiement" nom="moyen" options={MOYENS} valeur={valeur} onChange={onChange} />;
}

/** gratuit : entrée libre (ADR 0010), sans étape de paiement. */
export function Etapes({ courante, gratuit = false }: { courante: 1 | 2 | 3; gratuit?: boolean }) {
  const etapes = ['Billets', gratuit ? 'Coordonnées' : 'Paiement', 'Billets reçus'];
  return (
    <ol className={s.etapes} aria-label="Étapes de l'achat">
      {etapes.map((nom, i) => {
        const numero = i + 1;
        const classe = numero < courante ? s.etapeFaite : numero === courante ? s.etapeCourante : '';
        return (
          <li key={nom} className={`${s.etape} ${classe}`} aria-current={numero === courante ? 'step' : undefined}>
            {numero} · {nom}
            {numero < courante && <span className="visually-hidden"> (terminée)</span>}
          </li>
        );
      })}
    </ol>
  );
}

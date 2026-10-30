import { Badge, BottomNav, type NavItem, type Tone } from '@/design-system';
import type { StatutEvenement } from './api';

/** Navigation de l'espace organisateur : en bas sur téléphone, en haut sur ordinateur. */
const NAVIGATION: ReadonlyArray<NavItem> = [
  { to: '/organisateur', label: 'Événements', icon: 'chart', end: true },
  { to: '/organisateur/agents', label: 'Agents', icon: 'user' },
  { to: '/organisateur/evenements/nouveau', label: 'Créer', icon: 'plus' },
  { to: '/scanner', label: 'Contrôle', icon: 'scan' },
];

export function NavigationOrganisateur() {
  return <BottomNav items={NAVIGATION} label="Espace organisateur" />;
}

const STATUTS: Record<StatutEvenement, { libelle: string; ton: Tone }> = {
  BROUILLON: { libelle: 'Brouillon', ton: 'neutral' },
  PUBLIE: { libelle: 'En vente', ton: 'success' },
  ANNULE: { libelle: 'Annulé', ton: 'danger' },
  TERMINE: { libelle: 'Terminé', ton: 'neutral' },
};

/** Statut d'un événement : icône et texte, jamais la couleur seule. */
export function BadgeStatut({ statut }: { statut: StatutEvenement }) {
  const { libelle, ton } = STATUTS[statut];
  return (
    <Badge tone={ton} icon={statut === 'PUBLIE' ? 'check' : statut === 'ANNULE' ? 'close' : 'clock'}>
      {libelle}
    </Badge>
  );
}

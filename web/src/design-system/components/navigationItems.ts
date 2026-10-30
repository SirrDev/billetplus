import type { IconName } from './Icon';

/*
 * Données et utilitaires de navigation, hors de Navigation.tsx : le Fast Refresh de
 * Vite ne recharge à chaud qu'un fichier qui n'exporte QUE des composants
 * (règle react-refresh/only-export-components du lint).
 */

export interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  /** Active uniquement sur la route exacte (utile pour « / »). */
  end?: boolean;
}

export const buyerNavItems: ReadonlyArray<NavItem> = [
  { to: '/', label: 'Découvrir', icon: 'compass', end: true },
  { to: '/billets', label: 'Mes billets', icon: 'ticket' },
  { to: '/compte', label: 'Compte', icon: 'user' },
];

export const organizerNavItems: ReadonlyArray<NavItem> = [
  { to: '/organisateur', label: 'Ventes', icon: 'chart', end: true },
  { to: '/scanner', label: 'Contrôle', icon: 'scan' },
  { to: '/organisateur/evenements/nouveau', label: 'Créer', icon: 'plus' },
];

/** Attributs d'un panneau d'onglets, reliés à l'onglet de même id (voir SegmentedTabs, même idPrefix). */
export function tabPanelProps(idPrefix: string, id: string) {
  return { role: 'tabpanel' as const, id: `${idPrefix}-panel-${id}`, 'aria-labelledby': `${idPrefix}-tab-${id}` };
}

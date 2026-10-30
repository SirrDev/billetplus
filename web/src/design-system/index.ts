/*
 * Design system Billet+ — point d'entrée unique.
 * Importer UNIQUEMENT depuis ce fichier : `import { Button, formatFcfa } from '@/design-system'`.
 * Les fichiers internes (CSS modules, sous-dossiers) ne font pas partie de l'API publique.
 */
import '@fontsource-variable/bricolage-grotesque';
import '@fontsource-variable/plus-jakarta-sans';
import './styles/tokens.css';

export { Icon, type IconName, type IconProps } from './components/Icon';
export {
  Button,
  ButtonLink,
  IconButton,
  IconLink,
  type ButtonProps,
  type ButtonLinkProps,
  type ButtonSize,
  type ButtonVariant,
  type IconButtonProps,
  type IconButtonTone,
  type IconLinkProps,
} from './components/Button';
export { Chip, ChipGroup, FilterTag, type ChipGroupProps, type ChipOption, type ChipProps, type FilterTagProps } from './components/Chip';
export {
  PhoneField,
  RadioCardGroup,
  TextField,
  Toggle,
  type PhoneFieldProps,
  type RadioCardGroupProps,
  type RadioCardOption,
  type TextFieldProps,
  type ToggleProps,
} from './components/Form';
export {
  Badge,
  EmptyState,
  Notice,
  ProgressBar,
  SuccessHero,
  type BadgeProps,
  type EmptyStateProps,
  type NoticeProps,
  type ProgressBarProps,
  type SuccessHeroProps,
  type Tone,
} from './components/Feedback';
export {
  BottomNav,
  SegmentedTabs,
  TopBar,
  type BottomNavProps,
  type SegmentedTabsProps,
  type TabOption,
  type TopBarProps,
} from './components/Navigation';
export { buyerNavItems, organizerNavItems, tabPanelProps, type NavItem } from './components/navigationItems';
export { ListRow, Screen, StickyActionBar, VisuallyHidden, type ListRowProps, type ScreenProps } from './components/Layout';
export {
  EventFeaturedCard,
  EventRow,
  Poster,
  type EventCardProps,
  type EventRowProps,
  type EventSummary,
  type PosterProps,
  type PosterVariant,
} from './components/Event';
export {
  CountdownNotice,
  PriceSummary,
  QuantityStepper,
  TicketCategoryRow,
  TicketPass,
  type CountdownNoticeProps,
  type PriceLine,
  type PriceSummaryProps,
  type QuantityStepperProps,
  type TicketCategoryRowProps,
  type TicketPassProps,
  type TicketPassStatus,
} from './components/Ticketing';
export {
  BarChart,
  DataTable,
  MetricCard,
  type BarChartProps,
  type BarDatum,
  type Column,
  type DataTableProps,
  type MetricCardProps,
} from './components/Organizer';
export {
  EntryCounter,
  ScanResultPanel,
  ScannerViewfinder,
  type EntryCounterProps,
  type ScanResultPanelProps,
  type ScanStatus,
  type ScannerViewfinderProps,
} from './components/Scanner';

export { useCountdown, type Countdown } from './hooks/useCountdown';
export {
  formatCountdown,
  formatEventDate,
  formatFcfa,
  formatSnPhone,
  isValidSnMobile,
  sanitizeSnPhone,
} from './lib/format';

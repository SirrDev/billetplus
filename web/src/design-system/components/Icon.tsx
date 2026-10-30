import type { SVGProps } from 'react';

/**
 * Jeu d'icônes trait 24×24 (même graphisme que le prototype).
 * Les icônes sont décoratives (aria-hidden) : le sens doit toujours être porté par un texte
 * ou un aria-label sur l'élément parent.
 */
const PATHS = {
  back: ['M15 5l-7 7 7 7'],
  close: ['M6 6l12 12M18 6L6 18'],
  search: ['M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z', 'M16 16l5 5'],
  filter: ['M4 7h9M19 7h1M4 17h1M11 17h9', 'M16 4.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z', 'M8 14.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z'],
  pin: ['M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z', 'M12 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z'],
  calendar: ['M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z', 'M4 10h16M9 3v4M15 3v4'],
  user: ['M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z', 'M4 20c0-4 3.6-6 8-6s8 2 8 6'],
  ticket: ['M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4V7z', 'M14 7v10'],
  compass: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M15.5 8.5l-2 5-5 2 2-5z'],
  check: ['M5 12.5l4.5 4.5L19 7.5'],
  clock: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 7v5l3 2'],
  lock: ['M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z', 'M8 11V8a4 4 0 0 1 8 0v3'],
  warning: ['M12 8v5M12 17h.01', 'M10.3 4l-8 14a2 2 0 0 0 1.7 3h16a2 2 0 0 0 1.7-3l-8-14a2 2 0 0 0-3.4 0z'],
  exclamation: ['M12 6v8M12 18h.01'],
  download: ['M12 4v11m0 0l-4-4m4 4l4-4M5 20h14'],
  send: ['M4 12h14M13 6l6 6-6 6'],
  bell: ['M6 17V11a6 6 0 0 1 12 0v6l1.5 2h-15z', 'M10 21h4'],
  globe: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18'],
  help: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17h.01'],
  logout: ['M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9'],
  chevronRight: ['M9 5l7 7-7 7'],
  chevronDown: ['M6 9l6 6 6-6'],
  plus: ['M12 6v12M6 12h12'],
  minus: ['M6 12h12'],
  scan: ['M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M4 12h16'],
  chart: ['M4 20V10M10 20V4M16 20v-7M22 20H2'],
  wifiOff: ['M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 20h.01M3 3l18 18'],
  refresh: ['M4 12a8 8 0 0 1 14-5M20 12a8 8 0 0 1-14 5M18 3v4h-4M6 21v-4h4'],
  card: ['M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z', 'M3 10h18M16 14.5h2'],
  shield: ['M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6z'],
  mail: ['M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z', 'M3 8l9 6 9-6'],
} as const;

export type IconName = keyof typeof PATHS;

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name' | 'children'> {
  name: IconName;
  size?: number;
  strokeWidth?: number;
}

export function Icon({ name, size = 24, strokeWidth = 1.9, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

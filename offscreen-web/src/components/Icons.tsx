/* 线性图标集：24px 网格 / stroke 1.8 / round cap+join，与 APK designsystem 图标规范一致 */
type P = { size?: number; className?: string };

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
});

export const IconTimer = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="13" r="8" />
    <path d="M12 9v4l2.5 2.5M9 2h6M12 2v3" />
  </svg>
);

export const IconChart = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
  </svg>
);

export const IconLock = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <rect x="4" y="10" width="16" height="11" rx="3" />
    <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
  </svg>
);

export const IconBell = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8M13.7 21a2 2 0 0 1-3.4 0" />
  </svg>
);

export const IconWidget = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <rect x="3" y="3" width="18" height="18" rx="4" />
    <path d="M15 3v18M3 15h12" />
  </svg>
);

export const IconHeart = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M12 20s-7.5-4.6-9.3-9A5.4 5.4 0 0 1 12 6.5 5.4 5.4 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z" />
  </svg>
);

export const IconEyeOff = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M3 3l18 18M10.6 5.2A9.8 9.8 0 0 1 12 5c6 0 10 7 10 7a17 17 0 0 1-2.4 3.3M6.3 6.5A17 17 0 0 0 2 12s4 7 10 7a9.6 9.6 0 0 0 3.6-.7" />
    <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
  </svg>
);

export const IconDatabase = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <ellipse cx="12" cy="5" rx="8" ry="3" />
    <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
  </svg>
);

export const IconDownload = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M12 3v12m0 0l-4.5-4.5M12 15l4.5-4.5M4 19h16" />
  </svg>
);

export const IconHome = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M4 11.5 12 4l8 7.5M6 10.5V20h12v-9.5M10 20v-5h4v5" />
  </svg>
);

export const IconCalendar = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <rect x="3" y="5" width="18" height="16" rx="3" />
    <path d="M3 10h18M8 3v4M16 3v4" />
  </svg>
);

export const IconSettings = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.03 1.56V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.56 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1.03H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.65 8.9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1.03-1.56V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87v0a1.7 1.7 0 0 0 1.56 1.03H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.56 1.03z" />
  </svg>
);

export const IconSun = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </svg>
);

export const IconMoon = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
  </svg>
);

export const IconSystem = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <rect x="2" y="4" width="20" height="13" rx="2.5" />
    <path d="M8 21h8M12 17v4" />
  </svg>
);

export const IconMenu = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M4 6h16M4 12h16M4 18h16" />
  </svg>
);

export const IconClose = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M6 6l12 12M18 6L6 18" />
  </svg>
);

export const IconShield = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

export const IconMail = ({ size = 24 }: P) => (
  <svg {...base(size)}>
    <rect x="3" y="5" width="18" height="14" rx="3" />
    <path d="m3.5 7.5 8.5 6 8.5-6" />
  </svg>
);

export const ICONS: Record<string, (p: P) => JSX.Element> = {
  timer: IconTimer,
  chart: IconChart,
  lock: IconLock,
  bell: IconBell,
  widget: IconWidget,
  heart: IconHeart,
  'eye-off': IconEyeOff,
  database: IconDatabase,
  download: IconDownload,
  shield: IconShield,
  mail: IconMail,
};

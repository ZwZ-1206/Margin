import { useState } from 'react';
import type { Route } from '../App';
import type { ThemeMode } from '../hooks/useTheme';
import { RingLogo } from './RingLogo';
import { IconMenu, IconClose, IconSun, IconMoon, IconSystem } from './Icons';

const NAV: { key: Route; label: string }[] = [
  { key: 'home', label: '首页' },
  { key: 'download', label: '下载' },
  { key: 'changelog', label: '更新日志' },
  { key: 'support', label: '帮助与反馈' },
  { key: 'sponsor', label: '支持' },
];

const MODES: { key: ThemeMode; label: string; icon: typeof IconSun }[] = [
  { key: 'light', label: '浅色', icon: IconSun },
  { key: 'dark', label: '深色', icon: IconMoon },
  { key: 'system', label: '跟随系统', icon: IconSystem },
];

export function SiteHeader({
  route,
  onNavigate,
  mode,
  onModeChange,
}: {
  route: Route;
  onNavigate: (r: Route) => void;
  mode: ThemeMode;
  onModeChange: (m: ThemeMode) => void;
}) {
  const [open, setOpen] = useState(false);
  const CurrentIcon = MODES.find((m) => m.key === mode)?.icon ?? IconSystem;

  const go = (r: Route) => {
    onNavigate(r);
    setOpen(false);
  };

  return (
    <header className="site-header">
      <div className="container">
        <a
          className="brand"
          href="#/"
          onClick={(e) => {
            e.preventDefault();
            go('home');
          }}
        >
          <RingLogo size={28} />
          「留白」<small>Margin</small>
        </a>

        <nav className={`site-nav${open ? ' open' : ''}`}>
          {NAV.map((n) => (
            <a
              key={n.key}
              href={`#/${n.key === 'home' ? '' : n.key}`}
              className={route === n.key ? 'active' : ''}
              onClick={(e) => {
                e.preventDefault();
                go(n.key);
              }}
            >
              {n.label}
            </a>
          ))}
        </nav>

        <div className="header-actions">
          <button
            className="icon-btn"
            title={`主题：${MODES.find((m) => m.key === mode)?.label ?? ''}（点击切换）`}
            aria-label="切换主题"
            onClick={() => {
              const order: ThemeMode[] = ['light', 'dark', 'system'];
              onModeChange(order[(order.indexOf(mode) + 1) % order.length]);
            }}
          >
            <CurrentIcon size={20} />
          </button>
          <button
            className="icon-btn nav-toggle"
            aria-label="菜单"
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <IconClose size={20} /> : <IconMenu size={20} />}
          </button>
        </div>
      </div>
    </header>
  );
}

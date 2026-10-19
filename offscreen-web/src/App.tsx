import { useEffect, useState } from 'react';
import { useTheme } from './hooks/useTheme';
import { SiteHeader } from './components/SiteHeader';
import { SiteFooter } from './components/SiteFooter';
import { HomePage } from './pages/HomePage';
import { DownloadPage } from './pages/DownloadPage';
import { ChangelogPage } from './pages/ChangelogPage';
import { SupportPage } from './pages/SupportPage';

export type Route = 'home' | 'download' | 'changelog' | 'support';

function routeFromHash(): Route {
  const h = window.location.hash.replace(/^#\/?/, '');
  if (h === 'download' || h === 'changelog' || h === 'support') return h;
  return 'home';
}

export default function App() {
  const [route, setRoute] = useState<Route>(routeFromHash);
  const { mode, setMode } = useTheme();

  useEffect(() => {
    const onHash = () => setRoute(routeFromHash());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
  }, [route]);

  return (
    <div className="app-shell">
      <SiteHeader route={route} onNavigate={setRoute} mode={mode} onModeChange={setMode} />
      <main className="site-main">
        {route === 'home' && <HomePage onNavigate={setRoute} />}
        {route === 'download' && <DownloadPage onNavigate={setRoute} />}
        {route === 'changelog' && <ChangelogPage />}
        {route === 'support' && <SupportPage />}
      </main>
      <SiteFooter onNavigate={setRoute} />
    </div>
  );
}

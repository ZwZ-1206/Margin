import type { Route } from '../App';
import { RingLogo } from './RingLogo';
import { IconHeart } from './Icons';

export function SiteFooter({ onNavigate }: { onNavigate: (r: Route) => void }) {
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="footer-top">
          <div className="brand">
            <RingLogo size={24} />
            「留白」<small>Margin</small>
          </div>
          <nav className="footer-links">
            <a href="#/" onClick={(e) => { e.preventDefault(); onNavigate('home'); }}>首页</a>
            <a href="#/download" onClick={(e) => { e.preventDefault(); onNavigate('download'); }}>下载</a>
            <a href="#/changelog" onClick={(e) => { e.preventDefault(); onNavigate('changelog'); }}>更新日志</a>
            <a href="#/support" onClick={(e) => { e.preventDefault(); onNavigate('support'); }}>支持</a>
            <a href="https://github.com/ZwZ-1206/Margin" target="_blank" rel="noopener">GitHub</a>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <IconHeart size={14} /> 自愿赞助
            </span>
          </nav>
        </div>
        <p className="footer-note">
          © 2026 Margin「留白」 · Make room for life. · 完全免费 · 无会员 · 无广告 · Android 9+ · 本网站同时作为 iOS 侧的 PWA 入口
        </p>
      </div>
    </footer>
  );
}

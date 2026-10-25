import type { Route } from '../App';
import { FEATURES, PRIVACY_POINTS } from '../site/data';
import { ICONS } from '../components/Icons';
import { PhoneMockup } from '../components/PhoneMockup';
import { SponsorCard } from '../components/SponsorCard';

export function HomePage({ onNavigate }: { onNavigate: (r: Route) => void }) {
  return (
    <>
      <section className="hero">
        <div className="container">
          <div>
            <span className="eyebrow">ANDROID · 完全免费 · 无会员无广告</span>
            <div className="brandlockup">
              <p className="brand-kicker">A LITTLE MORE ROOM TO LIVE</p>
              <div className="brand-word">
                <span className="brand-en">Margin</span>
                <span className="brand-zh">「留白」</span>
              </div>
              <p className="brand-tag">
                Make room for life.
                <br />
                A little less scrolling. A little more living.
              </p>
            </div>
            <h1>
              放下手机，
              <br />
              为生活中的<span className="nw">其他精彩</span>
              <em>「留白」</em>
            </h1>
            <p className="lead">
              Margin「留白」是一款专注番茄钟与屏幕使用洞察应用。
              所有数据留在你的手机里，所有功能对所有用户免费开放。
              不收集账号，不贩卖注意力——只帮你把时间看明白，把生活过精彩。
            </p>
            <div className="hero-cta">
              <button className="btn btn-primary" onClick={() => onNavigate('download')}>
                <ICONS.download size={18} /> 下载 Android 版
              </button>
              <button className="btn btn-ghost" onClick={() => onNavigate('support')}>
                了解与支持
              </button>
            </div>
            <div className="hero-meta">
              <div className="item"><span className="num">3.6MB</span><span className="lbl">安装包体积</span></div>
              <div className="item"><span className="num">Android 9+</span><span className="lbl">系统要求</span></div>
              <div className="item"><span className="num">0</span><span className="lbl">第三方追踪 SDK</span></div>
              <div className="item"><span className="num">100%</span><span className="lbl">功能免费</span></div>
            </div>
          </div>
          <PhoneMockup />
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <span className="eyebrow">功能</span>
            <h2>少即是多的时间工具</h2>
            <p>六个核心能力，覆盖「看见时间 → 管理注意力 → 温和干预」的完整闭环。</p>
          </div>
          <div className="feature-grid">
            {FEATURES.map((f) => {
              const Icon = ICONS[f.icon] ?? ICONS.timer;
              return (
                <article className="card feature-card" key={f.title}>
                  <span className="fic"><Icon size={22} /></span>
                  <h3>{f.title}</h3>
                  <p>{f.desc}</p>
                </article>
              );
            })}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <span className="eyebrow">隐私</span>
            <h2>你的数据只属于你</h2>
            <p>没有账号体系，就没有可以泄露的东西。这是设计选择，不是营销话术。</p>
          </div>
          <div className="privacy-band">
            {PRIVACY_POINTS.map((p) => {
              const Icon = ICONS[p.icon] ?? ICONS.lock;
              return (
                <div className="card privacy-item" key={p.title}>
                  <span className="fic"><Icon size={22} /></span>
                  <div>
                    <b>{p.title}</b>
                    <span>{p.desc}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <SponsorCard />
        </div>
      </section>
    </>
  );
}

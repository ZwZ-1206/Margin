import { CHANGELOG } from '../site/data';

export function ChangelogPage() {
  return (
    <section className="section" style={{ paddingTop: 'var(--space-12)' }}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">更新日志</span>
          <h2>版本记录</h2>
          <p>与 App 内版本号联动。发布后每个版本的功能、修复与已知问题都会记录在此。</p>
        </div>

        <div className="timeline">
          {CHANGELOG.map((entry) => (
            <article className="tl-item" key={entry.version}>
              <div>
                <div className="ver">{entry.version}</div>
                <div className="date">{entry.date}</div>
              </div>
              <div className="body">
                <h3>
                  {entry.status === 'plan' ? '首个版本（规划内容）' : '发布说明'}{' '}
                  {entry.status === 'plan' && <span className="tl-badge plan">规划中</span>}
                </h3>
                <ul
                  dangerouslySetInnerHTML={{
                    __html: entry.items.map((i) => `<li>${i}</li>`).join(''),
                  }}
                />
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

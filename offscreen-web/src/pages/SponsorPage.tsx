import { IconHeart, IconMail, IconGithub } from '../components/Icons';

/* 赞助页：只放收款码与说明，不掺别的。 */
export function SponsorPage() {
  return (
    <section className="section" style={{ paddingTop: 'var(--space-12)' }}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">支持</span>
          <h2>请我喝杯咖啡</h2>
          <p>
            「留白」完全免费：无会员、无订阅、无广告、无功能墙。
            如果你觉得它有用，可以自愿扫码赞助——<b>这不会解锁任何功能</b>，因为所有功能本来就已经免费。
          </p>
        </div>

        <div className="sponsor-hero">
          <div className="qr-card">
            <img
              src="sponsor-qr.jpg"
              alt="微信收款码"
              className="qr-img-lg"
              width={320}
              height={389}
            />
            <p className="qr-hint">微信扫码 · 金额随意 · 一元也是心意</p>
          </div>

          <div className="sponsor-copy">
            <h3>
              <IconHeart size={18} /> 你的赞助花在哪
            </h3>
            <ul className="plain-list">
              <li>服务器与域名（官网、版本清单校验）</li>
              <li>签名证书与构建环境</li>
              <li>深夜改 bug 时的那杯咖啡</li>
            </ul>

            <h3 style={{ marginTop: 'var(--space-8)' }}>不花钱也能支持</h3>
            <p>
              给仓库点个 Star、把 App 推荐给一个总在刷手机的朋友、
              或者在邮件里告诉我哪里用着别扭——这些都和钱一样有用。
            </p>

            <div className="sponsor-actions">
              <a
                className="btn btn-ghost"
                href="https://github.com/ZwZ-1206/Margin"
                target="_blank"
                rel="noopener"
              >
                <IconGithub size={18} /> 去 GitHub 点 Star
              </a>
              <a className="btn btn-ghost" href={`mailto:3067326187@qq.com`}>
                <IconMail size={18} /> 写封邮件给我
              </a>
            </div>
          </div>
        </div>

        <p className="sponsor-footnote">
          赞助完全自愿。不赞助也能用全部功能，而且永远是。
        </p>
      </div>
    </section>
  );
}

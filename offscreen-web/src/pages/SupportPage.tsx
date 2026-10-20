import { FAQS } from '../site/data';
import { IconMail, IconShield, IconDatabase, IconHeart } from '../components/Icons';

const FEEDBACK_EMAIL = '3067326187@qq.com';

export function SupportPage() {
  return (
    <section className="section" style={{ paddingTop: 'var(--space-12)' }}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">支持</span>
          <h2>帮助与反馈</h2>
          <p>常见问题、反馈渠道与隐私摘要。找不到答案？发封邮件即可。</p>
        </div>

        <div className="faq">
          {FAQS.map((f) => (
            <details className="card faq-item" key={f.q}>
              <summary>{f.q}</summary>
              {f.a.map((p, i) => (
                <p key={i}>{p}</p>
              ))}
            </details>
          ))}
        </div>

        <div className="support-grid" style={{ marginTop: 'var(--space-12)' }}>
          <div>
            <h3>反馈渠道</h3>
            <div className="kv">
              <div className="row">
                <span className="k"><IconMail size={16} /></span>
                <span className="v">
                  邮件：<a className="mail-link" href={`mailto:${FEEDBACK_EMAIL}`}>{FEEDBACK_EMAIL}</a>
                </span>
              </div>
              <div className="row">
                <span className="k">GitHub</span>
                <span className="v">
                  <a className="mail-link" href="https://github.com/ZwZ-1206/Margin/issues" rel="noopener">
                    Issues 区提交问题
                  </a>
                </span>
              </div>
              <div className="row">
                <span className="k">回复预期</span>
                <span className="v">工作日 48 小时内</span>
              </div>
              <div className="row">
                <span className="k">建议附上</span>
                <span className="v">机型、系统版本、复现步骤、截图</span>
              </div>
            </div>
          </div>

          <div>
            <h3>隐私摘要</h3>
            <div className="kv">
              <div className="row">
                <span className="k"><IconShield size={16} /></span>
                <span className="v">无账号、无登录、不收集任何个人身份信息</span>
              </div>
              <div className="row">
                <span className="k"><IconDatabase size={16} /></span>
                <span className="v">专注与使用数据仅存本机；导出文件由你自行保管</span>
              </div>
              <div className="row">
                <span className="k">网络用途</span>
                <span className="v">仅内置帮助文档网页；无遥测、无崩溃上报</span>
              </div>
              <div className="row">
                <span className="k">数据删除</span>
                <span className="v">设置内一键清空，或直接卸载 App</span>
              </div>
            </div>
          </div>
        </div>

        <div className="support-sponsor-cta">
          <p>如果「留白」帮到了你，可以请我喝杯咖啡——不赞助也能用全部功能。</p>
          <a className="btn btn-accent" href="#/sponsor" onClick={(e) => {
            e.preventDefault();
            window.location.hash = '#/sponsor';
          }}>
            <IconHeart size={18} /> 前往支持页
          </a>
        </div>
      </div>
    </section>
  );
}

import { useCallback, useEffect, useState } from 'react';
import type { Route } from '../App';
import { ICONS } from '../components/Icons';
import { SponsorCard } from '../components/SponsorCard';
import { DownloadQR } from '../components/DownloadQR';
import {
  absoluteUrl,
  FALLBACK_RELEASE,
  INSTALL_STEPS,
  PERMISSIONS,
  VENDOR_GUIDES,
  copyText,
  fetchRelease,
  isNewer,
  type ReleaseInfo,
} from '../site/download';

type CheckState = 'idle' | 'checking' | 'latest' | 'outdated' | 'failed';

export function DownloadPage({ onNavigate }: { onNavigate: (r: Route) => void }) {
  const [release, setRelease] = useState<ReleaseInfo>(FALLBACK_RELEASE);
  const [live, setLive] = useState(false);
  const [check, setCheck] = useState<CheckState>('idle');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchRelease().then((r) => {
      if (cancelled || !r) return;
      setRelease(r);
      setLive(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const apkReady = release.apk.url.length > 0;
  const pageUrl = typeof window === 'undefined' ? '' : window.location.href.split('#')[0] + '#/download';
  const qrTarget = apkReady ? absoluteUrl(release.apk.url) : pageUrl;

  const runCheck = useCallback(async () => {
    setCheck('checking');
    const r = await fetchRelease();
    if (!r) {
      setCheck('failed');
      return;
    }
    setRelease(r);
    setLive(true);
    setCheck(isNewer(r.version, FALLBACK_RELEASE.version) ? 'outdated' : 'latest');
  }, []);

  const onCopySha = useCallback(async () => {
    if (!release.apk.sha256) return;
    const ok = await copyText(release.apk.sha256);
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    }
  }, [release.apk.sha256]);

  return (
    <section className="section" style={{ paddingTop: 'var(--space-12)' }}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">下载</span>
          <h2>获取 Margin「留白」</h2>
          <p>
            官网是 Margin「留白」唯一的下载渠道。无需注册、无需登录、没有任何功能门槛——
            下载即获得完整应用。
          </p>
        </div>

        {/* ===== 版本卡片 + 二维码 ===== */}
        <div className="dl-grid">
          <div className="card dl-card">
            <div className="dl-version">
              <span className="v">v{release.version}</span>
              {release.releaseDate ? (
                <span className="tag">{release.releaseDate}</span>
              ) : (
                <span className="tag">{apkReady ? '最新版' : '构建中 · 即将发布'}</span>
              )}
            </div>
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-body-s)' }}>
              {release.notes}
            </p>

            {apkReady ? (
              <a className="btn btn-primary btn-block" href={release.apk.url} rel="noopener">
                <ICONS.download size={18} /> 下载 APK（{release.sizeMB}MB）
              </a>
            ) : (
              <button className="btn btn-primary btn-block" disabled>
                <ICONS.download size={18} /> APK 尚未发布
              </button>
            )}

            <div className="dl-meta">
              <div className="m"><b>{release.sizeMB}MB</b><span>安装包大小</span></div>
              <div className="m"><b>{release.minAndroid}+</b><span>系统要求</span></div>
              <div className="m"><b>通用</b><span>架构 universal</span></div>
              <div className="m"><b>0</b><span>广告与追踪 SDK</span></div>
            </div>

            <ol className="dl-steps">
              {INSTALL_STEPS.map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ol>

            <div className="checksum-row">
              <span className="checksum-label">SHA-256</span>
              <code className="checksum">{release.apk.sha256 || '待发布时公布'}</code>
              {release.apk.sha256 && (
                <button className="btn btn-ghost btn-sm" onClick={onCopySha}>
                  {copied ? '已复制' : '复制'}
                </button>
              )}
            </div>
          </div>

          <div style={{ display: 'grid', gap: 'var(--space-5)' }}>
            <div className="card qr-card">
              <h3>手机扫码下载</h3>
              <div className="qr-wrap">
                <DownloadQR target={qrTarget} />
              </div>
              <p className="qr-hint">
                {apkReady ? '扫描后直接下载 APK' : '扫描在手机上打开本页（APK 发布后二维码将直接指向安装包）'}
              </p>
            </div>

            <div className="card" style={{ padding: 'var(--space-6)' }}>
              <h3 className="card-title">镜像与更新</h3>
              <div className="kv">
                {release.mirrors.map((m) => (
                  <div className="row" key={m.name}>
                    <span className="k">{m.name}</span>
                    <span className="v">
                      {m.url ? (
                        <a href={m.url} rel="noopener" className="mail-link">前往下载</a>
                      ) : (
                        `${m.note} · 待发布`
                      )}
                    </span>
                  </div>
                ))}
                <div className="row">
                  <span className="k">版本清单</span>
                  <span className="v">
                    {live ? '已连接 version.json' : '静态回退值'}
                  </span>
                </div>
              </div>
              <button
                className="btn btn-ghost btn-block"
                style={{ marginTop: 'var(--space-4)' }}
                onClick={runCheck}
                disabled={check === 'checking'}
              >
                {check === 'checking' ? '正在检查…' : '检查更新'}
              </button>
              {check === 'latest' && <p className="check-msg ok">已是最新版本 v{release.version}</p>}
              {check === 'outdated' && (
                <p className="check-msg warn">发现新版本 v{release.version}，刷新页面即可下载</p>
              )}
              {check === 'failed' && <p className="check-msg">检查失败，请稍后重试或直接对比上方版本号</p>}
            </div>

            <SponsorCard compact />
          </div>
        </div>

        {/* ===== 分厂商安装指南 ===== */}
        <div className="sub-block">
          <div className="section-head">
            <span className="eyebrow">安装</span>
            <h3>各品牌手机安装指引</h3>
            <p>安卓系统默认禁止安装来自浏览器的应用，按你的品牌放行一次即可，之后更新无需重复操作。</p>
          </div>
          <div className="vendor-grid">
            {VENDOR_GUIDES.map((g) => (
              <details className="card vendor-item" key={g.vendor}>
                <summary>
                  <span className="vendor-name">{g.vendor}</span>
                  <span className="vendor-system">{g.system}</span>
                </summary>
                <p className="vendor-path">{g.path}</p>
                <p className="vendor-tip">{g.tip}</p>
              </details>
            ))}
          </div>
        </div>

        {/* ===== 信任与安全 ===== */}
        <div className="sub-block">
          <div className="section-head">
            <span className="eyebrow">信任</span>
            <h3>为什么可以放心安装</h3>
            <p>
              侧载应用的风险来自「不透明」——「留白」把权限清单、签名指纹和文件校验全部公开，
              你可以逐项核对后再决定是否安装。
            </p>
          </div>

          <div className="trust-grid">
            <div className="card trust-card">
              <h4><ICONS.shield size={18} /> 安全声明</h4>
              <ul className="trust-list">
                <li>无账号系统、无广告 SDK、无统计与崩溃上报</li>
                <li>不含任何强制更新、后台静默下载或诱导安装行为</li>
                <li>全部数据仅存本机；可选导出备份，文件由你自行保管</li>
                <li>安装包经 VirusTotal 公开扫描{release.virusScan ? '（报告链接见版本卡片）' : '（发布时提供报告链接）'}</li>
              </ul>
            </div>

            <div className="card trust-card">
              <h4><ICONS.lock size={18} /> 完整性校验</h4>
              <div className="kv">
                <div className="row">
                  <span className="k">SHA-256</span>
                  <span className="v mono">{release.apk.sha256 || '待发布'}</span>
                </div>
                <div className="row">
                  <span className="k">签名证书指纹</span>
                  <span className="v mono">{release.certSha256 || '待发布'}</span>
                </div>
              </div>
              <p className="trust-note">
                校验方法：Windows 用 <code>certutil -hashfile 文件名.apk SHA256</code>；
                macOS / Linux 用 <code>shasum -a 256 文件名.apk</code>。
                输出应与上方一致；签名证书指纹可用 <code>apksigner verify --print-certs</code> 核对。
              </p>
            </div>
          </div>

          <div className="card perm-card">
            <h4>权限清单（共 {PERMISSIONS.length} 项，逐项说明用途）</h4>
            <table className="perm-table">
              <thead>
                <tr><th>权限</th><th>用途</th><th>敏感</th></tr>
              </thead>
              <tbody>
                {PERMISSIONS.map((p) => (
                  <tr key={p.name}>
                    <td className="mono">{p.name}</td>
                    <td>{p.why}</td>
                    <td>{p.sensitive ? <span className="badge warn">敏感</span> : <span className="badge">常规</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="trust-note">
              敏感权限均可拒绝后继续使用：拒绝录音/相机不影响核心功能；
              仅「使用情况访问」为屏幕洞察的必需项，拒绝后相关页面会显示引导而不是空白。
            </p>
          </div>
        </div>

        {/* ===== 更新机制 ===== */}
        <div className="sub-block">
          <div className="section-head">
            <span className="eyebrow">更新</span>
            <h3>如何获取新版本</h3>
          </div>
          <div className="update-grid">
            <div className="card update-card">
              <h4>应用内检查</h4>
              <p>App 启动时会读取本页的 <code>version.json</code> 清单（仅一次 HTTP 请求，不携带任何设备信息），发现新版本后在设置页提示，点击即来本页下载。</p>
            </div>
            <div className="card update-card">
              <h4>覆盖安装</h4>
              <p>下载新版本后直接安装即可覆盖旧版，专注记录与设置全部保留（同一签名证书）。卸载会清除数据，请先使用设置内的「导出备份」。</p>
            </div>
            <div className="card update-card">
              <h4>订阅通知</h4>
              <p>暂不提供邮件列表。可关注更新日志页的版本记录，或收藏本下载页每次手动检查——刻意保持简单，不收集你的联系方式。</p>
            </div>
          </div>
        </div>

        <div className="sub-block">
          <button className="btn btn-ghost" onClick={() => onNavigate('changelog')}>
            查看完整更新日志
          </button>
        </div>
      </div>
    </section>
  );
}

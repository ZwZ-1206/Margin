/* 桌面小组件展示区：三个尺寸的等比重绘。
   配色刻意不走站点 token——小组件是系统级 RemoteViews，浅色暖纸底 + 固定字色，
   不随 App 深色模式变化；这里用硬编码色值才能与真机上的样子一致。 */
import { ICONS } from './Icons';

/* 线性图标集里没有的两个语义图标，按同一规范（24px / stroke 1.8 / round）手绘 */
const IconTap = ({ size = 24 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 11.5V5.5a2 2 0 0 1 4 0v5" />
    <path d="M13 10.5V4.5a2 2 0 0 1 4 0v7" />
    <path d="M17 11.5a2 2 0 0 1 4 0v4.5a6 6 0 0 1-6 6h-1.5a6 6 0 0 1-5.2-3l-2.3-3.8a2 2 0 0 1 3.4-2.1L11 15.5" />
  </svg>
);

const IconRefresh = ({ size = 24 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 11.5a8 8 0 1 0-2.6 6.3" />
    <path d="M20 4v6h-6" />
  </svg>
);

/** TOP 3 单行：图标 + 名称 + 时长 + 相对第一名的占比条 */
function TopRow({ tone, label, time, pct }: { tone: string; label: string; time: string; pct: number }) {
  return (
    <div className="wg-row">
      <span className="wg-ic" style={{ background: tone }} aria-hidden="true">{label[0]}</span>
      <span className="wg-name">{label}</span>
      <span className="wg-time">{time}</span>
      <span className="wg-bar" aria-hidden="true"><i style={{ width: `${pct}%`, background: pct >= 99 ? '#0A7A61' : '#C4BFB2' }} /></span>
    </div>
  );
}

export function WidgetShowcase() {
  return (
    <section className="section widget-section">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">桌面小组件</span>
          <h2>把今天的屏幕时间，放到桌面上</h2>
          <p>
            不打开 App 也能看见时间去了哪。三种尺寸各就各位：小的看屏幕时间，中的一览三项指标，
            大的直接告诉你今天在哪些应用上花了最多时间。
          </p>
        </div>

        <div className="widget-stage">
          <figure className="widget-col widget-col-main">
            <div
              className="wgt wgt-lg"
              role="img"
              aria-label="大尺寸小组件（4×3）：今日屏幕 3 小时 42 分，专注 1 小时 12 分，今日拿起 23 次；使用时长前三名：短视频 1 小时 48 分、社交 52 分钟、阅读 31 分钟"
            >
              <div className="wg-head">
                <span className="wg-brand">「留白」</span>
                <span className="wg-pickup">今日拿起 23 次</span>
              </div>
              <div className="wg-hero">
                <span className="wg-screen">3小时42分</span>
                <span className="wg-screen-lbl">今日屏幕</span>
                <span className="wg-focus">专注 1小时12分</span>
              </div>
              <div className="wg-div" />
              <span className="wg-top-lbl">使用时长 TOP 3</span>
              <TopRow tone="#D6568A" label="短视频" time="1小时48分" pct={100} />
              <TopRow tone="#3E63DD" label="社交" time="52分钟" pct={48} />
              <TopRow tone="#7C6BD9" label="阅读" time="31分钟" pct={28} />
            </div>
            <figcaption>4×3 · 使用排行</figcaption>
          </figure>

          <div className="widget-col widget-col-side">
            <figure className="widget-col">
              <div
                className="wgt wgt-sm"
                role="img"
                aria-label="小尺寸小组件（2×2）：今日屏幕 3 小时 42 分，专注 1 小时 12 分，今日拿起 23 次"
              >
                <div className="wg-head">
                  <span className="wg-brand">「留白」</span>
                  <span className="wg-pickup">拿起 23</span>
                </div>
                <div className="wg-screen wg-screen-sm">3小时42分</div>
                <span className="wg-screen-lbl">今日屏幕</span>
                <div className="wg-div" />
                <span className="wg-focus">专注 1小时12分</span>
              </div>
              <figcaption>2×2 · 今日屏幕</figcaption>
            </figure>
            <figure className="widget-col">
              <div
                className="wgt wgt-md"
                role="img"
                aria-label="中尺寸小组件（4×1）：今日屏幕 3 小时 42 分，今日专注 1 小时 12 分，拿起次数 23"
              >
                <div className="wg-md-grid">
                  <div>
                    <span className="wg-md-lbl">今日屏幕</span>
                    <span className="wg-md-val wg-md-main">3小时42分</span>
                  </div>
                  <span className="wg-md-sep" aria-hidden="true" />
                  <div>
                    <span className="wg-md-lbl">今日专注</span>
                    <span className="wg-md-val wg-md-focus">1小时12分</span>
                  </div>
                  <div>
                    <span className="wg-md-lbl">拿起次数</span>
                    <span className="wg-md-val">23</span>
                  </div>
                </div>
              </div>
              <figcaption>4×1 · 概览条</figcaption>
            </figure>
          </div>
        </div>

        <div className="widget-points">
          <div className="card widget-point">
            <span className="fic"><ICONS.widget size={22} /></span>
            <div>
              <b>三种尺寸</b>
              <span>2×2、4×1、4×3 按桌面空间自选，同一份数据三种看法。</span>
            </div>
          </div>
          <div className="card widget-point">
            <span className="fic"><ICONS.chart size={22} /></span>
            <div>
              <b>使用时长 TOP 3</b>
              <span>大部件排出今天耗时最多的三个应用，占比条一眼看出谁是时间黑洞。</span>
            </div>
          </div>
          <div className="card widget-point">
            <span className="fic"><IconTap size={22} /></span>
            <div>
              <b>点一下就打开</b>
              <span>点部件进留白；点大部件里的某个应用行，直接打开那个应用。</span>
            </div>
          </div>
          <div className="card widget-point">
            <span className="fic"><IconRefresh size={22} /></span>
            <div>
              <b>自动保持新鲜</b>
              <span>打开 App、每次采集完成、开机都会立即刷新，系统每 30 分钟再兜底一次。</span>
            </div>
          </div>
        </div>

        <p className="widget-hint">
          <b>怎么添加：</b>长按桌面空白处 → 选择「小组件」→ 找到「留白」，把想要的尺寸拖到桌面即可。
          应用明细需要「使用情况访问」权限，未授权时大部件会显示引导文案，不影响其他功能。
        </p>
      </div>
    </section>
  );
}

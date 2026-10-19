/* Hero 区手机样机：展示 APK 首页界面风格（双环 + 指标 + 列表），与真机同一套 token */
export function PhoneMockup() {
  const R = 52;
  const C = 2 * Math.PI * R;
  const gap = (60 / 360) * C; // 缺口 60°，品牌符号

  return (
    <div className="phone-stage">
      <div className="phone">
        <div className="phone-notch" />
        <div className="phone-screen">
          <div className="phone-greet">下午好 · 10月19日 周一</div>
          <div className="phone-hero-card">
            <svg width="150" height="150" viewBox="0 0 150 150" aria-label="今日专注双环">
              {/* 外环：今日目标完成度 */}
              <circle cx="75" cy="75" r={R} fill="none" stroke="var(--divider)" strokeWidth="12" />
              <circle
                cx="75" cy="75" r={R} fill="none" stroke="var(--primary)" strokeWidth="12"
                strokeLinecap="round"
                strokeDasharray={`${C - gap} ${C}`}
                strokeDashoffset={C - gap - (C - gap) * 0.68}
                transform="rotate(120 75 75)"
              />
              {/* 内环：连续天数 */}
              <circle cx="75" cy="75" r={R - 16} fill="none" stroke="var(--divider)" strokeWidth="8" />
              <circle
                cx="75" cy="75" r={R - 16} fill="none" stroke="var(--accent)" strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={`${2 * Math.PI * (R - 16) - gap} ${2 * Math.PI * (R - 16)}`}
                strokeDashoffset={2 * Math.PI * (R - 16) - gap - (2 * Math.PI * (R - 16) - gap) * 0.43}
                transform="rotate(120 75 75)"
              />
              <text x="75" y="72" textAnchor="middle" fill="var(--text-primary)" fontSize="20" fontWeight="600" style={{ fontVariantNumeric: 'tabular-nums' }}>1h52m</text>
              <text x="75" y="92" textAnchor="middle" fill="var(--text-tertiary)" fontSize="10">今日专注 · 比昨日 ↓18%</text>
            </svg>
            <div className="phone-pills">
              <div className="phone-pill"><b>3</b><span>完成</span></div>
              <div className="phone-pill"><b>1</b><span>中断</span></div>
              <div className="phone-pill"><b>6 天</b><span>连续</span></div>
            </div>
          </div>
          <div className="phone-row">
            <span className="ic">学</span>
            <span className="bar"><i style={{ width: '72%' }} /></span>
            <span style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>48m</span>
          </div>
          <div className="phone-row">
            <span className="ic">读</span>
            <span className="bar"><i style={{ width: '45%' }} /></span>
            <span style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>30m</span>
          </div>
          <div className="phone-row">
            <span className="ic">工</span>
            <span className="bar"><i style={{ width: '30%' }} /></span>
            <span style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>20m</span>
          </div>
        </div>
        <div className="phone-tabbar">
          <span className="tab on"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M4 11.5 12 4l8 7.5M6 10.5V20h12v-9.5" /></svg>首页</span>
          <span className="tab"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2.5 2.5M9 2h6" /></svg>专注</span>
          <span className="tab"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></svg>统计</span>
          <span className="tab"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>日历</span>
        </div>
      </div>
    </div>
  );
}

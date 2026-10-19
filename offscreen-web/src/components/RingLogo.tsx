/* 「留白之环」品牌符号：缺口 60° 的圆环 + 光点 */
export function RingLogo({ size = 32, animated = false }: { size?: number; animated?: boolean }) {
  return (
    <svg width={size} height={size} viewBox="0 0 512 512" aria-label="「留白」">
      <g
        fill="none"
        stroke="var(--primary)"
        strokeWidth="30"
        strokeLinecap="round"
        style={animated ? { transformOrigin: '256px 256px', animation: 'ring-spin 12s linear infinite' } : undefined}
      >
        <path d="M 380 190 A 120 120 0 1 1 366 238" />
      </g>
      <circle cx="380" cy="190" r="14" fill="var(--primary)" />
      <circle cx="366" cy="238" r="14" fill="var(--primary)" />
      <circle cx="406" cy="184" r="10" fill="var(--accent)">
        {animated && (
          <animate attributeName="opacity" values="0.4;1;0.4" dur="2.4s" repeatCount="indefinite" />
        )}
      </circle>
      <style>{`@keyframes ring-spin { to { transform: rotate(360deg); } }`}</style>
    </svg>
  );
}

/* 占位收款码：提示替换为真实图片 */
export function PlaceholderQr() {
  const cells = 21;
  const cell = 8;
  const sizePx = cells * cell;
  // 伪随机但固定的图案（定位角 + 数据区）
  const dots: JSX.Element[] = [];
  const finder = (cx: number, cy: number) => {
    dots.push(<rect key={`f${cx}-${cy}`} x={cx * cell} y={cy * cell} width={7 * cell} height={7 * cell} rx={cell} fill="none" stroke="currentColor" strokeWidth={cell * 0.8} />);
    dots.push(<rect key={`fi${cx}-${cy}`} x={(cx + 2) * cell} y={(cy + 2) * cell} width={3 * cell} height={3 * cell} rx={cell * 0.5} fill="currentColor" />);
  };
  finder(0, 0); finder(14, 0); finder(0, 14);
  let seed = 7;
  for (let y = 0; y < cells; y++) {
    for (let x = 0; x < cells; x++) {
      const inFinder = (x < 8 && y < 8) || (x > 12 && y < 8) || (x < 8 && y > 12);
      if (inFinder) continue;
      seed = (seed * 1103515245 + 12345) % 2147483648;
      if (seed % 3 === 0) {
        dots.push(<rect key={`d${x}-${y}`} x={x * cell} y={y * cell} width={cell * 0.8} height={cell * 0.8} rx={1} fill="currentColor" />);
      }
    }
  }
  return (
    <svg viewBox={`0 0 ${sizePx} ${sizePx}`} width="100%" height="100%" role="img" aria-label="收款码占位图">
      {dots}
    </svg>
  );
}

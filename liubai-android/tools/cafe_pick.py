"""从 ebur128 日志提取每 10s 的短期响度统计，找平稳耐听的咖啡馆段。

注意：此版本 ffmpeg 的 ebur128 输出为两行一帧——
  行1: [Parsed_ebur128_0 @ ...] t: 0.199615 TARGET:-23 LUFS M:-120.7
  行2:  S:-120.7 I:-70.0 LUFS LRA: 0.0 LU FTPK: ... TPK: ...
"""
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "ebur.log"

raw = open(path, "rb").read()
text = None
for enc in ("utf-16", "utf-8"):
    try:
        cand = raw.decode(enc)
        if "M:" in cand and "S:" in cand:
            text = cand
            break
    except UnicodeDecodeError:
        continue
if text is None:
    print("无法解码日志")
    raise SystemExit(1)

t_re = re.compile(r"t:\s*([\d.]+).*?M:\s*(-?[\d.]+|-inf)")
s_re = re.compile(r"^\s*S:\s*(-?[\d.]+|-inf)")
frames = []  # (t, M, S)
cur_t = cur_m = None
for line in text.splitlines():
    if line.lstrip().startswith("[Parsed_ebur128") and "t:" in line:
        m = t_re.search(line)
        if m:
            cur_t = float(m.group(1))
            cur_m = None if m.group(2) == "-inf" else float(m.group(2))
    elif line.lstrip().startswith("S:") and cur_t is not None:
        m = s_re.match(line)
        if m:
            s = None if m.group(1) == "-inf" else float(m.group(1))
            frames.append((cur_t, cur_m, s))
            cur_t = None

print(f"帧数: {len(frames)}  时长: {frames[-1][0]:.1f}s")

blocks = {}
for t, m, s in frames:
    if s is None:
        continue
    blocks.setdefault(int(t // 10), []).append(s)

stats = []
for b in sorted(blocks):
    ss = blocks[b]
    mean_s = sum(ss) / len(ss)
    std_s = (sum((v - mean_s) ** 2 for v in ss) / len(ss)) ** 0.5
    stats.append((b * 10, mean_s, std_s))

print(f"{'t(s)':>8} {'S均值':>7} {'S波动':>7}")
for t, mean_s, std_s in stats:
    print(f"{t:8.0f} {mean_s:7.1f} {std_s:7.2f}")

# 候选：滑动 120s 窗口，S 均值在 [-30,-18]，平均波动 < 1.6
WIN = 12
best = []
for i in range(0, len(stats) - WIN + 1):
    seg = stats[i:i + WIN]
    m_mean = sum(x[1] for x in seg) / WIN
    m_std = sum(x[2] for x in seg) / WIN
    if -30 <= m_mean <= -18 and m_std < 1.6:
        score = abs(m_mean + 24) * 2 + m_std * 3
        best.append((score, seg[0][0], m_mean, m_std))

best.sort()
print("\n=== 候选 120s 段（前 15）===")
for score, t0, m_mean, m_std in best[:15]:
    print(f"t={t0:6.0f}s ({t0/60:5.1f}min)  S={m_mean:6.1f}  波动={m_std:4.2f}  分={score:5.2f}")

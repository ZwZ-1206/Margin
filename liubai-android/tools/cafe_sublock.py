"""在 640–800s（全片最稳最热闹段）内按 10s 子块挑最优质的 110s 窗口。

标准：RMS 适中且平稳、峰值低（无突发巨响）、1k–3.4k 人声床占比别太低。
"""
import math
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp import fft  # noqa: E402

FF = r"C:\Apps\ffmpeg-8.1.2-essentials_build\bin\ffmpeg.exe"
SRC = r"C:\Users\ZwZ\Downloads\audio..mp3"
SR = 22050
WIN = 4096


def load(start_sec, dur_sec):
    cmd = [FF, "-v", "error", "-ss", str(start_sec), "-t", str(dur_sec),
           "-i", SRC, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"]
    out = subprocess.run(cmd, capture_output=True, check=True).stdout
    n = len(out) // 2
    return [int.from_bytes(out[i * 2:i * 2 + 2], "little", signed=True) / 32768.0
            for i in range(n)]


def block_stats(x):
    rms = math.sqrt(sum(v * v for v in x) / len(x))
    peak = max(abs(v) for v in x)
    s134 = 0.0
    nfr = 0
    for off in range(0, len(x) - WIN, WIN):
        seg = x[off:off + WIN]
        X = fft([complex(v, 0.0) for v in seg])
        power = [abs(c) ** 2 for c in X]
        total = sum(power)
        if total <= 0:
            continue
        freqs = [k * SR / WIN for k in range(WIN // 2 + 1)]
        fold = [power[0]] + [power[k] + power[WIN - k] for k in range(1, WIN // 2)] + [power[WIN // 2]]
        s134 += sum(fold[k] for k in range(len(freqs)) if 1000 <= freqs[k] < 3400) / total
        nfr += 1
    return rms, peak, (s134 / nfr if nfr else 0.0)


T0, DUR = 640, 170
x = load(T0, DUR)
step = SR * 10
print(f"{'t(s)':>7} {'RMS':>7} {'峰值':>6} {'1k-3.4k':>8}")
blocks = []
for i in range(0, len(x) - step + 1, step):
    t = T0 + i / SR
    rms, peak, s = block_stats(x[i:i + step])
    blocks.append((t, rms, peak, s))
    print(f"{t:7.0f} {rms:7.4f} {peak:6.3f} {s * 100:7.1f}%")

# 滑动 11 块（110s）窗口：RMS 均值高、峰值低且平稳、人声床不差
print("\n=== 110s 窗口评分 ===")
W = 11
best = []
for i in range(0, len(blocks) - W + 1):
    seg = blocks[i:i + W]
    rmss = [b[1] for b in seg]
    peaks = [b[2] for b in seg]
    s134s = [b[3] for b in seg]
    m_rms = sum(rmss) / W
    sd_rms = (sum((v - m_rms) ** 2 for v in rmss) / W) ** 0.5
    m_peak = max(peaks)
    m_s = sum(s134s) / W
    score = m_rms * 300 + m_s * 60 - sd_rms * 400 - max(0, m_peak - 0.8) * 50
    best.append((score, seg[0][0], m_rms, sd_rms, m_peak, m_s))
best.sort(reverse=True)
for score, t, m_rms, sd_rms, m_peak, m_s in best[:8]:
    print(f"t={t:5.0f}s  RMS={m_rms:.4f}  波动={sd_rms:.4f}  峰={m_peak:.3f}  1k-3.4k={m_s * 100:.1f}%  分={score:.2f}")

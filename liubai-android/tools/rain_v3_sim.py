"""雨声 v3 仿真标定 v2：修正猝发衰减系数。

衰减到 0.001 所需时间 T 与系数 dec 的关系：dec = 0.001^(1/(T*sr))
  T=10ms -> dec=0.98446   T=25ms -> dec=0.99375   （溅落）
  T=7ms  -> dec=0.97788   T=23ms -> dec=0.99321   （微溅）
上一版误用 0.99955~0.99975（实际是 400ms 长尾），导致事件层几乎听不见。

v3 结构（i = 强度）：
  body   = brownN * (0.14 + 0.06*i)
  hiss   = (lp5k2 - lp900) * (0.80 + 0.65*i) * gust
  splash = 噪声猝发 10–25ms，源 (lp4200-lp900)，概率 0.00002+0.00013*i（0.9→6.6 次/s）
  patter = 噪声猝发 7–23ms，源 (lp5k2-lp900)，概率 0.0002+0.0018*i（8.8→88 次/s）
  plink  = 正弦+下滑，衰 dec=0.9993（≈250ms 余振），概率 0.000015+0.00006*i
各层幅度由本脚本按目标 RMS 反解。
"""
import math
import random
import sys

sys.path.insert(0, ".")
from dsp import brown, one_pole, report, rms, shape  # noqa: E402

SR = 44100
N = 1 << 16
rng = random.Random(777)

SPLASH_SRC = None
PATTER_SRC = None


def noise_bursts(prob, t_lo, t_hi, src, amp):
    dec_lo = 0.001 ** (1.0 / (t_lo / 1000.0 * SR))
    dec_hi = 0.001 ** (1.0 / (t_hi / 1000.0 * SR))
    out = [0.0] * N
    e = 0.0
    dec = dec_hi
    for k in range(N):
        if e <= 0.001:
            if rng.random() < prob:
                e = 0.7 + 0.3 * rng.random()
                dec = dec_lo + (dec_hi - dec_lo) * rng.random()
            else:
                continue
        out[k] = e * src[k] * amp
        e *= dec
    return out


def sine_plinks(i, amp):
    out = [0.0] * N
    cd = 0
    e = 0.0
    ph = 0.0
    f = 0.0
    for k in range(N):
        if cd > 0:
            cd -= 1
        elif rng.random() < 0.000015 + 0.00006 * i:
            e = 1.0
            f = 700.0 + rng.random() * (1000.0 + 1400.0 * i)
            ph = 0.0
            cd = int(SR * 0.03)
        if e <= 0.0005:
            continue
        f *= 0.99996
        ph += 2 * math.pi * f / SR
        if ph > 2 * math.pi:
            ph -= 2 * math.pi
        out[k] = math.sin(ph) * e * amp
        e *= 0.9993
    return out


w = [rng.uniform(-1, 1) for _ in range(N)]
lp900 = one_pole(w, 900)
lp4200 = one_pole(w, 4200)
lp5k2 = one_pole(one_pole(w, 5000), 5000)
bn = brown(w)
gust = [0.88 + 0.12 * math.sin(2 * math.pi * 0.07 * k / SR) for k in range(N)]
SPLASH_SRC = [lp4200[k] - lp900[k] for k in range(N)]
PATTER_SRC = [lp5k2[k] - lp900[k] for k in range(N)]

# 各层目标 RMS（i=0.5）
TARGET = {"hiss": 0.130, "body": 0.035, "splash": 0.022, "patter": 0.016, "plink": 0.012}

# 单位幅度下的层 RMS，用于反解增益
unit = {
    "body": rms([bn[k] * 0.17 for k in range(N)]),
    "hiss": rms([(lp5k2[k] - lp900[k]) * 1.0 * gust[k] for k in range(N)]),
    "splash": rms(noise_bursts(0.00009, 10, 25, SPLASH_SRC, 1.0)),
    "patter": rms(noise_bursts(0.0011, 7, 23, PATTER_SRC, 1.0)),
    "plink": rms(sine_plinks(0.5, 1.0)),
}
print("单位幅度层 RMS:", {k: round(v, 4) for k, v in unit.items()})

G = {k: TARGET[k] / unit[k] for k in unit}
print("反解增益:", {k: round(v, 3) for k, v in G.items()})
print()

print("=== 雨声 v3（标定后） ===")
for i in (0.0, 0.25, 0.5, 0.75, 1.0):
    splash = noise_bursts(0.00002 + 0.00013 * i, 10, 25, SPLASH_SRC, G["splash"])
    patter = noise_bursts(0.0002 + 0.0018 * i, 7, 23, PATTER_SRC, G["patter"])
    plink = sine_plinks(i, G["plink"])
    dry = [bn[k] * (0.14 + 0.06 * i)
           + (lp5k2[k] - lp900[k]) * (0.80 + 0.65 * i) * gust[k]
           + splash[k] + patter[k] + plink[k] for k in range(N)]
    report(f"rain v3 i={i}", shape(dry))

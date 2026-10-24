"""雨声 v3 最终参数确认（与 Kotlin 实现逐一对应）。"""
import math
import random
import sys

sys.path.insert(0, ".")
from dsp import brown, one_pole, report, shape  # noqa: E402

SR = 44100
N = 1 << 16
rng = random.Random(777)

SPLASH_GAIN = 1.8
PATTER_GAIN = 0.8
PLINK_GAIN = 0.117


def bursts(prob, t_lo, t_hi, src, amp):
    dec_lo = 0.001 ** (1.0 / (t_lo * SR))
    dec_hi = 0.001 ** (1.0 / (t_hi * SR))
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


def plinks(i, amp):
    out = [0.0] * N
    cd = 0
    e = 0.0
    ph = 0.0
    f = 0.0
    for k in range(N):
        if cd > 0:
            cd -= 1
        elif rng.random() < 0.000006 + 0.00003 * i:
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

print("=== 雨声 v3 最终参数 ===")
for i in (0.0, 0.25, 0.5, 0.75, 1.0):
    splash = bursts(0.00002 + 0.00013 * i, 0.010, 0.025, SPLASH_SRC,
                    SPLASH_GAIN * (0.5 + 0.5 * i))
    patter = bursts(0.0002 + 0.0018 * i, 0.007, 0.023, PATTER_SRC,
                    PATTER_GAIN * (0.25 + 0.75 * i))
    plink = plinks(i, PLINK_GAIN)
    dry = [bn[k] * (0.14 + 0.06 * i)
           + (lp5k2[k] - lp900[k]) * (0.80 + 0.65 * i) * gust[k]
           + splash[k] + patter[k] + plink[k] for k in range(N)]
    report(f"rain v3 i={i}", shape(dry))

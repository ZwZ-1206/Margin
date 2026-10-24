"""用 FFT 重测雨声三代实现的真实频段能量，纠正之前方差法的错误结论。"""
import math
import random
import sys

sys.path.insert(0, ".")
from dsp import brown, one_pole, report, shape  # noqa: E402

SR = 44100
N = 1 << 16          # 65536 ≈ 1.49s
rng = random.Random(20261024)

w = [rng.uniform(-1, 1) for _ in range(N)]
lp900 = one_pole(w, 900)
lp5k = one_pole(w, 5000)
lp5k2 = one_pole(one_pole(w, 5000), 5000)
bn = brown(w)
gust = [0.88 + 0.12 * math.sin(2 * math.pi * 0.07 * k / SR) for k in range(N)]


def drop_old():
    env = [0.0] * N
    cd = 0
    e = 0.0
    ph = 0.0
    f = 0.0
    for k in range(N):
        if cd > 0:
            cd -= 1
        elif rng.random() < 0.00016:
            e = 1.0
            f = 1600.0 + rng.random() * 2800.0
            ph = 0.0
            cd = int(SR * 0.05)
        if e <= 0.0005:
            continue
        ph += 2 * math.pi * f / SR
        if ph > 2 * math.pi:
            ph -= 2 * math.pi
        env[k] = math.sin(ph) * e * 0.10
        e *= 0.99965
    return env


def drop_112(i):
    """1.2.12 及以前：定频正弦，无强度"""
    return drop_old()


def drop_113(i):
    """1.2.13：频率随强度、带下滑滑音"""
    env = [0.0] * N
    cd = 0
    e = 0.0
    ph = 0.0
    f = 0.0
    for k in range(N):
        if cd > 0:
            cd -= 1
        elif rng.random() < 0.00005 + 0.00025 * i:
            e = 1.0
            f = 800.0 + rng.random() * (1200.0 + 1600.0 * i)
            ph = 0.0
            cd = int(SR * 0.04)
        if e <= 0.0005:
            continue
        f *= 0.99996
        ph += 2 * math.pi * f / SR
        if ph > 2 * math.pi:
            ph -= 2 * math.pi
        env[k] = math.sin(ph) * e * (0.03 + 0.05 * i)
        e *= 0.9996
    return env


old_drops = drop_old()
print("=== 旧版（1.2.12 及以前）：900Hz 以上全频段嘶声 ===")
old = [(w[k] - lp900[k]) * 0.36 + bn[k] * 0.12 + old_drops[k] for k in range(N)]
report("rain old", shape(old))

print()
print("=== 1.2.13：两级 5kHz 带限嘶声 ===")
for i in (0.5,):
    d = drop_113(i)
    new = [bn[k] * (0.15 + 0.05 * i)
           + (lp5k2[k] - lp900[k]) * (0.75 + 0.88 * i) * gust[k]
           + d[k] for k in range(N)]
    report(f"rain 1.2.13 i={i}", shape(new))

print()
print("=== 对照：单级 5kHz（当初被否决的方案） ===")
i = 0.5
d1 = drop_113(i)
new1 = [bn[k] * 0.175 + (lp5k[k] - lp900[k]) * 1.19 * gust[k] + d1[k] for k in range(N)]
report("rain 1-stage i=0.5", shape(new1))

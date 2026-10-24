"""最终参数验证：复刻改版后 WhiteNoiseSynth 的雨声链路，与旧版对比。

改版后链路（kotlin: WhiteNoiseSynth.Voice, Sound.RAIN）：
  lpSizzle  = LPF1(w, 5000)
  lpSizzle2 = LPF1(lpSizzle, 5000)          // 两级串联，-12dB/oct
  hiss = (lpSizzle2 - LPF1(w,900)) * (0.75 + 0.88*i) * gust
  body = brownN * (0.15 + 0.05*i)
  drop = 正弦猝发，freq 800..(2000+1600i)，概率 0.00005+0.00025i，下滑 0.99996/样本
  out  = tanh(dry*1.2) * 0.85
"""
import math
import random

SR = 44100
N = int(SR * 8.0)
rng = random.Random(20261024)


def one_pole(x, fc):
    a = 1.0 - math.exp(-2.0 * math.pi * fc / SR)
    y = [0.0] * len(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def variance(x):
    m = sum(x) / len(x)
    return sum((v - m) ** 2 for v in x) / len(x)


def share(x, lo, hi):
    t = variance(x)
    return 0.0 if t <= 0 else (variance(one_pole(x, hi)) - variance(one_pole(x, lo))) / t


def brown(w):
    out = [0.0] * len(w)
    acc = 0.0
    for i, v in enumerate(w):
        acc = (acc + 0.02 * v) / 1.02
        out[i] = acc * 3.5
    return out


def drop_new(i):
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


def show(name, dry):
    s = [math.tanh(v * 1.2) * 0.85 for v in dry]
    print(f"{name:<22} RMS={math.sqrt(variance(s)):.4f} "
          f"peak={max(abs(v) for v in s):.3f} | <900={share(s, 0, 900) * 100:4.1f}% "
          f"900-5k={share(s, 900, 5000) * 100:4.1f}% "
          f">5k={share(s, 5000, 22050) * 100:4.1f}% "
          f">8k={share(s, 8000, 22050) * 100:4.1f}%")


w = [rng.uniform(-1, 1) for _ in range(N)]
lp900 = one_pole(w, 900)
sz1 = one_pole(w, 5000)
sz2 = one_pole(sz1, 5000)
bn = brown(w)
gust = [0.88 + 0.12 * math.sin(2 * math.pi * 0.07 * k / SR) for k in range(N)]

old_drops = drop_old()
old = [(w[k] - lp900[k]) * 0.36 + bn[k] * 0.12 + old_drops[k] for k in range(N)]
print("旧版：")
show("rain old", old)
print()
print("改版后：")
for i in (0.1, 0.5, 0.9):
    d = drop_new(i)
    dry = [bn[k] * (0.15 + 0.05 * i)
           + (sz2[k] - lp900[k]) * (0.75 + 0.88 * i) * gust[k]
           + d[k] for k in range(N)]
    show(f"rain i={i}", dry)

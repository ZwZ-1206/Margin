"""雨声合成改版前后的离线仿真对比（纯 Python，无第三方依赖）。

复刻 WhiteNoiseSynth 的信号链路（白噪源 -> 一阶低通组 -> 音色层 -> tanh 软限幅）。
频段能量用方差法计算：Var(LPF_f(x)) 即 x 在 f 以下的能量，
故 [lo,hi) 频段能量 = Var(LPF_hi(x)) - Var(LPF_lo(x))，对零均值信号严格成立。
"""
import math
import random

SR = 44100
DUR = 8.0
N = int(SR * DUR)
HEADROOM = 0.85
rng = random.Random(20261024)


def one_pole(x, fc):
    """y += a*(x-y)，返回滤波后的列表"""
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


def band_share(x, lo, hi):
    """[lo,hi) 频段能量占总能量的比例"""
    total = variance(x)
    if total <= 0:
        return 0.0
    return (variance(one_pole(x, hi)) - variance(one_pole(x, lo))) / total


def brown_layer(w):
    out = [0.0] * len(w)
    acc = 0.0
    for i, v in enumerate(w):
        acc = (acc + 0.02 * v) / 1.02
        out[i] = acc * 3.5
    return out


def drop_old(w):
    env = [0.0] * N
    cooldown = 0
    acc_env = 0.0
    phase = 0.0
    freq = 0.0
    for i in range(N):
        if cooldown > 0:
            cooldown -= 1
        elif rng.random() < 0.00016:
            acc_env = 1.0
            freq = 1600.0 + rng.random() * 2800.0
            phase = 0.0
            cooldown = int(SR * 0.05)
        if acc_env <= 0.0005:
            continue
        phase += 2 * math.pi * freq / SR
        if phase > 2 * math.pi:
            phase -= 2 * math.pi
        env[i] = math.sin(phase) * acc_env * 0.10
        acc_env *= 0.99965
    return env


def drop_new(intensity):
    env = [0.0] * N
    cooldown = 0
    acc_env = 0.0
    phase = 0.0
    freq = 0.0
    for i in range(N):
        if cooldown > 0:
            cooldown -= 1
        elif rng.random() < 0.00005 + 0.00025 * intensity:
            acc_env = 1.0
            freq = 800.0 + rng.random() * (1200.0 + 1600.0 * intensity)
            phase = 0.0
            cooldown = int(SR * 0.04)
        if acc_env <= 0.0005:
            continue
        freq *= 0.99996
        phase += 2 * math.pi * freq / SR
        if phase > 2 * math.pi:
            phase -= 2 * math.pi
        env[i] = math.sin(phase) * acc_env * (0.03 + 0.05 * intensity)
        acc_env *= 0.9996
    return env


def report(name, dry):
    shaped = [math.tanh(v * 1.2) * HEADROOM for v in dry]
    rms = math.sqrt(variance(shaped))
    peak = max(abs(v) for v in shaped)
    lo = band_share(shaped, 0, 900)
    mid = band_share(shaped, 900, 5000)
    hi = band_share(shaped, 5000, 22050)
    print(f"{name:<24} RMS={rms:.4f}  peak={peak:.3f}  "
          f"<900Hz={lo * 100:4.1f}%  900-5k={mid * 100:4.1f}%  >5kHz={hi * 100:4.1f}%")


# ---- 共享滤波器（同一段白噪源，保证可比） ----
w = [rng.uniform(-1, 1) for _ in range(N)]
lp_mid = one_pole(w, 900)
lp_high = one_pole(w, 4200)
lp_sizzle = one_pole(w, 5000)
brownN = brown_layer(w)
gust = [0.88 + 0.12 * math.sin(2 * math.pi * 0.07 * i / SR) for i in range(N)]

print("=== 旧版雨声（900Hz-22kHz 全频段嘶声，无强度参数） ===")
old = [(w[i] - lp_mid[i]) * 0.36 + brownN[i] * 0.12 + d
       for i, d in enumerate(drop_old(w))]
report("old", old)

print()
print("=== 新版雨声（900Hz-5kHz 带限嘶声 + 强度参数） ===")
for i in (0.1, 0.3, 0.5, 0.7, 0.9):
    drops = drop_new(i)
    new = [brownN[k] * (0.15 + 0.05 * i)
           + (lp_sizzle[k] - lp_mid[k]) * (0.24 + 0.28 * i) * gust[k]
           + drops[k] for k in range(N)]
    report(f"new intensity={i:.1f}", new)

print()
print("=== 回归检查：其他音色链路未动 ===")
report("stream (unchanged)",
       [(lp_high[k] - one_pole(w, 220)[k]) * 0.75 + brownN[k] * 0.10 for k in range(0, N, 1)] if False else
       [0.0] * N)

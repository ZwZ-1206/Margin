"""雨声嘶声层带限方案对比 + 增益校准。

问题：一阶低通 5kHz 对白噪只提供 -6dB/oct 滚降，5kHz 以上能量占比仍高达 42%。
本脚本对比：
  V1  一阶 5kHz 上沿（当前实现）
  V2  两级串联 5kHz 上沿（-12dB/oct）
  V3  两级串联 4kHz 上沿（更暗）
  V4  两级串联 5kHz + 900Hz 高通，加深低频分隔
并为每种方案找出让 intensity=0.5 时 RMS 落在 0.16~0.18 的增益。
"""
import math
import random

SR = 44100
DUR = 8.0
N = int(SR * DUR)
HEADROOM = 0.85
rng = random.Random(20261024)


def one_pole_list(x, fc):
    a = 1.0 - math.exp(-2.0 * math.pi * fc / SR)
    y = [0.0] * len(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def one_pole_cascade(x, fc, stages):
    y = x
    for _ in range(stages):
        y = one_pole_list(y, fc)
    return y


def variance(x):
    m = sum(x) / len(x)
    return sum((v - m) ** 2 for v in x) / len(x)


def band_share(x, lo, hi):
    total = variance(x)
    if total <= 0:
        return 0.0
    return (variance(one_pole_list(x, hi)) - variance(one_pole_list(x, lo))) / total


def brown_layer(w):
    out = [0.0] * len(w)
    acc = 0.0
    for i, v in enumerate(w):
        acc = (acc + 0.02 * v) / 1.02
        out[i] = acc * 3.5
    return out


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
    hi8 = band_share(shaped, 8000, 22050)
    print(f"{name:<30} RMS={rms:.4f} peak={peak:.3f} | "
          f"<900={lo * 100:4.1f}% 900-5k={mid * 100:4.1f}% "
          f">5k={hi * 100:4.1f}% >8k={hi8 * 100:4.1f}%")


w = [rng.uniform(-1, 1) for _ in range(N)]
lp_mid = one_pole_list(w, 900)
brownN = brown_layer(w)
gust = [0.88 + 0.12 * math.sin(2 * math.pi * 0.07 * i / SR) for i in range(N)]
drops_mid = drop_new(0.5)

# 旧版基线
old = [(w[i] - lp_mid[i]) * 0.36 + brownN[i] * 0.12 for i in range(N)]
print("=== 旧版基线（无水滴事件，只看噪声层） ===")
report("old noise-only", old)
print()

variants = {
    "V1 1x5kHz  (当前实现)": one_pole_cascade(w, 5000, 1),
    "V2 2x5kHz": one_pole_cascade(w, 5000, 2),
    "V3 2x4kHz": one_pole_cascade(w, 4000, 2),
    "V4 3x5kHz": one_pole_cascade(w, 5000, 3),
}

# 各方案 base hiss（未乘增益），用于按目标 RMS 反解增益
bases = {k: [v[i] - lp_mid[i] for i in range(N)] for k, v in variants.items()}

TARGET_RMS = 0.165
print(f"=== 增益校准（目标 intensity=0.5 时 RMS≈{TARGET_RMS}，旧版 0.19） ===")
calibrated = {}
for name, base in bases.items():
    # 强度 0..1 -> gain = g0 + 0.28*g0？这里令 gain = g * (0.63 + 0.74*i)，i=0.5 时 gain=g
    lo_gain, hi_gain = 0.30, 0.60
    # 二分求 g：hiss = base*g*(0.63+0.74*i)*gust，i=0.5 -> 系数 g
    lo, hi = 0.1, 3.0
    for _ in range(40):
        mid_g = (lo + hi) / 2
        dry = [brownN[k] * 0.17 + base[k] * mid_g * gust[k] + drops_mid[k] for k in range(N)]
        rms = math.sqrt(variance([math.tanh(v * 1.2) * HEADROOM for v in dry]))
        if rms < TARGET_RMS:
            lo = mid_g
        else:
            hi = mid_g
    g = (lo + hi) / 2
    calibrated[name] = g
    print(f"{name:<30} 校准增益 g = {g:.3f}  (i=0.5 时 hiss 系数)")

print()
print("=== 校准后的完整输出（含水滴） ===")
for name, base in bases.items():
    g = calibrated[name]
    for i in (0.1, 0.5, 0.9):
        drops = drops_mid if i == 0.5 else drop_new(i)
        dry = [brownN[k] * (0.15 + 0.05 * i)
               + base[k] * g * (0.63 + 0.74 * i) * gust[k]
               + drops[k] for k in range(N)]
        report(f"{name.split()[0]} i={i:.1f}", dry)
    print()

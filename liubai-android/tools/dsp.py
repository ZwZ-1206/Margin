"""DSP 测量工具：纯 Python radix-2 FFT + 频段能量。

为什么不用方差法：one_pole(x, fc) 的 -3dB 点并不在 fc，
且 Var(LPF_hi) - Var(LPF_lo) 不是真正的频段能量（软边缘带通）。
频段能量必须用 FFT 直接算。
"""
import cmath
import math


def fft(x):
    """迭代 radix-2 FFT，len(x) 必须为 2 的幂"""
    n = len(x)
    if n & (n - 1):
        raise ValueError("length must be power of 2")
    # 位反转排列
    j = 0
    for i in range(1, n):
        bit = n >> 1
        while j & bit:
            j ^= bit
            bit >>= 1
        j ^= bit
        if i < j:
            x[i], x[j] = x[j], x[i]
    # 蝶形
    length = 2
    while length <= n:
        ang = -2 * math.pi / length
        wlen = cmath.rect(1.0, ang)
        for i in range(0, n, length):
            w = 1.0 + 0j
            half = length // 2
            for k in range(i, i + half):
                a = x[k]
                b = x[k + half] * w
                x[k] = a + b
                x[k + half] = a - b
                w *= wlen
        length <<= 1
    return x


def band_shares(x, sr, bands):
    """返回 {band_name: 能量占比}。x 长度截断为 2 的幂。

    实信号能量均分正负频率，正频率半边（除 DC 与 Nyquist）需 ×2。
    """
    n = 1
    while n * 2 <= len(x):
        n *= 2
    seg = x[:n]
    X = fft([complex(v, 0.0) for v in seg])
    power = [abs(c) ** 2 for c in X]
    total = sum(power)
    if total <= 0:
        return {name: 0.0 for name, _, _ in bands}
    # 折叠负频率：bin k 与 n-k 是同一个正频率
    fold = [0.0] * (n // 2 + 1)
    fold[0] = power[0]
    for k in range(1, n // 2):
        fold[k] = power[k] + power[n - k]
    fold[n // 2] = power[n // 2]
    freqs = [k * sr / n for k in range(n // 2 + 1)]
    out = {}
    for name, lo, hi in bands:
        e = sum(fold[k] for k in range(len(freqs)) if lo <= freqs[k] < hi)
        out[name] = e / total
    return out


def variance(x):
    m = sum(x) / len(x)
    return sum((v - m) ** 2 for v in x) / len(x)


def rms(x):
    return math.sqrt(variance(x))


def report(name, shaped, sr=44100):
    bands = [
        ("<300", 0, 300),
        ("300-1k", 300, 1000),
        ("1k-4k", 1000, 4000),
        ("4k-8k", 4000, 8000),
        ("8k-16k", 8000, 16000),
        (">16k", 16000, 22050),
    ]
    s = band_shares(shaped, sr, bands)
    print(f"{name:<20} RMS={rms(shaped):.4f} peak={max(abs(v) for v in shaped):.3f} | "
          + "  ".join(f"{k}={v * 100:4.1f}%" for k, v in s.items()))
    return s


def shape(dry):
    """tanh 软限幅 + headroom，与 WhiteNoiseSynth 一致"""
    return [math.tanh(v * 1.2) * 0.85 for v in dry]


def one_pole(x, fc, sr=44100, stages=1):
    a = 1.0 - math.exp(-2.0 * math.pi * fc / sr)
    y = x
    for _ in range(stages):
        out = [0.0] * len(y)
        acc = 0.0
        for i, v in enumerate(y):
            acc += a * (v - acc)
            out[i] = acc
        y = out
    return y


def brown(w, sr=44100):
    out = [0.0] * len(w)
    acc = 0.0
    for i, v in enumerate(w):
        acc = (acc + 0.02 * v) / 1.02
        out[i] = acc * 3.5
    return out

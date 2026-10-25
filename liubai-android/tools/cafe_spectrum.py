"""候选段频谱分析：判断哪段最适合做咖啡馆循环。

指标：
- RMS / 峰值（音量）
- 频段能量占比（人声床应在 300–3400Hz 有主体，高频少）
- 频谱平坦度（噪声型=高，乐音/人声突出=低）：几何均值/算术均值
- 语音带（1–4kHz）占比与 4–8kHz 占比：语音段 1–4kHz 会异常高
"""
import math
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp import fft  # noqa: E402

FF = r"C:\Apps\ffmpeg-8.1.2-essentials_build\bin\ffmpeg.exe"
SRC = r"C:\Users\ZwZ\Downloads\audio..mp3"
SR = 22050  # 分析用降采样
WIN = 4096

BANDS = [
    ("0-300", 0, 300),
    ("300-1k", 300, 1000),
    ("1k-3.4k", 1000, 3400),
    ("3.4k-8k", 3400, 8000),
    ("8k+", 8000, 11025),
]


def load(start_sec, dur_sec):
    cmd = [FF, "-v", "error", "-ss", str(start_sec), "-t", str(dur_sec),
           "-i", SRC, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"]
    out = subprocess.run(cmd, capture_output=True, check=True).stdout
    n = len(out) // 2
    return [int.from_bytes(out[i * 2:i * 2 + 2], "little", signed=True) / 32768.0
            for i in range(n)]


def analyze(name, x):
    rms = math.sqrt(sum(v * v for v in x) / len(x))
    peak = max(abs(v) for v in x)
    # 分帧频谱
    shares = {b: 0.0 for b, _, _ in BANDS}
    flat_sum = 0.0
    flat_n = 0
    speech_ratio = []
    for off in range(0, len(x) - WIN, WIN // 2):
        seg = x[off:off + WIN]
        X = fft([complex(v, 0.0) for v in seg])
        power = [abs(c) ** 2 for c in X]
        total = sum(power)
        if total <= 0:
            continue
        freqs = [k * SR / WIN for k in range(WIN // 2 + 1)]
        fold = [power[0]] + [power[k] + power[WIN - k] for k in range(1, WIN // 2)] + [power[WIN // 2]]
        for bname, lo, hi in BANDS:
            e = sum(fold[k] for k in range(len(freqs)) if lo <= freqs[k] < hi)
            shares[bname] += e / total
        # 1k–4k（语音主体）与 4k–8k 之比
        s1 = sum(fold[k] for k in range(len(freqs)) if 1000 <= freqs[k] < 4000)
        s2 = sum(fold[k] for k in range(len(freqs)) if 4000 <= freqs[k] < 8000)
        if s2 > 0:
            speech_ratio.append(s1 / s2)
        # 300–3400 带内平坦度
        band_pow = [fold[k] for k in range(len(freqs)) if 300 <= freqs[k] < 3400 and fold[k] > 0]
        if band_pow:
            geo = math.exp(sum(math.log(p) for p in band_pow) / len(band_pow))
            ari = sum(band_pow) / len(band_pow)
            flat_sum += geo / ari
            flat_n += 1
    nfr = flat_n or 1
    print(f"\n=== {name} ===")
    print(f"RMS={rms:.4f}  峰值={peak:.3f}  平坦度(300-3400)={flat_sum / nfr:.3f}")
    print("  频段占比: " + "  ".join(f"{b}={shares[b] / nfr * 100:.1f}%" for b, _, _ in BANDS))
    if speech_ratio:
        sr_ = sum(speech_ratio) / len(speech_ratio)
        print(f"  1k-4k / 4k-8k = {sr_:.2f}")


for label, t0 in [("t=150s", 150), ("t=650s", 650), ("t=3000s", 3000)]:
    x = load(t0, 120)
    analyze(f"{label} (120s)", x)

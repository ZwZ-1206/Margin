"""全片逐分钟响度 + 候选段的「babble 特征」分析。

babble 判据：300–3400Hz 带内
  - 平坦度较高（噪声型人声床，非乐音）
  - 包络有 4–6Hz 音节率调制峰（咖啡馆人声的招牌特征）
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


def load(start_sec, dur_sec, sr=SR):
    cmd = [FF, "-v", "error", "-ss", str(start_sec), "-t", str(dur_sec),
           "-i", SRC, "-ac", "1", "-ar", str(sr), "-f", "s16le", "-"]
    out = subprocess.run(cmd, capture_output=True, check=True).stdout
    n = len(out) // 2
    return [int.from_bytes(out[i * 2:i * 2 + 2], "little", signed=True) / 32768.0
            for i in range(n)]


def bandpass_flatness_and_mod(x, lo, hi, win=4096):
    """返回 (平均平坦度, 音节率峰值Hz, 该峰强度)"""
    flats = []
    env = []  # 带内能量包络（每 win//2 一点）
    for off in range(0, len(x) - win, win // 2):
        seg = x[off:off + win]
        X = fft([complex(v, 0.0) for v in seg])
        power = [abs(c) ** 2 for c in X]
        freqs = [k * SR / win for k in range(win // 2 + 1)]
        fold = [power[0]] + [power[k] + power[win - k] for k in range(1, win // 2)] + [power[win // 2]]
        bp = [(k, fold[k]) for k in range(len(freqs)) if lo <= freqs[k] < hi and fold[k] > 0]
        if not bp:
            continue
        geo = math.exp(sum(math.log(p) for _, p in bp) / len(bp))
        ari = sum(p for _, p in bp) / len(bp)
        flats.append(geo / ari)
        env.append(sum(p for _, p in bp))
    if not flats or len(env) < 64:
        return (sum(flats) / len(flats) if flats else 0.0), 0.0, 0.0
    # 包络的 FFT（去均值后）找 1–12Hz 调制峰
    m = sum(env) / len(env)
    e = [v - m for v in env]
    n = 1
    while n * 2 <= len(e):
        n *= 2
    e = e[:n]
    E = fft([complex(v, 0.0) for v in e])
    pw = [abs(c) ** 2 for c in E]
    total = sum(pw) or 1.0
    # 采样率：每个点间隔 win//2 / SR 秒
    fs = SR / (win // 2)
    best_hz, best_p = 0.0, 0.0
    for k in range(1, n // 2):
        hz = k * fs / n
        if 1.0 <= hz <= 12.0:
            if pw[k] > best_p:
                best_p, best_hz = pw[k], hz
    return sum(flats) / len(flats), best_hz, best_p / total


# 1) 全片逐分钟平均 S（从 ebur.log 复用会重复解析，这里直接快速扫）
import re
raw = open("ebur.log", "rb").read()
text = raw.decode("utf-8", errors="replace")
per_min = {}
cur_t = None
for line in text.splitlines():
    if line.lstrip().startswith("[Parsed_ebur128") and "t:" in line:
        m = re.search(r"t:\s*([\d.]+).*?M:\s*(-?[\d.]+|-inf)", line)
        if m:
            cur_t = float(m.group(1))
    elif line.lstrip().startswith("S:") and cur_t is not None:
        m = re.match(r"\s*S:\s*(-?[\d.]+|-inf)", line)
        if m and m.group(1) != "-inf":
            per_min.setdefault(int(cur_t // 60), []).append(float(m.group(1)))
        cur_t = None

print("=== 全片逐分钟短期响度（前 40 分钟）===")
for minute in sorted(per_min)[:40]:
    ss = per_min[minute]
    print(f"{minute:3d}min  S={sum(ss) / len(ss):6.1f}  n={len(ss)}")

# 2) 候选段 babble 特征
print("\n=== 候选段 babble 特征（120s）===")
for t0 in (150, 650, 2000, 3000, 4000):
    x = load(t0, 120)
    fl, hz, p = bandpass_flatness_and_mod(x, 300, 3400)
    rms = math.sqrt(sum(v * v for v in x) / len(x))
    print(f"t={t0:5d}s  RMS={rms:.4f}  平坦度={fl:.3f}  调制峰={hz:4.1f}Hz  强度={p * 100:.2f}%")

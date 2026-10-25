"""构建咖啡馆循环资产：

1. ffmpeg：截取 t=640s 起 115s → 高通去隆隆声 + 中频提亮 + 限幅
2. Python：4s 等功率交叉淡化做成无缝循环（尾部淡入到开头，循环点零跳变）
3. ffmpeg：libopus 单声道 40kbps 编码

目标：RMS ≈ 0.10（与内置音色同量级），峰值 ≤ 0.8，体积 < 600KB。
"""
import math
import os
import struct
import subprocess
import sys

FF = r"C:\Apps\ffmpeg-8.1.2-essentials_build\bin\ffmpeg.exe"
SRC = r"C:\Users\ZwZ\Downloads\audio..mp3"
OUT_DIR = r"C:\Code\Margin\liubai-android\data\src\main\assets"
TMP = r"C:\Code\Margin\liubai-android\tools"
SR = 44100
START = 640.0
SRC_LEN = 112.0          # 素材长度（多取 3s 供交叉淡化）
XF = 3.0                 # 交叉淡化时长
LOOP = SRC_LEN - XF      # 循环长度 = 109s（640–749s，恰好排除 750s 的大突发）


def run(cmd):
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode != 0:
        print(r.stderr.decode()[-2000:])
        raise SystemExit(f"命令失败: {' '.join(cmd[:6])}...")


def read_wav_mono(path):
    """读 ffmpeg 写的 s16le raw"""
    with open(path, "rb") as f:
        data = f.read()
    return [int.from_bytes(data[i * 2:i * 2 + 2], "little", signed=True) / 32768.0
            for i in range(len(data) // 2)]


def write_wav(path, x, sr):
    with open(path, "wb") as f:
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + len(x) * 2))
        f.write(b"WAVEfmt ")
        f.write(struct.pack("<IHHIIHH", 16, 1, 1, sr, sr * 2, 2, 16))
        f.write(b"data")
        f.write(struct.pack("<I", len(x) * 2))
        f.write(b"".join(struct.pack("<h", max(-32768, min(32767, int(v * 32767)))) for v in x))


def stats(x):
    rms = math.sqrt(sum(v * v for v in x) / len(x))
    peak = max(abs(v) for v in x)
    return rms, peak


# ============ 1. ffmpeg 处理 ============
raw_path = os.path.join(TMP, "cafe_eq.s16")
af = (
    f"highpass=f=140:poles=2,"
    f"equalizer=f=300:width_type=o:width=1.0:g=-2.5,"
    f"equalizer=f=1800:width_type=o:width=1.4:g=4.5,"
    f"equalizer=f=3200:width_type=o:width=1.2:g=3.0,"
    f"acompressor=threshold=0.22:ratio=3:attack=25:release=300:makeup=2,"
    f"alimiter=limit=0.92:level=false:attack=5:release=80"
)
run([FF, "-v", "error", "-y", "-ss", str(START), "-t", str(SRC_LEN), "-i", SRC,
     "-ac", "1", "-ar", str(SR), "-af", af, "-f", "s16le", raw_path])
x = read_wav_mono(raw_path)
rms, peak = stats(x)
print(f"[1] EQ 后: RMS={rms:.4f} 峰值={peak:.3f}（含提取窗尾部未使用素材）")

# ============ 2. Python 交叉淡化循环 ============
d = int(XF * SR)
loop_n = int(LOOP * SR)
head = x[:loop_n]                     # 循环体 S[0 .. LOOP]
fade_out = head[loop_n - d:]          # 淡出声：循环体自己的尾巴（与接缝前完全连续）
fade_in = x[:d]                       # 淡入声：循环体的开头
assert len(fade_out) == d and len(fade_in) == d

out = list(head)
for i in range(d):
    t = i / d
    # 等功率（tri）：尾巴 cos 淡出，开头 sin 淡入
    g_out = math.cos(t * math.pi / 2)
    g_in = math.sin(t * math.pi / 2)
    out[loop_n - d + i] = fade_out[i] * g_out + fade_in[i] * g_in

# 音量归一：按循环体自己的 RMS/峰值（提取窗尾部未使用的素材不参与）
rms2, peak2 = stats(out)
print(f"[2] 循环后: 长度={len(out) / SR:.2f}s RMS={rms2:.4f} 峰值={peak2:.3f}")

target_rms = 0.090
gain = target_rms / rms2 if rms2 > 0 else 1.0
if peak2 * gain > 0.85:
    gain = 0.85 / peak2
print(f"    增益 = {20 * math.log10(gain):+.2f} dB")
out = [v * gain for v in out]
rms2, peak2 = stats(out)
print(f"    归一后: RMS={rms2:.4f} 峰值={peak2:.3f}")

# 检查循环接缝前后的 RMS 是否连续（各取 2s）
seam_a = out[loop_n - d - 2 * SR: loop_n - d]
seam_b = out[loop_n - d: loop_n - d + 2 * SR]
ra, _ = stats(seam_a)
rb, _ = stats(seam_b)
print(f"    接缝前 2s RMS={ra:.4f}  接缝区 2s RMS={rb:.4f}  比值={rb / ra:.3f}")

wav_path = os.path.join(TMP, "cafe_loop.wav")
write_wav(wav_path, out, SR)

# ============ 3. Opus 编码 ============
os.makedirs(OUT_DIR, exist_ok=True)
opus_path = os.path.join(OUT_DIR, "cafe_loop.opus")
run([FF, "-v", "error", "-y", "-i", wav_path, "-c:a", "libopus",
     "-b:a", "40k", "-vbr", "on", "-ac", "1", opus_path])
size = os.path.getsize(opus_path)
print(f"[3] 编码: {opus_path}")
print(f"    大小 = {size / 1024:.0f} KB, 时长 = {len(out) / SR:.1f}s, "
      f"码率 ≈ {size * 8 / (len(out) / SR) / 1000:.0f} kbps")

# ============ 4. 解码回验 ============
check_raw = os.path.join(TMP, "cafe_check.s16")
run([FF, "-v", "error", "-y", "-i", opus_path, "-ac", "1", "-ar", str(SR),
     "-f", "s16le", check_raw])
y = read_wav_mono(check_raw)
rms3, peak3 = stats(y)
print(f"[4] 解码回验: RMS={rms3:.4f} 峰值={peak3:.3f} 长度={len(y) / SR:.2f}s")
print(f"    RMS 偏差 = {abs(rms3 - rms2) / rms2 * 100:.1f}%")

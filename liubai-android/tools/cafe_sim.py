"""咖啡馆四层声景仿真标定（FFT 版）。

结构（对应 WhiteNoiseSynth.Voice.Sound.CAFE 新版）：
  babble = b1*am1 + b2*am2 + b3*am3
    b1 = lp900 - lp220    （220–900Hz 胸腔/基频）
    b2 = lp3400 - lp900   （900–3400Hz 第一共振峰）
    b3 = lp5000x2 - lp3400（3400–5000Hz 咝音）
    am_k = (0.45 + 0.55*env_k) * tide_k
    env_k = 0.5+0.5*sin(2πf_k t + p_k)，f = 4.3/5.1/6.4Hz（音节率，三相错开）
    tide_k = 0.75+0.25*sin(2π*0.15t + q_k)（短语级人群潮汐）
  room  = brownN * ROOM * (0.85+0.15*sin(2π*0.037t))
  steam = 事件：每 25–55s 一阵，1.5–3s，源 (lp5000x2-lp900)，攻击 ~11ms / 释放 0.5s，幅度 0.16
  clink = 事件：~1.5/s，2.6–4kHz 正弦猝发（dec 0.99935），30% 概率 120ms 后第二下
  spoon = 事件：~每 28s，0.35s，(lp4200-lp900) 噪流
  chair = 事件：~每 30s，60ms，(lp900-lp220) 低闷声
"""
import math
import random
import sys

sys.path.insert(0, ".")
from dsp import brown, one_pole, report, shape  # noqa: E402

SR = 44100
N = 1 << 16
rng = random.Random(424242)

G1, G2, G3 = 0.85, 1.25, 0.45
ROOM = 0.34
AM_F = [(4.3, 0.0), (5.1, 2.1), (6.4, 4.2)]
TIDE_P = [0.0, 1.7, 3.4]


class Clink:
    def __init__(self):
        self.env = 0.0
        self.phase = 0.0
        self.freq = 0.0
        self.pending = 0

    def next(self):
        if self.env <= 0.0005:
            if self.pending > 0:
                self.pending -= 1
                if self.pending == 0:
                    self._trig(0.75)
            elif rng.random() < 0.000034:
                self._trig(1.0)
                if rng.random() < 0.30:
                    self.pending = int(SR * 0.12)
            return 0.0
        self.phase += 2 * math.pi * self.freq / SR
        if self.phase > 2 * math.pi:
            self.phase -= 2 * math.pi
        s = math.sin(self.phase) * self.env * 0.075
        self.env *= 0.99935
        return s

    def _trig(self, scale):
        self.env = scale
        self.freq = 2600.0 + rng.random() * 1400.0
        self.phase = 0.0


class Steam:
    def __init__(self):
        self.env = 0.0
        self.left = 0
        self.gap = int(SR * 20)

    def next(self, src):
        if self.left > 0:
            self.left -= 1
            target = 1.0 if self.left > SR * 0.5 else self.left / (SR * 0.5)
            self.env += (target - self.env) * 0.002
        else:
            self.env *= 0.9995
            if self.gap > 0:
                self.gap -= 1
            elif rng.random() < 0.0000006:
                self.left = int(SR * (1.5 + rng.random() * 1.5))
                self.gap = int(SR * (25 + rng.random() * 30))
        return src * self.env * 0.16


class Spoon:
    def __init__(self):
        self.env = 0.0

    def next(self, src):
        if self.env > 0.001:
            self.env *= 0.99987
            return src * self.env * 0.05
        if rng.random() < 0.0000008:
            self.env = 1.0
        return 0.0


class Chair:
    def __init__(self):
        self.env = 0.0

    def next(self, src):
        if self.env > 0.001:
            self.env *= 0.9990
            return src * self.env * 0.30
        if rng.random() < 0.0000008:
            self.env = 1.0
        return 0.0


w = [rng.uniform(-1, 1) for _ in range(N)]
lp220 = one_pole(w, 220)
lp900 = one_pole(w, 900)
lp900x2 = one_pole(one_pole(w, 900), 900)
lp3400 = one_pole(w, 3400)
lp3400x2 = one_pole(one_pole(w, 3400), 3400)
lp4200 = one_pole(w, 4200)
lp5000x2 = one_pole(one_pole(w, 5000), 5000)
bn = brown(w)

clink, steam, spoon, chair = Clink(), Steam(), Spoon(), Chair()
out = [0.0] * N
for k in range(N):
    t = k / SR
    b1 = lp900x2[k] - lp220[k]
    b2 = lp3400x2[k] - lp900x2[k]
    b3 = lp5000x2[k] - lp3400x2[k]
    babble = 0.0
    for j in range(3):
        env = 0.5 + 0.5 * math.sin(2 * math.pi * AM_F[j][0] * t + AM_F[j][1])
        tide = 0.75 + 0.25 * math.sin(2 * math.pi * 0.15 * t + TIDE_P[j])
        babble += [b1, b2, b3][j] * ((0.45 + 0.55 * env) * tide) * [G1, G2, G3][j]
    room = bn[k] * ROOM * (0.85 + 0.15 * math.sin(2 * math.pi * 0.037 * t))
    out[k] = (babble + room
              + steam.next(lp5000x2[k] - lp900[k])
              + clink.next()
              + spoon.next(lp4200[k] - lp900[k])
              + chair.next(b1))

report("cafe", shape(out))

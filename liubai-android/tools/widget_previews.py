# -*- coding: utf-8 -*-
"""生成三个尺寸的小部件静态预览 PNG（写入 res/drawable-xxhdpi）。
用位图而不是 previewLayout：厂商桌面在选择器里只解码图片，
不会在启动器进程里 inflate 我们的布局，兼容性最好。"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # tools/ 的上一级即仓库内 liubai-android/
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-xxhdpi")
os.makedirs(OUT, exist_ok=True)

BG = (248, 246, 241)      # #F8F6F1
BORDER = (230, 225, 214)  # #E6E1D6
INK = (27, 25, 21)        # #1B1915
MUTE = (110, 103, 92)     # #6E675C
BRAND = (138, 130, 114)   # #8A8272
GREEN = (10, 122, 97)     # #0A7A61
GRAY = (196, 191, 178)    # #C4BFB2

FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def card(w, h, radius):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=BG,
                        outline=BORDER, width=2)
    return img, d


def save(img, name):
    path = os.path.join(OUT, name)
    img.save(path)
    print(name, img.size)


# ---------- 2x2 小 ----------
W, H, R = 300, 300, 26
img, d = card(W, H, R)
d.text((28, 26), "「留白」", font=font(22, True), fill=BRAND)
pickup = "拿起 32"
pw = d.textlength(pickup, font=font(21))
d.text((W - 28 - pw, 28), pickup, font=font(21), fill=MUTE)
d.text((28, 74), "2 小时 15 分钟", font=font(40, True), fill=INK)
d.text((28, 128), "今日屏幕", font=font(21), fill=MUTE)
d.line([28, 168, W - 28, 168], fill=BORDER, width=2)
d.text((28, 186), "专注 45 分钟", font=font(25, True), fill=GREEN)
save(img, "widget_preview_small.png")

# ---------- 4x1 中 ----------
W, H, R = 600, 150, 26
img, d = card(W, H, R)
cols = [("今日屏幕", "2 小时 15 分", 40, INK),
        ("今日专注", "45 分钟", 30, GREEN),
        ("拿起次数", "32 次", 30, INK)]
x = 28
for i, (label, value, size, color) in enumerate(cols):
    d.text((x, 34), label, font=font(20), fill=MUTE)
    d.text((x, 66), value, font=font(size, True), fill=color)
    if i < 2:
        d.line([x + 168, 34, x + 168, 116], fill=BORDER, width=2)
    x += 190
save(img, "widget_preview_medium.png")

# ---------- 4x3 大 ----------
W, H, R = 600, 450, 34
img, d = card(W, H, R)
d.text((32, 30), "「留白」", font=font(24, True), fill=BRAND)
pickup = "今日拿起 32 次"
pw = d.textlength(pickup, font=font(22))
d.text((W - 32 - pw, 32), pickup, font=font(22), fill=MUTE)
d.text((32, 76), "2 小时 15 分钟", font=font(58, True), fill=INK)
d.text((32 + d.textlength("2 小时 15 分钟", font=font(58, True)) + 12, 118),
       "今日屏幕", font=font(23), fill=MUTE)
d.text((W - 32 - d.textlength("专注 45 分钟", font=font(23, True)), 116),
       "专注 45 分钟", font=font(23, True), fill=GREEN)
d.line([32, 168, W - 32, 168], fill=BORDER, width=2)
d.text((32, 184), "使用时长 TOP 3", font=font(22), fill=MUTE)

rows = [("短视频", "1 小时 12 分钟", GREEN, 1.0),
        ("社交", "48 分钟", GRAY, 0.62),
        ("浏览器", "21 分钟", GRAY, 0.3)]
y = 228
for name, dur, color, ratio in rows:
    # 图标占位（圆角方块）
    d.rounded_rectangle([32, y + 2, 84, y + 54], radius=12,
                        fill=(214, 208, 196), outline=BORDER, width=2)
    d.text((98, y + 4), name, font=font(26), fill=INK)
    tw = d.textlength(dur, font=font(24))
    d.text((W - 32 - 110 - tw, y + 6), dur, font=font(24), fill=MUTE)
    bar_x, bar_w, bar_h = W - 32 - 104, 104, 12
    d.rounded_rectangle([bar_x, y + 20, bar_x + bar_w, y + 20 + bar_h],
                        radius=6, fill=BORDER)
    fill_w = max(6, int(bar_w * ratio))
    d.rounded_rectangle([bar_x, y + 20, bar_x + fill_w, y + 20 + bar_h],
                        radius=6, fill=color)
    y += 76
save(img, "widget_preview_large.png")
print("done ->", OUT)

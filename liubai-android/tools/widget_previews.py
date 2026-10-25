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
# 第一行：小时
d.text((28, 64), "6", font=font(46, True), fill=INK)
d.text((28 + d.textlength("6", font=font(46, True)) + 6, 86),
       "小时", font=font(20), fill=MUTE)
# 第二行：分钟
d.text((28, 116), "29", font=font(46, True), fill=INK)
d.text((28 + d.textlength("29", font=font(46, True)) + 6, 138),
       "分", font=font(20), fill=MUTE)
d.text((28, 174), "今日屏幕", font=font(21), fill=MUTE)
d.line([28, 208, W - 28, 208], fill=BORDER, width=2)
d.text((28, 224), "专注 45 分钟", font=font(25, True), fill=GREEN)
save(img, "widget_preview_small.png")

# ---------- 4x1 中 ----------
W, H, R = 600, 150, 26
img, d = card(W, H, R)
cols = [("今日屏幕", "6小时29分", 32, INK),
        ("今日专注", "45分", 28, GREEN),
        ("拿起次数", "55", 28, INK)]
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
d.text((32, 26), "「留白」", font=font(22, True), fill=BRAND)
pickup = "今日拿起 32 次"
pw = d.textlength(pickup, font=font(21))
d.text((W - 32 - pw, 28), pickup, font=font(21), fill=MUTE)
d.text((32, 66), "2 小时 15 分钟", font=font(52, True), fill=INK)
d.text((32 + d.textlength("2 小时 15 分钟", font=font(52, True)) + 12, 104),
       "今日屏幕", font=font(21), fill=MUTE)
d.text((W - 32 - d.textlength("专注 45 分钟", font=font(21, True)), 102),
       "专注 45 分钟", font=font(21, True), fill=GREEN)
d.line([32, 146, W - 32, 146], fill=BORDER, width=2)
d.text((32, 160), "使用时长 TOP 5", font=font(20), fill=MUTE)

rows = [("短视频", "1 小时 12 分钟", GREEN, 1.0),
        ("社交", "48 分钟", GRAY, 0.62),
        ("浏览器", "21 分钟", GRAY, 0.3),
        ("购物", "12 分钟", GRAY, 0.18),
        ("音乐", "6 分钟", GRAY, 0.09)]
y = 196
for name, dur, color, ratio in rows:
    # 图标占位（圆角方块）
    d.rounded_rectangle([32, y + 1, 66, y + 35], radius=10,
                        fill=(214, 208, 196), outline=BORDER, width=2)
    d.text((80, y + 5), name, font=font(22), fill=INK)
    tw = d.textlength(dur, font=font(20))
    d.text((W - 32 - 100 - tw, y + 6), dur, font=font(20), fill=MUTE)
    bar_x, bar_w, bar_h = W - 32 - 96, 96, 10
    d.rounded_rectangle([bar_x, y + 13, bar_x + bar_w, y + 13 + bar_h],
                        radius=5, fill=BORDER)
    fill_w = max(6, int(bar_w * ratio))
    d.rounded_rectangle([bar_x, y + 13, bar_x + fill_w, y + 13 + bar_h],
                        radius=5, fill=color)
    y += 48
save(img, "widget_preview_large.png")
print("done ->", OUT)

#!/usr/bin/env python3
"""生成主屏幕图标：暮色山景 + 一块磨砂玻璃月历卡。python3 build/make_icons.py"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "img")
S = 1024


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scene():
    img = Image.new("RGB", (S, S))
    d = ImageDraw.Draw(img)
    stops = [(0, (18, 32, 78)), (.45, (92, 70, 140)), (.72, (238, 128, 98)), (1, (255, 196, 120))]
    for y in range(S):
        t = y / S
        for i in range(len(stops) - 1):
            if stops[i][0] <= t <= stops[i + 1][0]:
                k = (t - stops[i][0]) / (stops[i + 1][0] - stops[i][0])
                c = lerp(stops[i][1], stops[i + 1][1], k)
        d.line([(0, y), (S, y)], fill=c)
    d.ellipse([610, 470, 790, 650], fill=(255, 226, 180))
    ridges = [
        ([(0, 700), (180, 560), (330, 640), (520, 500), (700, 620), (880, 540), (1024, 610)], (70, 48, 92)),
        ([(0, 800), (220, 690), (420, 770), (640, 660), (860, 760), (1024, 700)], (42, 30, 66)),
        ([(0, 900), (260, 820), (520, 880), (780, 800), (1024, 860)], (22, 18, 40)),
    ]
    for pts, col in ridges:
        d.polygon(pts + [(S, S), (0, S)], fill=col)
    return img.filter(ImageFilter.GaussianBlur(2))


def main():
    os.makedirs(OUT, exist_ok=True)
    base = scene()
    # 玻璃卡：模糊 + 提亮 + 白色薄雾 + 亮边
    x0, y0, x1, y1, r = 212, 250, 812, 830, 110
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([x0, y0, x1, y1], r, fill=255)
    frosted = ImageEnhance.Color(base.filter(ImageFilter.GaussianBlur(38))).enhance(1.4)
    frosted = Image.blend(frosted, Image.new("RGB", (S, S), (255, 255, 255)), 0.22)
    shadow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(shadow).rounded_rectangle([x0, y0 + 40, x1, y1 + 40], r, fill=120)
    shadow = shadow.filter(ImageFilter.GaussianBlur(40))
    img = Image.composite(Image.new("RGB", (S, S), (0, 0, 0)), base, shadow)
    img = Image.composite(frosted, img, mask)
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle([x0, y0, x1, y1], r, outline=(255, 255, 255, 120), width=5)
    d.arc([x0 + 3, y0 + 3, x1 - 3, y0 + 2 * r], 200, 340, fill=(255, 255, 255, 200), width=6)
    # 顶部黄条 + 月历点阵（今天为实心白点）
    d.rounded_rectangle([x0 + 70, y0 + 80, x0 + 300, y0 + 118], 19, fill=(255, 214, 10, 255))
    cols, rows = 7, 5
    gx0, gy0, step = x0 + 78, y0 + 200, 74
    for rr in range(rows):
        for cc in range(cols):
            cx, cy = gx0 + cc * step, gy0 + rr * step
            if rr == 2 and cc == 4:
                d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], fill=(255, 255, 255, 255))
            else:
                a = 150 if cc not in (0, 6) else 90
                d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=(255, 255, 255, a))
    for size in (76, 120, 152, 180, 192, 512):
        img.resize((size, size), Image.LANCZOS).save(os.path.join(OUT, "icon-%d.png" % size), optimize=True)
    print("icons ->", OUT)


if __name__ == "__main__":
    main()

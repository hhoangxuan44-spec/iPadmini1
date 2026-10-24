# -*- coding: utf-8 -*-
"""
本地调试用的程序化「照片」（build_site.py --fake N 使用）：晨昏山脉、月夜海面、雪山、沙丘、林雾、
极光、城市夜景，以及画作比例的水墨山水、浮世绘式海浪、麦田油画。
不是为了好看，而是让界面在明亮 / 昏暗 / 高对比 / 低对比的画面上都能被检查到。需要 numpy。
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 2048, 1365


def _noise(rng, h, w, sigma):
    from numpy.fft import fft2, ifft2, fftfreq
    n = rng.standard_normal((h, w))
    fx = fftfreq(w)[None, :]
    fy = fftfreq(h)[:, None]
    k = np.exp(-2 * (np.pi ** 2) * sigma ** 2 * (fx ** 2 + fy ** 2))
    a = np.real(ifft2(fft2(n) * k))
    return (a - a.mean()) / (a.std() + 1e-9)


def _grad(h, w, stops):
    """竖直渐变：stops = [(位置 0-1, (r,g,b)), ...]"""
    ys = np.linspace(0, 1, h)
    out = np.zeros((h, w, 3))
    pos = [s[0] for s in stops]
    for c in range(3):
        col = np.interp(ys, pos, [s[1][c] for s in stops])
        out[:, :, c] = col[:, None]
    return out


def _glow(img, cx, cy, r, color, power=1.0, gain=1.0):
    h, w, _ = img.shape
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    a = np.clip(1 - d, 0, 1) ** (2.2 * power) * gain
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - a) + color[c] * a
    return img


def _ridge(rng, w, base, amp, rough=0.55, octaves=7):
    x = np.linspace(0, 1, w)
    y = np.zeros(w)
    f, a = 2.0, 1.0
    for _ in range(octaves):
        y += a * np.sin(2 * math.pi * (f * x + rng.uniform()))
        f *= 2.0 + rng.uniform(-0.3, 0.4)
        a *= rough
    y = y / (np.abs(y).max() + 1e-9)
    return base + amp * y


def _layer(img, ys, color, haze=0.0, haze_color=(255, 255, 255)):
    h, w, _ = img.shape
    yy = np.arange(h)[:, None]
    mask = (yy >= ys[None, :]).astype(float)
    # 边缘抗锯齿
    edge = np.clip(yy - ys[None, :] + 1, 0, 1)
    mask = np.minimum(mask, edge) if False else np.clip(yy - ys[None, :] + 0.5, 0, 1)
    depth = np.clip((yy - ys[None, :]) / 380.0, 0, 1)
    for c in range(3):
        col = color[c] * (1 - 0.0 * depth) + 0 * depth
        top = color[c] * (1 - haze) + haze_color[c] * haze
        layer = top * (1 - depth) + color[c] * 0.82 * depth
        img[:, :, c] = img[:, :, c] * (1 - mask) + layer * mask
    return img


def _finish(img, rng, grain=2.2, vig=0.0):
    h, w, _ = img.shape
    img = img + rng.standard_normal((h, w, 1)) * grain
    if vig:
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
        img = img * (1 - vig * np.clip(d - 0.5, 0, 1.2)[..., None])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def dusk(seed, warm=True):
    rng = np.random.default_rng(seed)
    top, mid, hor = ((38, 28, 78), (176, 78, 104), (255, 176, 96)) if warm else ((18, 36, 86), (88, 108, 160), (214, 176, 150))
    img = _grad(H, W, [(0, top), (0.38, mid), (0.58, hor), (1, hor)])
    sx, sy = rng.uniform(0.25, 0.75) * W, rng.uniform(0.48, 0.58) * H
    img = _glow(img, sx, sy, 900, (255, 214, 150), 0.9, 0.9)
    img = _glow(img, sx, sy, 150, (255, 246, 220), 0.6, 1.0)
    clouds = _noise(rng, H, W, 40)
    yy = np.arange(H)[:, None] / H
    cl = np.clip(clouds * 0.5 + 0.1, 0, 1) * np.clip(1 - np.abs(yy - 0.3) * 3.5, 0, 1)
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - cl * 0.35) + (255, 190, 170)[c] * cl * 0.35
    cols = [(150, 98, 118), (112, 70, 98), (78, 50, 78), (48, 32, 56), (24, 18, 32)]
    for i, col in enumerate(cols):
        ys = _ridge(rng, W, H * (0.58 + i * 0.075), 70 + i * 22, 0.52 + i * 0.03)
        img = _layer(img, ys, col, haze=0.5 - i * 0.1, haze_color=hor)
    return _finish(img, rng, 2.0, 0.25)


def moon_sea(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (4, 8, 22)), (0.5, (18, 34, 62)), (0.56, (38, 62, 88)), (0.57, (12, 22, 38)), (1, (3, 8, 16))])
    mx, my = rng.uniform(0.3, 0.7) * W, 0.24 * H
    img = _glow(img, mx, my, 700, (90, 110, 150), 1.0, 0.7)
    img = _glow(img, mx, my, 120, (255, 244, 214), 0.7, 1.0)
    st = rng.random((H, W)) > 0.9993
    img[: int(H * 0.55)][st[: int(H * 0.55)]] = 235
    hor = int(H * 0.57)
    waves = _noise(rng, H - hor, W, 3)
    colx = np.exp(-((np.arange(W) - mx) / 120.0) ** 2)[None, :]
    yy = np.linspace(0, 1, H - hor)[:, None]
    refl = np.clip(waves * 0.5 + 0.45, 0, 1) * colx * (1 - 0.5 * yy)
    for c in range(3):
        img[hor:, :, c] += refl * (255, 238, 196)[c] * 0.7
    return _finish(img, rng, 2.4, 0.3)


def snow(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (70, 128, 206)), (0.5, (170, 205, 238)), (1, (232, 240, 248))])
    img = _glow(img, rng.uniform(0.2, 0.8) * W, 0.1 * H, 900, (255, 255, 255), 0.8, 0.55)
    cols = [(206, 222, 240), (170, 192, 222), (226, 232, 242), (240, 244, 250)]
    for i, col in enumerate(cols):
        ys = _ridge(rng, W, H * (0.5 + i * 0.1), 150 - i * 20, 0.62 - i * 0.04)
        img = _layer(img, ys, col, haze=0.3 - i * 0.07, haze_color=(220, 232, 248))
    return _finish(img, rng, 1.8, 0.1)


def dunes(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (92, 150, 214)), (0.4, (238, 190, 140)), (0.6, (250, 214, 160)), (1, (250, 214, 160))])
    img = _glow(img, rng.uniform(0.2, 0.8) * W, 0.45 * H, 900, (255, 238, 200), 0.9, 0.8)
    cols = [(224, 160, 104), (206, 128, 78), (178, 98, 58), (132, 66, 40)]
    for i, col in enumerate(cols):
        ys = _ridge(rng, W, H * (0.58 + i * 0.1), 60 + i * 30, 0.3, 4)
        img = _layer(img, ys, col, haze=0.4 - i * 0.1, haze_color=(250, 214, 170))
    return _finish(img, rng, 2.6, 0.2)


def forest(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (150, 188, 176)), (0.5, (214, 226, 210)), (1, (120, 150, 120))])
    img = _glow(img, rng.uniform(0.25, 0.7) * W, 0.15 * H, 800, (255, 250, 220), 0.9, 0.9)
    cols = [(120, 156, 134), (82, 122, 98), (52, 86, 70), (28, 52, 44), (12, 26, 22)]
    for i, col in enumerate(cols):
        base = H * (0.46 + i * 0.1)
        x = np.arange(W)
        ys = _ridge(rng, W, base, 40 + i * 12, 0.55, 8) + 18 * np.abs(np.sin(x / (30 - i * 3.0) + rng.uniform(0, 6)))
        img = _layer(img, ys, col, haze=0.55 - i * 0.12, haze_color=(222, 232, 222))
    return _finish(img, rng, 2.0, 0.28)


def aurora(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (2, 6, 18)), (0.6, (8, 20, 36)), (1, (14, 26, 40))])
    ov = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(ov)
    for k in range(3):
        pts = []
        y0 = H * (0.2 + 0.12 * k)
        ph = rng.uniform(0, 6)
        for x in range(0, W + 40, 40):
            pts.append((x, y0 + 110 * math.sin(x / 330.0 + ph) + 40 * math.sin(x / 90.0 + ph * 2)))
        col = [(40, 255, 150), (90, 220, 255), (220, 90, 255)][k]
        for dy in range(0, 340, 6):
            a = (1 - dy / 340.0) ** 1.8
            d.line([(x, y + dy) for x, y in pts], fill=tuple(int(c * a * 0.5) for c in col), width=9)
    ov = ov.filter(ImageFilter.GaussianBlur(14))
    img = np.clip(img + np.asarray(ov, dtype=float), 0, 255)
    st = rng.random((H, W)) > 0.9994
    img[: int(H * 0.6)][st[: int(H * 0.6)]] = 230
    ys = _ridge(rng, W, H * 0.8, 40, 0.5, 6)
    img = _layer(img, ys, (210, 224, 236), haze=0.0)
    return _finish(img, rng, 2.0, 0.3)


def city(seed):
    rng = np.random.default_rng(seed)
    img = _grad(H, W, [(0, (14, 20, 56)), (0.5, (110, 70, 120)), (0.7, (240, 130, 96)), (1, (60, 30, 50))])
    img = _glow(img, rng.uniform(0.3, 0.7) * W, 0.66 * H, 900, (255, 170, 110), 0.8, 0.8)
    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(pil)
    x = 0
    while x < W:
        bw = int(rng.uniform(50, 150))
        bh = int(rng.uniform(120, 520) * (1.25 - abs(x - W / 2) / W))
        top = int(H * 0.82 - bh)
        d.rectangle([x, top, x + bw, H], fill=(14, 12, 24))
        for wx in range(x + 8, x + bw - 8, 16):
            for wy in range(top + 10, int(H * 0.82), 24):
                if rng.random() > 0.55:
                    d.rectangle([wx, wy, wx + 7, wy + 11], fill=(255, 208, 120))
        x += bw + int(rng.uniform(0, 14))
    d.rectangle([0, int(H * 0.82), W, H], fill=(10, 8, 18))
    return _finish(np.asarray(pil, dtype=float), rng, 2.0, 0.3)


def ink_mountain(seed):
    """竖幅水墨：宣纸底色 + 淡墨远山 + 浓墨近山"""
    rng = np.random.default_rng(seed)
    w, h = 1365, 2048
    img = np.zeros((h, w, 3)) + np.array([226, 214, 186.0])
    mott = _noise(rng, h, w, 80)
    img += mott[..., None] * 6
    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(pil, "RGBA")
    for layer in range(5):
        a = 40 + layer * 34
        base = h * (0.28 + layer * 0.14)
        pts = [(0, h)]
        x = 0
        while x <= w:
            pts.append((x, base - abs(rng.normal(0, 150 - layer * 14)) - 120 * (1 - abs(x - w * 0.5) / (w * 0.5))))
            x += int(rng.uniform(40, 120))
        pts.append((w, h))
        d.polygon(pts, fill=(34, 32, 30, a))
    pil = pil.filter(ImageFilter.GaussianBlur(1.6))
    return _finish(np.asarray(pil, dtype=float), rng, 1.5, 0.12)


def wave_print(seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((H, W, 3)) + np.array([232, 220, 190.0])
    pil = Image.fromarray(img.astype(np.uint8), "RGB")
    d = ImageDraw.Draw(pil)
    d.polygon([(0, H * 0.5), (W, H * 0.4), (W, H), (0, H)], fill=(34, 62, 112))
    for i in range(18):
        cx = rng.uniform(0, W)
        cy = H * (0.45 + 0.5 * rng.uniform())
        r = rng.uniform(60, 220)
        d.pieslice([cx - r, cy - r, cx + r, cy + r], 190, 350, fill=(236, 240, 238), outline=(22, 40, 82), width=6)
    d.polygon([(W * 0.62, H * 0.52), (W * 0.74, H * 0.3), (W * 0.86, H * 0.52)], fill=(120, 150, 190))
    return _finish(np.asarray(pil, dtype=float), rng, 1.4, 0.1)


def wheat(seed):
    rng = np.random.default_rng(seed)
    w, h = 2048, 1024
    img = _grad(h, w, [(0, (96, 150, 214)), (0.45, (180, 206, 224)), (0.5, (214, 172, 70)), (1, (150, 104, 36))])
    img = _glow(img, w * 0.7, h * 0.18, 500, (255, 255, 235), 0.9, 0.7)
    n = _noise(rng, h, w, 6)
    yy = np.arange(h)[:, None]
    m = (yy > h * 0.5)
    img[m[:, 0]] += (n[m[:, 0]][..., None] * np.array([18, 14, 4]))
    return _finish(img, rng, 1.8, 0.1)


SCENES = [
    # (函数, 参数, kind, 标题, 副标题, 标签, 风格倾向)
    (dusk, (1, True), "nature", "红霞山脉", "测试 · 程序生成", ["dusk", "autumn"]),
    (moon_sea, (2,), "sky", "月下海面", "测试 · 程序生成", ["night", "moon"]),
    (snow, (3,), "nature", "雪岭晴空", "测试 · 程序生成", ["winter"]),
    (dunes, (4,), "nature", "落日沙丘", "测试 · 程序生成", ["dusk", "summer"]),
    (forest, (5,), "nature", "林间晨雾", "测试 · 程序生成", ["dawn", "spring"]),
    (aurora, (6,), "sky", "极光夜", "测试 · 程序生成", ["night", "winter"]),
    (city, (7,), "city", "暮色城市", "测试 · 程序生成", ["dusk"]),
    (ink_mountain, (8,), "east", "秋山行旅图", "佚名 · 测试画作", ["autumn"]),
    (wave_print, (9,), "east", "海浪图", "测试 · 浮世绘式", ["summer"]),
    (wheat, (10,), "west", "麦田", "测试 · 油画式", ["summer"]),
    (dusk, (11, False), "nature", "蓝调山谷", "测试 · 程序生成", ["dawn"]),
    (dunes, (12,), "china", "大漠孤烟", "测试 · 程序生成", ["autumn"]),
]

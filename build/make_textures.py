#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
程序化材质贴图（可无缝平铺），网页和安卓共用：
  paper.png    宣纸 / 棉纸：纤维、斑驳、墨点杂质
  grain.png    中性颗粒：玻璃、色带、照片上的细微噪点（同时消除渐变色带）
  brushed.png  拉丝金属
  plaster.png  灰泥墙
  wood.png     深色胡桃木（画框）
  seal.png     印泥的不均匀遮罩（盖印时有些地方没印上）
生成结果直接提交进仓库，不在 CI 里生成（需要 numpy：pip install numpy Pillow）。
用法： python3 build/make_textures.py
"""
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_WEB = os.path.join(ROOT, "img", "tex")
OUT_APP = os.path.join(ROOT, "android", "app", "src", "main", "assets", "tex")
OUT_RES = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")


def blur(a, sx, sy=None):
    """周期边界的高斯模糊（FFT），所以结果可以无缝平铺。"""
    sy = sx if sy is None else sy
    h, w = a.shape
    fx = np.fft.fftfreq(w)[None, :]
    fy = np.fft.fftfreq(h)[:, None]
    k = np.exp(-2 * (np.pi ** 2) * ((sx * fx) ** 2 + (sy * fy) ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(a) * k))


def norm(a):
    a = a - a.mean()
    return a / (a.std() + 1e-9)


def octaves(rng, size, sigmas, weights, aniso=(1, 1)):
    h, w = size
    out = np.zeros(size)
    for s, wt in zip(sigmas, weights):
        out += wt * norm(blur(rng.standard_normal(size), s * aniso[0], s * aniso[1]))
    return norm(out)


def rgba(dark_a, light_a, dark_c=(0, 0, 0), light_c=(255, 255, 255)):
    """两层半透明（深、浅）合成一张 RGBA：浅色盖在深色上。"""
    da = np.clip(dark_a, 0, 1)
    la = np.clip(light_a, 0, 1)
    a = la + da * (1 - la)
    safe = np.maximum(a, 1e-6)
    out = np.zeros(da.shape + (4,))
    for c in range(3):
        out[..., c] = (light_c[c] * la + dark_c[c] * da * (1 - la)) / safe
    out[..., 3] = a * 255
    out[..., :3] = np.clip(out[..., :3], 0, 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def fibers(size, count, seed, length=(10, 46), width=1.0, curl=0.16):
    """画一层纤维（周期边界）：返回 0-1 的灰度图"""
    rng = np.random.default_rng(seed)
    n = size
    ss = 2
    img = Image.new("L", (n * ss, n * ss), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = rng.uniform(0, n * ss, 2)
        ang = rng.uniform(0, np.pi)
        ln = rng.uniform(*length) * ss
        v = int(rng.uniform(110, 255))
        pts = [(x, y)]
        steps = 6
        for i in range(steps):
            ang += rng.normal(0, curl)
            x += np.cos(ang) * ln / steps
            y += np.sin(ang) * ln / steps
            pts.append((x, y))
        for ox in (-n * ss, 0, n * ss):
            for oy in (-n * ss, 0, n * ss):
                d.line([(px + ox, py + oy) for px, py in pts], fill=v, width=max(1, int(round(width * ss * 0.8))))
    img = img.resize((n, n), Image.LANCZOS)
    return np.asarray(img, dtype=float) / 255.0


def speckles(size, count, seed, rmax=1.6):
    rng = np.random.default_rng(seed)
    ss = 2
    img = Image.new("L", (size * ss, size * ss), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = rng.uniform(0, size * ss, 2)
        r = rng.uniform(0.4, rmax) * ss
        v = int(rng.uniform(90, 255))
        for ox in (-size * ss, 0, size * ss):
            for oy in (-size * ss, 0, size * ss):
                d.ellipse([x + ox - r, y + oy - r, x + ox + r, y + oy + r], fill=v)
    img = img.resize((size, size), Image.LANCZOS)
    return np.asarray(img, dtype=float) / 255.0


def paper(n=256):
    rng = np.random.default_rng(7)
    mottle = octaves(rng, (n, n), (46, 18, 6), (1.0, 0.7, 0.4))
    fine = norm(rng.standard_normal((n, n)))
    dark_f = fibers(n, 300, 11, (10, 46), 0.9)
    light_f = fibers(n, 520, 12, (14, 64), 1.2)
    dust = speckles(n, 70, 13, 1.2)
    big = speckles(n, 5, 14, 2.2)
    dark = (np.clip(mottle, 0, 3) * 0.028 + dark_f * 0.17 + dust * 0.16 + big * 0.34 + np.clip(-fine, 0, 4) * 0.012)
    light = (np.clip(-mottle, 0, 3) * 0.075 + light_f * 0.42 + np.clip(fine, 0, 4) * 0.03)
    return rgba(dark, light, dark_c=(104, 78, 46), light_c=(255, 252, 244))


def grain(n=128):
    rng = np.random.default_rng(3)
    g = rng.standard_normal((n, n))
    g = g + 0.4 * blur(g, 0.8)
    a = np.clip(np.abs(g) * 0.075, 0, 0.32)
    out = np.zeros((n, n, 4))
    out[..., :3] = np.where(g[..., None] > 0, 255, 0)
    out[..., 3] = a * 255
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def brushed(w=256, h=64):
    rng = np.random.default_rng(5)
    n = rng.standard_normal((h, w))
    f = norm(blur(n, 46, 0.6)) * 0.8 + norm(blur(rng.standard_normal((h, w)), 9, 0.5)) * 0.45 + norm(rng.standard_normal((h, w))) * 0.12
    f = norm(f)
    return rgba(np.clip(-f, 0, 4) * 0.045, np.clip(f, 0, 4) * 0.06)


def plaster(n=256):
    rng = np.random.default_rng(9)
    m = octaves(rng, (n, n), (60, 22, 8, 3), (1.0, 0.8, 0.5, 0.3))
    fine = norm(rng.standard_normal((n, n)))
    return rgba(np.clip(m, 0, 4) * 0.045 + np.clip(-fine, 0, 4) * 0.018, np.clip(-m, 0, 4) * 0.032 + np.clip(fine, 0, 4) * 0.014,
                dark_c=(30, 24, 20), light_c=(255, 250, 240))


def wood(w=128, h=256):
    rng = np.random.default_rng(21)
    low = octaves(rng, (h, w), (26, 9), (1.0, 0.45), aniso=(7.0, 0.8))   # 纵向拉长的低频噪声，让纹理缓缓弯曲
    xx = np.arange(w)[None, :] / float(w)
    rings = 0.5 + 0.5 * np.sin(2 * np.pi * (xx * 4 + 0.22 * low))
    long_streak = norm(blur(rng.standard_normal((h, w)), 0.9, 48))
    pores = norm(blur(rng.standard_normal((h, w)), 0.6, 7))
    v = np.clip(0.30 * rings + 0.16 * long_streak + 0.06 * pores + 0.40, 0, 1)
    dark = np.array([42, 27, 18]), np.array([78, 50, 31]), np.array([116, 78, 50])
    lo = v < 0.5
    t = np.where(lo, v / 0.5, (v - 0.5) / 0.5)[..., None]
    out = np.where(lo[..., None], dark[0] * (1 - t) + dark[1] * t, dark[1] * (1 - t) + dark[2] * t)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")


def seal(n=96):
    rng = np.random.default_rng(31)
    yy, xx = np.mgrid[0:n, 0:n] / float(n - 1)
    edge = np.minimum(np.minimum(xx, 1 - xx), np.minimum(yy, 1 - yy))
    low = norm(blur(rng.standard_normal((n, n)), 2.2))
    fine = norm(blur(rng.standard_normal((n, n)), 0.8))
    wear = 0.9 * low + 0.7 * fine - 3.4 * np.clip(edge - 0.02, 0, 0.12) * 6
    a = np.clip(1.15 - 0.34 * np.clip(wear + 0.9, 0, 4), 0.0, 1.0)
    a = np.where(wear > 1.55, 0.0, a)                 # 少数地方没印上
    a = np.clip(0.2 + 0.8 * (a > 0.1) * (0.82 + 0.18 * (1 - np.clip(wear + 1, 0, 2) / 2)), 0, 1) * (a > 0.02)
    out = np.zeros((n, n, 4))
    out[..., :3] = 255
    out[..., 3] = a * 255
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def save(img, name, android_res=False):
    for d in (OUT_WEB, OUT_APP) + ((OUT_RES,) if android_res else ()):
        os.makedirs(d, exist_ok=True)
        fn = name if d != OUT_RES else "tex_" + name
        img.save(os.path.join(d, fn), optimize=True)
    print("%-12s %s" % (name, img.size))


if __name__ == "__main__":
    save(paper(), "paper.png", True)
    save(grain(), "grain.png", True)
    save(brushed(), "brushed.png", True)
    save(plaster(), "plaster.png", True)
    save(wood(), "wood.png", True)
    save(seal(), "seal.png")

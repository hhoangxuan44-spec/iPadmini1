# -*- coding: utf-8 -*-
"""
为每张图计算「日历主题」：
  - 画面最空的区域（日历摆放位置）
  - 主色、点缀色、文字深浅
  - 日历风格：minimal 极简 / glass 玻璃 / magazine 杂志 / band 色带 / paper 宣纸
按横屏 4:3（iPad 1024×768）的居中裁切来分析。
"""
import colorsys

from PIL import Image, ImageFilter, ImageStat

GRID_W, GRID_H = 128, 96

# 候选区域（屏幕比例 x0, y0, x1, y1）
REGIONS = {
    "tl": (0.03, 0.05, 0.42, 0.58),
    "tr": (0.58, 0.05, 0.97, 0.58),
    "bl": (0.03, 0.42, 0.42, 0.95),
    "br": (0.58, 0.42, 0.97, 0.95),
    "l": (0.0, 0.0, 0.40, 1.0),
    "r": (0.60, 0.0, 1.0, 1.0),
    "b": (0.0, 0.66, 1.0, 1.0),
}
CORNERS = ("tl", "tr", "bl", "br")


def _crop_43(img):
    w, h = img.size
    if w / h > 4 / 3:
        nw = int(h * 4 / 3)
        x = (w - nw) // 2
        return img.crop((x, 0, x + nw, h))
    nh = int(w * 3 / 4)
    y = (h - nh) // 2
    return img.crop((0, y, w, y + nh))


def _box(r):
    x0, y0, x1, y1 = REGIONS[r]
    return (int(x0 * GRID_W), int(y0 * GRID_H), int(x1 * GRID_W), int(y1 * GRID_H))


def _hex(rgb):
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(round(c)))) for c in rgb)


def _hls_to_hex(h, l, s):
    return _hex(tuple(c * 255 for c in colorsys.hls_to_rgb(h, l, s)))


def _lum(rgb):
    r, g, b = [c / 255.0 for c in rgb]
    return 0.299 * r + 0.587 * g + 0.114 * b


def analyze(img):
    """返回图像特征（未决定风格）。"""
    crop = _crop_43(img.convert("RGB")).resize((GRID_W, GRID_H), Image.LANCZOS)
    gray = crop.convert("L")
    edges = gray.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.BoxBlur(1))
    hsv = crop.convert("HSV")

    feats = {}
    for r in REGIONS:
        b = _box(r)
        e = ImageStat.Stat(edges.crop(b)).mean[0]
        st = ImageStat.Stat(gray.crop(b))
        feats[r] = {"edge": e, "lum": st.mean[0], "std": st.stddev[0]}

    # 调色板
    q = crop.quantize(colors=8, method=Image.Quantize.MEDIANCUT)
    pal = q.getpalette()
    colors = sorted(q.getcolors(), reverse=True)
    dominant = tuple(pal[colors[0][1] * 3: colors[0][1] * 3 + 3])
    best, best_score = None, -1
    for count, idx in colors:
        rgb = tuple(pal[idx * 3: idx * 3 + 3])
        h, l, s = colorsys.rgb_to_hls(*[c / 255.0 for c in rgb])
        score = count * (s ** 1.4) * (1 - abs(l - 0.5))
        if score > best_score:
            best_score, best = score, (h, l, s)
    sat = ImageStat.Stat(hsv.split()[1]).mean[0] / 255.0
    return {"regions": feats, "dominant": dominant, "vivid": best, "sat": sat}


def _accent(vivid, light_text):
    h, l, s = vivid if vivid else (0.12, 0.5, 0.0)
    if s < 0.12:  # 几乎无彩色的画面，用暖金色点缀
        h, s = 0.11, 0.75
    s = max(0.55, min(s * 1.25, 0.95))
    return _hls_to_hex(h, 0.72 if light_text else 0.36, s)


def _band_color(dominant, vivid):
    h, l, s = colorsys.rgb_to_hls(*[c / 255.0 for c in dominant])
    if s < 0.15 and vivid:
        h, s = vivid[0], max(s, vivid[2] * 0.6)
    l = 0.24 if l < 0.5 else 0.82
    s = min(0.55, max(0.18, s))
    return _hls_to_hex(h, l, s), ("light" if l < 0.5 else "dark")


def _paper_color(dominant):
    # 米色纸张里掺一点画面主色
    base = (243, 236, 220)
    return _hex(tuple(base[i] * 0.93 + dominant[i] * 0.07 for i in range(3)))


def assign(items):
    """
    items: 构建条目（含 kind / feat / w / h / poem / title），原地写入 theme。
    风格按排名分配，保证各种风格都有、分布均衡：
      画作：比例不适合铺满屏幕的一律「画廊装裱」完整展示；东方绘画带诗的轮流用「竖排诗笺」
      照片：极简 / 海报大字 / 杂志 / 色带 / 玻璃；中国风光带诗的轮流用「竖排诗笺」
    """
    def is_art(it):
        return it["kind"] in ("east", "west")

    art = sorted([it for it in items if is_art(it)], key=lambda t: t["title"])
    photos = [it for it in items if not is_art(it)]

    for it in art:
        r = it["w"] / float(it["h"])
        if r < 1.22 or r > 1.95:
            it["style"] = "gallery"
    east_poem = [it for it in art if "style" not in it and it["kind"] == "east" and it.get("poem")]
    for i, it in enumerate(east_poem):
        it["style"] = "vpoem" if i % 2 == 0 else "paper"
    rest = [it for it in art if "style" not in it]
    for i, it in enumerate(rest):
        it["style"] = "gallery" if i % 2 == 0 else "paper"

    china_poem = sorted([it for it in photos if it["kind"] == "china" and it.get("poem")], key=lambda t: t["title"])
    for i, it in enumerate(china_poem):
        if i % 2 == 0:
            it["style"] = "vpoem"
    photos = [it for it in photos if "style" not in it]

    def corner_quiet(it):
        rs = it["feat"]["regions"]
        return min(rs[c]["edge"] + rs[c]["std"] * 0.15 for c in CORNERS)

    def side_quiet(it):
        rs = it["feat"]["regions"]
        return min(rs[c]["edge"] + rs[c]["std"] * 0.1 for c in ("l", "r"))

    n = len(photos)
    pool = sorted(photos, key=corner_quiet)
    for i, it in enumerate(pool[: int(n * 0.36)]):
        it["style"] = "minimal" if i % 5 < 3 else "poster"
    rest = [it for it in photos if "style" not in it]
    for it in sorted(rest, key=side_quiet)[: int(n * 0.18)]:
        it["style"] = "magazine"
    rest = [it for it in photos if "style" not in it]
    for it in sorted(rest, key=lambda t: -t["feat"]["sat"])[: int(n * 0.22)]:
        it["style"] = "band"
    for it in photos:
        it.setdefault("style", "glass")

    for it in items:
        it["theme"] = _theme_for(it)


def _wall_color(dominant):
    h, l, s = colorsys.rgb_to_hls(*[c / 255.0 for c in dominant])
    return _hls_to_hex(h, 0.13, min(s, 0.25))


def _theme_for(it):
    f, style = it["feat"], it["style"]
    rs = f["regions"]
    if style in ("magazine", "vpoem"):
        pos = min(("l", "r"), key=lambda r: rs[r]["edge"] + rs[r]["std"] * 0.1)
    elif style == "gallery":
        pos = "r" if sum(ord(ch) for ch in it.get("title", "")) % 2 else "l"
    elif style == "band":
        pos = "b"
    else:
        pos = min(CORNERS, key=lambda r: rs[r]["edge"] + rs[r]["std"] * 0.15)
    region_lum = rs[pos]["lum"]

    t = {"style": style, "pos": pos}
    if style == "band":
        bg, ink = _band_color(f["dominant"], f["vivid"])
        t.update(bg=bg, ink=ink, accent=_accent(f["vivid"], ink == "light"))
    elif style in ("paper", "vpoem"):
        t.update(bg=_paper_color(f["dominant"]), ink="dark", accent="#b23a2e")
    elif style == "gallery":
        t.update(bg=_wall_color(f["dominant"]), ink="light", accent=_accent(f["vivid"], True))
    elif style == "glass":
        ink = "dark" if region_lum > 165 else "light"
        t.update(ink=ink, accent=_accent(f["vivid"], ink == "light"))
    else:  # minimal / magazine / poster：文字直接压在照片上
        ink = "dark" if region_lum > 150 else "light"
        t.update(ink=ink, accent=_accent(f["vivid"], ink == "light"),
                 lum=round(region_lum))
    return t

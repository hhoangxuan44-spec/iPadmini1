#!/usr/bin/env python3
"""
构建台历网站到 dist/：

  1. 从 Wikimedia Commons「精选图片」(Featured pictures) 挑选高质量图片：
     风景、自然现象、动物、天文、名画 / 浮世绘（全部为公有领域或 CC 自由授权）
  2. 每张图生成三个版本：
       m/  长边 1280，给 iPad mini 1 这类老设备（省内存）
       l/  长边 1920，给高分屏手机 / 新平板
       b/  宽 160 的预模糊小图，用来做「玻璃」背后的模糊（零运行时开销）
  3. 写出 data/photos.js 清单（含作者、授权、来源，页面上会署名）
  4. 自己的照片放进仓库根目录 photos/，也会一起加入

用法：
    pip install Pillow
    python3 build/build_site.py                # 正式构建（需要能访问 commons.wikimedia.org）
    python3 build/build_site.py --fake 16      # 离线生成 16 张假图，仅用于本地调试界面
    python3 build/build_site.py --count 160    # 指定图片数量
"""
import argparse
import colorsys
import hashlib
import html
import io
import json
import os
import random
import re
import shutil
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")
SITE_FILES = ["index.html", "manifest.webmanifest", "css", "js", "img"]
API = "https://commons.wikimedia.org/w/api.php"
UA = "DeskCalendarBuilder/1.0 (personal desk calendar; https://github.com/hhoangxuan44-spec/iPadmini1)"

# 图片来源（Commons 精选图片画廊页 / 分类）与配额。
# 某个来源不可用时会自动跳过，用其它来源补齐。
SOURCES = [
    # (类别, 页面, 配额)
    ("landscape", "Commons:Featured pictures/Places/Natural", 70),
    ("phenomena", "Commons:Featured pictures/Natural phenomena", 14),
    ("space", "Commons:Featured pictures/Astronomy", 10),
    ("animals", "Commons:Featured pictures/Animals/Mammals", 10),
    ("animals", "Commons:Featured pictures/Animals/Birds", 8),
    ("plants", "Commons:Featured pictures/Plants", 8),
    ("art", "Commons:Featured pictures/Artwork/Paintings", 26),
    ("art", "Commons:Featured pictures/Artwork/Prints", 10),
]
CATEGORY_FALLBACKS = [
    ("art", "Category:The Thirty-six Views of Mount Fuji", 12),
    ("art", "Category:One Hundred Famous Views of Edo", 10),
]

SIZES = {"m": 1280, "l": 1920}
CACHE = os.path.join(ROOT, "build", ".cache")
BLUR_W = 160


# ---------------------------------------------------------------- 网络

def http_get(url, binary=False, tries=4):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            return data if binary else data.decode("utf-8")
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (i + 1))
    raise last


def api(params):
    params = dict(params, format="json", formatversion="2")
    return json.loads(http_get(API + "?" + urllib.parse.urlencode(params)))


def list_page_images(title):
    """列出画廊页上引用的全部文件。"""
    files, cont = [], {}
    while True:
        p = {"action": "query", "prop": "images", "titles": title, "imlimit": "500"}
        p.update(cont)
        d = api(p)
        for page in d.get("query", {}).get("pages", []):
            for im in page.get("images", []) or []:
                files.append(im["title"])
        if "continue" not in d:
            break
        cont = d["continue"]
    return files


def list_category_files(title):
    files, cont = [], {}
    while True:
        p = {"action": "query", "list": "categorymembers", "cmtitle": title,
             "cmtype": "file", "cmlimit": "500"}
        p.update(cont)
        d = api(p)
        files += [m["title"] for m in d.get("query", {}).get("categorymembers", [])]
        if "continue" not in d:
            break
        cont = d["continue"]
    return files


def strip_html(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def image_infos(titles):
    out = {}
    for i in range(0, len(titles), 50):
        d = api({"action": "query", "prop": "imageinfo", "titles": "|".join(titles[i:i + 50]),
                 "iiprop": "size|mime|extmetadata|url", "iiextmetadatalanguage": "zh"})
        for page in d.get("query", {}).get("pages", []):
            ii = (page.get("imageinfo") or [None])[0]
            if ii:
                out[page["title"]] = ii
    return out


def clean_title(file_title, meta):
    name = strip_html((meta.get("ObjectName") or {}).get("value", ""))
    if not name or len(name) > 70:
        name = re.sub(r"^File:", "", file_title)
        name = re.sub(r"\.(jpe?g|png|tiff?|webp)$", "", name, flags=re.I)
        name = re.sub(r"[_]+", " ", name)
        name = re.sub(r"\s*\(\d+\)\s*$", "", name)
        name = re.sub(r"\b(DSC|IMG|P)\s?\d{3,}\b", "", name)
        name = re.sub(r"\s{2,}", " ", name).strip(" -–,")
    return name[:70]


# ---------------------------------------------------------------- 图片处理

def tone_and_accent(img):
    """计算整体明暗，并挑一个鲜艳的主题色（用于日期页的小点缀）。"""
    small = img.convert("RGB").resize((64, 64))
    lum = ImageStat.Stat(small.convert("L")).mean[0]
    q = small.quantize(colors=8, method=Image.Quantize.MEDIANCUT)
    pal = q.getpalette()
    best, best_score = None, -1
    for count, idx in q.getcolors():
        r, g, b = pal[idx * 3: idx * 3 + 3]
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        score = count * (s ** 1.5) * (1 - abs(l - 0.55))
        if score > best_score:
            best_score, best = score, (h, s)
    accent = "#ffcc00"
    if best and best[1] > 0.08:
        h, s = best
        r, g, b = colorsys.hls_to_rgb(h, 0.66, max(0.55, min(s * 1.2, 0.9)))
        accent = "#%02x%02x%02x" % (int(r * 255), int(g * 255), int(b * 255))
    return round(lum), accent


def save_versions(img, key, outdir):
    img = ImageOps.exif_transpose(img).convert("RGB")
    w, h = img.size
    entry = {"w": w, "h": h}
    for tag, side in SIZES.items():
        im = img.copy()
        im.thumbnail((side, side), Image.LANCZOS)
        p = os.path.join(outdir, tag, key + ".jpg")
        im.save(p, "JPEG", quality=82, optimize=True, progressive=False)
        entry[tag] = "photos/%s/%s.jpg" % (tag, key)
        if tag == "m":
            entry["w"], entry["h"] = im.size
    # 预模糊小图：先缩小再高斯模糊并提升饱和度（玻璃的“通透感”）
    bh = max(1, round(BLUR_W * h / w))
    b = img.resize((BLUR_W * 2, bh * 2), Image.LANCZOS).filter(ImageFilter.GaussianBlur(7))
    b = b.resize((BLUR_W, bh), Image.LANCZOS)
    b = ImageEnhance.Color(b).enhance(1.45)
    b.save(os.path.join(outdir, "b", key + ".jpg"), "JPEG", quality=72)
    entry["b"] = "photos/b/%s.jpg" % key
    entry["lum"], entry["accent"] = tone_and_accent(img)
    return entry


# ---------------------------------------------------------------- 选图

def pick_commons(count, seed):
    rnd = random.Random(seed)
    total_quota = sum(q for _, _, q in SOURCES)
    scale = count / total_quota
    chosen, seen = [], set()
    shortfall = 0

    def take(cat, files, quota):
        nonlocal shortfall
        files = [f for f in files if f not in seen and re.search(r"\.(jpe?g|png|tiff?)$", f, re.I)]
        rnd.shuffle(files)
        got = 0
        for i in range(0, min(len(files), quota * 4), 50):
            batch = files[i:i + 50]
            infos = image_infos(batch)
            for f in batch:
                ii = infos.get(f)
                if not ii or got >= quota:
                    continue
                w, h = ii.get("width", 0), ii.get("height", 0)
                if min(w, h) < 1400:
                    continue
                ratio = w / float(h)
                if not (1.2 <= ratio <= 2.1):  # 横幅照片：横屏铺满、竖屏裁切都好看
                    continue
                meta = ii.get("extmetadata", {})
                lic = strip_html((meta.get("LicenseShortName") or {}).get("value", ""))
                if not lic:
                    continue
                artist = strip_html((meta.get("Artist") or {}).get("value", "")) or "Unknown"
                seen.add(f)
                chosen.append({
                    "file": f, "cat": cat,
                    "title": clean_title(f, meta),
                    "artist": artist[:60],
                    "license": lic,
                    "page": ii.get("descriptionurl", ""),
                    "thumb": ii.get("url", ""),
                })
                got += 1
            if got >= quota:
                break
        print("  %-10s %3d/%d" % (cat, got, quota))
        shortfall += max(0, quota - got)

    for cat, page, quota in SOURCES:
        q = max(1, round(quota * scale))
        try:
            files = list_page_images(page)
            print("%s: %d files" % (page, len(files)))
            take(cat, files, q)
        except Exception as e:  # noqa: BLE001
            print("! %s: %s" % (page, e))
            shortfall += q
    for cat, page, quota in CATEGORY_FALLBACKS:
        if shortfall <= 0:
            break
        try:
            files = list_category_files(page)
            print("%s: %d files" % (page, len(files)))
            before = len(chosen)
            take(cat, files, min(quota, shortfall))
            shortfall -= len(chosen) - before
        except Exception as e:  # noqa: BLE001
            print("! %s: %s" % (page, e))
    return chosen


def thumb_url(file_title, width):
    name = re.sub(r"^File:", "", file_title).replace(" ", "_")
    return "https://commons.wikimedia.org/wiki/Special:FilePath/%s?width=%d" % (
        urllib.parse.quote(name), width)


def build_commons(count, seed, outdir):
    picks = pick_commons(count, seed)
    entries = []
    for i, p in enumerate(picks):
        key = hashlib.sha1(p["file"].encode("utf-8")).hexdigest()[:12]
        cached = os.path.join(CACHE, key + ".jpg")
        try:
            if os.path.exists(cached):
                with open(cached, "rb") as f:
                    data = f.read()
            else:
                # Wikimedia 只允许标准缩略图宽度（1280 / 1920 / 3840 …）
                data = http_get(thumb_url(p["file"], 1920), binary=True)
                os.makedirs(CACHE, exist_ok=True)
                with open(cached, "wb") as f:
                    f.write(data)
                time.sleep(0.5)
            img = Image.open(io.BytesIO(data))
            e = save_versions(img, key, outdir)
        except Exception as ex:  # noqa: BLE001
            print("  ✗ %s (%s)" % (p["file"], ex))
            continue
        e.update({k: p[k] for k in ("cat", "title", "artist", "license", "page")})
        entries.append(e)
        print("  ✓ [%d/%d] %s" % (i + 1, len(picks), p["title"]))
    return entries


def build_local(outdir):
    """仓库根目录 photos/ 下自己的照片。"""
    src = os.path.join(ROOT, "photos")
    entries = []
    if not os.path.isdir(src):
        return entries
    for n in sorted(os.listdir(src)):
        if not n.lower().endswith((".jpg", ".jpeg", ".png", ".webp")) or n.startswith("."):
            continue
        key = "own-" + hashlib.sha1(n.encode("utf-8")).hexdigest()[:10]
        try:
            e = save_versions(Image.open(os.path.join(src, n)), key, outdir)
        except Exception as ex:  # noqa: BLE001
            print("  ✗ %s (%s)" % (n, ex))
            continue
        e.update({"cat": "own", "title": os.path.splitext(n)[0], "artist": "", "license": "", "page": ""})
        entries.append(e)
        print("  ✓ own %s" % n)
    return entries


def build_fake(n, outdir):
    """离线调试用：程序化生成“日落山景”。"""
    rnd = random.Random(7)
    entries = []
    for i in range(n):
        W, H = 2048, 1365
        top = [rnd.randint(10, 120) for _ in range(3)]
        bot = [rnd.randint(150, 255), rnd.randint(80, 200), rnd.randint(40, 160)]
        img = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(img)
        for y in range(H):
            t = y / H
            d.line([(0, y), (W, y)], fill=tuple(int(top[k] + (bot[k] - top[k]) * t) for k in range(3)))
        sx, sy = rnd.randint(300, W - 300), rnd.randint(300, 700)
        d.ellipse([sx - 90, sy - 90, sx + 90, sy + 90], fill=(255, 236, 200))
        for layer in range(4):
            base = 650 + layer * 160
            shade = tuple(int(c * (0.55 - layer * 0.12)) for c in bot)
            pts = [(0, H)]
            x = 0
            while x <= W:
                pts.append((x, base + rnd.randint(-160, 120)))
                x += rnd.randint(120, 260)
            pts += [(W, H)]
            d.polygon(pts, fill=shade)
        img = img.filter(ImageFilter.GaussianBlur(1.2))
        e = save_versions(img, "fake%02d" % i, outdir)
        e.update({"cat": "landscape", "title": "Test Landscape %d" % (i + 1),
                  "artist": "Generated", "license": "CC0", "page": ""})
        entries.append(e)
    return entries


# ---------------------------------------------------------------- 主流程

def copy_site():
    for f in SITE_FILES:
        src = os.path.join(ROOT, f)
        dst = os.path.join(DIST, f)
        if os.path.isdir(src):
            shutil.copytree(src, dst)
        elif os.path.exists(src):
            shutil.copy2(src, dst)
    open(os.path.join(DIST, ".nojekyll"), "w").close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fake", type=int, default=0, help="生成 N 张假图（离线调试）")
    ap.add_argument("--count", type=int, default=150, help="从 Commons 选取的图片数量")
    ap.add_argument("--seed", default="desk-calendar", help="选图随机种子（改了会换一批图）")
    args = ap.parse_args()

    if os.path.isdir(DIST):
        shutil.rmtree(DIST)
    os.makedirs(DIST)
    photo_dir = os.path.join(DIST, "photos")
    for t in ("m", "l", "b"):
        os.makedirs(os.path.join(photo_dir, t))

    copy_site()
    entries = build_local(photo_dir)
    if args.fake:
        entries += build_fake(args.fake, photo_dir)
    else:
        entries += build_commons(args.count, args.seed, photo_dir)

    if len(entries) < 2:
        print("图片太少（%d 张），构建失败" % len(entries))
        sys.exit(1)

    os.makedirs(os.path.join(DIST, "data"), exist_ok=True)
    with open(os.path.join(DIST, "data", "photos.js"), "w", encoding="utf-8") as f:
        f.write("/* 自动生成，请勿手改 */\nvar PHOTOS = ")
        json.dump(entries, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")
    print("完成：%d 张图片 -> %s" % (len(entries), DIST))


if __name__ == "__main__":
    main()

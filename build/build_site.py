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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import theme  # noqa: E402
from catalog import CATALOG, POEMS, TAGS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")
SITE_FILES = ["index.html", "manifest.webmanifest", "css", "js", "img", "fonts"]
API = "https://commons.wikimedia.org/w/api.php"
UA = "DeskCalendarBuilder/1.0 (personal desk calendar; https://github.com/hhoangxuan44-spec/iPadmini1)"

# 图片来源与配额：(类别, 来源, 配额)
#   "Commons:..."   精选图片画廊页
#   "search:xxx"    搜索名为 xxx 的分类，取其中（含一层子分类）的文件
SOURCES = [
    ("landscape", "Commons:Featured pictures/Places/Natural", 64),
    ("phenomena", "Commons:Featured pictures/Natural phenomena", 12),
    ("space", "Commons:Featured pictures/Astronomy", 9),
    ("animals", "Commons:Featured pictures/Animals/Mammals", 9),
    ("animals", "Commons:Featured pictures/Animals/Birds", 7),
    ("plants", "Commons:Featured pictures/Plants", 5),
    ("art", "search:Thirty-six Views of Mount Fuji Hokusai", 10),
    ("art", "search:Fifty-three Stations of the Tokaido Hiroshige", 8),
    ("art", "search:Paintings by Claude Monet", 6),
    ("art", "search:Landscape paintings by Vincent van Gogh", 5),
    ("art", "search:Seascapes by Ivan Aivazovsky", 5),
    ("art", "search:Shan shui paintings", 4),
    ("art", "search:Paintings by Caspar David Friedrich", 4),
]
# 文件名里出现这些词的图片不要（建筑设施、标本、人像等不适合做台历）
EXCLUDE = re.compile(r"observatory|station|cement|factory|frisur|portrait|museum|specimen|skull|"
                     r"diagram|map\b|chart|logo", re.I)

REPORT = []


def report(msg):
    print(msg)
    REPORT.append(msg)


try:  # 繁体说明转简体（可选依赖）
    from zhconv import convert as zh_convert
except ImportError:  # pragma: no cover
    def zh_convert(s, _lang):
        return s

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


def search_category(text):
    d = api({"action": "query", "list": "search", "srsearch": text, "srnamespace": "14", "srlimit": "5"})
    hits = [x["title"] for x in d.get("query", {}).get("search", [])]
    report("  search %r -> %s" % (text, hits[:5]))
    return hits[0] if hits else None


def category_files_deep(title, depth=1):
    files = list_category_files(title)
    if depth > 0:
        d = api({"action": "query", "list": "categorymembers", "cmtitle": title,
                 "cmtype": "subcat", "cmlimit": "100"})
        for sub in d.get("query", {}).get("categorymembers", [])[:40]:
            files += category_files_deep(sub["title"], depth - 1)
    return files


def source_files(src):
    if src.startswith("search:"):
        cat = search_category(src.split(":", 1)[1])
        return category_files_deep(cat) if cat else []
    if src.startswith("Category:"):
        return category_files_deep(src)
    return list_page_images(src)


def captions(pageids):
    """Commons 结构化说明（人工撰写的简短描述），优先中文。"""
    out = {}
    langs = ["zh-hans", "zh-cn", "zh", "zh-hant", "en"]
    ids = ["M%d" % i for i in pageids if i]
    for i in range(0, len(ids), 50):
        try:
            d = api({"action": "wbgetentities", "ids": "|".join(ids[i:i + 50]),
                     "props": "labels", "languages": "|".join(langs)})
        except Exception as e:  # noqa: BLE001
            print("  ! captions: %s" % e)
            continue
        for mid, ent in (d.get("entities") or {}).items():
            labels = ent.get("labels") or {}
            for lg in langs:
                if lg in labels and labels[lg].get("value"):
                    out[int(mid[1:])] = labels[lg]["value"].strip()
                    break
    return out


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
                ii["pageid"] = page.get("pageid")
                out[page["title"]] = ii
    return out


def clean_title(file_title, meta):
    name = strip_html((meta.get("ObjectName") or {}).get("value", ""))
    if not name or len(name) > 70:
        name = re.sub(r"^File:", "", file_title)
        name = re.sub(r"\.(jpe?g|png|tiff?|webp)$", "", name, flags=re.I)
    name = name.replace("_", " ")
    if True:
        name = re.sub(r"\(d\.j\.b\.?\)|\(actm\.?\)", "", name)
        name = re.sub(r"\b\d{1,4}[-.]\d{1,2}[-.]\d{2,4}\b\.?", "", name)           # 日期
        name = re.sub(r"\b(19|20)\d{2}\b", "", name)                               # 年份
        name = re.sub(r"\b(DSCF?|IMG|P|asv|img|DD)[ _-]?\d+\w*|\b(MC|edit(ed)?|crop(ped)?|LCCN\d+)\b", "", name, flags=re.I)
        name = re.sub(r"\(\s*\d*\s*\)|\b\d{3,}\b", "", name)                    # 编号
        name = re.sub(r"(\s|-)+\d{1,2}$", "", name.strip())                         # 结尾序号
        name = re.sub(r"\s*([,.])(\s*[,.])+", r"\1", name)
        name = re.sub(r"\s+([,.])", r"\1", name)
        name = re.sub(r"\s{2,}", " ", name).strip(" -–,.")
        if name.count("(") > name.count(")"):
            name += ")"
    return name[:70]


# ---------------------------------------------------------------- 图片处理

def vividness(img):
    """清透鲜亮程度 0-100：饱和度为主，亮度为辅。暗调、灰调、单色会明显偏低。"""
    hsv = img.convert("HSV").resize((320, 320), Image.LANCZOS)
    sat, val = hsv.split()[1], hsv.split()[2]
    s = ImageStat.Stat(sat).mean[0] / 255.0
    v = ImageStat.Stat(val).mean[0] / 255.0
    q = 100 * (s ** 0.7) * (0.35 + 0.65 * v)
    if v < 0.32:                      # 整体偏暗：额外压低，夜景仍会偶尔出现
        q *= 0.35 + 0.65 * (v / 0.32)
    return int(round(q))


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
    entry["v"] = vividness(img)
    small = img.copy()
    small.thumbnail((640, 640), Image.LANCZOS)
    entry["feat"] = theme.analyze(small)
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
                if not ii or got >= quota or EXCLUDE.search(f):
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
                    "pageid": ii.get("pageid"),
                    "desc": strip_html((meta.get("ImageDescription") or {}).get("value", ""))[:240],
                    "w": w, "h": h,
                    "thumb": ii.get("url", ""),
                })
                got += 1
            if got >= quota:
                break
        report("  %-10s %3d/%d" % (cat, got, quota))
        shortfall += max(0, quota - got)

    for cat, src, quota in SOURCES:
        q = max(1, round(quota * scale))
        try:
            files = source_files(src)
            report("%s: %d files" % (src, len(files)))
            take(cat, files, q)
        except Exception as e:  # noqa: BLE001
            report("! %s: %s" % (src, e))
            shortfall += q
    # 不够的用风景补齐
    if shortfall > 0:
        try:
            take("landscape", list_page_images(SOURCES[0][1]), shortfall)
        except Exception as e:  # noqa: BLE001
            print("! refill: %s" % e)

    caps = captions([c["pageid"] for c in chosen])
    for c in chosen:
        cap = caps.get(c.pop("pageid", None) or -1)
        if cap:
            cap = zh_convert(cap, "zh-cn").rstrip("。.")
            c["title"] = cap if len(cap) <= 60 else cap[:58].rstrip() + "…"
    return chosen


def thumb_url(file_title, width):
    name = re.sub(r"^File:", "", file_title).replace(" ", "_")
    return "https://commons.wikimedia.org/wiki/Special:FilePath/%s?width=%d" % (
        urllib.parse.quote(name), width)


def build_catalog(outdir):
    """按 catalog.py 精选图库构建。"""
    files = [c[0] for c in CATALOG]
    infos = image_infos(files)
    entries = []
    for i, (f, kind, title, sub, desc) in enumerate(CATALOG):
        ii = infos.get(f)
        if not ii:
            report("  ✗ 找不到 %s" % f)
            continue
        meta = ii.get("extmetadata", {})
        key = hashlib.sha1(f.encode("utf-8")).hexdigest()[:12]
        cached = os.path.join(CACHE, key + ".jpg")
        try:
            if os.path.exists(cached):
                with open(cached, "rb") as fh:
                    data = fh.read()
            else:
                # Wikimedia 只允许标准缩略图宽度（1280 / 1920 / 3840 …）
                data = http_get(thumb_url(f, 1920), binary=True)
                os.makedirs(CACHE, exist_ok=True)
                with open(cached, "wb") as fh:
                    fh.write(data)
                time.sleep(0.5)
            e = save_versions(Image.open(io.BytesIO(data)), key, outdir)
        except Exception as ex:  # noqa: BLE001
            report("  ✗ %s (%s)" % (f, ex))
            continue
        e.update({
            "kind": kind, "title": title, "sub": sub, "desc": desc,
            "poem": list(POEMS[title]) if title in POEMS else None,
            "tags": TAGS.get(title, "").split(),
            "artist": (strip_html((meta.get("Artist") or {}).get("value", "")) or "")[:60],
            "license": strip_html((meta.get("LicenseShortName") or {}).get("value", "")),
            "page": ii.get("descriptionurl", ""),
        })
        entries.append(e)
        print("  ✓ [%d/%d] %s" % (i + 1, len(CATALOG), title))
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
        e.update({"kind": "own", "title": os.path.splitext(n)[0], "sub": "我的照片", "desc": "",
                  "artist": "", "license": "", "page": ""})
        entries.append(e)
        print("  ✓ own %s" % n)
    return entries


def build_fake(n, outdir):
    """离线调试用：程序化生成的「照片」和「画作」（见 fake_scenes.py），覆盖明亮、昏暗、横幅、竖幅等情况。"""
    try:
        import fake_scenes
    except ImportError:  # 没有 numpy：退回最简单的山景
        fake_scenes = None
    descs = [
        "海拔五千多米的山口，冰川湖静卧在积雪的乱石之间。",
        "从布罗莫火山口边缘望去，巴托克山布满放射状的沟壑。晨光斜照，像一座被雕刻过的金色金字塔。",
        "石英砂岩被流水与风雨切割了亿万年，留下成千上万根拔地而起的石柱。云雾漫过时，峰林像一座漂浮的城，"
        "站在观景台上，能听见很远处的瀑布声，也能看见山谷里缓缓移动的云影。",
    ]
    poems = [("东临碣石，以观沧海。水何澹澹，山岛竦峙。", "曹操《观沧海》"), ("海日生残夜，江春入旧年。", "王湾《次北固山下》"),
             ("大漠孤烟直，长河落日圆。", "王维《使至塞上》"), None, ("空山新雨后，天气晚来秋。", "王维《山居秋暝》")]
    entries = []
    for i in range(n):
        if fake_scenes:
            fn, args, kind, title, sub, tags = fake_scenes.SCENES[i % len(fake_scenes.SCENES)]
            args = (args[0] + 100 * (i // len(fake_scenes.SCENES)),) + tuple(args[1:])
            img = fn(*args)
            if i >= len(fake_scenes.SCENES):
                title = "%s %d" % (title, i // len(fake_scenes.SCENES) + 1)
        else:
            img = Image.new("RGB", (2048, 1365), (90, 80, 120))
            kind, title, sub, tags = "nature", "测试风景 %d" % (i + 1), "离线测试", ["dusk"]
        e = save_versions(img, "fake%02d" % i, outdir)
        poem = poems[i % len(poems)]
        if kind in ("east", "west") and not poem:
            poem = poems[0]
        e.update({"kind": kind, "title": title, "sub": sub, "desc": descs[i % 3],
                  "poem": list(poem) if poem else None, "tags": tags,
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


def _size(rel):
    return os.path.getsize(os.path.join(DIST, rel))


def pick_offline(entries, budget_mb):
    """
    挑一部分图做「离线图包」（iPad 断网时只轮播这些）。
    按类别、再按风格轮流挑选，保证题材和设计都齐全，直到达到大小上限。
    """
    groups = {}
    for e in entries:
        key = (e.get("kind", ""), e["t"]["style"])
        groups.setdefault(key, []).append(e)
    for g in groups.values():
        g.sort(key=lambda e: hashlib.sha1(e["m"].encode()).hexdigest())
    keys = sorted(groups, key=lambda k: (k[0], k[1]))
    budget, used, n = budget_mb * 1024 * 1024, 0, 0
    while any(groups.values()):
        progressed = False
        for k in keys:
            if not groups[k]:
                continue
            e = groups[k].pop(0)
            size = _size(e["m"]) + _size(e["b"])
            if used + size > budget:
                continue
            e["o"] = 1
            used += size
            n += 1
            progressed = True
        if not progressed:
            break
    report("离线图包：%d 张，%.1f MB" % (n, used / 1048576.0))


def write_appcache(entries):
    """
    生成 offline.appcache（HTML5 Application Cache，iOS 9 Safari 支持）。
    列出的文件第一次打开时会存到 iPad 本地，之后断网也能运行。
    文件内容一变（网站更新），版本号就变，iPad 联网时会自动更新。
    """
    files = []
    for root, _, names in os.walk(DIST):
        for n in names:
            rel = os.path.relpath(os.path.join(root, n), DIST).replace(os.sep, "/")
            if rel.startswith("photos/") or rel in ("offline.appcache", ".nojekyll"):
                continue
            files.append(rel)
    for e in entries:
        if e.get("o"):
            files += [e["m"], e["b"]]
    files.sort()
    h = hashlib.sha1()
    for rel in files:
        with open(os.path.join(DIST, rel), "rb") as fh:
            h.update(rel.encode() + fh.read())
    total = sum(_size(f) for f in files)
    with open(os.path.join(DIST, "offline.appcache"), "w", encoding="utf-8") as fh:
        fh.write("CACHE MANIFEST\n# version %s\n\nCACHE:\n" % h.hexdigest()[:16])
        fh.write("\n".join(files))
        fh.write("\n\nNETWORK:\n*\n")
    report("离线缓存：%d 个文件，共 %.1f MB" % (len(files), total / 1048576.0))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fake", type=int, default=0, help="生成 N 张假图（离线调试）")
    ap.add_argument("--offline-mb", type=float, default=22, help="离线图包大小上限（MB）")
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
        entries += build_catalog(photo_dir)

    if len(entries) < 2:
        print("图片太少（%d 张），构建失败" % len(entries))
        sys.exit(1)

    theme.assign(entries)
    styles = {}
    for e in entries:
        e["t"] = e.pop("theme")
        for k in ("feat", "style", "lum", "accent"):
            e.pop(k, None)
        styles[e["t"]["style"]] = styles.get(e["t"]["style"], 0) + 1
    report("风格分布：%s" % styles)

    pick_offline(entries, args.offline_mb)

    os.makedirs(os.path.join(DIST, "data"), exist_ok=True)
    with open(os.path.join(DIST, "data", "photos.js"), "w", encoding="utf-8") as f:
        f.write("/* 自动生成，请勿手改 */\nvar PHOTOS = ")
        json.dump(entries, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")
    write_appcache(entries)
    report("完成：%d 张图片 -> %s" % (len(entries), DIST))
    with open(os.path.join(ROOT, "build", "report.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(REPORT) + "\n")


if __name__ == "__main__":
    main()

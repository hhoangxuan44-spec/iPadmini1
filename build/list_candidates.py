#!/usr/bin/env python3
"""列出候选图片及其 Commons 元数据（用于人工精选和撰写描述），不下载图片。

    python3 build/list_candidates.py            # 按下面的 SPEC 列出
已经在 catalog.py 里的图片会自动跳过。
"""
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_site as b  # noqa: E402
from catalog import CATALOG  # noqa: E402

# (类别, 来源, 最多列出几张)
#   类别 photo：横幅照片（宽高比 1.25~2.1，会铺满屏幕）
#   类别 art：  画作（宽高比 0.5~2.6 都行，会用「画廊装裱」完整展示）
SPEC = [
    ("photo", "search:Featured pictures of China", 40),
    ("photo", "search:Quality images of China", 30),
    ("photo", "search:Featured pictures of Japan", 20),
    ("photo", "search:Featured pictures of cityscapes", 25),
    ("photo", "search:Featured pictures of architecture", 25),
    ("photo", "search:Featured pictures of churches", 10),
    ("photo", "search:Featured pictures of bridges", 10),
    ("photo", "search:Featured pictures of Italy", 15),
    ("photo", "search:Featured pictures of Kyoto", 10),
    ("art", "search:36 Views of Mount Fuji", 25),
    ("art", "search:One Hundred Famous Views of Edo", 25),
    ("art", "search:Shan shui paintings in the Palace Museum", 25),
    ("art", "search:Shan shui paintings in the National Palace Museum", 20),
    ("art", "search:Bird-and-flower paintings in the Palace Museum", 15),
    ("art", "search:Paintings by Ivan Shishkin", 12),
    ("art", "search:Paintings by J. M. W. Turner", 12),
    ("art", "search:Paintings by Isaac Levitan", 10),
    ("art", "search:Paintings by Albert Bierstadt", 10),
    ("art", "search:Paintings by Johannes Vermeer", 6),
    ("art", "search:Water Lilies by Claude Monet", 8),
]


def discover():
    """列出精选图片画廊页（Places 开头），方便下次挑来源。"""
    pages, cont = [], {}
    while True:
        p = {"action": "query", "list": "allpages", "apnamespace": "4",
             "apprefix": "Featured pictures/Places", "aplimit": "500"}
        p.update(cont)
        d = b.api(p)
        pages += [x["title"] for x in d.get("query", {}).get("allpages", [])]
        if "continue" not in d:
            break
        cont = d["continue"]
    return pages


def main():
    have = set(c[0] for c in CATALOG)
    rnd = random.Random("more-2026")
    lines, seen, n = [], set(have), 0
    try:
        pages = discover()
        lines.append("## 画廊页：" + " | ".join(pages[:60]))
    except Exception as e:  # noqa: BLE001
        lines.append("## 画廊页查询失败：%s" % e)

    for cat, src, quota in SPEC:
        try:
            files = b.source_files(src)
        except Exception as e:  # noqa: BLE001
            lines.append("## %s 失败：%s" % (src, e))
            continue
        files = [f for f in files if f not in seen and re.search(r"\.(jpe?g|png|tiff?)$", f, re.I)]
        rnd.shuffle(files)
        lo, hi = (1.25, 2.1) if cat == "photo" else (0.5, 2.6)
        got = 0
        for i in range(0, min(len(files), quota * 5), 50):
            batch = files[i:i + 50]
            infos = b.image_infos(batch)
            for f in batch:
                ii = infos.get(f)
                if not ii or got >= quota or b.EXCLUDE.search(f):
                    continue
                w, h = ii.get("width", 0), ii.get("height", 0)
                if min(w, h) < 1200 or not (lo <= w / float(h) <= hi):
                    continue
                meta = ii.get("extmetadata", {})
                if not b.strip_html((meta.get("LicenseShortName") or {}).get("value", "")):
                    continue
                seen.add(f)
                got += 1
                desc = b.strip_html((meta.get("ImageDescription") or {}).get("value", ""))
                date = b.strip_html((meta.get("DateTimeOriginal") or {}).get("value", ""))[:40]
                artist = b.strip_html((meta.get("Artist") or {}).get("value", ""))[:50]
                lines.append("#%d|%s|%dx%d|%s" % (n, cat, w, h, f))
                lines.append("  A: %s | %s" % (artist, date))
                if desc:
                    lines.append("  D: %s" % desc.replace("\n", " ")[:200])
                n += 1
            if got >= quota:
                break
        lines.append("## %s → %d 张（共 %d 个文件）" % (src, got, len(files)))

    out = os.path.join(b.ROOT, "build", "candidates.txt")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()

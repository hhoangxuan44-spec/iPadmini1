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
#   类别 art：  画作（宽高比 0.4~2.6 都行，会用「画廊装裱」完整展示）
# 分成几组，每组单独运行（python build/list_candidates.py 组名），输出一行一张，方便挑选。
def P(*names, q=12):
    return [("photo", "search:Featured pictures of " + n, q) for n in names]


def A(*names, q=8):
    return [("art", "search:Paintings by " + n, q) for n in names]


GROUPS = {
    "nature": (
        P("mountains", "lakes", "waterfalls", "sunsets", "national parks", q=22)
        + P("glaciers", "deserts", "forests", "coasts", "beaches", "islands", "volcanoes", "canyons", "rivers",
            "auroras", "snow", "autumn", "trees", "lighthouses", "fog", q=14)
        + P("Iceland", "Norway", "New Zealand", "Switzerland", "Canada", "the United States", "Australia", "Africa",
            "Scotland", "Ireland", "Austria", "Greece", "Turkey", "India", "Nepal", "Indonesia", "Argentina",
            "Chile", "Namibia", "Croatia", "Slovenia", "Vietnam", "Thailand", q=9)
    ),
    "wild": (
        P("birds", "mammals", q=25)
        + P("owls", "eagles", "penguins", "whales", "big cats", "bears", "deer", "horses", "marine life", "fish",
            "wolves", "elephants", "primates", "kingfishers", "swans", q=8)
        + P("astronomy", "the Milky Way", "nebulae", "the Moon", "eclipses", "clouds", "lightning", "stars",
            "galaxies", "planets", "comets", q=10)
    ),
    "city": (
        P("skylines", "castles", "palaces", "cathedrals", "temples", q=18)
        + P("mosques", "pagodas", "gardens", "night photography", "interiors", "bridges", "towers", "streets",
            "harbours", "canals", "ruins", "windmills", q=10)
        + P("Paris", "Venice", "London", "Vienna", "Rome", "Istanbul", "Tokyo", "Shanghai", "Beijing", "Singapore",
            "Barcelona", "Florence", "Amsterdam", "Berlin", "Saint Petersburg", "Moscow", "Lisbon", "Prague",
            "Budapest", "Seoul", "Kyoto", "Nara", "Bangkok", "Dubai", "Cairo", "Jerusalem", "Santorini", q=7)
    ),
    "china": (
        P("Yunnan", "Tibet", "Sichuan", "Guangxi", "Xinjiang", "Hangzhou", "Suzhou", "Anhui", "Guizhou", "Gansu",
          "Inner Mongolia", "Qinghai", "Fujian", "Guangdong", "Hunan", "Hubei", "Shaanxi", "Shandong", "Yunnan",
          "Taiwan", "Hong Kong", "Macau", "the Forbidden City", "the Great Wall of China", "Mount Huangshan",
          "the Li River", "Chinese gardens", "Chinese architecture", "Xi'an", "Lhasa", "Chengdu", "Lijiang",
          "Dali", "Guilin", "Yangshuo", "Jiuzhaigou", "Mount Emei", "Fujian tulou", "Zhangjiajie", "Harbin",
          "Dunhuang", "Pingyao", "Hangzhou's West Lake", q=8)
        + [("photo", "search:Quality images of China", 25), ("photo", "search:Valued images of China", 15),
           ("photo", "search:Quality images of Taiwan", 12), ("photo", "search:Quality images of Hong Kong", 10)]
    ),
    "east": (
        A("Hokusai", "Hiroshige", "Utagawa Kuniyoshi", "Ogata Kōrin", "Itō Jakuchū", "Maruyama Ōkyo", "Sesshū Tōyō",
          "Kawanabe Kyōsai", "Katsushika Ōi", "Tsukioka Yoshitoshi", "Suzuki Harunobu", "Utamaro", "Sakai Hōitsu", q=8)
        + A("Zhu Da", "Shitao", "Wang Hui", "Wang Yuanqi", "Wen Zhengming", "Shen Zhou", "Tang Yin", "Qiu Ying",
            "Fan Kuan", "Ma Yuan", "Xia Gui", "Huang Gongwang", "Ni Zan", "Wang Meng", "Zhao Mengfu", "Yun Shouping",
            "Xu Wei", "Dong Yuan", "Mi Fu", "Su Shi", "Zhao Ji", "Lang Shining", "Gu Kaizhi", "Zhang Zeduan",
            "Wu Zhen", "Ren Xiong", "Wu Changshuo", "Qi Baishi", "Zhang Daqian", "Xu Beihong", "Yuan Jiang", q=6)
        + [("art", "search:Song dynasty paintings", 25), ("art", "search:Ming dynasty paintings", 25),
           ("art", "search:Qing dynasty paintings", 25), ("art", "search:Yuan dynasty paintings", 20),
           ("art", "search:Chinese paintings in the National Palace Museum", 25),
           ("art", "search:Chinese landscape paintings", 25), ("art", "search:Chinese flower paintings", 15),
           ("art", "search:Korean paintings", 12), ("art", "search:Ukiyo-e landscapes", 20),
           ("art", "search:Fifty-three Stations of the Tōkaidō (Hiroshige)", 15),
           ("art", "search:Sixty-nine Stations of the Kiso Kaidō", 10), ("art", "search:Eight Views of Ōmi", 5)]
    ),
    "west": (
        A("Claude Monet", "Vincent van Gogh", "Paul Cézanne", "Pierre-Auguste Renoir", "Alfred Sisley", "Camille Pissarro",
          "J. M. W. Turner", "John Constable", "Camille Corot", "Gustave Courbet", "Caspar David Friedrich",
          "Frederic Edwin Church", "Thomas Cole", "Jasper Francis Cropsey", "Ivan Aivazovsky", "Arkhip Kuindzhi",
          "Vasily Polenov", "Fyodor Vasilyev", "Alexei Savrasov", "Thomas Moran", "Winslow Homer", "James Abbott McNeill Whistler",
          "John Singer Sargent", "Gustav Klimt", "Edvard Munch", "Paul Gauguin", "Georges Seurat", "Paul Signac",
          "Henri Rousseau", "Gustave Caillebotte", "Édouard Manet", "Eugène Boudin", "Charles-François Daubigny",
          "Jacob van Ruisdael", "Meindert Hobbema", "Claude Lorrain", "Canaletto", "Francesco Guardi",
          "Pieter Bruegel the Elder", "Ferdinand Hodler", "Peder Balke", "Johan Christian Dahl", "Jean-François Millet",
          "Berthe Morisot", "Albert Bierstadt", "Martin Johnson Heade", "John Frederick Kensett", "Fitz Henry Lane",
          "Ivan Shishkin", "Isaac Levitan", "Ilya Repin", "Nicholas Roerich", "Akseli Gallen-Kallela",
          "Carl Larsson", "Anders Zorn", "Hiroshi Yoshida", "William Turner of Oxford", "Gustave Doré",
          "Frederic Remington", "Vincent van Gogh's landscapes", q=8)
    ),
}
SPEC = sum(GROUPS.values(), [])


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
    group = sys.argv[1] if len(sys.argv) > 1 else "all"
    spec = SPEC if group == "all" else GROUPS[group]
    have = set(c[0] for c in CATALOG)
    rnd = random.Random("more-2026")
    lines, seen, n = [], set(have), 0

    for cat, src, quota in spec:
        try:
            files = b.source_files(src)
        except Exception as e:  # noqa: BLE001
            lines.append("## %s 失败：%s" % (src, e))
            continue
        files = [f for f in files if f not in seen and re.search(r"\.(jpe?g|png|tiff?)$", f, re.I)]
        rnd.shuffle(files)
        lo, hi = (1.25, 2.1) if cat == "photo" else (0.4, 2.6)
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
                lines.append("#%d|%s|%dx%d|%s|%s|%s|%s" % (n, cat[0], w, h, f[5:], artist[:30], date[:12], desc.replace("\n", " ")[:150]))
                n += 1
            if got >= quota:
                break
        lines.append("## %s → %d 张（共 %d 个文件）" % (src, got, len(files)))

    out = os.path.join(b.ROOT, "build", "candidates.txt")
    print("\n".join(lines))
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()

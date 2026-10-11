#!/usr/bin/env python3
"""列出候选图片及其 Commons 元数据（用于人工精选和撰写描述），不下载图片。

    python3 build/list_candidates.py 320
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_site as b  # noqa: E402


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 320
    picks = b.pick_commons(n, "desk-calendar")
    lines = []
    for i, p in enumerate(picks):
        lines.append("#%d|%s|%dx%d|%s" % (i, p["cat"], p["w"], p["h"], p["file"]))
        lines.append("  T: %s" % p["title"])
        if p.get("desc"):
            lines.append("  D: %s" % p["desc"].replace("\n", " ")[:220])
    out = os.path.join(b.ROOT, "build", "candidates.txt")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""把 build/more/*.py 里的手写条目（按候选序号）解析成 catalog_more.py（按文件名）。
用法：python build/gen_more.py <候选目录>   候选目录里放 cand_<组>.txt"""
import os, sys, glob, pprint
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "more"))
sys.path.insert(0, HERE)
import more_util
for f in sorted(glob.glob(os.path.join(HERE, "more", "*.py"))):
    n = os.path.basename(f)
    if n != "more_util.py":
        exec(compile(open(f, encoding="utf-8").read(), f, "exec"), {})
import catalog as old

cdir = sys.argv[1]
KINDS = {"nature", "sky", "animal", "east", "west", "city", "china"}
titles = set(e[2] for e in old.CATALOG)
files = set(e[0] for e in old.CATALOG)
cat, poems, tags, errs = [], {}, {}, []
for g, ents in more_util.ENTRIES.items():
    names = {}
    for ln in open(os.path.join(cdir, "cand_%s.txt" % g), encoding="utf-8"):
        p = ln.rstrip("\n").split("|")
        if p[0].startswith("#") and p[0][1:].isdigit():
            names[int(p[0][1:])] = p[3]
    for e in ents:
        fn = names.get(e["idx"])
        if not fn:
            errs.append("%s#%s 无候选" % (g, e["idx"])); continue
        fn = "File:" + fn
        if e["kind"] not in KINDS: errs.append("%s kind %s" % (e["title"], e["kind"]))
        if e["title"] in titles: errs.append("标题重复 " + e["title"]); continue
        if fn in files: errs.append("文件重复 %s %s" % (g, e["title"])); continue
        titles.add(e["title"]); files.add(fn)
        cat.append((fn, e["kind"], e["title"], e["sub"], e["desc"]))
        if e.get("poem"): poems[e["title"]] = tuple(e["poem"])
        if e.get("tags"): tags[e["title"]] = e["tags"]
for x in errs: print("!", x)
with open(os.path.join(HERE, "catalog_more.py"), "w", encoding="utf-8") as f:
    f.write("# -*- coding: utf-8 -*-\n# 由 gen_more.py 生成\n\nCATALOG_MORE = ")
    pprint.pprint(cat, stream=f, width=110)
    f.write("\nPOEMS_MORE = "); pprint.pprint(poems, stream=f, width=110)
    f.write("\nTAGS_MORE = "); pprint.pprint(tags, stream=f, width=110)
print(len(cat), "条；诗", len(poems), "标签", len(tags))

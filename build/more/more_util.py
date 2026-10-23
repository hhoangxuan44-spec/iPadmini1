# -*- coding: utf-8 -*-
"""手写条目的收集器：E(序号, 类型, 标题, 副标题, 描述, 标签, 诗句)。序号是候选列表里的 #n。"""
import sys

ENTRIES = {}
CUR = [None]


def group(name):
    CUR[0] = name
    ENTRIES.setdefault(name, [])


def E(idx, kind, title, sub, desc, tags="", poem=None):
    ENTRIES[CUR[0]].append(dict(idx=idx, kind=kind, title=title, sub=sub, desc=desc, tags=tags, poem=poem))

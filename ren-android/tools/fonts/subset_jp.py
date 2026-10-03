#!/usr/bin/env python3
"""Fetches Noto Serif JP (SIL OFL) subset to the characters Ren can show.

Ren's Japanese comes only from its own content (curriculum, stories) and from kana typed on its
flick keyboard, so a subset covers everything at a fraction of the full font's size. Anything
missing still renders through Android's system fallback. Rerun after changing tools/seed/content.
"""
import os
import re
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CONTENT = os.path.join(ROOT, "tools", "seed", "content")
OUT = os.path.join(ROOT, "app", "src", "main", "res", "font")
UA = "Mozilla/5.0 (Linux; Android 8.0)"  # old enough that Google Fonts serves TTF


def chars():
    text = ""
    for name in os.listdir(CONTENT):
        with open(os.path.join(CONTENT, name), encoding="utf-8") as f:
            text += f.read()
    kana = "".join(chr(c) for c in range(0x3041, 0x3097)) + "".join(chr(c) for c in range(0x30A1, 0x30FB)) + "ー・"
    punct = "、。「」『』（）！？：〜…　"
    keep = {c for c in text if ord(c) > 0x2FFF} | set(kana) | set(punct)
    return "".join(sorted(keep))


def fetch(weight, name):
    text = chars()
    url = "https://fonts.googleapis.com/css2?family=Noto+Serif+JP:wght@%d&text=%s" % (weight, urllib.parse.quote(text))
    css = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60).read().decode()
    src = re.search(r"url\((https://[^)]+)\)", css).group(1)
    data = urllib.request.urlopen(src, timeout=60).read()
    path = os.path.join(OUT, name)
    with open(path, "wb") as f:
        f.write(data)
    print(f"{name}: {len(text)} characters, {len(data) // 1024} KB")


if __name__ == "__main__":
    fetch(400, "noto_serif_jp_regular.ttf")
    fetch(600, "noto_serif_jp_semibold.ttf")

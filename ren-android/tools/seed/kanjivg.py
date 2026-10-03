"""Fetches and parses KanjiVG stroke data (CC BY-SA 3.0, http://kanjivg.tagaini.net)."""
import os
import re
import time
import urllib.request
import xml.etree.ElementTree as ET

CACHE = os.path.join(os.path.dirname(__file__), ".cache", "kanjivg")
BASE = "https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji/"
KVG = "{http://kanjivg.tagaini.net}"
SVG = "{http://www.w3.org/2000/svg}"

# Radical forms that KanjiVG files under their full character.
ALIASES = {"⻌": "辶"}


def _fetch(ch: str) -> str:
    os.makedirs(CACHE, exist_ok=True)
    name = f"{ord(ALIASES.get(ch, ch)):05x}.svg"
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        for attempt in range(5):
            try:
                with urllib.request.urlopen(BASE + name, timeout=60) as r:
                    data = r.read()
                break
            except OSError:
                if attempt == 4:
                    raise
                time.sleep(2 ** attempt)
        with open(path, "wb") as f:
            f.write(data)
    with open(path, encoding="utf-8") as f:
        return f.read()


def load(ch: str):
    """Returns (paths, groups).

    paths: list of SVG path strings, one per stroke, in stroke order (109x109 box).
    groups: every nested component group as
            {"e": element, "orig": original, "part": part, "p": position, "s": [stroke idx], "depth": n}.
    """
    raw = _fetch(ch)
    raw = re.sub(r"<!DOCTYPE.*?\]>", "", raw, flags=re.S)
    raw = re.sub(r'xmlns:kvg="[^"]*"', 'xmlns:kvg="http://kanjivg.tagaini.net"', raw)
    root = ET.fromstring(raw)
    stroke_group = next(g for g in root.iter(SVG + "g") if g.get("id", "").startswith("kvg:StrokePaths"))
    path_nodes = list(stroke_group.iter(SVG + "path"))
    paths = [p.get("d") for p in path_nodes]
    index = {id(p): i for i, p in enumerate(path_nodes)}
    top = next(iter(stroke_group))
    groups = []

    def walk(node, depth, inherited_pos):
        for child in node:
            if child.tag != SVG + "g":
                continue
            pos = child.get(KVG + "position") or inherited_pos
            groups.append({
                "e": child.get(KVG + "element") or "",
                "orig": child.get(KVG + "original") or "",
                "part": child.get(KVG + "part") or "",
                "p": pos or "",
                "s": [index[id(p)] for p in child.iter(SVG + "path")],
                "depth": depth,
            })
            walk(child, depth + 1, pos)

    walk(top, 1, "")
    return paths, groups

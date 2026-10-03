#!/usr/bin/env python3
"""Builds app/src/main/assets/database/ren_core.db, the read-only seed Room copies on first launch (spec §9).

Inputs
  content/*.tsv, content/stories.txt   hand-written curriculum (this repo)
  app/schemas/.../<version>.json       Room's exported schema, so tables match Room exactly
  KanjiVG      stroke order paths      CC BY-SA 3.0  http://kanjivg.tagaini.net
  KANJIDIC2    on/kun readings, JLPT   CC BY-SA 4.0  https://www.edrdg.org/kanjidic/kanjidic2.html
  Kanjium      pitch accents           CC BY-SA 4.0  https://github.com/mifunetoshiro/kanjium

Run from the repository root after any content or schema change:
  python3 tools/seed/build_seed_db.py
Downloads are cached in tools/seed/.cache. The script fails loudly on content that would break the app.
"""
import gzip
import hashlib
import json
import os
import sqlite3
import sys
import urllib.request
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import kanjivg  # noqa: E402

CONTENT = os.path.join(HERE, "content")
CACHE = os.path.join(HERE, ".cache")
SCHEMA_DIR = os.path.join(ROOT, "app", "schemas", "app.ren.data.local.RenDatabase")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "database", "ren_core.db")
NOT_INTRODUCED = 2**63 - 1
WORDS_PER_KANJI = 3

warnings = []


def warn(msg):
    warnings.append(msg)


def die(msg):
    sys.exit(f"error: {msg}")


def download(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print(f"downloading {url}")
        with urllib.request.urlopen(url, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def rows(name):
    """Yields (section, fields) for each non-comment line of a pipe-separated content file."""
    section = None
    with open(os.path.join(CONTENT, name), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            if line.startswith("@"):
                section = line[1:].strip()
                continue
            yield section, split_fields(line)


def split_fields(line):
    """Splits on '|' outside {ruby|markup} braces."""
    fields, depth, cur = [], 0, []
    for c in line:
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        if c == "|" and depth == 0:
            fields.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
    fields.append("".join(cur).strip())
    return fields


def cp(ch):
    return "".join(f"{ord(c):04x}" for c in ch)


def short_hash(*parts):
    return hashlib.sha1("|".join(parts).encode()).hexdigest()[:10]


def is_kanji(c):
    return "一" <= c <= "鿿" or "㐀" <= c <= "䶿" or c == "々"


def to_hiragana(s):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


# ---------------------------------------------------------------- reference data

def load_kanjidic(chars):
    path = download("http://www.edrdg.org/kanjidic/kanjidic2.xml.gz", "kanjidic2.xml.gz")
    out = {}
    with gzip.open(path) as f:
        for _, el in ET.iterparse(f):
            if el.tag != "character":
                continue
            lit = el.findtext("literal")
            if lit in chars:
                rm = el.find("reading_meaning")
                on, kun = [], []
                if rm is not None:
                    for r in rm.iter("reading"):
                        if r.get("r_type") == "ja_on":
                            on.append(r.text)
                        elif r.get("r_type") == "ja_kun":
                            kun.append(r.text)
                jlpt = el.findtext("misc/jlpt")
                out[lit] = {"on": on[:3], "kun": kun[:4], "jlpt": int(jlpt) if jlpt else None}
            el.clear()
    return out


def load_accents():
    path = download(
        "https://raw.githubusercontent.com/mifunetoshiro/kanjium/master/data/source_files/raw/accents.txt",
        "accents.txt")
    acc = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            word, reading, pattern = parts[0], parts[1], parts[2]
            first = pattern.split(",")[0]
            if first.isdigit():
                acc.setdefault((word, to_hiragana(reading or word)), int(first))
    return acc


# ---------------------------------------------------------------- markup

def parse_markup(markup):
    """Mirror of RubyMarkup.parse in the app: returns [(text, reading|None, lemma|None)]."""
    out, i = [], 0
    while i < len(markup):
        if markup[i] == "{":
            j = markup.index("}", i)
            fields = markup[i + 1:j].split("|")
            base = fields[0]
            reading = fields[1] if len(fields) > 1 and fields[1] else None
            lemma = fields[2] if len(fields) > 2 and fields[2] else base
            out.append((base, reading, lemma))
            i = j + 1
        else:
            j = markup.find("{", i)
            j = len(markup) if j < 0 else j
            out.append((markup[i:j], None, None))
            i = j
    return out


def morae_count(text):
    small = "ゃゅょぁぃぅぇぉャュョァィゥェォ"
    return sum(1 for c in text if c not in small and not c.isspace() and c not in "。、「」！？・：　")


# ---------------------------------------------------------------- components

# KanjiVG names a few shapes by their written variant.
ELEMENT_ALIASES = {"止": {"龰"}}


def match_components(char, parts, groups, part_ids, stroke_total, part_strokes):
    """Maps authored parts to KanjiVG stroke groups without overlap.

    Falls back to an unnamed group, then to leftover ungrouped strokes, whose stroke count matches the part.
    """
    used, comps = set(), []
    top_level = set()
    for g in groups:
        if g["depth"] == 1:
            top_level |= set(g["s"])
    for part in parts:
        if part == char:
            continue
        names = {part} | ELEMENT_ALIASES.get(part, set())
        cands = [g for g in groups if ({g["e"], g["orig"]} & names) and not (set(g["s"]) & used)]
        strokes, pos = None, ""
        if cands:
            best = min(cands, key=lambda g: (g["depth"], g["s"][0]))
            strokes, pos = set(best["s"]), best["p"]
            if best["part"]:
                # KanjiVG splits enclosing shapes (囗, 門…) into numbered pieces at the same depth.
                for g in groups:
                    if (g["e"] == best["e"] and g["part"] and g["depth"] == best["depth"]
                            and not (set(g["s"]) & used)):
                        strokes |= set(g["s"])
        else:
            n = part_strokes.get(part)
            unnamed = [g for g in groups if not g["e"] and len(g["s"]) == n and not (set(g["s"]) & used)]
            loose = [i for i in range(stroke_total) if i not in top_level and i not in used]
            if unnamed:
                strokes, pos = set(unnamed[0]["s"]), unnamed[0]["p"]
            elif n and len(loose) == n:
                strokes = set(loose)
        if not strokes:
            warn(f"{char}: part {part} has no stroke group in KanjiVG")
            continue
        used |= strokes
        comps.append({"e": part, "r": part_ids[part], "p": pos, "s": sorted(strokes)})
    return comps


# ---------------------------------------------------------------- build

def main():
    schema_files = sorted(f for f in os.listdir(SCHEMA_DIR) if f.endswith(".json"))
    schema = json.load(open(os.path.join(SCHEMA_DIR, schema_files[-1]), encoding="utf-8"))["database"]

    # Kana --------------------------------------------------------------------------
    kana = []
    for section, f in rows("kana.tsv"):
        ch, romaji, mnemonic, word, meaning = f
        paths, _ = kanjivg.load(ch)
        kana.append({"id": f"kana_{cp(ch)}", "character": ch, "romaji": romaji,
                     "script": "HIRAGANA" if section == "hiragana" else "KATAKANA",
                     "strokeCount": len(paths), "strokePathsSvg": "\n".join(paths),
                     "mnemonic": mnemonic, "exampleWord": word, "exampleMeaning": meaning})

    # Radicals ------------------------------------------------------------------------
    radicals = []
    for _, f in rows("radicals.tsv"):
        ch, meaning, story = f
        paths, _ = kanjivg.load(ch)
        radicals.append({"id": f"rad_{cp(ch)}", "character": ch, "englishMeaning": meaning,
                         "strokeCount": len(paths), "strokePathsSvg": "\n".join(paths),
                         "mnemonicStory": story, "visualAssetPath": ""})
    radical_ids = {r["character"]: r["id"] for r in radicals}
    if len(radical_ids) != len(radicals):
        die("duplicate radical")

    # Kanji ---------------------------------------------------------------------------
    kanji_rows = [(int(section.split()[1]), f) for section, f in rows("kanji.tsv")]
    chars = {f[0] for _, f in kanji_rows}
    if len(chars) != len(kanji_rows):
        die("duplicate kanji")
    dic = load_kanjidic(chars)
    kanji, part_ids = [], dict(radical_ids)
    part_strokes = {r["character"]: r["strokeCount"] for r in radicals}
    for stage, f in kanji_rows:
        ch, meaning, parts_s, story = f
        paths, groups = kanjivg.load(ch)
        parts = [] if parts_s == "-" else parts_s.split(",")
        for p in parts:
            if p not in part_ids and p != ch:
                die(f"{ch}: part {p} is neither a radical nor an earlier kanji")
        comps = match_components(ch, parts, groups, part_ids, len(paths), part_strokes)
        if parts == [ch] and ch in radical_ids:
            comps = [{"e": ch, "r": radical_ids[ch], "p": "", "s": list(range(len(paths)))}]
        ids = [part_ids[p] if p != ch else radical_ids.get(ch) for p in parts]
        ids = [i for i in ids if i]
        names = {r["id"]: r["englishMeaning"] for r in radicals}
        names.update({k["id"]: k["primaryMeaning"] for k in kanji})
        if parts and parts != [ch]:
            equation = " + ".join(f"{p} ({names[part_ids[p]]})" for p in parts) + f" = {ch} ({meaning})"
        else:
            equation = f"{ch} ({meaning})"
        info = dic.get(ch) or die(f"{ch} missing from KANJIDIC2")
        jlpt = {4: 5, 3: 4, 2: 3, 1: 1}.get(info["jlpt"], 5 if stage == 2 else 4)
        kanji.append({"id": f"kanji_{cp(ch)}", "character": ch, "primaryMeaning": meaning,
                      "onyomi": json.dumps(info["on"], ensure_ascii=False),
                      "kunyomi": json.dumps(info["kun"], ensure_ascii=False),
                      "strokeCount": len(paths), "strokePathsSvg": "\n".join(paths),
                      "radicalIds": json.dumps(ids), "mnemonicEquation": equation, "jlptLevel": jlpt,
                      "mnemonicStory": story, "componentsJson": json.dumps(comps, ensure_ascii=False),
                      "_stage": stage})
        part_strokes.setdefault(ch, len(paths))
        part_ids.setdefault(ch, f"kanji_{cp(ch)}")  # radicals win when both exist
    kanji_by_char = {k["character"]: k for k in kanji}
    kanji_order = {k["character"]: i for i, k in enumerate(kanji)}

    # Vocabulary ----------------------------------------------------------------------
    accents = load_accents()
    vocab = []

    # Kanjium files these under their kanji spellings.
    spelled = {"おいしい": "美味しい", "ありがとう": "有難う", "すみません": "済みません", "どこ": "何処",
               "ください": "下さい", "よろしく": "宜しく", "はじめまして": "初めまして", "おにぎり": "お握り",
               "ごちそうさま": "御馳走様", "とても": "迚も"}

    def pitch(text, reading):
        r = to_hiragana(reading)
        for key in (text, spelled.get(text), reading):
            if key and (key, r) in accents:
                return accents[(key, r)]
        return -1

    def kanji_ids_of(text):
        out = []
        for c in text:
            if is_kanji(c):
                if c not in kanji_by_char:
                    die(f"word {text} uses {c}, which is not in the curriculum")
                out.append(kanji_by_char[c]["id"])
        return list(dict.fromkeys(out))

    for _, f in rows("vocab.tsv"):
        text, reading, meaning, ex_jp, ex_en = f
        p = pitch(text, reading)
        if p < 0:
            warn(f"no pitch accent for {text} ({reading})")
        vocab.append({"id": f"vocab_{short_hash(text, reading)}", "text": text, "reading": reading,
                      "pitchAccentPattern": p, "englishMeaning": meaning,
                      "kanjiIds": json.dumps(kanji_ids_of(text)), "audioAssetPath": "",
                      "exampleSentenceJp": ex_jp, "exampleSentenceEn": ex_en, "cardType": "SINGLE_WORD"})
    vocab_by_text = {}
    for v in vocab:
        if v["text"] in vocab_by_text:
            die(f"duplicate word {v['text']}")
        vocab_by_text[v["text"]] = v

    collocations = []
    for _, f in rows("collocations.tsv"):
        (phrase, reading, meaning, noun, noun_r, noun_g, particle, role, verb, verb_r, verb_g,
         note, ex_jp, ex_en) = f
        vid = f"col_{short_hash(phrase, reading)}"
        vocab.append({"id": vid, "text": phrase, "reading": reading, "pitchAccentPattern": -1,
                      "englishMeaning": meaning, "kanjiIds": json.dumps(kanji_ids_of(phrase)),
                      "audioAssetPath": "", "exampleSentenceJp": ex_jp, "exampleSentenceEn": ex_en,
                      "cardType": "COLLOCATION"})
        collocations.append({"vocabId": vid, "noun": noun, "nounReading": noun_r, "nounGloss": noun_g,
                             "particle": particle, "particleRole": role, "verb": verb, "verbReading": verb_r,
                             "verbGloss": verb_g, "note": note})

    # Lesson units ----------------------------------------------------------------------
    units, cards, seq = [], [], 0

    def add_unit(uid, utype, stage, title, item_ids, item_types):
        nonlocal seq
        units.append({"id": uid, "type": utype, "stage": stage, "sequence": seq, "title": title,
                      "itemIds": json.dumps(item_ids), "completedAt": None})
        for iid, itype in zip(item_ids, item_types):
            cards.append(card(iid, itype, uid, stage, seq))
        seq += 1

    def card(iid, itype, uid, stage, sequence):
        return {"itemId": iid, "itemType": itype, "state": 0, "stability": 0.0, "difficulty": 0.0,
                "elapsedDays": 0, "scheduledDays": 0, "reps": 0, "lapses": 0, "lastReviewTimestamp": 0,
                "dueTimestamp": NOT_INTRODUCED, "unitId": uid, "stage": stage, "sequence": sequence}

    rows_of_five = ["あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの", "はひふへほ",
                    "まみむめも", "やゆよ", "らりるれろ", "わをん"]
    kana_by_char = {k["character"]: k for k in kana}
    for script in ("HIRAGANA", "KATAKANA"):
        for row in rows_of_five:
            chars_ = row if script == "HIRAGANA" else "".join(chr(ord(c) + 0x60) for c in row)
            ids = [kana_by_char[c]["id"] for c in chars_]
            add_unit(f"unit_kana_{cp(chars_[0])}", "KANA_GROUP", 0, " ".join(chars_), ids, ["KANA"] * len(ids))

    for r in radicals:
        add_unit(f"unit_{r['id']}", "RADICAL", 1, r["character"], [r["id"]], ["RADICAL"])

    def last_kanji(text):
        ks = [c for c in text if is_kanji(c)]
        return max(ks, key=lambda c: kanji_order[c]) if ks else None

    words_by_kanji = {}
    for v in vocab:
        if v["cardType"] != "SINGLE_WORD":
            continue
        lk = last_kanji(v["text"])
        if lk:
            words_by_kanji.setdefault(lk, []).append(v)
    colloc_by_kanji = {}
    for c in collocations:
        v = next(x for x in vocab if x["id"] == c["vocabId"])
        colloc_by_kanji.setdefault(last_kanji(v["text"]), []).append(v)

    attached = set()
    for k in kanji:
        words = words_by_kanji.get(k["character"], [])[:WORDS_PER_KANJI]
        if not words:
            warn(f"{k['character']} has no anchor word")
        ids = [k["id"]] + [w["id"] for w in words]
        attached |= {w["id"] for w in words}
        add_unit(f"unit_{k['id']}", "KANJI", k["_stage"], k["character"], ids, ["KANJI"] + ["VOCAB"] * len(words))
        for cv in colloc_by_kanji.get(k["character"], []):
            add_unit(f"unit_{cv['id']}", "COLLOCATION", k["_stage"], cv["text"], [cv["id"]], ["VOCAB"])
            attached.add(cv["id"])

    # Words outside units can still be added from the Reader.
    for v in vocab:
        if v["id"] in attached:
            continue
        lk = last_kanji(v["text"])
        stage = kanji_by_char[lk]["_stage"] if lk else 0
        cards.append(card(v["id"], "VOCAB", "", stage, 100_000 + len(cards)))

    # Stories -----------------------------------------------------------------------
    stories, current = [], None
    with open(os.path.join(CONTENT, "stories.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("=="):
                sid, cat, title, title_en, icon, tint = split_fields(line[2:])
                current = {"id": sid, "category": cat, "title": title, "titleEn": title_en,
                           "iconChar": icon, "tint": tint, "sentences": []}
                stories.append(current)
            else:
                jp, en = [x.strip() for x in line.split("||")]
                for text, _, lemma in parse_markup(jp):
                    if lemma and lemma not in vocab_by_text:
                        die(f"story {current['id']}: word {lemma} is not in vocab.tsv")
                current["sentences"].append({"jp": jp, "en": en})
    story_rows = []
    for i, s in enumerate(stories):
        morae = sum(morae_count("".join((r or t) for t, r, _ in parse_markup(x["jp"]))) for x in s["sentences"])
        duration = round(morae / 6.0 + 0.9 * len(s["sentences"]))
        story_rows.append({"id": s["id"], "title": s["title"], "titleEn": s["titleEn"], "category": s["category"],
                           "iconChar": s["iconChar"], "tint": s["tint"], "durationSec": max(10, duration),
                           "audioAssetPath": "", "sentencesJson": json.dumps(s["sentences"], ensure_ascii=False),
                           "sequence": i})

    # Write ---------------------------------------------------------------------------
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for q in schema["setupQueries"]:
        db.execute(q)

    def insert(table, items):
        if not items:
            return
        cols = [c for c in items[0] if not c.startswith("_")]
        db.executemany(f"INSERT INTO {table} ({','.join(cols)}) VALUES ({','.join('?' * len(cols))})",
                       [[it[c] for c in cols] for it in items])

    insert("kana", kana)
    insert("radicals", radicals)
    insert("kanji", kanji)
    insert("vocabulary", vocab)
    insert("collocations", collocations)
    insert("stories", story_rows)
    insert("lesson_units", units)
    insert("fsrs_cards", cards)
    db.execute(f"PRAGMA user_version = {schema['version']}")
    db.commit()
    db.execute("VACUUM")
    db.close()

    for w in warnings:
        print("warning:", w)
    stage_counts = {}
    for u in units:
        stage_counts[u["stage"]] = stage_counts.get(u["stage"], 0) + 1
    print(f"kana {len(kana)}, radicals {len(radicals)}, kanji {len(kanji)}, words {len(vocab)} "
          f"({len(collocations)} pairs), stories {len(stories)}, units {len(units)} {stage_counts}, "
          f"cards {len(cards)}")
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({os.path.getsize(OUT) // 1024} KB)")


if __name__ == "__main__":
    main()

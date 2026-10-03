# Third-party data and fonts

The bundled database `app/src/main/assets/database/ren_core.db` is built by `tools/seed/build_seed_db.py`
from the sources below. Because it adapts share-alike data, the database itself is distributed under
CC BY-SA 4.0 (compatible with the CC BY-SA 3.0 KanjiVG terms).

| Source | Used for | Licence |
|---|---|---|
| [KanjiVG](http://kanjivg.tagaini.net), © Ulrich Apel | Stroke paths and component groups for every kana, radical and kanji; the app icon | CC BY-SA 3.0 |
| [KANJIDIC2](https://www.edrdg.org/kanjidic/kanjidic2.html), Electronic Dictionary Research and Development Group | On and kun readings, JLPT levels | CC BY-SA 4.0 |
| [Kanjium](https://github.com/mifunetoshiro/kanjium) | Tokyo pitch-accent numbers | CC BY-SA 4.0 |
| [Geist](https://github.com/vercel/geist-font), © The Geist Project Authors | Interface typeface | SIL Open Font License 1.1, see `licenses/Geist-OFL.txt` |
| [Newsreader](https://github.com/productiontype/Newsreader), © The Newsreader Project Authors | Reading typeface for English text | SIL Open Font License 1.1, see `licenses/Newsreader-OFL.txt` |
| [Noto Serif JP](https://github.com/notofonts/noto-cjk), © Adobe | Mincho glyphs for stories and sentences, subset by `tools/fonts/subset_jp.py` | SIL Open Font License 1.1, see `licenses/NotoSerifJP-OFL.txt` |

Mnemonic stories, example sentences, stories and all other curriculum text in `tools/seed/content/`
were written for this project.

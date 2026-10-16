#!/usr/bin/env python3
"""
Translation I/O for the help pages: the Markdown in
shared/src/commonMain/composeResources/files/help, which the app and the docs site both show.
strings_sync.py does the same job for strings.xml, and this keeps its commands and file shapes:

    languages                      how much help work each language has
    fetch   --lang X | --all       write <code>-help-untranslated.json (the work to do) and
                                   <code>-help-translated.json (every up-to-date block)
    validate --lang X --file F     check a {unit: translation} file before applying it
    apply   --lang X --file F | --all --out-dir D [--revise]
                                   write translations into help-<page>.<lang>.md; --revise
                                   also takes fixes to up-to-date blocks, from a review
    acknowledge --lang X --page P  mark a page as checked against the current English
    check                          every translation is marked and lines up with its English

How a translation knows what it was translated from: each help-<page>.<lang>.md has a
`translated-from:` entry in its front matter, the git blob id of the English page it matches.
`apply` sets it when it brings a page up to date, and nothing else does, so a hand edit (a typo
fix, say) never hides an English change the page hasn't caught up with. A blob id is the file's
content hash, so it survives the pages moving directory.

A page is split into blocks at blank lines. Every translation has the same blocks as the English
it was translated from, in the same order (`check` enforces it), so comparing that English with
today's, block by block, says exactly which blocks of the translation are stale:

  - stale        blocks whose English changed, was added or removed, with `was` (the English
                 they were translated from), `source` (the English now) and `target` (the
                 current translation of `was`)
  - untranslated a page the language doesn't have at all: every block, plus its title
  - title        the page's front matter `title:`, when the English one changed

A unit's `source` and `target` are its blocks joined with blank lines, and a translation must
have the same number of blocks, the same Liquid tags ({% if ... %}, {{ accuracy }}), the same
link targets and the same heading levels.

Language codes are Weblate's (pt_BR, zh_Hans, nb_NO, id), as strings_sync.py and
translations/guidance use. The pages use the web code (pt-BR, zh-CN, nb, id).

`apply` edits the working tree and does not commit. `fetch` refuses to run while help pages
have uncommitted changes, because it reads the English from git.
"""
import argparse
import difflib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

REPO = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
HELP = REPO / "shared/src/commonMain/composeResources/files/help"
RES = REPO / "shared/src/commonMain/composeResources"

# Weblate-style code -> the web code the help pages use, where they differ
WEB_CODE = {"en_GB": "en-GB", "fr_CA": "fr-CA", "pt_BR": "pt-BR", "zh_Hans": "zh-CN",
            "nb_NO": "nb"}
# values-* directory suffix -> Weblate-style code, where they differ
CODE_FOR_DIR = {"en-rGB": "en_GB", "fr-rCA": "fr_CA", "pt-rBR": "pt_BR", "zh-rCN": "zh_Hans",
                "nb": "nb_NO", "in": "id"}

# Docs-site pages built from Jekyll includes, which are only in English
ENGLISH_ONLY = {"help-beacon-styles", "help-offline-map-extracts"}

# Front matter the translation copies from the English. title, parent, lang and permalink are
# the language's own.
SYNCED_KEYS = ("layout", "has_toc", "nav_order")
MARKER = "translated-from"

BLANK_LINES = re.compile(r"\n[ \t]*\n\s*")
LIQUID = re.compile(r"\{%.*?%\}|\{\{.*?\}\}")
LINK_TARGET = re.compile(r"\]\(([^)\s]+)\)")
HEADING = re.compile(r"^(#+)\s")
WHITESPACE = re.compile(r"\s+")


def git(*args: str, input: Optional[str] = None) -> str:
    return subprocess.run(["git", *args], cwd=REPO, check=True, capture_output=True,
                          text=True, input=input).stdout


def all_codes() -> list[str]:
    dirs = sorted(p.parent.name[len("values-"):] for p in RES.glob("values-*/strings.xml"))
    return [CODE_FOR_DIR.get(d, d) for d in dirs]


def web(code: str) -> str:
    return WEB_CODE.get(code, code.replace("_", "-"))


def english_pages() -> list[str]:
    return sorted(p.stem for p in HELP.glob("*.md") if "." not in p.stem)


# --- pages -------------------------------------------------------------------------------

class Page:
    """A help page: its front matter, as ordered (key, raw value) pairs, and its blocks."""

    def __init__(self, text: str):
        self.front: Optional[list[tuple[str, str]]] = None
        body = text.replace("\r\n", "\n")
        if body.startswith("---\n"):
            end = body.index("\n---", 3)
            self.front = []
            for line in body[4:end].split("\n"):
                key, _, value = line.partition(":")
                self.front.append((key.strip(), value.strip()))
            body = body[end + 4:]
        self.blocks = [b.rstrip() for b in BLANK_LINES.split(body.strip()) if b.strip()]

    def get(self, key: str) -> Optional[str]:
        for k, v in self.front or []:
            if k == key:
                return v
        return None

    def set(self, key: str, value: Optional[str]):
        if self.front is None:
            self.front = []
        if value is None:
            self.front = [(k, v) for k, v in self.front if k != key]
        elif any(k == key for k, _ in self.front):
            self.front = [(k, value if k == key else v) for k, v in self.front]
        else:
            self.front.append((key, value))

    @property
    def title(self) -> Optional[str]:
        value = self.get("title")
        return None if value is None else unquote(value)

    def text(self) -> str:
        out = ""
        if self.front is not None:
            out = "---\n" + "".join(f"{k}: {v}\n" for k, v in self.front) + "---\n\n"
        return out + "\n\n".join(self.blocks) + "\n"


def unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] == '"':
        return value[1:-1].replace('\\"', '"')
    return value


def quote(value: str) -> str:
    # Quote a YAML scalar whenever it could be read as anything but a plain string
    if re.search(r"""[:#"'\[\]{}&*!|>%@`,]|^\s|\s$|^-""", value):
        return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return value


def page_path(page: str, code: Optional[str]) -> Path:
    return HELP / (f"{page}.md" if code is None else f"{page}.{web(code)}.md")


def read(path: Path) -> Optional[Page]:
    return Page(path.read_text()) if path.exists() else None


def blob_id(path: Path) -> str:
    return git("hash-object", str(path)).strip()


def blob(sha: str) -> Optional[Page]:
    try:
        return Page(git("cat-file", "blob", sha))
    except subprocess.CalledProcessError:
        return None


def same(a: str, b: str) -> bool:
    norm = lambda s: WHITESPACE.sub(" ", s.replace(" ", " ").replace(" ", " ")).strip()
    return norm(a) == norm(b)


def joined(blocks: list[str]) -> str:
    return "\n\n".join(blocks)


# --- finding the work --------------------------------------------------------------------

def units_for(page: str, code: str) -> tuple[list[dict], Optional[str]]:
    """The work units for one page in one language, and a problem that blocks it, if any."""
    english = read(page_path(page, None))
    translation = read(page_path(page, code))
    if translation is None:
        units = [{"id": f"{page}#0", "kind": "untranslated", "page": page,
                  "source": joined(english.blocks)}]
        if english.title is not None:
            units.append({"id": f"{page}#title", "kind": "untranslated", "page": page,
                          "source": english.title})
        return units, None

    marker = translation.get(MARKER)
    was = blob(marker) if marker else None
    if was is None or len(was.blocks) != len(translation.blocks):
        why = ("has no translated-from" if not marker else
               f"translated-from {marker} is not in this repository" if was is None else
               "doesn't have the same blocks as the English it was translated from")
        return [], f"{page_path(page, code).name} {why}; see `check`"

    units = []
    matcher = difflib.SequenceMatcher(None, [WHITESPACE.sub(" ", b) for b in was.blocks],
                                      [WHITESPACE.sub(" ", b) for b in english.blocks],
                                      autojunk=False)
    for op, i1, i2, j1, j2 in matcher.get_opcodes():
        if op == "equal" and all(same(was.blocks[i], english.blocks[j])
                                 for i, j in zip(range(i1, i2), range(j1, j2))):
            continue
        units.append({"id": f"{page}#{j1}", "kind": "stale", "page": page,
                      "was": joined(was.blocks[i1:i2]),
                      "source": joined(english.blocks[j1:j2]),
                      "target": joined(translation.blocks[i1:i2]),
                      "span": [i1, i2, j1, j2]})
    if english.title is not None and was.title is not None and not same(english.title, was.title):
        units.append({"id": f"{page}#title", "kind": "title", "page": page,
                      "was": was.title, "source": english.title,
                      "target": translation.title or ""})
    return units, None


def translated(code: str) -> list[dict]:
    """One unit per block, and per title, of each page that is up to date, for review."""
    units = []
    for page in english_pages():
        if page in ENGLISH_ONLY or units_for(page, code) != ([], None):
            continue
        english, translation = read(page_path(page, None)), read(page_path(page, code))
        for n, (source, target) in enumerate(zip(english.blocks, translation.blocks)):
            units.append({"id": f"{page}#{n}", "kind": "translated", "page": page,
                          "source": source, "target": target})
        if english.title is not None:
            units.append({"id": f"{page}#title", "kind": "translated", "page": page,
                          "source": english.title, "target": translation.title or ""})
    return units


def pending(code: str) -> tuple[list[dict], list[str]]:
    units, problems = [], []
    for page in english_pages():
        if page in ENGLISH_ONLY:
            continue
        page_units, problem = units_for(page, code)
        units += page_units
        if problem:
            problems.append(problem)
    return units, problems


def dirty() -> list[str]:
    out = git("status", "--porcelain", "--", str(HELP))
    return [line[3:] for line in out.splitlines()]


# --- validating and applying -------------------------------------------------------------

def check_unit(unit: dict, text) -> list[str]:
    where = unit["id"]
    if not isinstance(text, str) or not text.strip():
        return [f"{where}: empty or not a string"]
    if unit["id"].endswith("#title"):
        return [f"{where}: a title is one line"] if "\n" in text.strip() else []
    errors = []
    source, ours = BLANK_LINES.split(unit["source"].strip()), BLANK_LINES.split(text.strip())
    if len(source) != len(ours):
        return [f"{where}: {len(ours)} blocks, the English has {len(source)} "
                "(blocks are separated by blank lines)"]
    if LIQUID.findall(unit["source"]) != LIQUID.findall(text):
        errors.append(f"{where}: Liquid tags differ from the English: "
                      f"{LIQUID.findall(unit['source'])} vs {LIQUID.findall(text)}")
    if LINK_TARGET.findall(unit["source"]) != LINK_TARGET.findall(text):
        errors.append(f"{where}: link targets differ from the English")
    for n, (s, t) in enumerate(zip(source, ours)):
        hs, ht = HEADING.match(s), HEADING.match(t)
        if (hs and hs.group(1)) != (ht and ht.group(1)):
            errors.append(f"{where}: block {n + 1} is not the same kind of heading as the English")
    return errors


def validate(code: str, translations: dict, units: list[dict]) -> tuple[list[str], list[str]]:
    by_id = {u["id"]: u for u in units}
    errors, notes = [], []
    for uid, text in translations.items():
        if uid not in by_id:
            errors.append(f"{uid}: not pending for {code} (fetched before a commit?)")
        else:
            errors += check_unit(by_id[uid], text)
    for page in sorted({u["page"] for u in units if u["kind"] != "translated"}):
        ids = {u["id"] for u in units if u["page"] == page}
        done = ids & translations.keys()
        if done and done != ids:
            notes.append(f"{page}: only {len(done)} of {len(ids)} units, so it won't be applied")
    missing = len({u["id"] for u in units if u["kind"] != "translated"} - translations.keys())
    if missing:
        notes.append(f"{missing} pending unit(s) not in the file")
    return errors, notes


def parent_label(code: str, english: Page) -> Optional[str]:
    """The language's "Using Soundscape" title, which its pages' parent must match."""
    if english.get("parent") is None:
        return None
    user = read(page_path("user", code))
    label = user.title if user and user.title else unquote(english.get("parent"))
    # Always quoted, as every page's parent has been
    return '"' + label.replace('"', '\\"') + '"'


def updated_page(page: str, code: str, units: list[dict], translations: dict) -> Page:
    english = read(page_path(page, None))
    translation = read(page_path(page, code))
    blocks_for = lambda uid: BLANK_LINES.split(translations[uid].strip())

    if translation is None:
        new = Page(page_path(page, None).read_text())
        new.blocks = blocks_for(f"{page}#0")
        if new.front is not None:
            new.set("title", quote(translations[f"{page}#title"].strip()))
            new.set("parent", parent_label(code, english))
            new.set("lang", web(code))
            new.set("permalink", english.get("permalink") or f"/users/{page}.html")
        new.set(MARKER, blob_id(page_path(page, None)))
        return new

    by_span = {tuple(u["span"]): u for u in units if u["kind"] == "stale"}
    was = blob(translation.get(MARKER))
    matcher = difflib.SequenceMatcher(None, [WHITESPACE.sub(" ", b) for b in was.blocks],
                                      [WHITESPACE.sub(" ", b) for b in english.blocks],
                                      autojunk=False)
    blocks = []
    for op, i1, i2, j1, j2 in matcher.get_opcodes():
        unit = by_span.get((i1, i2, j1, j2))
        blocks += blocks_for(unit["id"]) if unit else translation.blocks[i1:i2]
    translation.blocks = blocks
    if f"{page}#title" in translations:
        translation.set("title", quote(translations[f"{page}#title"].strip()))
    for key in SYNCED_KEYS:
        if english.get(key) is not None:
            translation.set(key, english.get(key))
    translation.set(MARKER, blob_id(page_path(page, None)))
    return translation


def apply(code: str, translations: dict, units: list[dict]) -> list[str]:
    written = []
    for page in sorted({u["page"] for u in units if u["kind"] != "translated"}):
        ids = {u["id"] for u in units if u["page"] == page}
        if not ids <= translations.keys():
            continue
        page_path(page, code).write_text(updated_page(page, code, units, translations).text())
        written.append(page_path(page, code).name)
    # Review fixes to pages that are up to date. They change the text, never what English the
    # page was translated from.
    for page in sorted({u["page"] for u in units if u["kind"] == "translated"
                        and u["id"] in translations}):
        translation = read(page_path(page, code))
        for uid, text in translations.items():
            name, _, which = uid.partition("#")
            if name != page:
                continue
            if which == "title":
                translation.set("title", quote(text.strip()))
            else:
                translation.blocks[int(which)] = text.strip()
        page_path(page, code).write_text(translation.text())
        written.append(page_path(page, code).name)
    return written


# --- commands ----------------------------------------------------------------------------

def codes_from(args) -> list[str]:
    known = all_codes()
    if getattr(args, "all", False) or not args.lang:
        return known
    for code in args.lang:
        if code not in known:
            sys.exit(f"Unknown language {code} (known: {' '.join(known)})")
    return args.lang


def cmd_languages(args):
    for code in codes_from(args):
        units, problems = pending(code)
        pages = {u["page"] for u in units if u["kind"] == "untranslated"}
        stale = [u for u in units if u["kind"] != "untranslated"]
        print(f"{code:8} untranslated pages={len(pages):3}  stale units={len(stale):4}"
              + (f"  PROBLEMS={len(problems)}" if problems else ""))
        if args.lang:
            for u in units:
                print(f"    {u['kind']:13} {u['id']}")
            for p in problems:
                print(f"    problem       {p}")


def cmd_fetch(args):
    if dirty():
        sys.exit("Help pages have uncommitted changes; commit or stash them first:\n  "
                 + "\n  ".join(dirty()))
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for code in codes_from(args):
        target = out / f"{code}-help-untranslated.json"
        target.unlink(missing_ok=True)
        units, problems = pending(code)
        if problems:
            print(f"{code}: " + "; ".join(problems), file=sys.stderr)
        if units:
            target.write_text(json.dumps(units, indent=1, ensure_ascii=False) + "\n")
            print(f"{code}: {len(units)} unit(s) -> {target}")
        done = out / f"{code}-help-translated.json"
        done.write_text(json.dumps(translated(code), indent=1, ensure_ascii=False) + "\n")


def load(args, code: str) -> dict:
    path = Path(args.file) if getattr(args, "file", None) else \
        Path(args.out_dir) / f"{code}-help-translations.json"
    return json.loads(path.read_text()) if path.exists() else {}


def cmd_validate(args):
    code = args.lang[0]
    errors, notes = validate(code, load(args, code), pending(code)[0])
    for n in notes:
        print(f"note: {n}")
    for e in errors:
        print(f"error: {e}")
    if errors or (args.require_complete and notes):
        sys.exit(1)
    print(f"{code}: OK")


def cmd_apply(args):
    codes = codes_from(args) if args.all else args.lang
    plans = {}
    for code in codes:
        translations = load(args, code)
        if not translations:
            continue
        units = pending(code)[0] + (translated(code) if args.revise else [])
        errors, _ = validate(code, translations, units)
        if errors:
            sys.exit(f"{code}: not applying anything:\n  " + "\n  ".join(errors))
        plans[code] = (translations, units)
    for code, (translations, units) in plans.items():
        for name in apply(code, translations, units):
            print(f"{code}: wrote {name}")


def cmd_acknowledge(args):
    """Mark pages as matching the current English without changing their text."""
    code = args.lang[0]
    for page in args.page:
        english, translation = read(page_path(page, None)), read(page_path(page, code))
        if translation is None:
            sys.exit(f"No {page_path(page, code).name} to acknowledge")
        if len(translation.blocks) != len(english.blocks):
            sys.exit(f"{page_path(page, code).name} has {len(translation.blocks)} blocks and the "
                     f"English {len(english.blocks)}: it needs translating, not acknowledging")
        translation.set(MARKER, blob_id(page_path(page, None)))
        page_path(page, code).write_text(translation.text())
        print(f"{code}: {page} acknowledged")


def cmd_check(args):
    """Every translation is marked, its marker is a real English page and the blocks line up."""
    problems = 0
    for path in sorted(HELP.glob("*.*.md")):
        page = path.name.split(".")[0]
        translation = read(path)
        marker = translation.get(MARKER)
        was = blob(marker) if marker else None
        problem = ("no translated-from" if not marker else
                   f"translated-from {marker} is not in this repository" if was is None else
                   f"{len(translation.blocks)} blocks, its English had {len(was.blocks)}"
                   if len(translation.blocks) != len(was.blocks) else
                   f"{page}.md is not an English page" if not page_path(page, None).exists()
                   else None)
        if problem:
            problems += 1
            print(f"{path.name}: {problem}")
    print(f"{problems} problem(s)")
    sys.exit(1 if problems else 0)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("languages")
    p.add_argument("--lang", nargs="+")
    p.set_defaults(fn=cmd_languages)
    p = sub.add_parser("fetch")
    p.add_argument("--lang", nargs="+")
    p.add_argument("--all", action="store_true")
    p.add_argument("--out-dir", required=True)
    p.set_defaults(fn=cmd_fetch)
    p = sub.add_parser("validate")
    p.add_argument("--lang", nargs=1, required=True)
    p.add_argument("--file")
    p.add_argument("--out-dir", default=".")
    p.add_argument("--require-complete", action="store_true")
    p.set_defaults(fn=cmd_validate)
    p = sub.add_parser("apply")
    p.add_argument("--lang", nargs="+")
    p.add_argument("--all", action="store_true")
    p.add_argument("--file")
    p.add_argument("--out-dir", default=".")
    p.add_argument("--revise", action="store_true",
                   help="also accept fixes to up-to-date blocks (from translation-review)")
    p.set_defaults(fn=cmd_apply)
    p = sub.add_parser("acknowledge")
    p.add_argument("--lang", nargs=1, required=True)
    p.add_argument("--page", nargs="+", required=True)
    p.set_defaults(fn=cmd_acknowledge)
    p = sub.add_parser("check")
    p.set_defaults(fn=cmd_check)
    args = ap.parse_args()
    if args.cmd == "apply" and not (args.all or args.lang):
        sys.exit("apply needs --lang or --all")
    if args.cmd == "apply" and args.file and (args.all or len(args.lang) != 1):
        sys.exit("--file goes with exactly one --lang")
    args.fn(args)


if __name__ == "__main__":
    main()

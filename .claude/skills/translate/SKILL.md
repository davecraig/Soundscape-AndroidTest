---
name: translate
description: Translate Soundscape-Android's untranslated and stale strings directly in the repo's shared/composeResources values-*/strings.xml files, and its untranslated and stale help pages (the Markdown in composeResources/files/help), in-session, then commit. Use when the user asks to translate untranslated/missing/unfinished strings, bring translations up to date after an English change, or run a translation pass — for a language the app doesn't support at all yet, use [[add-language]] instead.
---

# Translate untranslated and stale strings

You do the translating yourself, in-session, and write the results straight into
`shared/src/commonMain/composeResources/values-<lang>/strings.xml`. Weblate is no longer
in the loop: the repo is the source of truth, and translations land as ordinary commits.

`scripts/strings_sync.py` handles the deterministic file work — finding what needs
translating, validating, and writing the XML. It never translates anything.

What needs work comes from `scripts/find-stale-translations.py`, the repo's replacement
for Weblate's "needs editing": a string is **untranslated** if the language has no entry
for it, and **stale** if its English changed after the translation was last changed.

## Args

Optional language codes narrow scope, e.g. `/translate de fr`. With no args,
process every language `languages` reports as having pending work. Codes are the ones
`translations/guidance/` uses (`de`, `fr_CA`, `zh_Hans`, `en_GB`, `nb_NO`, `id`);
`strings_sync.py` maps them to the `values-*` directories.

## Procedure

Work in a **fresh, empty** output directory — a previous run's files are
indistinguishable from this run's. `fetch` deletes its own outputs before writing.

1. Run `python3 .claude/skills/translate/scripts/strings_sync.py languages`
   and `python3 .claude/skills/translate/scripts/help_sync.py languages`
   to see every language's untranslated and stale counts. If everything is 0, say so
   and stop. The steps below are for strings; the help pages follow the same steps with
   `help_sync.py` (see "Help pages").

2. Fetch:
   ```
   python3 .claude/skills/translate/scripts/strings_sync.py fetch --all --out-dir <dir>
   ```
   or `fetch --lang <code> [<code> ...] --out-dir <dir>` for a subset. It refuses to run
   while translation files have uncommitted changes, because the stale check reads git
   history — commit or stash them first.

   It writes, per language, `<code>-untranslated.json` (the work: every pending unit,
   with `kind` = `untranslated` or `stale`) and `<code>-translated.json` (everything
   already translated and up to date, for anchoring).

3. Read `docs/developers/translations.md` and
   `docs/developers/translation-terminology.md` for app context and the
   canonical meaning of Soundscape-specific terms. The alternate wordings in
   the terminology doc are English glosses to explain each concept — not a
   shortlist to translate from; prefer the word the target language's own
   mapping apps already use.

   Then load `translations/guidance/_common.md` and
   `translations/guidance/<code>.md` if it exists — recorded native-speaker
   decisions for that language. Any term with status `confirmed` or `agreed`
   is binding: use it verbatim (in the right case/inflection) rather than
   coining your own. This is the point of the file — new strings should be
   born consistent with what a native speaker already approved, instead of
   being corrected in a later review round.

4. For each language, translate the units in `<code>-untranslated.json`:

   - `source` is the English text; `note` is the translator comment from the English
     `strings.xml`, saying where the string is used — read it.
   - **Stale units** (`kind: "stale"`) have a `target` (the current translation) and `was`
     (the English it was translated from). Compare `was` with `source` to see what
     changed, then produce the **whole** new translation (rule C16) — keep the existing
     translation's choices wherever the English didn't change. If the change needs
     nothing from this language (an English typo fix, say), don't rewrite it:
     acknowledge it instead, with
     `python3 scripts/find-stale-translations.py --lang <code> --acknowledge <key> ...`
     (one language per call). Weblate still marks it "needs editing" until step 9 clears it.
   - **Plural units** have `source`/`target` objects of `{quantity: text}`. Translate every
     quantity the language uses (the same set as its other plurals) — see
     [[compose-plurals-select-on-int-only]].
   - **Anchor to prior art rather than translating cold.** `<code>-translated.json`
     holds every string already approved in that language. Before translating,
     find the sibling strings that share wording with the new one and reuse
     their choices — it keeps terminology consistent and settles questions the
     English can't. Real examples: a new `%1$s, %2$s` joiner should copy the
     existing `%1$s, %2$s` string verbatim, which is what preserves the Arabic
     `،`, Japanese `、` and Chinese `，` separators instead of an ASCII comma;
     an "X next to Y" string should take its preposition from the existing
     "Sidewalk next to %1$s". Grep the file for the distinctive English word.
   - Head-final languages usually need the clause restructured, not just the
     words swapped: Japanese `%2$s 沿いの%1$s`, Turkish `%2$s yanındaki %1$s`,
     Korean `%2$s 옆 %1$s`. Follow whatever order the sibling strings use.
   - If two new strings would collapse onto the same word (e.g. "near" and
     "next to" both rendering as Romanian `lângă`), pick a distinct term for
     one of them — they are separate strings because the app distinguishes them.
   - Preserve markdown, line breaks, and placeholders (`%1$s`, `%2$d`) exactly.
     Placeholders are positional: dropping one crashes at format time and
     swapping two silently transposes the arguments.
   - Where no direct translation exists, prefer a clear, concise, contextually
     appropriate phrase over a literal one, remembering this is an audio-first
     app for blind and low-vision users.

   Write `{context-key: translated-text}` to `<dir>/<code>-translations.json` (a plural's
   value is an object of `{quantity: text}`). Translate in batches of roughly 25-30 units
   so each batch stays checkable.

5. Validate:
   ```
   python3 .claude/skills/translate/scripts/strings_sync.py validate --lang <code> --file <dir>/<code>-translations.json --out-dir <dir>
   ```
   This checks placeholders, line breaks, escaped quotes, empty values and plural
   quantities, and that every key really is pending for the language — a key that isn't
   is how a stale fetch cache shows up. A partial file is fine and reported as a note;
   add `--require-complete` when a file is meant to cover everything.

6. Apply, which validates again and writes nothing if any language fails:
   ```
   python3 .claude/skills/translate/scripts/strings_sync.py apply --all --out-dir <dir>
   ```
   or `apply --lang <code> --file <path> --out-dir <dir>`. Existing entries are replaced
   in place; new ones are inserted after their nearest English neighbour.

7. Check the diff (`git diff --stat`, and spot-read a few languages), then run the
   truncation check from the Notes. Commit on main, one commit for the pass, describing
   what was translated. Don't push — the user does that ([[commit-directly-on-main]],
   [[never-push-upstream]]).

8. Confirm with `strings_sync.py languages`: the languages you did should now read 0
   (the stale check only sees committed changes). Report a short table: language,
   untranslated/stale strings done, anything skipped or acknowledged.

9. **If you acknowledged anything, clear Weblate's "needs editing" marks.** Weblate flags
   every translation when its English changes and only unflags it when the text changes,
   so an acknowledged string would count as untranslated there forever. Once the commit is
   pushed and Weblate has pulled it (its repository status shows the commit), run
   ```
   python3 .claude/skills/translate/scripts/weblate_sync.py clear-needs-editing [--lang <code> ...]
   ```
   It is a dry run: it lists the units it would set back to translated, and holds any the
   repo still has pending, whose Weblate text differs from the repo's, or that contain bare
   ASCII `"` (Weblate strips those when it saves a unit, which turns a state change into a
   text change committed only on Weblate's side). Check the list,
   then rerun with `--apply`. The component is kept locked, which refuses unit changes
   (403), so `--apply` unlocks it for the writes and always locks it again, even on
   failure. This writes to a shared service: give the user the `--apply` command to run
   with `!` rather than running it yourself. It changes only the unit's state, never its
   text, so Weblate's next update from the repo leaves it alone.

## Help pages

The help and FAQ are not in `strings.xml`. They are Markdown pages in
`shared/src/commonMain/composeResources/files/help/` (`help-<page>.md` in English,
`help-<page>.<web code>.md` per language), which the app's Help screen and the docs site
both show. `scripts/help_sync.py` is their `strings_sync.py`, with the same commands, and a
translation pass covers both: do the help after the strings, in the same output directory.

Each translated page records the English page it was translated from (`translated-from:` in
its front matter, a git blob id) and has the same blocks, separated by blank lines. The work is
worked out by comparing that English with today's, block by block.

1. `python3 .claude/skills/translate/scripts/help_sync.py languages` shows each language's
   untranslated pages and stale units, and any page `check` would reject.
2. `help_sync.py fetch --all --out-dir <dir>` (or `--lang <code> ...`) writes
   `<code>-help-untranslated.json`. Like `strings_sync.py fetch`, it refuses to run while help
   pages have uncommitted changes. A unit is
   - `stale`: some blocks of a page whose English changed, was added or removed. `was` is the
     English they were translated from, `source` the English now and `target` the current
     translation of `was` (empty for a new block). Rewrite `target` to say what `source`
     says, keeping its existing choices wherever the English didn't change.
   - `untranslated`: a whole page (`<page>#0`) the language doesn't have, and its title
     (`<page>#title`).
   - `title`: the front matter `title:`, whose English changed.
3. Translate as for strings, anchored to the language's existing help pages and strings: read
   the language's other `help-*.<web code>.md` pages for the terms they use. UI names in the
   help (in `*italics*`) must match the language's `strings.xml` exactly, since they name
   buttons and settings the user has to find.
4. Write `{unit id: translation}` to `<dir>/<code>-help-translations.json`. A unit's
   translation has its blocks separated by blank lines, and must keep the English's block
   count, heading levels, link targets and Liquid tags exactly: `{% if platform == "ios" %}`,
   `{% else %}`, `{% endif %}`, `{% if app %}`, `{{ accuracy }}`, `{{ quality }}`. Never
   translate a tag or a link target (`help-routes.md`), only the text around it.
5. `help_sync.py validate --lang <code> --out-dir <dir>`, then
   `help_sync.py apply --all --out-dir <dir>` (or `--lang <code>`). A page is only written once
   every unit of it is translated, and `apply` sets its `translated-from:`. For a new page it
   writes the front matter too: the language's `parent:` comes from its `user.<web code>.md`.
6. If an English change needs nothing from a language (a typo fix), acknowledge the page
   instead: `help_sync.py acknowledge --lang <code> --page <page> ...`. This only works when
   the page still has the same blocks as the English.
7. `help_sync.py check` must pass, and the shared tests (`HelpPagesTest`) check every page in
   every language. Commit the help with the strings, in the same commit.

Don't edit `translated-from:` by hand: setting it to the current English claims the page has
caught up with it.

## Notes

- **Translate the whole string, never just the part that changed** (rule C16 in
  `translations/guidance/_common.md`). After a bulk pass, run
  `python3 .claude/skills/translate/scripts/truncation_check.py <dir> <code>` and
  check every flag against the English.
- **Write quotes bare or typographic, never escaped.** Compose Resources shows `\"` and
  `\'` literally ([[composeresources-no-quote-escaping]]); `validate` refuses them. French
  no-break spaces before `: ; ? !` are added at build time (`composeResourcesForBuild`), so
  plain spaces are fine.
- `find-stale-translations.py` judges an in-repo translation against the English in its own
  commit, so a rebased push can't make it look stale. Weblate's old commits are dated by
  author time, and its squash add-on carries the squash's time; see the script's docstring.
- Weblate now only mirrors the repo and collects suggestions; it accepts no direct
  translations and never commits back. `weblate_sync.py` is kept for reading from it
  (suggestions, statistics), plus `clear-needs-editing` (step 9), which changes only unit
  state. Never upload through it: an upload would be overwritten by Weblate's next update
  from the repo.

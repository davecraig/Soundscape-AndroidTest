---
title: Language support
layout: page
parent: Information for developers
has_toc: false
---

# Language support

We aim to support as many languages as possible. Soundscape is translated into 45 languages, and the translations live in this repository: `shared/src/commonMain/composeResources/values-<lang>/strings.xml`, alongside the English source in `values/strings.xml`. **The repository is the source of truth.**

Native speakers help mainly through the [per-language questionnaires]({{ "/help-translate/" | relative_url }}) and by email to soundscapeAndroid@scottishtecharmy.support. [Weblate](https://hosted.weblate.org/projects/soundscape-android/androidkmp/) mirrors the repository so that anyone can browse the translations and leave suggestions there, but it no longer writes to the repository.

A guide to key Soundscape terminology can be found [here]({% link developers/translation-terminology.md %}).

## The translation loop

1. A developer adds a new string or changes an existing one, in the English `values/strings.xml` only. The comment above the string must explain what it's for and where it's heard: translators (human or AI) have nothing else to go on.
2. The change lands on `main`. Every language now has the string as **untranslated** (a new key) or **stale** (the English changed after the translation was last changed).
3. `scripts/find-stale-translations.py` lists them, per language:
   ```
   scripts/find-stale-translations.py                  # counts for every language
   scripts/find-stale-translations.py --lang de --diff # the strings, with how the English changed
   ```
4. A translation pass fills them in and commits them to the language files, like any other code change. This is normally done with the `translate` Claude Code skill (see [AI translations](#ai-translations)), but a translator can equally edit the files by hand and open a pull request.
5. Weblate picks up the new state of the repository on its next update.

Stale detection is worked out from git history, as Weblate's "needs editing" used to be. Quote and whitespace changes are ignored on both sides. If an English change needs nothing from a language (a typo fix, say), acknowledge it rather than rewriting the translation:
```
scripts/find-stale-translations.py --lang de --acknowledge <key> ...
```
This records the current English for that string in `translations/stale-acknowledged.json`, and the string stays quiet until the English changes again.

### Feedback from native speakers

Questionnaire answers, emails and Weblate suggestions are turned into recorded decisions in `translations/guidance/<code>.md`, with a corpus-wide sweep for every other string each decision affects. `translations/guidance/_common.md` holds the rules that apply to every language. These files are what every later translation and review pass starts from, so a correction isn't undone by the next pass. The `translation-feedback` and `translation-review` skills do this work. Weblate suggestions are collected with `.claude/skills/translate/scripts/weblate_sync.py suggestions`, which writes one file per language with open suggestions for the feedback skill to review.

### Format notes

* Quotes and apostrophes go in bare, or better, as typographic quotes. Compose Resources shows `\"` and `\'` literally.
* French no-break spaces before `: ; ? !` are added at build time (`composeResourcesForBuild`), so plain spaces in the files are fine.
* Plurals select on whole numbers only, and each language needs exactly its own CLDR categories.

## Adding a whole new language

Translations for a language that isn't enabled don't affect the app, because we explicitly whitelist the languages to include. The `add-language` skill creates `values-<qualifier>/strings.xml` and translates every string into it; enabling the language is then a separate, deliberate step. The files to change are:
* Add the language to the `resourceConfigurations` list in `app/build.gradle.kts`. Anything not in this list is excluded from the build and this is to block out partial translations.
* Add the language to `getAllLanguages` in `LanguageScreen.kt`. This also requires the name of the language in that language e.g. Español for Spanish.
* Also add the language to `MockLanguagePreviewData` in `LanguageScreen.kt` so that the `@Preview` of the `LanguageScreen` remains accurate.
* Edit `res/xml/locales_config.xml` to include the new language code. This is the list of languages that the app advertises to Android. The list is used within Android to show the user what languages are supported by an app and allow per-app language configuration.
* Add the language to the documentation website so the localized help pages are generated and served (see [The documentation website](#the-documentation-website) below): add an entry to `localeMap` in `DocumentationScreens.kt` (mapping the Android resource qualifier to its web/BCP-47 code) and add the same web code to the `languages` list in `docs/_config.yml`.

Check a new language's locale against the plural rules Compose Resources knows before adding it: an unknown locale throws rather than falling back.

## AI translations

Most of our languages don't yet have a native speaker checking them, so translations are kept up to date with AI. This is done in-session with Claude Code skills in `.claude/skills/`, which translate using the whole existing corpus for that language, the terminology guide and the recorded native-speaker decisions:

* `translate` — translates every untranslated and stale string, validates the result (placeholders, line breaks, plural forms, escaping) and commits it. Its helper `strings_sync.py` does the file work.
* `translation-review` — reviews existing translations for a language and reports findings. It only applies fixes when explicitly asked.
* `translation-review-all` — runs `translation-review` on every language at once, one subagent per language, and combines the findings into one summary with the cross-language problems pulled out. Review only: fixes are then applied one language at a time.
* `translation-feedback` — turns native-speaker feedback into recorded decisions and a sweep.
* `add-language` — adds a new language.
* `translation-questionnaire` — writes and refreshes the published questionnaires.

(They were called `weblate-translate`, `weblate-review`, `weblate-feedback` and `weblate-add-language` until 2026-09-30, when they stopped working through Weblate.)

## The documentation website

This website is built with [Jekyll](https://jekyllrb.com/) and the [just-the-docs](https://just-the-docs.com/) theme, and is published to GitHub Pages by the `jekyll-gh-pages.yml` workflow. It is multilingual via the [`jekyll-polyglot`](https://github.com/untra/polyglot) plugin: every supported language gets its own URL subtree (e.g. `/de/`, `/fr-CA/`) and a language switcher appears in the header. Pages that have no translation in a given language fall back to the English version automatically, so the developer documentation (which is English-only) still appears under every language.

### Help pages are Markdown in the app's resources

The user help pages are hand-edited Markdown files in `shared/src/commonMain/composeResources/files/help/`. They are the only source of the help: the app's Help screen renders them (on Android and iOS), and the `jekyll-gh-pages.yml` workflow copies them into `docs/users/` before building the site, so the site URLs are unchanged. Until 2026-10 they were generated from `strings.xml`; the help and FAQ text is no longer in `strings.xml` at all.

* English has no suffix, e.g. `help-routes.md`.
* Each other language has its web/BCP-47 code as a suffix, e.g. `help-routes.de.md`, and carries a `lang:` entry plus a `permalink:` pinned to the English page's URL (e.g. `permalink: /users/help-routes.html`). polyglot matches translations by URL, so the shared permalink is what makes it serve the German file at `/de/users/help-routes.html` rather than a separate URL. The app shows the page in the UI's language: `language-REGION` first (`pt-BR`), then the bare language, then English.
* `parent:` must match the `title:` of `user.<lang>.md` in the same language, so just-the-docs nests the page under that language's "Using Soundscape" section. `nav_order:` sets its place in that list.
* `help-and-tutorials.<lang>.md` is the app's Help index. It has no front matter and isn't published on the site, whose navigation does the same job.

Text that differs by platform, or appears only in the app, uses Liquid conditions. Jekyll evaluates them with `platform` and `app` undefined, so the site gets the Android text and none of the app-only text; the app evaluates them itself (`applyConditions` in `MarkdownProcessor.kt`):

{% raw %}
```
{% if platform == "ios" %}
Text for iPhone.
{% else %}
Text for Android, and for the website.
{% endif %}

{% if app %}
Current accuracy: {{ accuracy }} ({{ quality }})
{% endif %}
```
{% endraw %}

A paragraph containing {% raw %}`{{ accuracy }}`{% endraw %} is the GPS accuracy page's live line: the app fills in the current accuracy and one of `help_gps_accuracy_good`/`_ok`/`_poor`, which stay in `strings.xml`. FAQ pages are split on their `###` questions.

`HelpPagesTest` checks every page in every language for both platforms: that the index links the same pages everywhere, that no Liquid tag survives, that every FAQ question has an answer and that each GPS page has its live line.

### Keeping help translations up to date

Edit only the English page (`help-<slug>.md`); the translations are brought up to date by the `translate` skill, as strings are. Each translation records the English it was translated from as `translated-from:` in its front matter: the git blob id of that version of the English page, which survives the files moving. A translation also has the same blocks (paragraphs, headings and list items, separated by blank lines) as that English, so comparing it with today's English, block by block, shows exactly which blocks of the translation are stale.

`.claude/skills/translate/scripts/help_sync.py` does that comparison, with the same commands as `strings_sync.py`:

* `help_sync.py languages` lists each language's stale blocks and missing pages.
* `fetch`, `validate` and `apply` hand out the stale and missing blocks as units, check translations of them (block count, heading levels, link targets, Liquid tags) and write them in. `apply` is the only thing that moves `translated-from:` on, so a hand edit to a translation (a typo fix) never hides an English change it hasn't caught up with.
* `acknowledge --lang <code> --page <slug>` marks a page as checked against the current English when the change needs nothing from that language.
* `check` fails if a translation has no `translated-from:`, names English that isn't in the repository, or doesn't have the same blocks. It runs in the `run-tests.yaml` lint job.

`help-beacon-styles` and `help-offline-map-extracts` are English only: they are built from Jekyll includes. `how-it-works` (the docs site's "The Technology Behind Soundscape") is only translated into some languages, so the others show as having one untranslated page.

### Other pages

`index.md` and the legal pages are not translated yet, so they are currently English only and rely on polyglot's English fallback under each `/<lang>/` URL. A translation can be added later by committing a per-language variant (e.g. `index.de.md` with `lang: de`). The legal pages (privacy policy, terms and conditions) should only ever be translated by a human reviewer — do not machine-translate them.

### Linking to localized pages

Do **not** use Jekyll's {% raw %}`{% link users/help-….md %}`{% endraw %} tag to link to a help page (or any other page that has translations). The {% raw %}`{% link %}`{% endraw %} tag resolves by *source filename*, but in each non-default language pass polyglot replaces the English page with its localized variant in the document set — so the English source filename is not found and the build fails with `Could not find document … in tag 'link'`.

Instead, link to such pages by URL so polyglot can rewrite it to the active language:

{% raw %}
```
[Help using Media Controls]({{ "/users/help-using-media-controls.html" | relative_url }})
```
{% endraw %}

{% raw %}`{% link %}`{% endraw %} is still fine for English-only pages (developer docs, `user.md`), because their English source stays in every language pass.

## Weblate

Weblate follows the repository, and anyone with an account can leave suggestions. The component no longer accepts direct translations and never commits back, so it can't cause git conflicts, strip quotes or lose no-break spaces, as it used to. Don't unlock it for direct editing without first reinstating a way to merge its changes, because edits made there would otherwise be overwritten by the next update from the repository.

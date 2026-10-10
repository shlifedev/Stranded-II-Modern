<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"en","syncedHash":"6d84a1f727e881e08c218947f3b593012b18efbdf842ff340e447816ef3d1405"} -->

# Localization guide

[English](localization.md) · [한국어](localization-ko.md)

[Back to README](../README.md) · [Playing and settings](playing.md) · [Development guide](development.md)

## Current support

The game supports Korean (`ko`) and English (`en`) catalogs. Each edition uses one executable for all languages; the selected language is read at launch. A language change in the options takes effect on the next launch.

Editing translations or fonts and adding languages does not require recompiling the EXE. Prepare both `locales/<code>.tsv` and `locales/<code>.json`, then run `localize` to register the language automatically. See the [font guide](fonts.md) for font selection.

## Edition-specific translations

Catalogs and language metadata in `locales/` are shared. Add edition text in `locales/editions/classic/<code>.tsv` or `locales/editions/extended/<code>.tsv` when needed. Only the selected edition's TSV is applied over the shared catalog: matching keys replace entries and new keys are added. Define new English source keys in that edition's `en.tsv` as well. Missing Korean or other translations fall back to that edition's English text.

Language registration and font metadata still use the shared `locales/<code>.json`. Edition TSVs follow the same syntax, substitution-variable, and reference checks. `localize --edition extended` and `check --edition extended` target only Extended and do not change Classic catalogs. Omitting the edition targets Classic.

## File roles

| File | Role |
| --- | --- |
| [locales/en.tsv](../locales/en.tsv) | Reference English text and the complete set of keys |
| [locales/ko.tsv](../locales/ko.tsv) | Korean translations |
| `locales/<code>.json` | Language display name and optional font file path |
| [locales/legacy-index.json](../locales/legacy-index.json) | Mapping of the original 2,261 translation numbers to fixed keys |
| [resources/source-keys.json](../resources/source-keys.json) | Original displayed game text → translation key |
| [resources/help-terms.json](../resources/help-terms.json) | Script help terms → translation key |
| `resources/overrides/` | Resources edited directly; create this folder when needed |
| [src/engine/includes/localization.bb](../src/engine/includes/localization.bb) | Catalog loading and text-reference resolution |

Edit ordinary translations in `en.tsv` or `ko.tsv`. Change the original-text-to-key mappings when changing which parts of the original resources are translated.

## Translating existing text

1. Find the original text and key in `locales/en.tsv`.
2. Edit the text for the same key in `locales/ko.tsv`. Add the key if it is missing.
3. Close the game and run the following from the repository root:

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\Play.cmd
```

`localize` refreshes catalogs, the language registry, and per-language fonts together. It does not change game resources, saves, or settings. Check wrapping, clipping, colors, and substitution variables in the game. For first-time environment setup, see the [development guide](development.md).

## TSV format

Save as UTF-8 without BOM. Each line is `key<TAB>text`, where `<TAB>` means one actual tab character.

```text
quest.example.greeting<TAB>Welcome, $name!
```

The Korean file can use this text for the same key:

```text
quest.example.greeting<TAB>$name님, 환영합니다!
```

- Keys use Latin letters, digits, underscores (`_`), periods (`.`), and hyphens (`-`). Do not duplicate a key within a file.
- Blank lines and lines starting with `#` are ignored.
- Escape line feeds in text as `\n`, carriage returns as `\r`, tabs as `\t`, and backslashes as `\\`.
- English is the reference catalog. A key missing from Korean falls back to English. An empty value after the tab is intentionally blank and does not fall back to English.
- A key missing from both catalogs produces `[missing: key]` and an entry in the runtime folder's `core/localization.log`. Resolve missing references through `check` before finishing a change.

Keep the names and numbers of existing keys such as `legacy.0000` and migrated hash keys. Give new text descriptive keys such as `quest.island1.arrival`.

## Connecting new text

Add the new key and text to both catalogs, then place `[[loc:key]]` where it should appear.

Engine example:

```blitzbasic
bmpf_txt(20,20,"[[loc:ui.language]]")
```

Game script example:

```text
msg "[[loc:quest.example.greeting]]";
```

You can combine multiple references with ordinary text. References support up to eight levels of nesting; do not create circular references.

Place edited resources under `resources/overrides/`, preserving their path relative to the runtime folder. For example, `resources/overrides/mods/Stranded II/sys/custom.s2s` is copied to `build/runtime/mods/Stranded II/sys/custom.s2s` during `setup`. New files must be connected separately so the game reads or runs them.

Write text resources as UTF-8. There is no need to copy entire original models or music files. Using `overrides` to overwrite saves, `modern.ini`, `settings.cfg`, or `settings.inf` is not allowed.

`.s2` maps are binary files with string lengths; do not perform bulk replacements in a text editor. Use the map editor or [scripts/s2map.py](../scripts/s2map.py), preserving terrain, numeric IDs, non-string fields, and attachments.

## What to preserve in translations

Keep the same names and set of UI arguments such as `$1` and script variables such as `$wood` as in English. You may change their order to suit the translation.

Also preserve `$key_*` key names, `$img=` image-path markers, and `!0`–`!6` color/font markers. Commands, event names, object IDs, file paths, and conditions are not translation targets.

Diary titles are also lookup keys. Preserve the original titles and translate their display aliases.

Original game resources use CP1252; prepared resources and engine sources use UTF-8. Preparation tools convert the original string delimiters 166/182/191 to 31/30/29. Do not replace numeric IDs indiscriminately.

## Saves and text references

Script substitution variables preserve their values at command execution time. The engine generates this saved representation:

```text
[[loc:key|name=HEX_UTF8_BYTES]]
```

The engine creates this representation; translators do not write it manually. References resolve in the current language when displayed, while stored variable values are preserved.

Ordinary Korean text in earlier Korean-edition saves has no translation key, so it may remain unchanged after switching languages. Follow the game verification steps in the [development guide](development.md) when checking actual save compatibility and language switching.

## Adding Japanese or other languages

1. Create `locales/ja.json`. Codes use lowercase ASCII letters/digits separated by hyphens, such as `ja` or `pt-br`. The display name can use the language's own script.

```json
{
  "name": "日本語",
  "font": "deps/fonts/MyJapaneseFont.ttf"
}
```

2. Put the font at that path, relative to the repository root. Omit `font` to use the default Noto font.
3. Create `locales/ja.tsv` as UTF-8 without BOM. Copy English and translate it, or include only translated keys. Replace `<TAB>` below with an actual tab.

```text
ui.language<TAB>言語
```

4. Run `localize → check → Play.cmd`. Under **Settings → Graphics → Language**, left-click the choice to advance or right-click to go back. Korean and English appear first, followed by additional languages sorted by code.
5. Apply, close, and restart the game. Missing translations appear in English. If the configured pack has been removed or is not installed, startup falls back to English. Cancel discards the pending selection.

No engine source or EXE change is needed for each added language. An EXE predating this feature requires one initial rebuild. Keep the base English and Korean packs. Add new English keys to `en.tsv` first; languages lacking those keys display English. An empty value after a tab is intentionally blank, so omit the entire row for an untranslated entry.

A complete Japanese translation is not bundled. Unicode BMP characters are supported; check actual menus, the diary, long text, and existing saves in the new language. Supplementary-plane emoji, complex shaping, right-to-left layout, and additional Korean IME/filename support are outside this feature. See the [font guide](fonts.md) for glyph fallback, previews, and distribution.

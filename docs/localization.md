<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"en","syncedHash":"edd1b601e551ac4e609e7251e17c70581cf0290c0528e1992b320b167a3ccc62"} -->

# Translating the game

[English](localization.md) · [한국어](localization-ko.md)

[Back to README](../README.md) · [Development guide](development.md)

**Edit `locales/<code>.tsv` for the language you want to translate.** English source text is in [locales/en.tsv](../locales/en.tsv); Korean translations are in [locales/ko.tsv](../locales/ko.tsv). Translation, language, and font changes do not require rebuilding the EXE.

## 1. Edit the translation file

1. Find the text in `locales/en.tsv`.
2. In your language's TSV, translate **only the text to the right of the same key**. If the key is absent, add a row with that key.
3. Save as **UTF-8 without BOM**.

Each line is `key<TAB>text`. Replace `<TAB>` below with one actual tab character.

```text
quest.example.greeting<TAB>Welcome, $name!
quest.example.greeting<TAB>$name님, 환영합니다!
```

The first line is an example for the English file, the second for the Korean file. Do not put the same key twice in one file.

- Keep keys and variables/markers such as `$1`, `$wood`, `$key_*`, `$img=...`, and `!0`–`!6` unchanged.
- Write line breaks as `\n`, tabs as `\t`, and backslashes as `\\` within text.
- Omit untranslated rows to display English instead. **Leaving the value after the tab empty displays an empty string.**
- Do not translate commands, events, IDs, or paths. Keep ordinary translation edits in the TSV files.

## 2. Apply and check

To test locally, complete [environment setup and the first build](development.md#setting-up-the-development-environment) once. You can also contribute just the edited TSV without setting up a local build.

Close the game you are testing and run these commands in **PowerShell from the repository root**:

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\build\runtime\StrandedII.exe
```

Select the language under **Settings → Graphics → Language**, apply, and restart the game. Visit the screens containing your changes and check missing characters, clipping, wrapping, and variable values. For missing-character errors, see [font setup](#choose-a-font).

## Add a new language

For example, Japanese uses `ja`. Language codes use lowercase letters, digits, and hyphens (`ja`, `pt-br`, etc.).

1. Create `locales/ja.tsv` with keys from `en.tsv` and your translations. Including only translated rows is fine.
2. Create `locales/ja.json`:

```json
{
  "name": "日本語"
}
```

3. Follow `localize → check → launch` above. The new language appears in settings automatically.

Keep the bundled English and Korean packs. Japanese translations are not currently bundled.

## Choose a font

The default font is Noto Sans KR. To use another font, put its `.ttf` or `.otf` file in `deps/fonts/` and add `"font": "deps/fonts/MyFont.ttf"` to the language's `locales/<code>.json`. Paths are relative to the repository root; no Windows font installation is needed.

- After changes, follow `localize → check → launch` above. Remove the `font` field to restore the default font specified by the initial `setup --font`.
- Missing characters fall back to the default Noto, then fonts specified by other language packs. If none contains a character, preparation stops, reports the character, `U+XXXX`, and translation key, and preserves the existing pack. Select a font containing it and rerun.
- Variable fonts are supported, but `.ttc`, supplementary-plane emoji, complex shaping, and right-to-left layout are not.

Previews are in `build/runtime/core/locales/<code>/preview.png`; font usage is recorded in `build/runtime/core/locales/fonts-report.json`. For Extended, look under `build/extended/runtime/`. Check the final appearance in the game.

## Edition-specific translations

Both editions use `locales/<code>.tsv`. To change text in just one edition, put **only the changed rows** in the following file. Create the folders if needed.

| Edition | File |
| --- | --- |
| Classic | `locales/editions/classic/<code>.tsv` |
| Extended | `locales/editions/extended/<code>.tsv` |

Matching keys override shared translations. When adding a new key, also add its English source to that edition's `en.tsv`. Language names and fonts use the shared `locales/<code>.json`.

Commands default to Classic. After [preparing Extended](development.md#classic-and-extended), use the following commands for it. To apply shared translations to both editions, run the commands once for each edition.

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe --edition extended
.\dev.ps1 check -Python .\.venv\Scripts\python.exe --edition extended
.\build\extended\runtime\StrandedII-Extended.exe
```

## Share your translation

For a translation contribution, share the edited TSV and, for a new language, its JSON. If you chose a separate font, include its download instructions and license information.

To distribute a pack to players, run `localize`, verify it, and bundle the edition's **entire `core/locales/` folder**: `build/runtime/core/locales/` for Classic, or `build/extended/runtime/core/locales/` for Extended.

- Include `languages.dat`, TSV files, and each language's `glyphs.dat` and BMP pages. You may omit `preview.png` and `fonts-report.json`, which contains absolute paths from the development PC.
- Check that the font permits image generation and distribution, and include required license notices. The default Noto notices are in [licenses/](../licenses/).

Recipients should close the game, back up and replace their existing `core/locales/`, then select the language in the game and restart. They need an engine for the same edition that supports language packs; do not install them with an older EXE. No separate Python or original font installation is needed.

To connect new text in game code, see [localization for developers](development.md#localizing-new-text-and-resources).

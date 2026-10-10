<!-- dev-docs-ai-translation: {"document":"docs/fonts.md","language":"en","syncedHash":"bf17f8592cd47e16341a30e1efa59c12d78f8a58b7cf4a28c1b40df395ca2eba"} -->

# Changing fonts

[English](fonts.md) · [한국어](fonts-ko.md)

[Back to README](../README.md) · [Localization guide](localization.md) · [Development guide](development.md)

The default font is Noto Sans KR. Follow these steps to change its appearance or supply characters needed by a new language. Complete [environment setup](development.md#setting-up-the-development-environment) first.

## Select and apply a font

1. Put your `.ttf` or `.otf` file in `deps/fonts/`. No Windows font installation is needed.
2. Add `font` to the language's JSON. For example, `locales/ko.json`:

```json
{
  "name": "한국어",
  "font": "deps/fonts/MyFont.ttf"
}
```

3. Close the game you are testing and run from the repository root in PowerShell:

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\Play.cmd
```

4. Select that language, restart, and check menus, the diary, numbers, and long-text wrapping.

Paths are relative to the repository root; using `/` is convenient. To restore the default font, remove `font` and repeat the commands. The default is the Noto Sans KR file specified by the initial `setup --font`.

For Extended, add `--edition extended` to `localize` and `check`, then launch `Play-Extended.cmd`. Font settings are shared, but each edition's runtime folder must be refreshed separately.

## Missing or incorrect characters

- If a file is missing, check the JSON path against the actual filename.
- Characters absent from your font fall back to the default Noto, then fonts specified by other language packs.
- If no font has a character, preparation stops and reports the character, `U+XXXX`, and translation key. Select a font containing it and rerun. A failed update preserves the previous working pack.
- `.ttf`, `.otf`, and variable fonts are supported. `.ttc`, supplementary-plane emoji, complex shaping, right-to-left layout, and additional IME input support are not supported.

Preview `build/runtime/core/locales/ko/preview.png`; replace `ko` for another language. `fonts-report.json` in the same `core/locales/` directory records which fonts were used. For Extended, replace the path prefix with `build/extended/runtime/`. Check the final appearance in the game.

## Files to distribute

After `localize` and verification, bundle the edition's **entire `core/locales/` folder**: `build/runtime/core/locales/` for Classic, or `build/extended/runtime/core/locales/` for Extended.

Recipients should close the game, back up their existing `core/locales/`, replace it with the supplied folder, and select the language in the game. They need an engine for the same edition that supports these language packs; do not install them with an older EXE. Players do not need Python or the original font files.

- Include `languages.dat`, TSV files, and each language's `glyphs.dat` and BMP pages.
- You may omit `preview.png` and `fonts-report.json`. The report contains absolute paths from the development PC.
- Check that the font permits image generation and distribution, and include required license notices. The default Noto notices are in [licenses/](../licenses/).

<!-- dev-docs-ai-translation: {"document":"docs/fonts.md","language":"en","syncedHash":"c8e4cfb6470cbb332a6eacbcb60172c00ea83d56d2868396057973556e918ab1"} -->

# Fonts for language packs

[English](fonts.md) · [한국어](fonts-ko.md)

[Back to README](../README.md) · [Localization guide](localization.md) · [Development guide](development.md)

## Change a font with one path and one command

Complete the initial development `setup`, then close the game and follow these steps.

1. Put a `.ttf` or `.otf` file in `deps/fonts/`. Installing it in Windows is unnecessary.
2. Set the `font` path in that language's JSON. For example, `locales/ko.json`:

```json
{
  "name": "한국어",
  "font": "deps/fonts/MyFont.ttf"
}
```

3. Run this from the repository root:

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
```

4. Restart with `Play.cmd`. Korean, Latin letters, and numbers use the chosen font while Korean is selected. Menu highlight/error colors, small text, and diary text are generated from the same font.

Relative paths are resolved from the **repository root**. Use `/` in JSON paths for convenience. Absolute paths work too, but relative paths are preferable for configuration shared with other developers. Missing or unsupported font files produce an error identifying the path.

To restore the default, remove `font` and run `localize` again. A language pack containing only `name` uses the Noto Sans KR file specified by the initial `setup --font`. Custom fonts do not need a hash entry in `config/dependencies.json`.

## Fonts with missing characters

Each character is looked up in this order:

1. The selected language pack's font.
2. The default Noto Sans KR font.
3. Other language packs' fonts, ordered by language code.

For example, choosing a Latin-only font for Korean uses that font for Latin letters and numbers, and Noto for Korean. **Missing translations falling back to English** and **missing glyphs falling back to another font** are independent. Each language's images include characters from all registered catalogs so language names and text saved in other languages can still appear.

Static and variable fonts are supported. The initial sizes are 16px for normal text and 12px for small text. Variable fonts with a weight axis use 500, clamped to the supported range; other axes retain their defaults. Fonts exceeding the existing UI row height are scaled down automatically. Spacing, spaces, alignment, wrapping, and input carets use the generated advances.

Support currently covers Unicode BMP characters. Supplementary-plane characters such as emoji, complex shaping, right-to-left layout, and new IME input support are outside this feature. Use individual `.ttf`/`.otf` files instead of `.ttc` collections.

## Previews and diagnostics

| File | Contents |
| --- | --- |
| `build/runtime/core/locales/ko/preview.png` | Preview of the seven font states for Korean |
| `build/runtime/core/locales/fonts-report.json` | Selected fonts, glyph counts, fitting scales, and source fonts for fallback glyphs |
| `build/localization-report.json` | Summary of the last `localize` run |

Replace `ko` with another language code to find its preview. Check the final result in the game too, especially long language names, numbers, menus, the diary, carets, and long text wrapping.

If no font covers a character, preparation stops with the character, `U+XXXX`, and the translation key using it. Assign a font that includes that character to the relevant language pack and try again. Failed font generation or a failed publication replacement preserves the previous working pack. Edited translations and fonts do not take effect until the error is fixed.

## Distribution

After successful preparation, distribute **the complete `build/runtime/core/locales/` directory**. The `languages.dat` registry, TSV catalogs, and per-language `glyphs.dat` and BMP pages form one set. Players only select a language in the game; they need no Python, source TTF/OTF files, or system font installation. An engine build containing this feature is required; do not install these packs with an older EXE.

Check that each font's license permits generating and distributing images, and include the required copyright notices. `fonts-report.json` contains absolute font paths from the development PC. You may omit that report and `preview.png` when distributing, but retain `languages.dat`, TSV, `glyphs.dat`, and BMP files. The default Noto notices are already in `licenses/`. Follow each custom font's redistribution terms when adding font files to a repository too.

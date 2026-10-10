<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"en","syncedHash":"d10347ab9cad61ac3d63ebaee6290c53ff96f5c50c05d8996bd084cafa1c4e48"} -->

# Translating the game

[English](localization.md) · [한국어](localization-ko.md)

[Back to README](../README.md) · [Development guide](development.md) · [Font guide](fonts.md)

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
.\Play.cmd
```

Select the language under **Settings → Graphics → Language**, apply, and restart the game. Visit the screens containing your changes and check missing characters, clipping, wrapping, and variable values. For missing-character errors, see [font setup](fonts.md).

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

The default font is Noto Sans KR. To use another font, add `"font": "deps/fonts/MyFont.ttf"` to the JSON and provide that file. Font paths are relative to the repository root. Keep the bundled English and Korean packs. Japanese translations are not currently bundled.

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
.\Play-Extended.cmd
```

## Share your translation

For a translation contribution, share the edited TSV and, for a new language, its JSON. If you chose a separate font, include its download instructions and license information.

To create a language pack players can install, see [distributing language packs](fonts.md#files-to-distribute). To connect new text in game code, see [localization for developers](development.md#localizing-new-text-and-resources).

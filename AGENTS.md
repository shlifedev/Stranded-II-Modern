<!-- dev-docs-ai-translation: {"document":"AGENTS.md","language":"en","syncedHash":"82227922362c5ab204bdfa514557129c71741fa57a05ed32b689788a706046dc"} -->

# Stranded II development instructions

[English](AGENTS.md) · [한국어](AGENTS-ko.md)

The architecture has Classic and Extended editions using a shared engine, edition-specific resources, and language packs selected at launch (EN/KO bundled). Communicate with the user in Korean.

- Extended provides individual Settings > QOL controls for recipe browsing, storage transfers, closing emptied corpses, display sorting, and held construction input. Extended-only changes must not alter Classic gameplay or content. Verify shared engine changes in both editions.
- The entrypoints are `src/engine/StrandedII.bb` and `src/engine/StrandedIIExtended.bb`. Select implementations in `editions/<edition>/modules.bb`. When Extended behavior diverges, separate only the required modules rather than copying the whole engine.
- Commands default to Classic when `--edition classic|extended` is omitted. Classic uses `build/runtime`; Extended uses `build/extended/runtime`. Do not share or automatically copy settings or saves.
- Put edition resources in `resources/editions/<edition>/overrides/` and translations in `locales/editions/<edition>/<code>.tsv`. Shared resource/catalog changes affect both editions. Write Extended rebalancing only in its own paths.
- `main`/`dev` are stable/development channels. Pushes and PRs check both editions; a `main` push automatically publishes a Classic stable release after checks pass. Extended or development releases require selecting the edition and `publish=true` in the manual workflow. Do not publish a release or trigger one by pushing without a request.

- Edit `src/engine/**/*.bb` directly. These files are tracked sources, not generated output. Do not run the old `src/ko-full` generators against the current engine.
- Follow `scripts/dev.py setup|localize|build|run|check` and the directory structure in `docs/development.md`. Python handles preparation, fonts, and verification only; it does not regenerate engine code.
- Development dependencies are in `deps/game`, `deps/Blitz3D-V1.118`, and `deps/fonts`. `games/`, `dist/`, `archive/`, `tools/`, `src/ko-full/`, and `src/upstream/` are local cleanup targets requested by the user and are not needed for active builds. Specify external dependencies through setup arguments.
- Local cleanup of `tools/` was deferred because the existing Blitz3D editor was running. The user was asked to save and close it. Confirm that it has closed, then recheck the cleanup scope and Git tracking before cleanup. Do not interrupt the current game.
- Do not overwrite the user's game or saves, or forcibly terminate a running user game. Verify changes in `build/runtime` or a separate copy. Preserve current saves and existing user data in `build/runtime/preserved`; do not delete them as generated output during cleanup.
- `language=<registered code>` takes effect on restart. Language packs pair `locales/<code>.tsv` with `.json` metadata; `localize` refreshes catalogs and fonts without rebuilding the EXE. Do not force the game to close through the language Apply button. Preserve FPS and display settings.
- Catalogs use UTF-8 without BOM and fixed keys. Their syntax is documented in `docs/localization.md`. Missing translated entries fall back to English; entries missing in both languages produce diagnostics.
- `.s2` maps are length-prefixed binary files. Use `scripts/s2map.py` to change only string spans, preserving terrain, IDs, non-string fields, and attachments.
- Official input uses CP1252; prepared data and engine sources use UTF-8. Convert original delimiters 166/182/191 to 31/30/29 only in strings. Do not replace numeric IDs indiscriminately.
- Diary titles are lookup IDs. Translate their display aliases only. Do not translate commands, events, or paths. Preserve `$1`, `$wood`, `$key_*`, `$img=`, and font markers.
- Frame rate and Alt-Tab handling are in `modern_runtime.bb`, `modern_math.bb`, `modern_ui.bb`, and their callers. Preserve full-frame elapsed time, the 100ms cap, fractional time accumulation, and the shared presentation/focus path.
- Run actual Blitz builds and `check` as appropriate for the change. Distinguish non-graphical timing tests from actual rendering FPS. Do not claim to have verified display, saving, or progression without checking them.
- Use only the Computer Use skill's `node_repl` + `@oai/sky` for Windows GUI interaction. Stop GUI input when the user interrupts. Do not bypass this through terminal-driven UI automation.
- Use sub-agents only when the user explicitly requests them.
- The user requested removal of untracked local material unnecessary for development or running the game. Preserve dependencies and user data, and check paths and Git tracking before deletion. Do not commit, push, or rewrite history without a separate request.
- Preserve original and third-party licenses. Verification procedures are in `docs/development.md`. Do not accumulate one-off execution or cleanup records in public guides. Local cleanup records are in `build/cleanup-report.json`.

## Creating and updating documentation

- When the user requests documentation creation or updates, or the current task changes implementation or usage and documentation needs updating, first read the latest [docs/auto-gen-please.json](docs/auto-gen-please.json) and create or update the necessary documents. Do not rewrite every listed document on every task.
- For each entry in the `docs` array, `file-name` identifies the target file relative to `docs/`, `style` specifies the writing style and length, and `desc` defines the document's purpose and content scope. Follow these instructions and describe the current implementation and verified facts concisely. Process identical duplicate entries only once.
- Follow the translation rules below for updated documents. The protection for `README-ko.md` also takes precedence over this configuration; recommend any changes to that file only in the conversation. This JSON guides agent work and is not a background document generation service.

## AI documentation translation

Maintain project Markdown documents in the languages configured by the root `.dev-docs-ai-translation.json`. This is an agent workflow, not a background service or an AI API integration. Keep all document variants in Git. Documentation languages are independent of game catalogs and settings.

### Exception for the user-maintained README

- The user authors and edits `README-ko.md`. Edit it only when the user explicitly requests changes to that file, and only within the requested scope. Feature development or documentation synchronization requests alone do not authorize editing this file.
- When additions or corrections are needed, recommend the reason and proposed content or wording in the conversation. Preserve the user's sentences, voice, and structure, and do not automatically apply suggestions to the file.
- This exception takes precedence over the translation and synchronization rules below. Do not write to `README-ko.md`, including automatic translation, prose polishing, formatting, language navigation, metadata, or `syncedHash` updates. When synchronizing other README languages, use the Korean original as a read-only source and exempt its hash update from synchronization completion requirements. Do not propagate changes from other languages back into the Korean file.

### Configuration and document groups

- `defaultLanguage` is `en`; its documents keep their existing unsuffixed filenames. `preferredEditLanguage` is `ko`. `targetLanguages` initially contains only `ko`.
- Validate the JSON before writing: language codes must be nonempty strings consisting of ASCII letters/digits separated by single hyphens, unique ignoring case. The default language must not also appear in `targetLanguages`, and the preferred editing language must be active. Treat invalid settings as an error, without rewriting documents.
- Enumerate tracked files and untracked files not excluded by Git, for example with `git ls-files --cached --others --exclude-standard`. Include `.md` extensions case-insensitively. Do not scan `.git`, ignored dependencies, local builds, or archives for additional documents. Do not modify upstream license originals.
- Group each default document with same-directory variants named `<stem>-<language>.md`, such as `README.md` and `README-ko.md`. Configured language suffixes are reserved for document variants. Recognize a new Korean document such as `docs/example-ko.md` even when its English counterpart does not exist yet. Do not treat a variant as a separate document to translate recursively.
- Each nonempty variant, including English, starts with one metadata line and one blank separator line:

```text
<!-- dev-docs-ai-translation: {"document":"README.md","language":"ko","syncedHash":"<SHA-256>"} -->
```

- `document` identifies the group's unsuffixed repository-relative path using forward slashes. `language` identifies this variant. `syncedHash` is SHA-256 of this variant's own synchronized body, not the English body's hash. These documents are editable; the comment does not mark them read-only.
- Normalize CRLF and CR to LF. Remove only the first metadata line and its one blank separator line before hashing the remaining body as UTF-8. Do not trim remaining whitespace. Save documents and metadata as UTF-8 without BOM, with LF line endings. For example, calculate a Korean document's hash with:

```python
from hashlib import sha256
from pathlib import Path

text = Path("README-ko.md").read_text(encoding="utf-8")
if text.startswith("<!-- dev-docs-ai-translation: "):
    text = text.split("\n", 1)[1]
    if text.startswith("\n"):
        text = text[1:]
print(sha256(text.encode("utf-8")).hexdigest())
```

### Selecting the editing language

- At task start, inspect staged changes, unstaged changes, and untracked documents. Recheck edits made during the task before finishing. Also check stored body hashes so edits committed before synchronization are not missed.
- A variant is an editing input when its body differs from `syncedHash`, or when a newly authored document has no synchronization metadata. Git changes whose bodies still match their synchronization hashes are previous AI output, not new editing inputs. Metadata-only and checkout line-ending changes do not select an editing language.
- Select the input separately for each document group: one changed language wins; with multiple changed languages, prefer `preferredEditLanguage`, then `defaultLanguage`. If neither is among multiple changed languages, preserve the edits and ask the user to choose. With the initial settings, changed Korean takes priority even when English also changed; English-only edits take priority when Korean has not changed since synchronization.
- Read the changed variants and use the selected version as the content authority. Translate directly from that version into every other active language. Do not require the user to first rewrite Korean edits in English. Changes to `AGENTS-ko.md` must also update the actual `AGENTS.md` entrypoint; translated filenames are not separate instruction entrypoints.
- If there are no editing inputs but a target variant is missing, use the existing preferred editing language, otherwise English, otherwise a remaining active variant. Generate only missing variants. Do not retranslate already synchronized variants unnecessarily.

### Completing synchronization

- Complete the language synchronization before finishing an authorized repository-editing task. For a read-only request, report pending synchronization without changing files. This workflow does not start running when no agent task is active.
- Adding a configured language creates missing variants for all nonempty document groups. Adding a Korean document creates its English counterpart and other configured variants. Leave empty placeholders empty and untranslated until substantive content is added.
- Preserve facts, validation status, commands, executable code blocks, identifiers, paths, settings, numbers, and substitution variables. Translate prose and descriptive diagrams without inventing features or claiming tests that were not performed.
- Include language navigation in each nonempty variant. Link to same-language documents where available, falling back to English. Update heading fragments and reference-style links as needed, without rewriting code examples, resource links, or external URLs.
- When configured languages or document names change, refresh navigation in existing active variants without retranslating unchanged prose. Navigation lists active variants; retired files remain preserved.
- For an explicit document rename or deletion, handle its language variants together. A missing translation alone is regenerated. Removing a language from configuration stops its synchronization and preserves existing variants, metadata, and content; do not reclassify retired variants as new sources.
- Before overwriting an output, confirm that it belongs to the same document group. Refuse unrelated files, inconsistent metadata, invalid paths, or case-insensitive filename collisions, and report the problem.
- Update synchronization hashes for all active variants only after all translations and link checks for that group succeed. Never advance hashes for an incomplete or failed synchronization. Preserve unrelated work and do not stage or commit automatically.
- Verify JSON, metadata, expected filenames, hashes, language navigation, relative links, and changed content. Re-running synchronization must not change synchronized bodies. In particular, earlier uncommitted AI output must not override a later real English-only edit. For documentation-only changes, do not run game builds or gameplay checks.

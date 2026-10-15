# 1.0.1

- Install ZIP and folder mods directly with their final folder name; avoid provider errors caused by renaming temporary folders or rereading copied files.
- Preserve existing mods by numbering duplicate folder names. Continue reporting actual copy errors.
- Add manual folder cleanup in each linked engine, including hidden installation leftovers and disabled mods, with confirmation before deletion.
- Improve removal of incomplete installations and support providers that require deleting children first.

# 1.0

First production release. Full Spanish history: [release-notes/1.0-es.md](release-notes/1.0-es.md).

- Complete translations for 10 language variants, including Brazilian Portuguese, French, German, Japanese, Korean, Simplified Chinese and Traditional Chinese.
- Localized metadata editor, mod status/details and progress messages. Updated existing English, Spanish and Indonesian guides.
- Translation coverage and formatting-parameter validation for all 150 strings.

- Guided welcome screen to select engines and link their folders. Existing settings remain available during setup.
- Local release notes shown once per installed version code, also accessible in Settings.
- Optional daily and manual update checks through Google Play; updates open the existing store listing.
- Search mods by name or author and refresh the mod list after installation.
- Correct alternative install destinations for Plus Engine and P-Slice.
- Pre-install metadata inspection and explicit compatibility states. Verified API ranges for official FNF 0.8.1–0.8.4 replace the previous exact 0.8.4 check. Unknown ports/builds remain unverified.
- ZIP checks for traversal, absolute paths, duplicates, file/directory conflicts, nesting, entry count and actual decompressed size.
- Stage installations in temporary folders and read files back with SHA-256 verification before finishing. Existing mods are not overwritten.
- Failed copies do not delete the original during fallback moves.
- Explicit folder grant management, bounded metadata/icon reads and private internal activities.

# 0.7 beta

- Separate screens and installation utilities.
- Engine detection, game/folder shortcuts and improved ZIP root detection.

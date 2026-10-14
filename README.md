All translations here!
https://es.crowdin.com/project/fnf-mod-installer

## Version 1.0

Guided engine setup, Google Play update checks, searchable mods and safer installation.
See [CHANGELOG.md](CHANGELOG.md).

Languages: English, Spanish, Indonesian, Brazilian Portuguese, French, German, Japanese, Korean, Simplified Chinese and Traditional Chinese. Full Spanish release history: [release-notes/1.0-es.md](release-notes/1.0-es.md).

### Build and verify

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

To test alongside the Play Store installation without replacing its data:

```powershell
.\gradlew.bat assembleDebug -PisolatedPreview=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.leninasto.fnfmodinstaler.preview/com.leninasto.fnfmodinstaler.MainActivity
```

The preview application ID is `com.leninasto.fnfmodinstaler.preview`. Google Play links always use the production application ID.

### Release maintenance

Check all translations with `python tools/check_locales.py`. Build a signed production AAB with `python tools/build_release.py`; credentials are prompted securely and passed only through the build environment.

- Increase `versionCode` and update `versionName` for each release. Version 1.0 uses code 4; confirm this exceeds every code already uploaded to Play Console.
- Update `release_notes` in each locale and `CHANGELOG.md`. The local history appears once for each increased installed version code.
- Configure `FNF_RELEASE_STORE_FILE`, `FNF_RELEASE_STORE_PASSWORD`, `FNF_RELEASE_KEY_ALIAS` and `FNF_RELEASE_KEY_PASSWORD` with the existing Play upload key. Never change the production application ID or publish a debug-signed artifact.
- The update checker uses [Google Play AppUpdateManager](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java), so availability respects the user/account release track. End-to-end availability must be tested through Play internal testing using a lower installed version code. A manually installed preview can report that checking is unavailable.
- API profiles are intentionally limited to verified official releases. The range `>=0.8.0 <0.9.0` was checked in `PolymodHandler.hx` for [v0.8.1](https://github.com/FunkinCrew/Funkin/blob/v0.8.1/source/funkin/modding/PolymodHandler.hx), v0.8.2, v0.8.3 and [v0.8.4](https://github.com/FunkinCrew/Funkin/blob/v0.8.4/source/funkin/modding/PolymodHandler.hx). Extend `assessApi` only after verifying the corresponding release. This checks metadata eligibility, not script/runtime compatibility or the embedded Polymod library version.
- Archives are limited to 50,000 entries, 32 path segments, 512 MiB per entry and 2 GiB total decompressed data. Multiple independent mods in one ZIP require separating the archive first.
- The target document provider must support creating, reading, writing and renaming folders. On failure, temporary install folders are deleted where the provider allows it; existing mods are preserved.

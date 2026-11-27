# AniVerse

A local-first Android anime client built with Kotlin and Jetpack Compose. There is no backend, no account and no bundled content: every series you see comes from a third-party extension that you install yourself from a repository URL you paste in.

## What it does

- Browse, search, filter and paginate the sources of installed extensions
- Library with categories, sorting, bulk selection and a grid/list toggle
- Automatic library update checks (WorkManager, Wi-Fi only by default) and an Updates feed
- Continue watching, watch history and local, per-episode playback progress
- Offline downloads to app storage or a folder you pick with the system file picker
- Versioned JSON backup and restore (merge or replace) through the system document picker
- Material 3 UI with dynamic colour, pure-black mode and a reduced-motion setting

## What it deliberately does not do

- No accounts, login, profiles or cloud sync (no MAL or AniList integration)
- No bundled or hardcoded sources, and no scraping code in this repository
- No analytics, ads, crash reporting or tracking of any kind
- No telemetry leaves the device: the only network calls are yours (repositories, extensions' own requests, APK downloads and media streams)

## Requirements to build

- JDK 17
- Android SDK Platform 35 and build tools for AGP 8.7.2
- Gradle 8.10.2 or newer. The wrapper properties are committed but `gradle/wrapper/gradle-wrapper.jar` is intentionally not, so run `gradle wrapper` once or open the project in Android Studio.

```bash
# debug build
gradle :app:assembleDebug

# unit tests
gradle :app:testDebugUnitTest
```

`minSdk` is 26. Release builds enable R8 and resource shrinking; see `app/proguard-rules.pro`.

## Project layout

```
app/                UI (Compose + Material 3), Hilt wiring, Room, repositories, workers, player
source-api/         The public extension contract that third-party APKs compile against
source-runtime/     Host side of the extension bridge: provider, class loader, protocol, guards
aniyomi-api/        Host-side Aniyomi extension API (models, network, request helpers, filters)
```

Layering is `ui -> domain -> data`. Network DTOs never leave `data/`; the UI only sees domain models. Room schemas are exported to `app/schemas` on the first build and version 2 ships a real `MIGRATION_1_2` (no destructive fallback).

## Extension model

Extensions are ordinary APKs loaded in an isolated `:extension` process through a `ContentProvider` (`app.aniverse.source.runtime.ExtensionProvider`, authority `${applicationId}.extensions`), talking to it with a small length-prefixed JSON protocol. Two APK formats are supported:

- **AniVerse extensions** built against `source-api`, with a `SourceFactory` service entry and an optional `assets/aniverse-extension.json`
- **Aniyomi extensions** (the format the public repositories such as Keiyoushi and Yuzono publish): the app ships the exact host-side API those APKs were compiled against in `aniyomi-api`, wires up the network client and the Injekt registry they expect, and adapts their `AnimeSource`s to the app's own `Source` contract

APKs that are neither (for example manga-only extensions) are rejected at install time with a reason, instead of being installed and failing when they are first opened.

- API version: `app.aniverse.source.api.EXTENSION_API_VERSION` (currently `1`); mismatched extensions are refused with a readable error
- Entry point: extensions declare the action `app.aniverse.source.api.EXTENSION`; the app discovers them through a `<queries>` entry, never `QUERY_ALL_PACKAGES`
- Each call has a 20 second timeout; a source that fails three times opens a circuit breaker for ten minutes, and an extension is disabled after five recorded failures
- Filters, popular/latest/search, details, episodes and video resolution all cross the same bridge

Installation goes through `PackageInstaller`. Before installing, the app checks the APK's package name and version code against the repository index, refuses downgrades, refuses a different signing certificate than an already installed copy, and blocks cleartext APK URLs.

## Security and trust

- Repository indexes and APK URLs must be `https://` (`UrlGuard` and `RepoUrlResolver`); bare APK file names resolve to the repository's `apk/` folder, with the flat layout retried automatically when a repository stores them next to the index, and missing icons fall back to `icon/<package>.png`
- `QUERY_ALL_PACKAGES` is declared because extensions are separate APKs that have to be opened and loaded; Aniyomi-format extensions declare no intent action of their own, so the action-based `<queries>` entry alone cannot see them
- An untrusted repository always requires an explicit confirmation, and its extensions are never auto-updated. Trusted repositories install with one tap and can update automatically
- Extensions asking for permissions a source normally does not need show a second confirmation with the permission list
- `usesCleartextTraffic` is enabled only because extension media URLs are often plain http; the player shows a warning when a stream is not https
- The only exported component is the launcher activity; extensions are reached through a non-exported provider in a separate process

## Backup format

Plain versioned JSON (`BackupFile.CURRENT_VERSION = 1`) written to a document you choose: repositories, extension metadata, series, episodes, categories, progress, history and settings. APK files, downloaded media and any secrets are never included. Import can merge with or replace what is on the device.

## Known limitations

- Downloads support progressive MP4 only. HLS/DASH-only sources fail with a readable message instead of downloading something unplayable.
- Playback happens inside the player screen; there is no background audio service or media notification yet.
- Subtitles are side-loaded from tracks the source reports (WebVTT, SRT, SSA); styling options are limited to size and background.
- Localisation is English only.
- Aniyomi extensions run against extension-library generation 16. Torrent-based sources, manga extensions and per-source settings screens (`ConfigurableAnimeSource`) are not implemented: sources keep their default preferences (quality, mirrors, ...) and torrent playback is refused with a message.
- No automated UI test coverage yet: the included tests cover version comparison, playback thresholds, index parsing and backup serialisation.

## Tests

```bash
gradle :app:testDebugUnitTest
```

- `VersionComparatorTest` - dotted/loose version comparison and update detection
- `ProgressThresholdTest` - continue-watching, watched and remaining-time rules
- `RepoIndexParserTest` - bare and wrapped indexes, URL resolution, malformed entry skipping
- `BackupJsonTest` - backup round trip and format version

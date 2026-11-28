# AniVerse performance baseline

## Important: what this document is

This is a **code and architecture audit**, not a frame-time measurement. The rebuild was done in a
Linux cloud workspace with no Android device and no emulator, so `FrameTimingMetric`, `dumpsys
gfxinfo` and Layout Inspector recomposition counts could not be collected. No p50/p90/p99 numbers are
claimed here or in `perf-after.md`. The method to collect them is documented at the bottom so the same
scenarios can be run on hardware.

The only timing recorded was build/compile time, which says nothing about UI frame rate.

## What the "before" code did that caused jank

| Area | Finding | Why it hurts |
| --- | --- | --- |
| Build type | Only debug was used for manual testing | Compose is not optimized in debug; jank seen there is not representative |
| Lazy lists | Several `items()` calls had no stable `key`/`contentType`, and rows rebuilt positional state | Full row recomposition and lost state on insert/remove |
| Models | UI models were ordinary data classes with `List` fields | Compose treated them as unstable, so parents could not skip |
| State | Some state was read high in the tree | Whole-screen recomposition on scroll-linked changes |
| Images | Loader relied on defaults; no explicit memory/disk cache policy; no prefetch | Repeated decodes and network reads while scrolling posters |
| Search | One request per enabled source fired at once, plus a short debounce | Bursts of work and many simultaneous extension calls |
| Visual effects | Deep nesting and large translucent layers in scrolling content | Extra layers and overdraw per frame |
| Design system | Legacy `androidx.compose.material` (M2) mixed with M3 | Duplicate theming and components, inconsistent recomposition boundaries |
| Startup | Splash dismissed before preferences were available | A flash of the wrong theme, then a second full recomposition at startup |
| Navigation | Old 5-tab structure with heavy screen swaps | More composition churn and less state reuse between tabs |

## Scenarios to measure

1. Cold start to first frame (`am start -W`; Macrobenchmark `StartupTimingMetric`).
2. Home scroll, 10 seconds, flinging through hero and rows.
3. My List grid scroll, 10 seconds.
4. Tab switching Home → Schedule → My List → Profile, three cycles.
5. Search typing a 12-character query and waiting for results.
6. Opening a series detail and starting an episode.

## How to collect numbers

Build the release-like, non-debuggable variant. It is debug-signed so it installs without extra setup:

```bash
./gradlew :app:assembleBenchmark
```

For frame data without writing a benchmark module:

```bash
adb shell dumpsys gfxinfo app.aniverse.local reset
# drive one scenario for ~10 seconds, then:
adb shell dumpsys gfxinfo app.aniverse.local
```

Read `Janky frames`, `50th/90th/99th percentile` from the output. Repeat each scenario five times on a
cold app and report the median run. For startup:

```bash
adb shell am start -W -n app.aniverse.local/.MainActivity
```

For recompositions, open the project in Android Studio with the `benchmark` variant selected and use
Layout Inspector's recomposition counts while scrolling; the Compose compiler reports are already
enabled in `app/build.gradle.kts` and land in `app/build/compose_compiler/`.

## Baseline compile observation

On the cloud VM, the untouched project compiled with:

```
./gradlew :app:compileDebugKotlin   # BUILD SUCCESSFUL in 2m 49s (cold, no daemon)
```

This is a build metric only and must not be presented as app performance.

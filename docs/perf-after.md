# AniVerse performance after the rebuild

## Measurement status

**No frame-time numbers were produced.** This workspace has no Android device or emulator, so the
required cold-start, scroll, tab-switch and search measurements could not be run. The definition of
"60 fps" is therefore **not verified** here. What was verified is listed below, with the exact checks.

Use `docs/perf-before.md` for the scenario definitions and commands. Run them on a physical device
against the `benchmark` build type before treating the performance goal as met.

## Changes made

### Composition cost

- `Series`, `Episode`, `Category`, `DownloadItem`, `DownloadGroup`, `ContinueWatchingItem`,
  `HistoryEntry`, `RepoExtension`, `InstalledExtension`, `UpdateItem`, `UpdateGroup` and `Settings`
  are annotated `@Immutable` (the repository never mutates these lists in place).
- Strong skipping is enabled in `app/build.gradle.kts`; the generated report
  `app/build/compose_compiler/app_debug-classes.txt` contains **no unstable classes**.
- Lazy rows, grids and episode lists pass stable `key`s and `contentType`s. Library list and grid
  share one model list; episode rows key by episode id.
- Scroll-linked work uses `graphicsLayer` / lazy-list scroll offsets instead of recomposing the
  screen: the Home top bar and the Details header parallax read the list state inside modifiers.
- State is collected with `collectAsStateWithLifecycle`; screens expose render-ready state from their
  ViewModels.

### Images

- One shared Coil loader with a 180 ms crossfade, a memory cache capped at 20% of app memory and a
  256 MiB disk cache (`AniVerseApp`).
- Poster/grid images are measured by their composable size (Coil's default request sizing), so the
  loader does not decode at source resolution. The hero is the one intentionally large request.

### Work off the main thread

- Repository mapping, extension calls and database streams stay in `viewModelScope`/IO, as before.
- Search debounces 300 ms and runs at most **three source searches concurrently** with a
  `Semaphore`; a new keystroke cancels the previous search job.
- Home source fan-out is capped at three concurrent loads, with per-source error isolation.

### Visual cost

- No blur in scrolling content; scrims are gradients and the circular card buttons are flat
  translucent fills.
- Gradients are drawn in a single `Modifier.background` per layer, not nested composables.
- No legacy M2 components remain in the Compose UI; the only `androidx.compose.material` imports are
  `material.icons`, which are vectors and do not bring the M2 design system.

### Startup

- The splash screen is held until the DataStore settings flow emits, so the first composition already
  has the correct mode, palette, AMOLED and accent. There is no second full theme swap at startup.
- The app is edge-to-edge; system bar icon contrast is applied by `applySystemBars`.

## Checks actually run

| Check | Result |
| --- | --- |
| `:app:compileDebugKotlin` | passed |
| `:app:compileBenchmarkKotlin` | passed (release-like, non-debuggable variant) |
| `ThemeContrastTest` (all palettes, both modes, custom accents) | passed |
| Compose compiler report, unstable classes | none in app debug classes |
| Frame timings / jank % | **not run — no device** |
| Baseline profile generation | **not run — no device** |

## How to finish the measurement

1. Connect a physical device (API 26+), enable USB debugging.
2. `./gradlew :app:installBenchmark`
3. Run the six scenarios from `perf-before.md` five times each; record p50/p90/p99 and jank %.
4. Cold start: compare `am start -W` total time against the same command on the pre-rebuild build.
5. Add the numbers to this file. Do not replace this section with estimates.

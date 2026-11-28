# AniVerse UI rebuild

## Structure

```
Home ──────────────── hero pager, Continue watching, Top Airing, Trending, Latest, per-source rows
Schedule ──────────── My List's old Updates data, week strip + grouped release cards
My List ───────────── old Library, segmented (All / Watching / Downloaded / categories), grid or list
Profile ───────────── Downloads, Extensions, Repositories, History, Settings, Appearance, About
Search ────────────── top bar on Home; pinned field, recent searches, filters, aggregated poster grid
Details ───────────── parallax artwork header, palette tint, poster overlap, episodes, related
```

Bottom navigation is exactly four tabs. Search is not a tab. Notifications open Schedule, which is
where new episode/extension activity is shown.

## Design system

`ui/components` contains the shared kit: `AniTopBar`, `AniLargeHeader`, `PosterCard`, `PosterGrid`,
`PosterRow`, `ContinueWatchingCard`, `SectionHeader`, `PillButton`, `GenreChip`, `StatusChip`,
`EpisodeRow`, `SegmentedToggle`, `AniBottomSheet`, `EmptyState`, `ErrorState`, `LoadingState`,
`PosterGridSkeleton`. Colors and shapes come from `MaterialTheme`; the only fixed colors live in
`ui/theme/Palette.kt` (`AniRatingYellow`, the white rating chip, HD chip) and are exposed through
`LocalAniColors`.

All screens use the same dark-first scheme. Light mode uses white/very light surfaces with the same
accent. Posters are 12 dp, cards 16 dp, buttons and chips pill-shaped, sheets 28 dp top corners.

## Theme engine

`ThemePalette` (in `domain/model/Models.kt`) is the single registry:

| Entry | Character |
| --- | --- |
| `CRIMSON` | default vivid red |
| `MIDNIGHT` | blue-violet |
| `SAKURA` | pink |
| `OCEAN` | teal/cyan |
| `FOREST` | green |
| `SUNSET` | orange |
| `MONOCHROME` | neutral greys |
| `DYNAMIC` | Material You, Android 12+ only |

Each palette derives a full dark and light Material 3 scheme from a seed in `ui/theme/Palette.kt`,
with `contrastOn()` choosing black or white text by measured contrast. `ThemeContrastTest` checks
primary, background, surface, surfaceVariant, primaryContainer, secondaryContainer, error and
errorContainer pairs for WCAG AA (4.5:1) in both modes.

`ThemeRepository` persists palette, mode, AMOLED, accent ARGB and reduce motion in DataStore. The
splash is held until the first settings emission. Theme changes animate via `animateColorAsState` in
`AniVerseTheme`.

The five static Plus Jakarta Sans weights are fetched by `.github/workflows/build-apk.yml` before
Gradle runs, so the built APK always bundles them while the repository stays source-only. The SIL
Open Font License is committed at `docs/licenses/PlusJakartaSans-OFL.txt`.

### Add a theme

1. Add one entry to `ThemePalette` and one case to `PaletteSpec` in `ui/theme/Palette.kt`
   (`lightPrimary`, `darkPrimary`, plus optional secondary/tertiary/tint seeds).
2. That's it — `ThemePalette.entries` drives the Appearance picker, and the scheme builder and
   contrast test pick it up automatically.

## Motion

`ui/theme/Motion.kt` holds the durations and springs. `MotionSpec` exposes fade-through/slide
transitions for nav, press scale, and staggered entrance. `AnimatedContent` handles loading/empty/
error/content swaps. Tabs use shared-axis transitions; lists key their items so removals animate.
`LocalReduceMotion` and the in-app toggle disable entrance/press animations while keeping state
changes instant.

## Data AniVerse does not have

These elements are deliberately **absent**, not faked:

- Age rating and HD badges — the source API has no rating/quality field, so `RatingChip`/`HdChip`
  render only when a future source supplies the value.
- Release year and season — not exposed by the extension API, so the metadata row and year filter
  hide. Search's filter sheet says so explicitly.
- Episode progress percentage and per-episode watched time — episode rows show watched state from the
  library, and Continue watching uses the real playback position.
- Accounts / profiles — there is no account system, so Profile only lists real destinations.
- Plan to Watch / Completed / Favorites segments — library entries have no status field, so My List
  offers All, Watching (real continue-watching history) and Downloaded (completed local downloads).

## Player

Player behavior, Media3 controls and extension streaming are unchanged. The screen inherits the
theme's system bar icon colors from `AniVerseTheme`; no player logic was modified.

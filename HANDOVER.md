# Handover — Mirage (Card Swap Camera)

Read this first, then [`CLAUDE.md`](CLAUDE.md) (the full spec). This file is
session-to-session state; `CLAUDE.md` is the stable spec and doesn't change
week to week.

## Where things stand

- **Branch**: `milestone-3-state-and-settings` (not yet merged to `main` —
  name is stale now, work has gone well past milestone 3)
- **Milestones 1–3 — done.** Camera permission/portrait/immersive setup,
  mirrored front-camera feed via `ImageAnalysis` (no `PreviewView`), and
  `ForceState`/`CardRepository`/`HiddenControls` (2s corner long-press →
  Settings, volume keys step the forced card). All verified on Aryan's
  physical Motorola.
- **Milestone 4 (Mode A) — done, manual trigger only.** Tap anywhere outside
  the hidden corner toggles the overlay (stand-in for a real shutter-button
  tap until Milestone 7 exists). `CardCompositor.compositeFixed()` draws the
  forced card at a configurable scale/blur (now Settings sliders, see below)
  with a 120ms fade-in, mirrored to match the already-mirrored camera feed.
  **`AUTO_BRIGHTNESS` trigger was never built** — `Trigger` persists and has
  a Settings spinner, but nothing reads it yet to auto-show/hide.
- **Milestone 5 (OpenCV integration) — done.** `OpenCVLoader.initDebug()` in
  `FakeCameraActivity.onCreate()`; `FrameProcessor` converts the oriented
  frame to a `Mat` via `Utils.bitmapToMat` for `CardDetector`.
- **Milestone 6 (Mode B) — substantially done.** `CardDetector.detect()`
  tries an edge-based 4-point quad first (`Canny` → `dilate` →
  `findContours` → `approxPolyDP`), falls back to a brightness-threshold
  blob (Otsu) with skin-pixel subtraction when edges are too weak (a white
  card has low edge contrast against skin/background) — see the class's own
  doc comments for the full reasoning trail. Hysteresis (2 hits to show, 6
  misses to hide) and EMA corner smoothing were pulled forward from
  "Milestone 6 polish" into this pass, because raw unsmoothed detection was
  unusable to even evaluate. `CardCompositor.compositeTracked()` does the
  real warp (`getPerspectiveTransform` + `warpPerspective`), brightness/
  contrast matching (`Core.meanStdDev`), edge softening, and a feathered
  (not hard-cut) alpha blend. **Not done**: the spec's "light noise" step B.7
  detail — lower priority, deferred.
  - **Known tuning history, don't re-litigate from scratch**: thresholds were
    repeatedly guessed, tested wrong, then corrected from real Logcat
    calibration data (search this repo's chat history / the `CardDetector`
    comments for specifics — aspect ratio, skin HSV bands, area fractions
    are all justified from measured values, not guesses).
  - **Decision**: the real trick only ever shows the card's **white face** to
    the camera (back is pink, never shown) — so pink-card roll/yaw tuning
    was explicitly abandoned as solving a problem that doesn't matter. Don't
    re-open that thread unless the routine changes.
- **Settings UI — fully wired.** Card grid (52-cell `RecyclerView`, text
  labels not thumbnails — see why in `CardGridAdapter`'s doc comment), mode
  + trigger spinners, overlay blur/scale sliders, detection-sensitivity
  slider, and a debug-overlay switch that draws a live FPS counter. All
  persist via `ForceState`/`SharedPreferences`.
- **Milestone 7 (fake camera chrome) — not started, blocked.** Needs the
  target phone's exact model (see Open Questions).

## Process note for this repo

**Claude writes the code directly now** (changed 2026-11-12, Aryan's
explicit request, partway through Mode B once individual steps got large
enough — OpenCV integration, perspective geometry — that typing them in by
hand stopped being the useful part of the exercise). This replaces the
original "Aryan writes, Claude guides" agreement. Compile
(`./gradlew compileDebugJavaWithJavac`) before handing anything over. Keep
explaining what the code does and why, tutorial-style — that part of the
agreement is unchanged (Aryan's first Android project, iOS/Java background).

## Known environment gotchas (so you don't re-debug them)

- This is Aryan's first Android project. He's done iOS + Java before, not
  Android — lean on iOS/Java analogies when explaining Android-specific
  concepts.
- First Android Studio open on this machine had a stale `.idea/` folder;
  fixed by closing/reopening the project root fresh. If "Run button greyed
  out" reappears, check `.idea/` is synced to the current Gradle files.
- Local Gradle 9.6.0 + AGP 9.4.1 are cached; a different machine needs to
  download these.
- Test device: a physical Motorola phone, USB debugging. Emulators are not
  reliable for judging this app — front-camera feed is synthetic.
- `./gradlew.bat compileDebugJavaWithJavac --console=plain` is the fast
  sanity check used throughout this project before handing code back for
  on-device testing — doesn't install/run anything, just confirms it builds.

## Open questions — still unanswered (from CLAUDE.md)

- **Target phone brand/model and Android version?** Still only "Motorola" —
  this is now the actual blocker for Milestone 7.
- **Card assets**: only `7C.png` and `KH.png` exist in `assets/cards/`.
  **All 52 need to be photographed from the magician's real deck before any
  real performance** — this is a physical task (CLAUDE.md: "flat, even
  light, cropped to the card edge, ~500×700px"), not a code task, and it's
  the actual critical-path blocker for using this for real, independent of
  any remaining software work.
- **Mode A vs. Mode B — answered.** Mode B (tracked) is required; this is
  why all of Milestone 6 happened. Don't re-ask this one.
- **FPS acceptance criterion (≥24fps)**: now actually measurable via the
  debug-overlay FPS counter (Settings → toggle it on) — but nobody has
  reported back a real number yet with the full Mode B pipeline (detection +
  warp + brightness-match + feathered blend) running. Worth checking before
  assuming performance is fine.

## Next step

No single obvious next step — last status check-in surfaced several
candidates (see Open Questions above for the asset/phone blockers). Ask
Aryan what he wants prioritized rather than assuming: more Mode B
reliability work (e.g. testing the "Known risks" scenarios — white/bright
ceiling, low light — never specifically stress-tested), the deferred light
noise detail, or starting to close out the asset/phone-model blockers above.

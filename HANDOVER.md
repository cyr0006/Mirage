# Handover — Mirage (Card Swap Camera)

Read this first, then [`CLAUDE.md`](CLAUDE.md) (the full spec). This file is
session-to-session state; `CLAUDE.md` is the stable spec and doesn't change
week to week — though as of 2026-11-14 it also carries a chunk of the ML
pivot's own planning detail (see "ML detector initiative" in there), since
that's substantial enough to count as spec, not just session chatter.

## Where things stand

- **Branch**: `milestone-3-state-and-settings` (not yet merged to `main` —
  name is stale now, work has gone well past milestone 3)
- **Milestones 1–5 — done.** Camera/portrait/immersive setup, mirrored
  front-camera feed, `ForceState`/`CardRepository`/`HiddenControls`, OpenCV
  integration, Mode A (manual trigger only — `AUTO_BRIGHTNESS` never built).
  All verified on Aryan's physical Motorola.
- **Settings UI — fully wired.** Card grid, mode/trigger spinners,
  overlay blur/scale sliders, detection-sensitivity slider, debug-overlay
  switch (draws a live FPS counter). All persist via `SharedPreferences`.
- **Milestone 6 (Mode B, classical CV) — built, working, and now
  deliberately being superseded.** `CardDetector`/`CardCompositor` do the
  full pipeline (edge quad → brightness-blob fallback with skin exclusion →
  warp → brightness-match → feathered blend), including real fixes found
  from on-device calibration data: halation correction, skin-pixel
  subtraction so a gripping hand doesn't grow the detected region, a
  camera AE/AWB lock (`CameraController`) to stop white-balance drift from
  misreading the card as skin-colored, and a "partial entry" mode where the
  overlay only shows the matching *slice* of the fake card while the real
  one is still sliding into frame (`CardDetector.Quad.touches*` flags +
  `CardCompositor.computeSrcCorners`).
  - **Why "being superseded," not "broken"**: two full calibration rounds
    (day, then night) each fixed a real, data-confirmed problem and each
    also surfaced a *new* one at the margins (small/dim/partially-visible
    card color readings become unreliable in low light — not a threshold
    problem, a physics-of-small-samples problem). That pattern is the
    actual decision driver, not one bad session. Full reasoning in
    `card-detection-writeup.txt` (repo root) — written for Aryan's website,
    but it's the accurate internal postmortem too.
  - **Decision (2026-11-13/14): move detection to a trained model.**
    Warp/blend/composite all stay as-is — only `CardDetector`'s corner-finding
    job is being replaced. See "ML detector initiative" in `CLAUDE.md` for
    the full plan. The classical pipeline isn't deleted: it's now also the
    bootstrap/pre-fill mechanism for labeling training data.
  - **Pink-card roll/yaw tuning was explicitly abandoned** (the real trick
    only ever shows the white face) — don't re-open that thread.
- **Milestone 7 (fake camera chrome) — not started, blocked** on the target
  phone's exact model (see Open Questions).

## ML initiative — exact state as of 2026-11-14

This is the live thread. In order:

1. **Labeling tool — built and smoke-tested working**: `tools/label-tool.html`.
   Standalone, no install, open directly in Chrome/Edge. Picks a folder of
   frame images, auto-saves `labels.json` back into that folder as you label
   (File System Access API; falls back to manual download on other
   browsers). Labels are 4 corner points (TL/TR/BR/BL) or an explicit "no
   card." Can pre-load a `guesses.json` to speed up review.
2. **Aryan is filming the shoot list** (possibly with a friend helping) —
   see "ML detector initiative" in `CLAUDE.md` for the exact list: 15 clips,
   ~20-30s each, 3 lighting conditions (day/artificial/night) × 5 scenarios
   (full-frame hold, real entry direction, other entry directions, bad
   occluded grip, negative/no-card). **This session ended here** — next
   session starts once footage exists.
3. **Not yet built, and this is the right next coding step regardless of
   whether footage has landed yet**: a Python/OpenCV script that ports
   `CardDetector`'s logic to batch-process extracted frames and produce a
   `guesses.json` pre-fill file for the labeling tool. This is a mechanical
   port of logic that already exists and is fully understood — it doesn't
   need footage to exist first, so if Aryan starts a session before footage
   is ready, build this.
4. **Not yet built**: frame extraction from video (`ffmpeg`, sample every
   5th-10th frame, not every one) — trivial, do it once real clips exist.
5. **Not yet built**: the actual training pipeline (architecture choice,
   transfer learning, TFLite conversion, Android integration). Estimate
   given to Aryan: 1-2+ weeks with real iteration uncertainty, starting
   once labeled data exists — don't undersell this when the session resumes.

## Process note for this repo

**Claude writes the code directly** (changed 2026-11-12, Aryan's explicit
request, partway through Mode B once individual steps got large enough —
OpenCV integration, perspective geometry — that typing them in by hand
stopped being the useful part of the exercise). Compile
(`./gradlew compileDebugJavaWithJavac`) before handing anything over. Keep
explaining what the code does and why, tutorial-style — Aryan's first
Android project, iOS/Java background — that part is unchanged.

## Known environment gotchas (so you don't re-debug them)

- This is Aryan's first Android project. iOS/Java background, no prior
  Android — lean on iOS/Java analogies.
- First Android Studio open on this machine had a stale `.idea/` folder;
  fixed by closing/reopening the project root fresh.
- Local Gradle 9.6.0 + AGP 9.4.1 are cached; a different machine needs to
  download these.
- Test device: a physical Motorola phone, USB debugging. Emulators aren't
  reliable for judging this app — front-camera feed is synthetic.
- `./gradlew.bat compileDebugJavaWithJavac --console=plain` is the fast
  sanity check used throughout this project before handing code back for
  on-device testing.
- Calibration workflow that's worked repeatedly: add a temporary `Log.d`
  (tag `"Mirage"`) printing the specific numbers in question, ask Aryan to
  reproduce the real condition and filter Logcat on that tag, read the real
  numbers back, decide from data. Don't guess thresholds. Remove the
  logging once calibrated — it's been added and removed several times this
  project; that's expected, not a sign something's wrong.

## Open questions — still unanswered (from CLAUDE.md)

- **Target phone brand/model and Android version?** Still only "Motorola" —
  the actual blocker for Milestone 7.
- **Card assets**: only `7C.png` and `KH.png` exist. All 52 **white faces**
  (the pink back is never shown to camera, confirmed — don't worry about
  matching it) still need real photographs before any real performance,
  independent of the ML work.
- ~~Is Mode A enough, or is Mode B required?~~ **Answered**: Mode B. Don't
  re-ask.
- **FPS acceptance criterion (≥24fps)**: measurable now via the debug FPS
  counter, but nobody's reported back a number with the full Mode B
  pipeline running. Worth checking at some point, not urgent right now
  given the detector itself is being replaced.

## Next step

**Aryan is away filming.** When the next session starts:

- **If footage has landed**: extract frames (`ffmpeg`, every 5th-10th
  frame), build the Python pre-fill script (item 3 above) if not already
  done, run it over the frames to produce `guesses.json`, then open
  `tools/label-tool.html` and start labeling for real.
- **If footage hasn't landed yet**: build the Python pre-fill script now
  anyway (item 3) — it doesn't need footage to write, only to run, so
  there's no reason to wait idle.

Don't default back to "more classical CV tuning" as the next move — that
thread was deliberately closed out this session (see the ML initiative
section above) after two full calibration rounds showed the pattern of
diminishing, condition-specific returns.

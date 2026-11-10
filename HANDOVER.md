# Handover — Mirage (Card Swap Camera)

Read this first, then [`CLAUDE.md`](CLAUDE.md) (the full spec). This file is
session-to-session state; `CLAUDE.md` is the stable spec and doesn't change
week to week.

## Where things stand

- **Branch**: `milestone-3-state-and-settings` (not yet merged to `main`)
- **Milestone 1 — done**: empty project, CameraX deps, camera permission
  flow, portrait lock, immersive mode, `FLAG_KEEP_SCREEN_ON`. Verified
  running on Aryan's physical Motorola phone.
- **Milestone 2 — done**: `CameraController` binds the front camera via
  CameraX `ImageAnalysis` only (no `PreviewView` — see "Key decisions" in
  CLAUDE.md). `FrameProcessor` converts each `ImageProxy` to a `Bitmap`,
  rotates it by `imageInfo.getRotationDegrees()`, mirrors it horizontally,
  and hands it to `FrameRenderer`, which draws it on the `SurfaceView` in
  `activity_fake_camera.xml`. Verified: live mirrored front-camera feed on
  screen, no crash, no black screen. Not yet verified: actual FPS on
  target device (acceptance criteria wants ≥24 fps) — nobody has measured
  this yet, worth doing before trusting Mode A timing later.
- **Milestone 3 — not started.** `ForceState`, `CardRepository`,
  `HiddenControls` are still stub classes with `TODO` comments only.

## Process note for this repo

The working agreement in `CLAUDE.md` says Aryan writes the implementation
and Claude guides. **Milestones 1 and 2 were written directly by Claude**,
at Aryan's explicit request for that session ("give me the code"). Unless
Aryan says otherwise again, **default back to the guide-don't-write
agreement starting with Milestone 3** — explain what to do and which
APIs/classes to use, let him write it, review after.

## Known environment gotchas (so you don't re-debug them)

- This is Aryan's first Android project. He's done iOS + Java before, not
  Android — lean on iOS/Java analogies when explaining Android-specific
  concepts (lifecycle, `SurfaceView`, Gradle vs. Xcode build settings, etc.).
- First Android Studio open on this machine had a stale `.idea/` folder from
  before the project existed, so Gradle sync never ran and there was no run
  configuration. Fixed by closing and reopening the project root folder
  fresh. If a similar "Run button greyed out" or "need to edit a
  configuration" issue reappears, check `.idea/` is actually synced to the
  current `settings.gradle.kts`/`build.gradle.kts`, not stale.
- Local Gradle 9.6.0 + AGP 9.4.1 are already cached on this machine, so
  first builds are fast; a different machine will need to download these.
- Test device: a physical Motorola phone, connected via USB
  debugging. Emulators are explicitly not reliable for judging this app —
  front-camera feed on an emulator is synthetic and won't reflect real
  timing/FPS.

## Open questions — still unanswered (from CLAUDE.md)

These block real progress past Milestone 4 and should be asked again if
Aryan hasn't answered them yet:

- **Target phone brand/model and Android version?** (Only "Motorola" is
  known; exact model/OS version needed for Milestone 7's fake camera UI and
  for tuning Mode A's blur/position to that specific lens.)
- **Which deck does the magician use?** (Needed to photograph real card
  faces for `assets/cards/`; currently empty except for a placeholder note.)
- **Is Mode A enough for his routine, or is Mode B required?** (Determines
  whether Milestones 5–6, the OpenCV tracking work, are needed at all.)

## Next step

Milestone 3: `ForceState` (SharedPreferences-backed mode/trigger/forced
card), `CardRepository` (loads `assets/cards/*.png`), wire up
`HiddenControls` (2s long-press top-left → Settings, volume up/down → step
forced card) in `FakeCameraActivity`. All three classes already exist as
stubs with `TODO` comments pointing at this milestone — see
`app/src/main/java/com/aryan/mirage/data/`, `.../controls/`.

Per the process note above: guide Aryan through writing this rather than
writing it directly, unless he asks again.

# Mirage — Card Swap Camera

A magic-trick app. The phone lies on a table, screen up, showing what looks
like the stock camera app using the **front camera**. The magician holds a
playing card face-down over the phone, so the card's face points at the
camera and spectators only see its back. The screen shows a **different
card** (a pre-selected "forced" card) instead of the real one.

Full spec, architecture, and build order: see [`CLAUDE.md`](CLAUDE.md).

## Status

Milestone 3 in progress (state + hidden settings). See
[`HANDOVER.md`](HANDOVER.md) for exact current state and next steps.

## Requirements

- Android Studio (current stable)
- Android SDK: compileSdk/targetSdk 35, minSdk 26
- JDK 17
- A physical Android device for testing — the camera-swap illusion cannot be
  meaningfully judged on an emulator

## Getting started

1. Clone the repo and open the root folder (the one containing
   `settings.gradle.kts`) in Android Studio — not the `app` subfolder.
2. Let Gradle sync finish.
3. Connect a physical device over USB with Developer Options → USB debugging
   enabled, and accept the debugging prompt on the device.
4. Select the `app` run configuration and your device, then Run
   (`Shift+F10`).
5. Approve the camera permission prompt on first launch.

## Project structure

```
FakeCameraActivity       launcher activity: full-screen camera feed + hidden controls
 ├─ CameraController      binds CameraX (front lens) with ImageAnalysis only
 ├─ FrameProcessor        per-frame pipeline (mirror → detect → composite)
 │   ├─ CardDetector      OpenCV quad detection + temporal smoothing (Mode B)
 │   └─ CardCompositor    warp + colour/blur match + feathered blend
 ├─ FrameRenderer         draws output frames to a SurfaceView
 └─ HiddenControls        long-press zone, volume keys
SettingsActivity          hidden screen: forced card, mode, trigger, tuning sliders
CardRepository            loads card bitmaps from assets/cards/
ForceState                current card, mode, settings (SharedPreferences)
```

## Tech stack

- Java, Android Studio, Gradle (Kotlin DSL build files)
- [CameraX](https://developer.android.com/training/camerax) — `camera-core`,
  `camera-camera2`, `camera-lifecycle`
- [OpenCV for Android](https://opencv.org/) (`org.opencv:opencv`, Maven
  Central, 4.9+) — used from Milestone 5 onward for card detection

## Build order (milestones)

1. Project setup — empty Java project, CameraX dependencies, camera
   permission, portrait lock, immersive mode
2. Front camera feed — `ImageAnalysis` → Bitmap → `SurfaceView`, correct
   rotation and mirroring
3. State + Settings — `ForceState`, `CardRepository`, hidden long-press to
   open Settings, volume-key card stepping
4. Mode A — fixed overlay, manual trigger first, then `AUTO_BRIGHTNESS`.
   **Test the trick with the magician here.**
5. OpenCV integration — convert frames to `Mat`, debug-draw detected quads
   (no swap yet)
6. Mode B — perspective warp, then blending, then smoothing and hysteresis
7. Fake camera UI — copy the target phone's stock camera chrome
8. Polish — performance pass, edge cases (white ceiling, low light, fingers
   on corners)

Full detail on each milestone, acceptance criteria, and known risks: see
[`CLAUDE.md`](CLAUDE.md).

## Card assets

`app/src/main/assets/cards/{rank}{suit}.png` — e.g. `AS.png`, `10H.png`,
`KD.png`. Placeholder-only until the magician's actual deck is photographed;
see [`app/src/main/assets/cards/README.md`](app/src/main/assets/cards/README.md).

## Design decisions

- **`CardRepository` caches decoded `Bitmap`s in memory, keyed by filename.**
  Decoding a PNG from `assets/` is disk + CPU work; the compositor will end up
  asking for the forced card's bitmap on every frame or trigger once Mode
  A/B are live, and the app needs to sustain ≥24 fps (see acceptance criteria
  in `CLAUDE.md`). Decoding once and reusing the `Bitmap` avoids repeating
  that work dozens of times a second.

## Working agreement

This project is being built with Claude Code as a step-by-step guide, not an
autopilot — see the "Working agreement for the session" section of
[`CLAUDE.md`](CLAUDE.md) before starting a new session.

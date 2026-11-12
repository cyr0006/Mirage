# Card Swap Camera — Android MVP Spec

Hand this file to Claude at the start of a development session (or drop it in the repo root as `CLAUDE.md`).

## Working agreement for the session
- **Claude writes the code directly (as of 2026-11-12, Aryan's explicit request).** This replaced the original "Aryan writes, Claude guides" agreement starting partway through Milestone 4, once the work moved into OpenCV/Mode B territory. Claude implements, compiles (`gradlew compileDebugJavaWithJavac`) to catch errors before handing it over, and Aryan tests on the real device.
- **This is my first Android project** (iOS + Java background, no Android). Even though Claude writes the code now, keep explaining it the same way as before:
  - Say what the code does *and* how it fits into the app's architecture — which other class calls it, what reads this state later, why this piece exists.
  - Treat it like a dev tutorial: introduce new Android-specific APIs/concepts as they come up (lifecycle, `SharedPreferences`, `SurfaceView`, Gradle vs. Xcode build settings, etc.), ideally with an iOS/Java analogy.
  - Keep explanations simple and concise — no padding.
- Be direct and critical. Flag bad decisions early.
- Work one milestone at a time (see Build Order). Don't move on until the current milestone runs on a real device.

## What the app does
A magic-trick app. The phone lies on a table, screen up, showing what looks like the stock camera app using the **front camera**. The magician holds a playing card face-down over the phone, so the card's face points at the camera and spectators only see its back. The screen shows a **different card** (a pre-selected "forced" card) instead of the real one. When the real card is turned over, it doesn't match what was on screen.

## Platform and stack
- Android, **minSdk 26**, targetSdk = latest stable, **portrait only**
- **Java**, Android Studio, Gradle (Kotlin DSL build files are fine)
- **CameraX**: `camera-core`, `camera-camera2`, `camera-lifecycle`
- **OpenCV for Android**: `org.opencv:opencv` (Maven Central, 4.9+)
- Test device: the magician's actual phone (brand/model: _fill in_)

## Screens
### 1. FakeCameraActivity (launcher)
- Full-screen, immersive (hide status/nav bars), `FLAG_KEEP_SCREEN_ON`
- Shows the processed front-camera feed, rendered by the app itself
- On top: a copy of the target phone's stock camera UI (shutter button, mode labels, etc.), built from screenshots of that phone's real camera app
- Hidden controls (none visible):
  - Long-press top-left corner for 2 s → open Settings
  - Volume up / down → next / previous card in the force list
  - Optional: tap the shutter button → manual overlay show/hide toggle

### 2. SettingsActivity (hidden)
- Pick the forced card (52-card grid)
- Mode: `FIXED_OVERLAY` | `TRACKED` | `REAL` (no swap, so the app can be shown honestly)
- Trigger: `MANUAL` | `AUTO_BRIGHTNESS` (fixed mode) | `AUTO_DETECT` (tracked mode)
- Sliders: overlay blur, overlay scale/position (fixed mode), detection sensitivity
- Toggle: debug overlay (draw detected contours/corners, FPS)
- Persist everything in `SharedPreferences`

## Architecture
```
FakeCameraActivity
 ├─ CameraController     – binds CameraX (front lens) with ImageAnalysis only
 ├─ FrameProcessor       – per-frame pipeline (mirror → detect → composite)
 │   ├─ CardDetector     – OpenCV quad detection + temporal smoothing
 │   └─ CardCompositor   – warp + colour/blur match + feathered blend
 ├─ FrameRenderer        – draws output frames to a SurfaceView
 └─ HiddenControls       – long-press zone, volume keys
CardRepository           – loads card bitmaps from assets/cards/
ForceState               – current card, mode, settings (SharedPreferences)
```

### Key decisions (don't change without a reason)
- **Render frames yourself.** Use `ImageAnalysis` (`OUTPUT_IMAGE_FORMAT_RGBA_8888`, `STRATEGY_KEEP_ONLY_LATEST`), then process and draw to a `SurfaceView`. **Do not** use `PreviewView` with a View on top: its frames aren't synced with analysis frames, so the overlay would lag behind the hand.
- **Mirror manually.** Analysis frames are not mirrored; the stock front preview is. Flip horizontally (`Core.flip(mat, mat, 1)`) so the output looks like the real camera app. The card overlay must be mirrored the same way as a real card would appear.
- Handle rotation using `imageInfo.getRotationDegrees()`.
- Run processing on a single background executor. Never on the UI thread.

## Card assets
- `assets/cards/{rank}{suit}.png`, e.g. `AS.png`, `10H.png`, `KD.png` (ranks A,2–10,J,Q,K; suits S,H,D,C)
- **Photograph the magician's actual deck** (flat, even light, cropped to the card edge, ~500×700 px). Use a public-domain set only as a placeholder until then.

## Mode A — Fixed overlay (MVP target)
- The magician holds the card at roughly the same spot each time (about 20–30 cm above the lens)
- When triggered, fade in (~120 ms) the forced card at the configured position and scale, Gaussian-blurred to match how out-of-focus a real card looks at that distance
- `AUTO_BRIGHTNESS` trigger: the mean luminance of the central region jumps above baseline + threshold for N frames → show; drops below it for M frames → hide. The baseline adapts slowly while nothing is showing.

## Mode B — Tracked replacement
Per frame, on a downscaled copy (e.g. 480 px wide):
1. Greyscale → `GaussianBlur` → `Canny` (or `adaptiveThreshold`) → `dilate`
2. `findContours` → for the largest contours, `approxPolyDP`; accept a 4-point convex quad with aspect ratio about 1.25–1.55 and area above X% of the frame
3. Fallback: `minAreaRect` on the largest bright blob (for when fingers cover a corner)
4. Order the corners consistently (TL, TR, BR, BL). Smooth them over time (exponential moving average). Hysteresis: show after N consecutive detections, hide after M misses.
5. Scale the corners back up to full resolution
6. `getPerspectiveTransform` + `warpPerspective` the card bitmap onto the quad
7. Blend: match the mean/std brightness of the detected region, apply blur at a similar level, add light noise, then composite with a feathered mask (blurred quad mask as alpha)
8. Debug mode: draw the quad, corner indices and FPS

## Acceptance criteria
- At least 24 fps on the target phone with the card swap on
- Overlay appears within ~150 ms of the card arriving; no visible flicker while the card is held steady
- Side by side with the real camera app, a layperson can't tell the difference within ~5 s
- No visible UI or toast gives the trick away; Settings can only be reached through the hidden gesture
- Camera permission flow works on first launch. The permission prompt should happen during setup, not during a performance.

## Build order (milestones)
1. **Project setup**: empty Java project, CameraX dependencies, camera permission, portrait lock, immersive mode
2. **Front camera feed**: `ImageAnalysis` → Bitmap → `SurfaceView`, with correct rotation and mirroring. Check FPS.
3. **State + Settings**: `ForceState`, `CardRepository`, hidden long-press to open Settings, volume-key card stepping
4. **Mode A**: fixed overlay, manual trigger first, then `AUTO_BRIGHTNESS`. **Test the trick with the magician here.**
5. **OpenCV integration**: convert frames to `Mat`, debug-draw detected quads (no swap yet)
6. **Mode B**: perspective warp, then blending, then smoothing and hysteresis
7. **Fake camera UI**: copy the target phone's stock camera chrome
8. **Polish**: performance pass, edge cases (white ceiling, low light, fingers on corners)

## Known risks
- **Fingers covering corners** break quad detection. Use the `minAreaRect` fallback and have him hold the card by its long edges.
- **White or bright ceiling** gives low contrast. Use the edge-based path and tune thresholds on the target phone.
- **Performance on older phones**: detect on downscaled frames and reuse `Mat` buffers (don't allocate per frame).
- **Camera UIs differ by brand**: build the fake UI for the target phone only.
- **IP**: copy the trick's concept, not the iOS app's assets or UI.

## Open questions (answer at the start of the session)
- Target phone brand/model and Android version?
- Which deck does he use (for photographing the card faces)?
- Is Mode A enough for his routine, or is Mode B required?

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

**Status as of 2026-11-14: this classical pipeline is built and working, but has a demonstrated ceiling.** Two real calibration rounds (day, then night with an actual structural fix — locking camera AE/AWB) each fixed a real, data-confirmed problem, but each also surfaced a new one at the margins (small/dim/partially-revealed card). The pattern — every fix works, but only for the condition it was tested under — is itself the signal, not impatience. See `card-detection-writeup.txt` in the repo root for the full writeup (written for an external audience, but it's the accurate internal postmortem too).

**Decision: move the detection step (not the whole pipeline) to a trained model.** Everything downstream of "here are the 4 corners" — warp, brightness-match, blend — stays as-is; only `CardDetector`'s job of finding those corners is being replaced. The classical pipeline isn't being thrown away: it's now the bootstrap/pre-fill mechanism for labeling training data (see below), and may end up staying in some form (e.g. as a fallback). This is a genuinely bigger initiative than anything else in this file — different toolchain (model training, TFLite), real data-collection work, no bounded "should take one session" estimate. See "ML detector initiative" below for where this actually stands.

## ML detector initiative (started 2026-11-13/14)

**Why**: see the Mode B status note above — classical thresholds don't generalize across lighting/distance/occlusion the way a trained model should.

**Labels**: 4 corner points (TL/TR/BR/BL, matching `CardDetector.Quad` exactly) per frame where a card is visible, or an explicit "no card" for negative frames (hand alone, face, empty scene — these matter as much as positive examples, since false-positives on skin have been the recurring failure).

**Target volume**: ~800-1,500 positive frames + ~200-400 negative frames. Bootstrapped from the existing classical detector for the easy/clean cases (pre-fill a guess, human just confirms or drags into place) rather than hand-labeling everything from zero — the classical pipeline is still useful here even though it's being replaced as the live detector.

**Tooling already built**:
- `tools/label-tool.html` — a standalone, no-install labeling tool. Open directly in Chrome/Edge. Picks a folder of frame images (auto-saves `labels.json` back into that folder as you go, via the File System Access API) or falls back to a plain file picker + manual download on other browsers. Can load a pre-filled `guesses.json` to speed up review. See the tool's own sidebar for controls/shortcuts.
- **Not yet built**: the Python/OpenCV script that ports `CardDetector`'s logic to batch-process extracted frames and produce that `guesses.json` pre-fill file. This doesn't strictly need footage to exist first — it's a mechanical port of logic that already exists in `CardDetector.java` — so it can be written before or independent of the footage shoot.
- **Not yet built**: frame extraction from video (one `ffmpeg` command per clip, sampling every 5th-10th frame, not every one) — trivial once there are actual video files to point it at.
- **Not yet built**: the actual training pipeline (pick a lightweight architecture, train via transfer learning, convert to TFLite, integrate into the app). Realistic estimate given to Aryan: 1-2+ weeks with real iteration uncertainty, once labeled data exists.

**Shoot list given to Aryan** (he may film with a friend's help): 15 short clips (~20-30s each) — for each of 3 lighting conditions (day / indoor artificial / night), capture (1) full-frame hold at varying position/rotation/distance, (2) the card's real entry/exit direction, swept in and out a few times, (3) entry from 1-2 other directions for robustness, (4) a deliberately bad/occluded grip, (5) a negative clip with no card — hand alone + face in frame.

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
- **Target phone brand/model and Android version?** Still unanswered — still the actual blocker on Milestone 7 (fake camera chrome).
- **Card assets**: only `7C.png` and `KH.png` exist in `assets/cards/`. The real routine only ever shows the card's **white face** to the camera (confirmed 2026-11-13) — the pink back is never relevant, so that's one less thing to worry about matching. All 52 white faces still need real photographs before any real performance, independent of all the ML work above.
- ~~Is Mode A enough, or is Mode B required?~~ **Answered**: Mode B (tracked) is required. Don't re-ask this.

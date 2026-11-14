package com.aryan.mirage.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.SystemClock;

import androidx.camera.core.ImageProxy;

import com.aryan.mirage.composite.CardCompositor;
import com.aryan.mirage.data.Card;
import com.aryan.mirage.data.CardRepository;
import com.aryan.mirage.data.ForceState;
import com.aryan.mirage.detect.CardDetector;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;

/**
 * Per-frame pipeline: mirror -> detect -> composite. See SPEC.md "Key decisions"
 * (manual mirroring, rotation via imageInfo.getRotationDegrees()) and Modes A/B.
 */
public class FrameProcessor {

    private static final long FADE_IN_MS = 120;
    private static final long FPS_UPDATE_INTERVAL_MS = 1000;

    private final CardDetector cardDetector;
    private final CardCompositor cardCompositor;
    private final FrameRenderer frameRenderer;
    private final CardRepository cardRepository;
    private final ForceState forceState;

    // Reused across frames for the Bitmap -> Mat conversion -- see SPEC.md "Known risks"
    // (don't allocate buffers per frame).
    private final Mat frameMat = new Mat();

    private boolean overlayVisible = false;
    private long overlayShownAtMs = 0;

    private int framesSinceFpsUpdate = 0;
    private long lastFpsUpdateMs = 0;
    private float currentFps = 0f;

    public FrameProcessor(CardDetector cardDetector, CardCompositor cardCompositor,
                           FrameRenderer frameRenderer, CardRepository cardRepository,
                           ForceState forceState) {
        this.cardDetector = cardDetector;
        this.cardCompositor = cardCompositor;
        this.frameRenderer = frameRenderer;
        this.cardRepository = cardRepository;
        this.forceState = forceState;
    }

    /**
     * Called from HiddenControls' manual-trigger tap. Stands in for a real shutter-button
     * tap until Milestone 7 builds the fake camera chrome.
     */
    public void toggleOverlayVisible() {
        overlayVisible = !overlayVisible;
        if (overlayVisible) {
            overlayShownAtMs = SystemClock.elapsedRealtime();
        }
    }

    public void process(ImageProxy image) {
        try {
            Bitmap raw = toBitmap(image);
            Bitmap oriented = mirrorAndRotate(raw, image.getImageInfo().getRotationDegrees());
            raw.recycle();

            ForceState.Mode mode = forceState.getMode();
            if (mode == ForceState.Mode.FIXED_OVERLAY && overlayVisible) {
                Card forcedCard = forceState.getForcedCard();
                Bitmap card = cardRepository.getBitmap(forcedCard);
                long elapsedMs = SystemClock.elapsedRealtime() - overlayShownAtMs;
                float alpha = Math.min(1f, elapsedMs / (float) FADE_IN_MS);
                cardCompositor.compositeFixed(oriented, card, forcedCard.toString(),
                        forceState.getOverlayScale(), forceState.getOverlayBlur(), alpha);
            } else if (mode == ForceState.Mode.TRACKED) {
                cardDetector.setSensitivity(forceState.getDetectionSensitivity());
                Utils.bitmapToMat(oriented, frameMat);
                CardDetector.Quad quad = cardDetector.detect(frameMat);
                if (quad != null) {
                    // compositeTracked itself shows only the slice of the fake card that
                    // corresponds to how much of the real one is actually visible --
                    // see its own doc comment. Partial or full, this is the one path now.
                    Card forcedCard = forceState.getForcedCard();
                    Bitmap card = cardRepository.getBitmap(forcedCard);
                    cardCompositor.compositeTracked(frameMat, card, forcedCard.toString(), quad);
                    Utils.matToBitmap(frameMat, oriented);
                }
            }

            updateFps();
            if (forceState.isDebugOverlayEnabled()) {
                drawFpsCounter(oriented, currentFps);
            }

            frameRenderer.render(oriented);
            oriented.recycle();
        } finally {
            // Must always close, or CameraX stops delivering new frames after this one.
            image.close();
        }
    }

    /** Rolling once-a-second FPS estimate -- the spec's acceptance criterion is >=24fps,
     * and this is the first time it's actually been measured rather than eyeballed. */
    private void updateFps() {
        framesSinceFpsUpdate++;
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - lastFpsUpdateMs;
        if (elapsed >= FPS_UPDATE_INTERVAL_MS) {
            currentFps = framesSinceFpsUpdate * 1000f / elapsed;
            framesSinceFpsUpdate = 0;
            lastFpsUpdateMs = now;
        }
    }

    private static void drawFpsCounter(Bitmap frame, float fps) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.GREEN);
        paint.setTextSize(48f);
        new Canvas(frame).drawText(
                String.format(java.util.Locale.US, "%.0f fps", fps), 24f, 64f, paint);
    }

    /** RGBA_8888 analysis frame -> Bitmap, cropping off any row-stride padding. */
    private static Bitmap toBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();

        // The buffer's row width (rowStride / pixelStride) can be wider than the real
        // image width due to padding, so decode at that width first, then crop.
        Bitmap bitmap = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        if (rowStride != width * pixelStride) {
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();
            return cropped;
        }
        return bitmap;
    }

    /**
     * Analysis frames are unrotated and unmirrored; fix both so the output matches
     * what a real front-camera preview looks like. See SPEC.md "Key decisions".
     */
    private static Bitmap mirrorAndRotate(Bitmap source, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        matrix.postScale(-1, 1);
        return Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}

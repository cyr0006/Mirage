package com.aryan.mirage.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.camera.core.ImageProxy;

import com.aryan.mirage.composite.CardCompositor;
import com.aryan.mirage.detect.CardDetector;

import java.nio.ByteBuffer;

/**
 * Per-frame pipeline: mirror -> detect -> composite. See SPEC.md "Key decisions"
 * (manual mirroring, rotation via imageInfo.getRotationDegrees()) and Modes A/B.
 *
 * Milestone 2 only needs a correctly oriented, mirrored frame on screen — detection
 * (CardDetector) and compositing (CardCompositor) aren't called yet, which is why
 * callers can pass null for both until Milestone 4/5.
 */
public class FrameProcessor {

    private final CardDetector cardDetector;
    private final CardCompositor cardCompositor;
    private final FrameRenderer frameRenderer;

    public FrameProcessor(CardDetector cardDetector, CardCompositor cardCompositor,
                           FrameRenderer frameRenderer) {
        this.cardDetector = cardDetector;
        this.cardCompositor = cardCompositor;
        this.frameRenderer = frameRenderer;
    }

    public void process(ImageProxy image) {
        try {
            Bitmap raw = toBitmap(image);
            Bitmap oriented = mirrorAndRotate(raw, image.getImageInfo().getRotationDegrees());
            raw.recycle();
            frameRenderer.render(oriented);
            oriented.recycle();
        } finally {
            // Must always close, or CameraX stops delivering new frames after this one.
            image.close();
        }
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

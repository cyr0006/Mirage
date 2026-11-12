package com.aryan.mirage.composite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.opencv.core.Mat;

import com.aryan.mirage.detect.CardDetector;

/**
 * Warp + colour/blur match + feathered blend. See SPEC.md "Mode A" (fixed position/
 * scale + Gaussian blur) and "Mode B" step 6-7 (perspective warp + blend onto a quad).
 *
 * Mode A stays in plain Bitmap/Canvas — OpenCV isn't integrated until Milestone 5, and
 * drawing a static image onto another doesn't need it. Mode B (below) still uses Mat
 * since it genuinely needs warpPerspective.
 */
public class CardCompositor {

    private Bitmap cachedProcessedCard;
    private String cachedCardKey;
    private float cachedScale;
    private float cachedBlurStrength;

    /**
     * Mode A: composite the forced card at a fixed position/scale/blur onto the frame.
     * Draws directly into {@code frame} (it's mutable, freshly created per frame by
     * FrameProcessor) rather than allocating a new Bitmap.
     *
     * The scaled+blurred card is cached and only rebuilt when cardKey/scale/blurStrength
     * actually change -- rebuilding it every frame (two Bitmap allocations plus a blur
     * pass) would eat into the 24fps budget for no reason, since none of those inputs
     * change on their own between frames.
     */
    public void compositeFixed(Bitmap frame, Bitmap card, String cardKey, float scale,
                                float blurStrength, float alpha) {
        boolean cacheStale = cachedProcessedCard == null
                || !cardKey.equals(cachedCardKey)
                || scale != cachedScale
                || blurStrength != cachedBlurStrength;

        if (cacheStale) {
            cachedProcessedCard = buildProcessedCard(card, frame.getWidth(), scale, blurStrength);
            cachedCardKey = cardKey;
            cachedScale = scale;
            cachedBlurStrength = blurStrength;
        }

        float left = (frame.getWidth() - cachedProcessedCard.getWidth()) / 2f;
        float top = (frame.getHeight() - cachedProcessedCard.getHeight()) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha((int) (alpha * 255));

        Canvas canvas = new Canvas(frame);
        canvas.save();
        // Mirror the card around its own center -- the camera frame is already
        // mirrored (selfie-style), so a real held card would look mirrored too.
        canvas.scale(-1, 1,
                left + cachedProcessedCard.getWidth() / 2f,
                top + cachedProcessedCard.getHeight() / 2f);
        canvas.drawBitmap(cachedProcessedCard, left, top, paint);
        canvas.restore();
    }

    private static Bitmap buildProcessedCard(Bitmap card, int frameWidth, float scale,
                                              float blurStrength) {
        int targetWidth = (int) (frameWidth * scale);
        int targetHeight = (int) (card.getHeight() * ((float) targetWidth / card.getWidth()));
        Bitmap scaledCard = Bitmap.createScaledBitmap(card, targetWidth, targetHeight, true);
        return blur(scaledCard, blurStrength);
    }

    /**
     * Cheap stand-in for a true Gaussian blur (OpenCV's Imgproc.GaussianBlur isn't
     * available until Milestone 5): shrink the bitmap down, then scale it back up.
     * Bilinear resampling on the way back up smears detail into a convincing blur
     * for very little cost.
     */
    private static Bitmap blur(Bitmap source, float strength) {
        if (strength <= 0f) {
            return source;
        }
        int smallWidth = Math.max(1, (int) (source.getWidth() * (1f - strength)));
        int smallHeight = Math.max(1, (int) (source.getHeight() * (1f - strength)));
        Bitmap small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true);
        Bitmap result = Bitmap.createScaledBitmap(small, source.getWidth(), source.getHeight(), true);
        small.recycle();
        return result;
    }

    /** Mode B: warp the card bitmap onto the detected quad and blend it in. */
    public void compositeTracked(Mat frame, Mat cardBitmap, CardDetector.Quad quad) {
        // TODO (Milestone 6): getPerspectiveTransform + warpPerspective, match mean/std
        // brightness, matching blur + light noise, feathered-mask composite.
    }
}

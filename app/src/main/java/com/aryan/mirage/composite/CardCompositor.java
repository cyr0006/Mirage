package com.aryan.mirage.composite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.aryan.mirage.detect.CardDetector;

import java.util.Arrays;
import java.util.List;

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

    // A standard poker card is ~3.5in x 2.5in -- used to estimate how much of the card is
    // actually visible when it's still entering/exiting frame (see computeSrcCorners).
    private static final double CARD_ASPECT_HEIGHT_OVER_WIDTH = 1.4;

    // Mode B's warped card, cached the same way as compositeFixed's processed card --
    // rebuilding this (Bitmap->Mat conversion, flip) every frame would be wasted work
    // since the forced card doesn't change on its own between frames.
    private Mat cachedCardMat;
    private String cachedTrackedCardKey;
    private final Mat warpedCard = new Mat();
    private final Mat warpedMask = new Mat();
    private final Mat featheredMask = new Mat();

    // Scratch buffers for matchBrightness/alphaBlend below -- reused across frames rather
    // than allocated per call, same reasoning as everywhere else performance-sensitive.
    private final Mat frameFloat = new Mat();
    private final Mat cardFloat = new Mat();
    private final Mat maskFloat = new Mat();
    private final Mat maskMultiChannel = new Mat();
    private final Mat invMaskSingle = new Mat();
    private final Mat invMaskMultiChannel = new Mat();
    private final Mat blended = new Mat();

    /**
     * Mode B: warp the card bitmap onto the detected quad, match its brightness/contrast
     * to the region it's covering, soften its edges slightly, then blend it in through a
     * feathered (soft-edged) mask instead of a hard cutout. Modifies {@code frame} in
     * place. See SPEC.md "Mode B" step 7.
     *
     * While the card is still entering/exiting frame (quad.isPartial()), only the
     * corresponding slice of the fake card is warped in -- see computeSrcCorners() --
     * so a card that's 40% revealed shows 40% of the fake one, correctly scaled, not the
     * whole image squeezed into a quarter of its real size.
     */
    public void compositeTracked(Mat frame, Bitmap card, String cardKey, CardDetector.Quad quad) {
        updateCachedTrackedCard(card, cardKey);

        Point[] srcPoints = computeSrcCorners(cachedCardMat, quad);
        MatOfPoint2f srcCorners = new MatOfPoint2f(srcPoints);
        MatOfPoint2f dstCorners = new MatOfPoint2f(
                new Point(quad.topLeft[0], quad.topLeft[1]),
                new Point(quad.topRight[0], quad.topRight[1]),
                new Point(quad.bottomRight[0], quad.bottomRight[1]),
                new Point(quad.bottomLeft[0], quad.bottomLeft[1]));

        Mat transform = Imgproc.getPerspectiveTransform(srcCorners, dstCorners);
        srcCorners.release();

        Imgproc.warpPerspective(cachedCardMat, warpedCard, transform, frame.size());
        transform.release();

        // Built directly from the destination quad, not by warping a mask -- warping a
        // mask that's white outside the (possibly cropped) srcCorners region would let
        // the hidden part of the card "bleed" into frame wherever the perspective
        // transform happens to extrapolate it to. Filling the quad's own footprint
        // directly has no such issue: the visible region is exactly the quad, full stop.
        warpedMask.create(frame.size(), CvType.CV_8UC1);
        warpedMask.setTo(new Scalar(0));
        MatOfPoint dstPoly = new MatOfPoint(dstCorners.toArray());
        Imgproc.fillConvexPoly(warpedMask, dstPoly, new Scalar(255));
        dstPoly.release();
        dstCorners.release();

        // Match brightness/contrast using the hard (unblurred) mask, so the stats reflect
        // only genuine card pixels -- not diluted by a fringe of background/card-edge
        // pixels the way the feathered mask below would include.
        matchBrightness(frame, warpedCard, warpedMask);

        // Soften the card's own edges slightly -- a real card at this distance wouldn't
        // be perfectly crisp like a rendered PNG.
        Imgproc.GaussianBlur(warpedCard, warpedCard, new Size(3, 3), 0);

        // Feather the mask edges so the blend below fades in rather than cutting in
        // sharply -- this is the mask actually used for the blend. Kept fairly narrow:
        // a soft edge necessarily bleeds a little past the true boundary by design, and
        // a wider kernel here was making the overlay read as visibly larger than the
        // real card.
        Imgproc.GaussianBlur(warpedMask, featheredMask, new Size(9, 9), 0);

        alphaBlend(frame, warpedCard, featheredMask);
    }

    /**
     * Which rectangle of the source card bitmap to warp. Fully visible (not isPartial())
     * -> the whole card, same as always. Clipped on exactly one axis -> estimate how much
     * of the card is actually showing by comparing the quad's visible dimension on that
     * axis to what it "should" be given the other (unclipped) dimension and the card's
     * known aspect ratio, then crop the source to just that fraction -- from whichever
     * side is still off-screen. Clipped on two axes at once (a corner, not an edge) is a
     * rarer case this doesn't specially handle; falls back to the whole card rather than
     * guessing wrong.
     */
    private static Point[] computeSrcCorners(Mat cardMat, CardDetector.Quad quad) {
        int cols = cardMat.cols();
        int rows = cardMat.rows();

        double top = 0, bottom = rows, left = 0, right = cols;

        boolean verticalClip = quad.touchesTop ^ quad.touchesBottom;
        boolean horizontalClip = quad.touchesLeft ^ quad.touchesRight;

        if (verticalClip && !horizontalClip) {
            double visibleWidth = avgDistance(quad.topLeft, quad.topRight, quad.bottomLeft, quad.bottomRight);
            double visibleHeight = avgDistance(quad.topLeft, quad.bottomLeft, quad.topRight, quad.bottomRight);
            double expectedFullHeight = visibleWidth * CARD_ASPECT_HEIGHT_OVER_WIDTH;
            double visibleFraction = clamp01(visibleHeight / expectedFullHeight);
            if (quad.touchesTop) {
                // Card's top is still off-screen -- what's visible is its bottom slice.
                top = rows * (1 - visibleFraction);
            } else {
                // touchesBottom: card's bottom is still off-screen -- top slice visible.
                bottom = rows * visibleFraction;
            }
        } else if (horizontalClip && !verticalClip) {
            double visibleWidth = avgDistance(quad.topLeft, quad.topRight, quad.bottomLeft, quad.bottomRight);
            double visibleHeight = avgDistance(quad.topLeft, quad.bottomLeft, quad.topRight, quad.bottomRight);
            double expectedFullWidth = visibleHeight / CARD_ASPECT_HEIGHT_OVER_WIDTH;
            double visibleFraction = clamp01(visibleWidth / expectedFullWidth);
            if (quad.touchesLeft) {
                left = cols * (1 - visibleFraction);
            } else {
                right = cols * visibleFraction;
            }
        }

        return new Point[] {
                new Point(left, top), new Point(right, top),
                new Point(right, bottom), new Point(left, bottom)
        };
    }

    private static double avgDistance(double[] a1, double[] a2, double[] b1, double[] b2) {
        return (distance(a1, a2) + distance(b1, b2)) / 2.0;
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static double clamp01(double value) {
        return Math.max(0.1, Math.min(1.0, value));
    }

    /**
     * Rescales {@code card} in place so its mean/std brightness within {@code mask}'s
     * footprint matches {@code frame}'s there -- otherwise the overlay looks like a flat
     * sticker pasted over footage with its own real lighting and shading.
     */
    private void matchBrightness(Mat frame, Mat card, Mat mask) {
        MatOfDouble frameMean = new MatOfDouble();
        MatOfDouble frameStd = new MatOfDouble();
        Core.meanStdDev(frame, frameMean, frameStd, mask);

        MatOfDouble cardMean = new MatOfDouble();
        MatOfDouble cardStd = new MatOfDouble();
        Core.meanStdDev(card, cardMean, cardStd, mask);

        double[] fm = frameMean.toArray();
        double[] fs = frameStd.toArray();
        double[] cm = cardMean.toArray();
        double[] cs = cardStd.toArray();
        frameMean.release();
        frameStd.release();
        cardMean.release();
        cardStd.release();

        // Must work in float -- (pixel - mean) can go negative, which an 8-bit unsigned
        // Mat would clip to 0 and permanently lose before the final add below.
        card.convertTo(cardFloat, CvType.CV_32F);
        Core.subtract(cardFloat, new Scalar(cm[0], cm[1], cm[2], cm[3]), cardFloat);
        Core.multiply(cardFloat, new Scalar(
                safeRatio(fs[0], cs[0]), safeRatio(fs[1], cs[1]), safeRatio(fs[2], cs[2]), 1),
                cardFloat);
        Core.add(cardFloat, new Scalar(fm[0], fm[1], fm[2], fm[3]), cardFloat);
        cardFloat.convertTo(card, CvType.CV_8U);
    }

    private static double safeRatio(double numerator, double denominator) {
        return denominator > 1.0 ? numerator / denominator : 1.0;
    }

    /** Per-pixel weighted blend of frame and card using mask (0-255) as a continuous
     * alpha -- Mat.copyTo only supports an all-or-nothing mask, not a feathered one. */
    private void alphaBlend(Mat frame, Mat card, Mat mask) {
        Core.bitwise_not(mask, invMaskSingle);
        toFourChannelFloat(mask, maskMultiChannel);
        toFourChannelFloat(invMaskSingle, invMaskMultiChannel);

        frame.convertTo(frameFloat, CvType.CV_32F);
        card.convertTo(cardFloat, CvType.CV_32F);

        Core.multiply(cardFloat, maskMultiChannel, cardFloat);
        Core.multiply(frameFloat, invMaskMultiChannel, frameFloat);
        Core.add(cardFloat, frameFloat, blended);

        blended.convertTo(frame, CvType.CV_8U);
    }

    /** Broadcasts a single-channel 0-255 mask into a 4-channel [0,1] float Mat, one copy
     * per channel, so it can be multiplied directly against an RGBA float Mat. */
    private void toFourChannelFloat(Mat singleChannel8U, Mat destMultiChannelFloat) {
        singleChannel8U.convertTo(maskFloat, CvType.CV_32F, 1.0 / 255.0);
        List<Mat> channels = Arrays.asList(maskFloat, maskFloat, maskFloat, maskFloat);
        Core.merge(channels, destMultiChannelFloat);
    }

    private void updateCachedTrackedCard(Bitmap card, String cardKey) {
        if (cachedTrackedCardKey != null && cachedTrackedCardKey.equals(cardKey)) {
            return;
        }
        if (cachedCardMat != null) {
            cachedCardMat.release();
        }

        cachedCardMat = new Mat();
        Utils.bitmapToMat(card, cachedCardMat);
        // Mirror the card the same way compositeFixed does -- the frame is already
        // mirrored (selfie-style), so a real held card would look mirrored too.
        Core.flip(cachedCardMat, cachedCardMat, 1);

        cachedTrackedCardKey = cardKey;
    }
}

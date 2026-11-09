package com.aryan.mirage.detect;

import org.opencv.core.Mat;

/**
 * OpenCV quad detection + temporal smoothing. See SPEC.md "Mode B — Tracked
 * replacement" steps 1-4. Stretch goal after Mode A ships — see Build order.
 */
public class CardDetector {

    /**
     * Result of one detection pass. Corners ordered TL, TR, BR, BL, in full-resolution
     * coordinates (already scaled back up from the downscaled working copy).
     */
    public static class Quad {
        public final double[] topLeft;
        public final double[] topRight;
        public final double[] bottomRight;
        public final double[] bottomLeft;

        public Quad(double[] topLeft, double[] topRight, double[] bottomRight, double[] bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
        }
    }

    public Quad detect(Mat frame) {
        // TODO (Milestone 6): downscale -> greyscale -> GaussianBlur -> Canny -> dilate ->
        // findContours -> approxPolyDP (aspect ratio ~1.25-1.55) with minAreaRect fallback,
        // order corners, EMA-smooth, hysteresis (N hits to show, M misses to hide).
        return null;
    }
}

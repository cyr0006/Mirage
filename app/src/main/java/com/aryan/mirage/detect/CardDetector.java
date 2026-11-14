package com.aryan.mirage.detect;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenCV quad detection. See SPEC.md "Mode B — Tracked replacement" steps 1-4.
 *
 * Milestone 5: per-frame detection, debug-drawn by FrameProcessor, no swap yet. Full
 * EMA corner smoothing is still Milestone 6, but basic hysteresis (below) is pulled
 * forward now -- without it, every single-frame miss (motion blur, a lighting flicker)
 * instantly drops the quad, which makes it impossible to tell "detection is broken"
 * apart from "detection works but any real-time CV pipeline misses occasional frames."
 */
public class CardDetector {

    // TEMPORARY -- night-lighting calibration round 2, 2026-11-13. Remove once the
    // thresholds below are re-tuned from real night-condition numbers. Filter Logcat on
    // "Mirage" to read these.
    private static final String TAG = "Mirage";

    private static final int WORKING_WIDTH = 480;
    // Pulled back toward an actual card's proportions -- these had been loosened round
    // after round to chase missed detections, to the point they'd happily match a hand
    // or a face too. The skin-tone check below is the real fix for that; these just stop
    // being "almost anything" shaped.
    // Lowered from 0.04 on 2026-11-13 to catch a card sooner while it's still entering
    // frame -- see detectBrightBlob's doc comment on why that path no longer also gates
    // on aspect ratio, which is the other half of "catch it earlier."
    private static final double MIN_AREA_FRACTION = 0.02; // of the downscaled frame area
    private static final double MAX_AREA_FRACTION = 0.35;
    // Still used by the edge path (findBestQuad) -- set from real measured values (see
    // CardDetector calibration log, 2026-11-12): the white card's own aspect ratio
    // ranged 1.06-1.95 across natural handling.
    private static final double MIN_ASPECT_RATIO = 1.0;
    private static final double MAX_ASPECT_RATIO = 2.0;

    // A corner this close to the downscaled frame's edge counts as "touching" it --
    // i.e. the card's silhouette continues off-screen there. See touchedEdges().
    private static final int BOUNDARY_TOLERANCE_PX = 4;

    // Also set from that calibration log: measured face hue clustered tightly at 27-37,
    // just outside the original 25 cutoff -- raised to 40 to actually catch it. White
    // card hue (84-120) and pink card hue (148-153) are nowhere near this band, so this
    // doesn't risk rejecting either. Hand didn't cluster by hue/saturation at all in the
    // data (aspect ratio is what actually screens it out, being heavily elongated).
    private static final double SKIN_HUE_MAX = 40;
    private static final double SKIN_HUE_MIN_WRAP = 170; // hue wraps around at 179/0
    private static final double SKIN_SATURATION_MIN = 40;

    private static final int HITS_TO_SHOW = 2;
    private static final int MISSES_TO_HIDE = 6;
    // Exponential-moving-average weight for corner smoothing: lower = smoother but more
    // lag behind real movement, higher = snappier but jitterier.
    private static final double SMOOTHING_ALPHA = 0.4;

    // Reused across frames -- see SPEC.md "Known risks": don't allocate Mat buffers
    // per frame, it's expensive and this needs to sustain 24fps.
    private final Mat downscaled = new Mat();
    private final Mat grey = new Mat();
    private final Mat blurred = new Mat();
    private final Mat edges = new Mat();
    private final Mat dilated = new Mat();
    private final Mat lightBlurred = new Mat();
    private final Mat brightMask = new Mat();
    private final Mat erodedBrightMask = new Mat();
    private final Mat bgr = new Mat();
    private final Mat hsv = new Mat();
    private final Mat skinMaskLow = new Mat();
    private final Mat skinMaskHigh = new Mat();
    private final Mat skinMask = new Mat();
    private final Mat notSkinMask = new Mat();
    private final Mat hierarchy = new Mat();
    private final Mat dilateKernel = Mat.ones(5, 5, CvType.CV_8U);
    private final Mat erodeKernel = Mat.ones(5, 5, CvType.CV_8U);

    private boolean tracking = false;
    private int hitStreak = 0;
    private int missStreak = 0;
    private Quad lastGoodQuad = null;

    // Settings' "detection sensitivity" slider, 0-1 around a 0.5 midpoint that reproduces
    // the hardcoded MIN_AREA_FRACTION above exactly -- see setSensitivity().
    private double sensitivity = 0.5;

    /** @param sensitivity 0-1; higher accepts smaller/more marginal candidates. Called
     * once per frame from FrameProcessor, before detect(). */
    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    /**
     * Result of one detection pass. Corners ordered TL, TR, BR, BL, in full-resolution
     * coordinates (already scaled back up from the downscaled working copy). The four
     * {@code touches*} flags say whether that corner-side sits right at the frame's own
     * edge -- meaning the card's real silhouette continues off-screen there, i.e. it's
     * still (partly) entering or exiting frame. CardCompositor uses these to show only
     * the matching slice of the fake card instead of squeezing the whole thing into
     * whatever's currently visible.
     */
    public static class Quad {
        public final double[] topLeft;
        public final double[] topRight;
        public final double[] bottomRight;
        public final double[] bottomLeft;
        public final boolean touchesLeft;
        public final boolean touchesTop;
        public final boolean touchesRight;
        public final boolean touchesBottom;

        public Quad(double[] topLeft, double[] topRight, double[] bottomRight, double[] bottomLeft,
                     boolean touchesLeft, boolean touchesTop, boolean touchesRight, boolean touchesBottom) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
            this.touchesLeft = touchesLeft;
            this.touchesTop = touchesTop;
            this.touchesRight = touchesRight;
            this.touchesBottom = touchesBottom;
        }

        /** True while any side is still off-screen -- the card is actively entering or
         * exiting, as opposed to fully in frame and just being held/moved around. */
        public boolean isPartial() {
            return touchesLeft || touchesTop || touchesRight || touchesBottom;
        }
    }

    /**
     * @param frame full-resolution RGBA Mat. Returns null unless a card-shaped quad has
     * been seen in at least {@code HITS_TO_SHOW} consecutive frames, and keeps returning
     * the last good quad through up to {@code MISSES_TO_HIDE} consecutive misses before
     * giving up -- see the class doc for why this exists already at Milestone 5.
     */
    public Quad detect(Mat frame) {
        Quad raw = detectRaw(frame);

        if (raw != null) {
            hitStreak++;
            missStreak = 0;
            // No smoothing while the card is still entering/exiting (isPartial()) --
            // EMA is for damping jitter on an otherwise-still card, and would otherwise
            // just lag behind a silhouette that's genuinely changing fast and for real.
            lastGoodQuad = (lastGoodQuad == null || raw.isPartial()) ? raw : smooth(lastGoodQuad, raw);
            if (!tracking && hitStreak >= HITS_TO_SHOW) {
                tracking = true;
            }
        } else {
            missStreak++;
            hitStreak = 0;
            if (missStreak >= MISSES_TO_HIDE) {
                tracking = false;
                lastGoodQuad = null;
            }
        }

        return tracking ? lastGoodQuad : null;
    }

    /** Blends two quads' corners with an exponential moving average, to damp per-frame
     * jitter from recomputing detection independently every frame with no memory. */
    private static Quad smooth(Quad previous, Quad current) {
        return new Quad(
                lerp(previous.topLeft, current.topLeft),
                lerp(previous.topRight, current.topRight),
                lerp(previous.bottomRight, current.bottomRight),
                lerp(previous.bottomLeft, current.bottomLeft),
                current.touchesLeft, current.touchesTop, current.touchesRight, current.touchesBottom);
    }

    private static double[] lerp(double[] previous, double[] current) {
        return new double[] {
                previous[0] + (current[0] - previous[0]) * SMOOTHING_ALPHA,
                previous[1] + (current[1] - previous[1]) * SMOOTHING_ALPHA
        };
    }

    /** One frame's raw detection pass, with no memory of previous frames. */
    private Quad detectRaw(Mat frame) {
        double scaleFactor = (double) WORKING_WIDTH / frame.width();
        int workingHeight = (int) Math.round(frame.height() * scaleFactor);
        Imgproc.resize(frame, downscaled, new Size(WORKING_WIDTH, workingHeight));

        Imgproc.cvtColor(downscaled, grey, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(grey, blurred, new Size(5, 5), 0);
        Imgproc.Canny(blurred, edges, 30, 120);
        Imgproc.dilate(edges, dilated, dilateKernel);

        // Computed once per frame for the skin-tone check -- candidates get checked
        // against this same Mat rather than recomputing the color conversion per candidate.
        Imgproc.cvtColor(downscaled, bgr, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

        // A per-pixel skin mask, also computed once per frame -- detectBrightBlob uses
        // this to carve skin pixels out of its bright-region mask before fitting a shape,
        // so a hand touching the card doesn't grow the detected region to include it.
        // Hue wraps at 179/0, so skin's two hue sub-ranges need two separate inRange calls.
        Core.inRange(hsv, new Scalar(0, SKIN_SATURATION_MIN, 0), new Scalar(SKIN_HUE_MAX, 255, 255), skinMaskLow);
        Core.inRange(hsv, new Scalar(SKIN_HUE_MIN_WRAP, SKIN_SATURATION_MIN, 0), new Scalar(179, 255, 255), skinMaskHigh);
        Core.bitwise_or(skinMaskLow, skinMaskHigh, skinMask);
        Core.bitwise_not(skinMask, notSkinMask);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // sensitivity=0.5 (the slider's default) multiplies by exactly 1.0, reproducing
        // the original hardcoded threshold; 1.0 halves it (more lenient), 0.0 raises it
        // by 50% (more conservative).
        double minArea = WORKING_WIDTH * workingHeight * MIN_AREA_FRACTION * (1.5 - sensitivity);
        double maxArea = WORKING_WIDTH * workingHeight * MAX_AREA_FRACTION;
        MatOfPoint2f bestQuad = findBestQuad(contours, minArea, maxArea);

        Point[] corners;
        if (bestQuad != null) {
            corners = orderCorners(bestQuad.toArray());
            bestQuad.release();
        } else {
            corners = detectBrightBlob(minArea, maxArea);
        }

        for (MatOfPoint contour : contours) {
            contour.release();
        }

        if (corners == null) {
            return null;
        }
        boolean[] touched = touchedEdges(corners, WORKING_WIDTH, workingHeight);
        return toFullResolutionQuad(corners, scaleFactor, touched);
    }

    /** Which sides of the downscaled frame these corners sit right up against -- see
     * the Quad class doc comment for what that means. */
    private static boolean[] touchedEdges(Point[] corners, int frameWidth, int frameHeight) {
        boolean left = false, top = false, right = false, bottom = false;
        for (Point p : corners) {
            if (p.x <= BOUNDARY_TOLERANCE_PX) {
                left = true;
            }
            if (p.x >= frameWidth - 1 - BOUNDARY_TOLERANCE_PX) {
                right = true;
            }
            if (p.y <= BOUNDARY_TOLERANCE_PX) {
                top = true;
            }
            if (p.y >= frameHeight - 1 - BOUNDARY_TOLERANCE_PX) {
                bottom = true;
            }
        }
        return new boolean[] { left, top, right, bottom };
    }

    /** Largest contour that approximates to a convex 4-point, card-shaped quad. */
    private MatOfPoint2f findBestQuad(List<MatOfPoint> contours, double minArea, double maxArea) {
        MatOfPoint2f best = null;
        double bestArea = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < minArea || area > maxArea) {
                continue;
            }

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);
            contour2f.release();

            Point[] points = approx.toArray();
            boolean isGoodQuad = false;
            if (points.length == 4) {
                MatOfPoint asMat = new MatOfPoint(points);
                boolean convex = Imgproc.isContourConvex(asMat);
                asMat.release();

                if (convex) {
                    double aspectRatio = aspectRatioOf(points);
                    double[] hueSat = meanHueSat(points);
                    boolean skin = looksLikeSkin(hueSat[0], hueSat[1]);
                    Log.d(TAG, String.format(Locale.US,
                            "edgeQuad: area=%.0f aspect=%.2f hue=%.0f sat=%.0f skin=%b",
                            area, aspectRatio, hueSat[0], hueSat[1], skin));
                    isGoodQuad = aspectRatio >= MIN_ASPECT_RATIO && aspectRatio <= MAX_ASPECT_RATIO
                            && !skin && area > bestArea;
                }
            }

            if (isGoodQuad) {
                if (best != null) {
                    best.release();
                }
                best = approx;
                bestArea = area;
            } else {
                approx.release();
            }
        }
        return best;
    }

    /**
     * Fallback when edge detection fails to find a clean 4-point quad -- this covers both
     * SPEC.md "Known risks" cases: fingers covering a corner break the edge-based 4-point
     * approximation, and a low-contrast boundary (a white card against skin/background --
     * exactly what edge detection struggles with) never forms a sharp enough edge for
     * Canny to find in the first place.
     *
     * Edge detection depends on a brightness *gradient* at the card's boundary. This
     * doesn't -- it segments on absolute brightness instead, via Otsu's method (picks
     * the cutoff that best splits the image into two brightness clusters, no manual
     * threshold needed). A card, especially a white one, is usually distinctly brighter
     * overall than a hand or typical background even when its edge is soft, so this finds
     * it as "the bright blob" even where Canny sees no edge at all.
     *
     * Uses a lighter blur than the edge-detection pass (3x3, not 5x5) and erodes the
     * resulting mask -- a bright white card is prone to overexposure/halation in the
     * camera sensor, where its apparent brightness bleeds slightly past its true edge,
     * and heavier blur smears that bleed outward even further. Both of those grow the
     * detected region past the card's real boundary, which is why the tracked overlay
     * came out visibly larger than the actual card. Eroding pulls it back in.
     *
     * Also carves skin-colored pixels (notSkinMask, built in detectRaw()) out of the
     * mask before fitting a shape to it. A well-lit hand is bright enough to pass the
     * same Otsu threshold as the card, so a hand touching the card's edge merges into
     * one connected bright region -- this is what was causing the tracked overlay to
     * expand to include part of the hand. Checking a candidate's *average* color after
     * the fact (as the skin-tone check elsewhere does) can't catch this: averaging
     * card-white with hand-skin dilutes the skin signal below the threshold even though
     * part of the region clearly is skin. Removing skin pixels before shape-fitting,
     * pixel by pixel, catches it regardless of what fraction of the blob is hand.
     *
     * Deliberately does NOT gate on aspect ratio (unlike findBestQuad) -- a card still
     * sliding into frame is clipped by the screen edge, so its visible silhouette is
     * some other shape entirely until it's almost fully in view; requiring "looks like
     * a full flat card" here would mean never tracking it until it already is one.
     * Skin exclusion is the real defense against a bare hand here, not shape.
     */
    private Point[] detectBrightBlob(double minArea, double maxArea) {
        Imgproc.GaussianBlur(grey, lightBlurred, new Size(3, 3), 0);
        // threshold() returns the actual cutoff Otsu picked -- logging it directly shows
        // how the brightness split is landing differently under different lighting.
        double otsuThreshold = Imgproc.threshold(
                lightBlurred, brightMask, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Log.d(TAG, String.format(Locale.US, "otsuThreshold=%.0f", otsuThreshold));
        Core.bitwise_and(brightMask, notSkinMask, brightMask);
        Imgproc.erode(brightMask, erodedBrightMask, erodeKernel);

        List<MatOfPoint> brightContours = new ArrayList<>();
        Imgproc.findContours(erodedBrightMask, brightContours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint largest = null;
        double largestArea = 0;
        for (MatOfPoint contour : brightContours) {
            double area = Imgproc.contourArea(contour);
            if (area >= minArea && area <= maxArea && area > largestArea) {
                largest = contour;
                largestArea = area;
            }
        }

        Point[] result = null;
        if (largest != null) {
            Point[] ordered = approximateQuad(largest);
            double[] hueSat = meanHueSat(ordered);
            boolean skin = looksLikeSkin(hueSat[0], hueSat[1]);
            Log.d(TAG, String.format(Locale.US,
                    "brightBlob: area=%.0f aspect=%.2f hue=%.0f sat=%.0f skin=%b",
                    largestArea, aspectRatioOf(ordered), hueSat[0], hueSat[1], skin));
            if (!skin) {
                result = ordered;
            }
        }

        for (MatOfPoint contour : brightContours) {
            contour.release();
        }
        return result;
    }

    /**
     * Prefers the contour's actual 4-point shape over a bounding rectangle: rolling or
     * yawing a flat card relative to the lens turns its silhouette into a trapezoid, not
     * a smaller rectangle, and {@code minAreaRect} can only ever return a true rectangle
     * -- it has no way to represent that distortion. {@code approxPolyDP} returns the
     * contour's real corners instead, trapezoid included, so this tries that first and
     * only falls back to the bounding rectangle when the contour doesn't cleanly reduce
     * to a convex quad (e.g. a finger breaking the shape).
     */
    private Point[] approximateQuad(MatOfPoint contour) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        double perimeter = Imgproc.arcLength(contour2f, true);
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);
        contour2f.release();

        Point[] points = approx.toArray();
        approx.release();

        if (points.length == 4) {
            MatOfPoint asMat = new MatOfPoint(points);
            boolean convex = Imgproc.isContourConvex(asMat);
            asMat.release();
            if (convex) {
                return orderCorners(points);
            }
        }

        MatOfPoint2f contourAsFloat = new MatOfPoint2f(contour.toArray());
        RotatedRect rect = Imgproc.minAreaRect(contourAsFloat);
        contourAsFloat.release();
        Point[] rectPoints = new Point[4];
        rect.points(rectPoints);
        return orderCorners(rectPoints);
    }

    /** Mean [hue, saturation] inside this candidate's own quad (not the whole frame). */
    private double[] meanHueSat(Point[] corners) {
        Mat mask = Mat.zeros(hsv.size(), CvType.CV_8UC1);
        MatOfPoint poly = new MatOfPoint(corners);
        Imgproc.fillConvexPoly(mask, poly, new Scalar(255));
        poly.release();

        Scalar meanColor = Core.mean(hsv, mask);
        mask.release();
        return new double[] { meanColor.val[0], meanColor.val[1] };
    }

    private static boolean looksLikeSkin(double hue, double saturation) {
        boolean skinHue = hue <= SKIN_HUE_MAX || hue >= SKIN_HUE_MIN_WRAP;
        return skinHue && saturation >= SKIN_SATURATION_MIN;
    }

    private static double aspectRatioOf(Point[] corners) {
        Point[] ordered = orderCorners(corners);
        double width = distance(ordered[0], ordered[1]);
        double height = distance(ordered[1], ordered[2]);
        if (width <= 0 || height <= 0) {
            return 0;
        }
        return Math.max(width, height) / Math.min(width, height);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /**
     * An unordered set of 4 points doesn't tell you which corner is which. Sum (x+y) is
     * smallest at the top-left corner and largest at bottom-right; difference (y-x) is
     * smallest at top-right and largest at bottom-left. That's enough to sort any
     * roughly axis-aligned quad into TL/TR/BR/BL regardless of rotation.
     */
    private static Point[] orderCorners(Point[] points) {
        Point topLeft = points[0], topRight = points[0], bottomRight = points[0], bottomLeft = points[0];
        double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;

        for (Point p : points) {
            double sum = p.x + p.y;
            double diff = p.y - p.x;
            if (sum < minSum) { minSum = sum; topLeft = p; }
            if (sum > maxSum) { maxSum = sum; bottomRight = p; }
            if (diff < minDiff) { minDiff = diff; topRight = p; }
            if (diff > maxDiff) { maxDiff = diff; bottomLeft = p; }
        }
        return new Point[] { topLeft, topRight, bottomRight, bottomLeft };
    }

    private static Quad toFullResolutionQuad(Point[] corners, double scaleFactor, boolean[] touched) {
        double[][] scaled = new double[4][2];
        for (int i = 0; i < 4; i++) {
            scaled[i][0] = corners[i].x / scaleFactor;
            scaled[i][1] = corners[i].y / scaleFactor;
        }
        return new Quad(scaled[0], scaled[1], scaled[2], scaled[3],
                touched[0], touched[1], touched[2], touched[3]);
    }
}

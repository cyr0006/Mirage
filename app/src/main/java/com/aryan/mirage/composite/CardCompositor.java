package com.aryan.mirage.composite;

import org.opencv.core.Mat;

import com.aryan.mirage.detect.CardDetector;

/**
 * Warp + colour/blur match + feathered blend. See SPEC.md "Mode A" (fixed position/
 * scale + Gaussian blur) and "Mode B" step 6-7 (perspective warp + blend onto a quad).
 */
public class CardCompositor {

    /** Mode A: composite the forced card at a fixed position/scale/blur onto the frame. */
    public void compositeFixed(Mat frame, Mat cardBitmap, float scale, float blurRadius) {
        // TODO (Milestone 4): fade-in (~120ms), position per Settings, Gaussian blur to
        // match the real card's out-of-focus look at ~20-30cm.
    }

    /** Mode B: warp the card bitmap onto the detected quad and blend it in. */
    public void compositeTracked(Mat frame, Mat cardBitmap, CardDetector.Quad quad) {
        // TODO (Milestone 6): getPerspectiveTransform + warpPerspective, match mean/std
        // brightness, matching blur + light noise, feathered-mask composite.
    }
}

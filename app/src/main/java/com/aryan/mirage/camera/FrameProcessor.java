package com.aryan.mirage.camera;

import androidx.camera.core.ImageProxy;

import com.aryan.mirage.composite.CardCompositor;
import com.aryan.mirage.detect.CardDetector;

/**
 * Per-frame pipeline: mirror -> detect -> composite. See SPEC.md "Key decisions"
 * (manual mirroring, rotation via imageInfo.getRotationDegrees()) and Modes A/B.
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
        // TODO (Milestone 2+): convert to Mat, flip horizontally, rotate by
        // image.getImageInfo().getRotationDegrees(), run detection (Mode B) or the
        // fixed-position trigger (Mode A), composite, hand the result to frameRenderer.
        image.close();
    }
}

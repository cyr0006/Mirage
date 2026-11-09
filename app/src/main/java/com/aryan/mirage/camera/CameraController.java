package com.aryan.mirage.camera;

import android.content.Context;

import androidx.lifecycle.LifecycleOwner;

/**
 * Binds CameraX (front lens) with ImageAnalysis only — no Preview use case.
 * See SPEC.md "Key decisions": frames are rendered by this app, not PreviewView.
 */
public class CameraController {

    private final Context context;
    private final LifecycleOwner lifecycleOwner;

    public CameraController(Context context, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void start(FrameProcessor frameProcessor) {
        // TODO (Milestone 2): ProcessCameraProvider -> bind ImageAnalysis
        // (OUTPUT_IMAGE_FORMAT_RGBA_8888, STRATEGY_KEEP_ONLY_LATEST) on a single
        // background executor, forward each frame to frameProcessor.
    }

    public void stop() {
        // TODO: unbind use cases.
    }
}

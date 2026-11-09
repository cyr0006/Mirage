package com.aryan.mirage.camera;

import android.graphics.Bitmap;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Draws output frames to a SurfaceView. See SPEC.md "Key decisions".
 */
public class FrameRenderer {

    private final SurfaceView surfaceView;

    public FrameRenderer(SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
    }

    public void render(Bitmap frame) {
        // TODO (Milestone 2): lock the SurfaceHolder's canvas, draw the bitmap, unlock+post.
        SurfaceHolder holder = surfaceView.getHolder();
    }
}

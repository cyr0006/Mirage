package com.aryan.mirage.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

/**
 * Draws output frames to a SurfaceView. See SPEC.md "Key decisions".
 */
public class FrameRenderer {

    private final SurfaceView surfaceView;
    private volatile boolean surfaceReady;

    public FrameRenderer(SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
        // The Surface may not exist yet when the camera starts delivering frames;
        // render() drops frames until surfaceCreated() fires.
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                surfaceReady = true;
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                // No-op: each frame is scaled to the current surface size on every draw.
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                surfaceReady = false;
            }
        });
    }

    /** Called from the background analysis executor — never touches the UI thread. */
    public void render(Bitmap frame) {
        if (!surfaceReady) {
            return;
        }
        SurfaceHolder holder = surfaceView.getHolder();
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawBitmap(frame, null, new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }
}

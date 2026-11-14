package com.aryan.mirage.camera;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Binds CameraX (front lens) with ImageAnalysis only — no Preview use case.
 * See SPEC.md "Key decisions": frames are rendered by this app, not PreviewView.
 */
public class CameraController {

    private static final String TAG = "CameraController";

    // How long to let the camera settle on the baseline scene (table/background, before
    // any card is held up) before locking AE/AWB -- see lockAutoExposureAndWhiteBalance().
    private static final long AE_AWB_LOCK_DELAY_MS = 2000;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProcessCameraProvider cameraProvider;

    public CameraController(Context context, LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void start(FrameProcessor frameProcessor) {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(context);

        // The listener runs on the main executor because bindToLifecycle() must be
        // called on the main thread.
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bind(frameProcessor);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to obtain ProcessCameraProvider", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bind(FrameProcessor frameProcessor) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        // All frame processing happens on this single background executor, never on
        // the UI thread — see SPEC.md "Key decisions".
        imageAnalysis.setAnalyzer(analysisExecutor, frameProcessor::process);

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis);
        lockAutoExposureAndWhiteBalance(camera);
    }

    /**
     * Locks auto-exposure and auto-white-balance a couple of seconds after the camera
     * starts, once it's had time to settle on the baseline scene. Without this, a bright
     * card suddenly filling part of the frame shifts the scene's overall color statistics
     * enough that the camera's continuous AWB reacts and re-converges -- during that
     * ~250ms transient, a white card's measured hue can swing all the way down into real
     * skin-tone territory (see CardDetector's calibration log, night round, 2026-11-13:
     * hue dropped to 11-12, matching measured face hue of 10-16, before climbing back to
     * its stable ~110-120 once AWB settled). No hue/saturation threshold can tell those
     * apart, because during that transient they really are the same color reading.
     * Locking AE/AWB keeps the reading stable instead of chasing a moving target.
     *
     * Tradeoff: the camera won't adapt if the room's actual lighting changes after this
     * fires -- acceptable for a short, controlled performance, not for an open-ended one.
     */
    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void lockAutoExposureAndWhiteBalance(Camera camera) {
        mainHandler.postDelayed(() -> {
            CaptureRequestOptions options = new CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                    .build();
            Camera2CameraControl.from(camera.getCameraControl()).setCaptureRequestOptions(options);
        }, AE_AWB_LOCK_DELAY_MS);
    }

    public void stop() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        analysisExecutor.shutdown();
    }
}

package com.aryan.mirage.camera;

import android.content.Context;
import android.util.Log;

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

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

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
        cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis);
    }

    public void stop() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        analysisExecutor.shutdown();
    }
}

package com.aryan.mirage;

import android.os.Bundle;
import android.view.SurfaceView;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import com.aryan.mirage.camera.CameraController;
import com.aryan.mirage.camera.FrameProcessor;
import com.aryan.mirage.camera.FrameRenderer;
import com.aryan.mirage.controls.HiddenControls;
import com.aryan.mirage.data.ForceState;

/**
 * Launcher activity. See SPEC.md "1. FakeCameraActivity" and the architecture diagram.
 */
public class FakeCameraActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                }
                // TODO: handle denial (explain + exit, or retry) before the next milestone.
            });

    private CameraController cameraController;
    private FrameRenderer frameRenderer;
    private HiddenControls hiddenControls;
    private ForceState forceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_fake_camera);
        enterImmersiveMode();

        forceState = new ForceState(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        // TODO (Milestone 3): wire up HiddenControls — long-press top-left (2s) -> Settings,
        // volume up/down -> step forced card.
    }

    private void startCamera() {
        // Do not use PreviewView here — see SPEC.md "Key decisions".
        SurfaceView surfaceView = findViewById(R.id.surfaceView);
        frameRenderer = new FrameRenderer(surfaceView);
        // CardDetector/CardCompositor plug in at Milestone 4/5 — unused for now.
        FrameProcessor frameProcessor = new FrameProcessor(null, null, frameRenderer);
        cameraController = new CameraController(this, this);
        cameraController.start(frameProcessor);
    }

    @Override
    protected void onDestroy() {
        if (cameraController != null) {
            cameraController.stop();
        }
        super.onDestroy();
    }

    private void enterImmersiveMode() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}

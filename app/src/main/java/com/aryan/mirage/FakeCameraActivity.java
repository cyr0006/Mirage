package com.aryan.mirage;

import android.os.Bundle;
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
        // TODO (Milestone 2): bind CameraController to the SurfaceView via FrameRenderer.
        // Do not use PreviewView here — see SPEC.md "Key decisions".
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

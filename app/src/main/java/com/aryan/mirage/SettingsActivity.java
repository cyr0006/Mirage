package com.aryan.mirage;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aryan.mirage.data.ForceState;

/**
 * Hidden activity, reached only via HiddenControls' long-press gesture. See SPEC.md
 * "2. SettingsActivity".
 */
public class SettingsActivity extends AppCompatActivity {

    private ForceState forceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        forceState = new ForceState(this);

        // TODO (Milestone 3+): wire up card grid, mode/trigger spinners, sliders and the
        // debug-overlay switch to ForceState, persisting on every change.
    }
}

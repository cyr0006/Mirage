package com.aryan.mirage.controls;

import android.view.KeyEvent;
import android.view.View;

/**
 * Long-press top-left corner (2s) -> Settings. Volume up/down -> next/previous
 * forced card. See SPEC.md "1. FakeCameraActivity / Hidden controls".
 */
public class HiddenControls {

    public interface Listener {
        void onOpenSettingsRequested();
        void onNextCard();
        void onPreviousCard();
    }

    private final Listener listener;

    public HiddenControls(Listener listener) {
        this.listener = listener;
    }

    /** Attach the long-press-corner gesture to the activity's root view. */
    public void attachTo(View rootView) {
        // TODO (Milestone 3): detect a 2s press confined to the top-left corner region,
        // then call listener.onOpenSettingsRequested().
    }

    /** Call from the Activity's onKeyDown; return true if the key was consumed. */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            listener.onNextCard();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            listener.onPreviousCard();
            return true;
        }
        return false;
    }
}

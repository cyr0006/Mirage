package com.aryan.mirage.controls;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
        void onManualTriggerTap();
    }

    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openSettingsRunnable;

    public HiddenControls(Listener listener) {
        this.listener = listener;
        this.openSettingsRunnable = () -> this.listener.onOpenSettingsRequested();
    }
    private static final long LONG_PRESS_MS = 2000;
    private static final int CORNER_SIZE_DP = 80;
    private static final long TAP_MAX_MS = 300;

    /** Attach the long-press-corner gesture to the activity's root view. */
    @SuppressLint("ClickableViewAccessibility")
    public void attachTo(View rootView) {
        float density = rootView.getResources().getDisplayMetrics().density;
        float cornerSizePx = CORNER_SIZE_DP * density;

        rootView.setOnTouchListener((v,event) -> {
            boolean inCorner = event.getX() <= cornerSizePx && event.getY() <= cornerSizePx;

            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(inCorner){
                        handler.postDelayed(openSettingsRunnable, LONG_PRESS_MS);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(openSettingsRunnable);
                    if (!inCorner && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        long pressDuration = event.getEventTime() - event.getDownTime();
                        if (pressDuration < TAP_MAX_MS) {
                            listener.onManualTriggerTap();
                        }
                    }
                    return false;
                default:
                    return false;
            }
        });
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

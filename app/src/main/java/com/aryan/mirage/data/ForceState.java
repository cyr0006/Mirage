package com.aryan.mirage.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Current forced card, mode, trigger and tuning sliders. Persisted in
 * SharedPreferences. See SPEC.md "2. SettingsActivity".
 */
public class ForceState {
    private static final String KEY_MODE = "mode";
    private static final String KEY_TRIGGER = "trigger";
    public static final String KEY_RANK = "forces_rank";
    public static final String KEY_SUIT = "forced_suit";
    private static final String KEY_OVERLAY_BLUR = "overlay_blur";
    private static final String KEY_OVERLAY_SCALE = "overlay_scale";
    private static final String KEY_DETECTION_SENSITIVITY = "detection_sensitivity";
    private static final String KEY_DEBUG_OVERLAY = "debug_overlay";
    private static final String PREFS_NAME = "force_state";

    // Defaults match what FrameProcessor had hardcoded before these sliders existed, so
    // wiring them doesn't change behavior for anyone who hasn't touched Settings yet.
    private static final float DEFAULT_OVERLAY_BLUR = 0.3f;
    private static final float DEFAULT_OVERLAY_SCALE = 0.4f;
    private static final float DEFAULT_DETECTION_SENSITIVITY = 0.5f;

    public enum Mode { FIXED_OVERLAY, TRACKED, REAL }

    public enum Trigger { MANUAL, AUTO_BRIGHTNESS, AUTO_DETECT }

    private final SharedPreferences prefs;

    public ForceState(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Card getForcedCard() {
        String rankCode = prefs.getString(KEY_RANK, Card.Rank.SEVEN.code);
        String suitCode = prefs.getString(KEY_SUIT, Card.Suit.CLUBS.code);
        return new Card(Card.Rank.fromCode(rankCode), Card.Suit.fromCode(suitCode));
    }

    public void setForcedCard(Card card) {
        prefs.edit()
                .putString(KEY_RANK, card.rank.code)
                .putString(KEY_SUIT, card.suit.code)
                .apply();
    }

    public Mode getMode() {
        String value = prefs.getString(KEY_MODE, Mode.FIXED_OVERLAY.name());
        return Mode.valueOf(value);
    }

    public void setMode(Mode mode) {
        prefs.edit().putString(KEY_MODE, mode.name()).apply();
    }

    public Trigger getTrigger() {
        String value = prefs.getString(KEY_TRIGGER, Trigger.MANUAL.name());
        return Trigger.valueOf(value);
    }

    public void setTrigger(Trigger trigger) {
        prefs.edit().putString(KEY_TRIGGER, trigger.name()).apply();
    }

    public void stepForcedCard(int direction){
        Card current = getForcedCard();
        Card.Rank[] ranks = Card.Rank.values();
        Card.Suit[] suits = Card.Suit.values();
        int index = current.suit.ordinal() * ranks.length + current.rank.ordinal();
        int total = ranks.length * suits.length;
        index = (index + direction + total) % total;
        setForcedCard(new Card(ranks[index % ranks.length], suits[index / ranks.length]));
    }

    public float getOverlayBlur() {
        return prefs.getFloat(KEY_OVERLAY_BLUR, DEFAULT_OVERLAY_BLUR);
    }

    public void setOverlayBlur(float blur) {
        prefs.edit().putFloat(KEY_OVERLAY_BLUR, blur).apply();
    }

    public float getOverlayScale() {
        return prefs.getFloat(KEY_OVERLAY_SCALE, DEFAULT_OVERLAY_SCALE);
    }

    public void setOverlayScale(float scale) {
        prefs.edit().putFloat(KEY_OVERLAY_SCALE, scale).apply();
    }

    /** 0-1, where 0.5 (the default) reproduces CardDetector's original hardcoded
     * thresholds; higher values accept smaller/more marginal detections. */
    public float getDetectionSensitivity() {
        return prefs.getFloat(KEY_DETECTION_SENSITIVITY, DEFAULT_DETECTION_SENSITIVITY);
    }

    public void setDetectionSensitivity(float sensitivity) {
        prefs.edit().putFloat(KEY_DETECTION_SENSITIVITY, sensitivity).apply();
    }

    public boolean isDebugOverlayEnabled() {
        return prefs.getBoolean(KEY_DEBUG_OVERLAY, false);
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEBUG_OVERLAY, enabled).apply();
    }

    // TODO: overlay position slider, if that turns out to be needed once the fake
    // camera UI (Milestone 7) fixes exactly where the lens sits on screen.
}

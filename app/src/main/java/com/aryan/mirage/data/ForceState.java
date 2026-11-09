package com.aryan.mirage.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Current forced card, mode, trigger and tuning sliders. Persisted in
 * SharedPreferences. See SPEC.md "2. SettingsActivity".
 */
public class ForceState {

    private static final String PREFS_NAME = "force_state";

    public enum Mode { FIXED_OVERLAY, TRACKED, REAL }

    public enum Trigger { MANUAL, AUTO_BRIGHTNESS, AUTO_DETECT }

    private final SharedPreferences prefs;

    public ForceState(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Card getForcedCard() {
        // TODO (Milestone 3): read rank/suit keys, default to a sensible card.
        return null;
    }

    public void setForcedCard(Card card) {
        // TODO (Milestone 3): persist rank/suit keys.
    }

    public Mode getMode() {
        // TODO (Milestone 3): read + parse, default FIXED_OVERLAY.
        return Mode.FIXED_OVERLAY;
    }

    public void setMode(Mode mode) {
        // TODO (Milestone 3): persist.
    }

    public Trigger getTrigger() {
        // TODO (Milestone 3): read + parse, default MANUAL.
        return Trigger.MANUAL;
    }

    public void setTrigger(Trigger trigger) {
        // TODO (Milestone 3): persist.
    }

    // TODO: overlay blur, overlay scale/position, detection sensitivity, debug-overlay
    // toggle — same read/persist pattern as above, added as each milestone needs them.
}

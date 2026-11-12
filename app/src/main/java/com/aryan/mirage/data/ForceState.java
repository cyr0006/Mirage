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
    private static final String PREFS_NAME = "force_state";

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

    // TODO: overlay blur, overlay scale/position, detection sensitivity, debug-overlay
    // toggle — same read/persist pattern as above, added as each milestone needs them.
}

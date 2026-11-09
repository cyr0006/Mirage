package com.aryan.mirage.data;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads card bitmaps from assets/cards/. See SPEC.md "Card assets".
 */
public class CardRepository {

    private final Context context;
    private final Map<String, Bitmap> cache = new HashMap<>();

    public CardRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public Bitmap getBitmap(Card card) {
        // TODO (Milestone 3): load from "cards/" + card.assetFileName() via
        // context.getAssets().open(...), decode, cache in `cache`.
        return cache.get(card.assetFileName());
    }
}

package com.aryan.mirage.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
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
        String fileName = card.assetFileName();
        Bitmap cached = cache.get(fileName);

        if (cached != null) {
            return cached;
        }
        try (InputStream stream = context.getAssets().open("cards/" + fileName)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            return bitmap;
        } catch (IOException e){
            throw new RuntimeException("Missing card Asset: " + fileName, e);
        }
    }
}

package com.example.simpleapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class ThemeHelper {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_BACKGROUND = "background_base64";
    private static final String KEY_BG_ALPHA = "bg_alpha";

    private SharedPreferences prefs;

    public ThemeHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkMode(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply();
    }

    public void saveBackground(Bitmap bitmap) {
        if (bitmap == null) {
            prefs.edit().remove(KEY_BACKGROUND).apply();
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        prefs.edit().putString(KEY_BACKGROUND, base64).apply();
    }

    public Bitmap getBackground() {
        String base64 = prefs.getString(KEY_BACKGROUND, null);
        if (base64 == null) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasBackground() {
        return prefs.contains(KEY_BACKGROUND);
    }

    public int getBgAlpha() {
        return prefs.getInt(KEY_BG_ALPHA, 100);
    }

    public void setBgAlpha(int alpha) {
        prefs.edit().putInt(KEY_BG_ALPHA, alpha).apply();
    }
}
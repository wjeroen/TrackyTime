package com.timetracker.overlay;

import android.content.Context;
import android.content.SharedPreferences;

public class OverlayPreferences {

    private static final String PREFS = "overlay_prefs";
    private SharedPreferences sp;

    public OverlayPreferences(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getBgColor() { return sp.getInt("bg_color", 0xFF1E1E2E); }
    public void setBgColor(int c) { sp.edit().putInt("bg_color", c).apply(); }

    public int getTextColor() { return sp.getInt("text_color", 0xFFCDD6F4); }
    public void setTextColor(int c) { sp.edit().putInt("text_color", c).apply(); }

    public int getAccentColor() { return sp.getInt("accent_color", 0xFF89B4FA); }
    public void setAccentColor(int c) { sp.edit().putInt("accent_color", c).apply(); }

    // 0-255
    public int getOpacity() { return sp.getInt("opacity", 230); }
    public void setOpacity(int o) { sp.edit().putInt("opacity", o).apply(); }

    // 0=small, 1=medium, 2=large
    public int getSize() { return sp.getInt("size", 1); }
    public void setSize(int s) { sp.edit().putInt("size", s).apply(); }

    public float getTextSize() {
        switch (getSize()) {
            case 0: return 12f;
            case 2: return 18f;
            default: return 14f;
        }
    }

    public float getTimerTextSize() {
        switch (getSize()) {
            case 0: return 16f;
            case 2: return 24f;
            default: return 20f;
        }
    }
}

package com.timetracker.overlay;

import android.content.Context;
import android.content.SharedPreferences;

public class OverlayPreferences {

    private static final String PREFS = "overlay_prefs";
    private SharedPreferences sp;

    public OverlayPreferences(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getBgColor() { return sp.getInt("bg_color", 0xFFFFFFFF); }
    public void setBgColor(int c) { sp.edit().putInt("bg_color", c).apply(); }

    public int getTextColor() { return sp.getInt("text_color", 0xFF000000); }
    public void setTextColor(int c) { sp.edit().putInt("text_color", c).apply(); }

    // 0-255 (153 ≈ 60% user-visible opacity)
    public int getOpacity() { return sp.getInt("opacity", 153); }
    public void setOpacity(int o) { sp.edit().putInt("opacity", o).apply(); }

    // Border color (accent) — used for the overlay border
    public int getAccentColor() { return sp.getInt("accent_color", 0xFF000000); }
    public void setAccentColor(int c) { sp.edit().putInt("accent_color", c).apply(); }

    // 0-6 dp (0 = no border)
    public int getBorderWidth() { return sp.getInt("border_width", 1); }
    public void setBorderWidth(int w) { sp.edit().putInt("border_width", w).apply(); }

    // Overlay pulse (bg + border breathe in sync with timeline bar)
    public boolean isOverlayPulseEnabled() { return sp.getBoolean("overlay_pulse", true); }
    public void setOverlayPulseEnabled(boolean on) { sp.edit().putBoolean("overlay_pulse", on).apply(); }

    // 0=small, 1=medium, 2=large
    public int getSize() { return sp.getInt("size", 0); }
    public void setSize(int s) { sp.edit().putInt("size", s).apply(); }

    /** Unified text size — activity name, timer, separator all use this. */
    public float getTextSize() {
        switch (getSize()) {
            case 0: return 14f;
            case 2: return 20f;
            default: return 16f;
        }
    }

    /** Same as getTextSize() — timer matches activity text. */
    public float getTimerTextSize() {
        return getTextSize();
    }
}

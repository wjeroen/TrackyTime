package com.timetracker.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    public int getBorderWidth() { return sp.getInt("border_width", 2); }
    public void setBorderWidth(int w) { sp.edit().putInt("border_width", w).apply(); }

    // Overlay pulse (bg + border breathe in sync with timeline bar)
    public boolean isOverlayPulseEnabled() { return sp.getBoolean("overlay_pulse", true); }
    public void setOverlayPulseEnabled(boolean on) { sp.edit().putBoolean("overlay_pulse", on).apply(); }

    // 0=small, 1=medium, 2=large, 3=extra large
    public int getSize() { return sp.getInt("size", 0); }
    public void setSize(int s) { sp.edit().putInt("size", s).apply(); }

    /** Unified text size — activity name, timer, separator all use this. */
    public float getTextSize() {
        switch (getSize()) {
            case 0: return 14f;
            case 2: return 20f;
            case 3: return 30f; // extra large for computer use
            default: return 16f;
        }
    }

    /** Same as getTextSize() — timer matches activity text. */
    public float getTimerTextSize() {
        return getTextSize();
    }

    // Quick-select activity shortcuts (newline-delimited)
    public List<String> getQuickActivities() {
        String raw = sp.getString("quick_activities", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }
    public void setQuickActivities(List<String> activities) {
        sp.edit().putString("quick_activities", String.join("\n", activities)).apply();
    }

    /**
     * Export all preferences as a JSON-compatible string.
     * Format: "bgColor:0xFFFFFFFF,textColor:0xFF000000,opacity:153,..."
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("bgColor:0x").append(Integer.toHexString(getBgColor())).append(",");
        sb.append("textColor:0x").append(Integer.toHexString(getTextColor())).append(",");
        sb.append("opacity:").append(getOpacity()).append(",");
        sb.append("accentColor:0x").append(Integer.toHexString(getAccentColor())).append(",");
        sb.append("borderWidth:").append(getBorderWidth()).append(",");
        sb.append("overlayPulse:").append(isOverlayPulseEnabled()).append(",");
        sb.append("size:").append(getSize());
        return sb.toString();
    }

    /**
     * Import preferences from an exported string. Backward-compatible: ignores unrecognized keys.
     */
    public void importFromString(String data) {
        if (data == null || data.isEmpty()) return;
        String[] pairs = data.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String value = kv[1].trim();
            try {
                switch (key) {
                    case "bgColor":
                        setBgColor((int) Long.parseLong(value.replace("0x", ""), 16));
                        break;
                    case "textColor":
                        setTextColor((int) Long.parseLong(value.replace("0x", ""), 16));
                        break;
                    case "opacity":
                        setOpacity(Integer.parseInt(value));
                        break;
                    case "accentColor":
                        setAccentColor((int) Long.parseLong(value.replace("0x", ""), 16));
                        break;
                    case "borderWidth":
                        setBorderWidth(Integer.parseInt(value));
                        break;
                    case "overlayPulse":
                        setOverlayPulseEnabled(Boolean.parseBoolean(value));
                        break;
                    case "size":
                        setSize(Integer.parseInt(value));
                        break;
                    // Ignore unrecognized keys (forward compatibility)
                }
            } catch (Exception e) {
                // Skip malformed values
            }
        }
    }
}

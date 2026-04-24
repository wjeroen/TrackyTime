package com.timetracker.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverlayPreferences {

    private static final String PREFS = "overlay_prefs";
    private SharedPreferences sp;
    private SharedPreferences crashSp; // separate file — avoids triggering live-update listener

    public OverlayPreferences(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        crashSp = ctx.getSharedPreferences("crash_recovery", Context.MODE_PRIVATE);
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

    // Border opacity (0-255, default 153 ≈ 60%) — independent from background opacity
    public int getBorderOpacity() { return sp.getInt("border_opacity", 153); }
    public void setBorderOpacity(int o) { sp.edit().putInt("border_opacity", o).apply(); }

    // Overlay pulse (bg + border breathe in sync with timeline bar)
    public boolean isOverlayPulseEnabled() { return sp.getBoolean("overlay_pulse", true); }
    public void setOverlayPulseEnabled(boolean on) { sp.edit().putBoolean("overlay_pulse", on).apply(); }

    // Breathing transparency (-50 to +50, default +30)
    // Positive = more transparent at dim point, negative = more opaque
    public int getBreathingTransparency() { return sp.getInt("breathing_transparency", 30); }
    public void setBreathingTransparency(int v) { sp.edit().putInt("breathing_transparency", v).apply(); }

    // Breathing brightness (-50 to +50, default -25)
    // Positive = brighten, negative = darken
    public int getBreathingBrightness() { return sp.getInt("breathing_brightness", -25); }
    public void setBreathingBrightness(int v) { sp.edit().putInt("breathing_brightness", v).apply(); }

    // Breathing grayscale (0 to 100, default 0)
    // Controls how much the background desaturates toward grayscale during breathing dim point
    public int getBreathingGrayscale() { return sp.getInt("breathing_grayscale", 0); }
    public void setBreathingGrayscale(int v) { sp.edit().putInt("breathing_grayscale", v).apply(); }

    // Use task color as background (false = custom color, true = task's color)
    public boolean isUseTaskColorBg() { return sp.getBoolean("use_task_color_bg", false); }
    public void setUseTaskColorBg(boolean on) { sp.edit().putBoolean("use_task_color_bg", on).apply(); }

    // Task color background brightness adjustment (-100 to +100, default -30)
    // Applied on top of task's color when use_task_color_bg is true
    public int getTaskColorBrightness() { return sp.getInt("task_color_brightness", -30); }
    public void setTaskColorBrightness(int v) { sp.edit().putInt("task_color_brightness", v).apply(); }

    // Text stroke/outline (TV subtitle style with auto-contrast)
    public boolean isTextStrokeEnabled() { return sp.getBoolean("text_stroke", false); }
    public void setTextStrokeEnabled(boolean on) { sp.edit().putBoolean("text_stroke", on).apply(); }

    // Stroke width in pixels (1-10, default 4) — applies to text stroke AND icon stroke
    public int getStrokeWidth() { return sp.getInt("stroke_width", 4); }
    public void setStrokeWidth(int w) { sp.edit().putInt("stroke_width", w).apply(); }

    // UI elements opacity (50-255, default 0x99=153) — buttons, separator, hint text, paused timer
    public int getUiElementsOpacity() { return sp.getInt("ui_elements_opacity", 0x99); }
    public void setUiElementsOpacity(int o) { sp.edit().putInt("ui_elements_opacity", o).apply(); }

    // Immersive clock — display current time when phone is in immersive/fullscreen mode
    public boolean isImmersiveClockEnabled() { return sp.getBoolean("immersive_clock", false); }
    public void setImmersiveClockEnabled(boolean on) { sp.edit().putBoolean("immersive_clock", on).apply(); }

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

    // Signal to overlay that a task color changed in the DB (timestamp, triggers pref listener)
    public void notifyTaskColorChanged() { sp.edit().putLong("color_change_signal", System.currentTimeMillis()).apply(); }

    // Quick-select activity shortcuts (newline-delimited)
    public List<String> getQuickActivities() {
        String raw = sp.getString("quick_activities", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }
    public void setQuickActivities(List<String> activities) {
        sp.edit().putString("quick_activities", String.join("\n", activities)).apply();
    }

    // ---- Crash recovery (uses separate SharedPreferences file) ----

    /** Set a crash recovery checkpoint when an activity starts. */
    public void setCrashRecovery(String name, long startTime) {
        crashSp.edit()
            .putBoolean("active", true)
            .putString("name", name)
            .putLong("start_time", startTime)
            .putInt("elapsed_seconds", 0)
            .apply();
    }

    /** Update the heartbeat with current elapsed seconds (called every 5s). */
    public void updateCrashHeartbeat(int elapsedSeconds) {
        crashSp.edit().putInt("elapsed_seconds", elapsedSeconds).apply();
    }

    public boolean hasCrashRecovery() { return crashSp.getBoolean("active", false); }
    public String getCrashName() { return crashSp.getString("name", ""); }
    public long getCrashStartTime() { return crashSp.getLong("start_time", 0); }
    public int getCrashElapsedSeconds() { return crashSp.getInt("elapsed_seconds", 0); }

    /** Clear the checkpoint (called after successful save or intentional discard). */
    public void clearCrashRecovery() {
        crashSp.edit().clear().apply();
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
        sb.append("borderOpacity:").append(getBorderOpacity()).append(",");
        sb.append("overlayPulse:").append(isOverlayPulseEnabled()).append(",");
        sb.append("breathingTransparency:").append(getBreathingTransparency()).append(",");
        sb.append("breathingBrightness:").append(getBreathingBrightness()).append(",");
        sb.append("breathingGrayscale:").append(getBreathingGrayscale()).append(",");
        sb.append("useTaskColorBg:").append(isUseTaskColorBg()).append(",");
        sb.append("taskColorBrightness:").append(getTaskColorBrightness()).append(",");
        sb.append("textStroke:").append(isTextStrokeEnabled()).append(",");
        sb.append("strokeWidth:").append(getStrokeWidth()).append(",");
        sb.append("uiElementsOpacity:").append(getUiElementsOpacity()).append(",");
        sb.append("immersiveClock:").append(isImmersiveClockEnabled()).append(",");
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
                    case "borderOpacity":
                        setBorderOpacity(Integer.parseInt(value));
                        break;
                    case "overlayPulse":
                        setOverlayPulseEnabled(Boolean.parseBoolean(value));
                        break;
                    case "breathingTransparency":
                        setBreathingTransparency(Integer.parseInt(value));
                        break;
                    case "breathingBrightness":
                        setBreathingBrightness(Integer.parseInt(value));
                        break;
                    case "breathingGrayscale":
                        setBreathingGrayscale(Integer.parseInt(value));
                        break;
                    case "useTaskColorBg":
                        setUseTaskColorBg(Boolean.parseBoolean(value));
                        break;
                    case "taskColorBrightness":
                        setTaskColorBrightness(Integer.parseInt(value));
                        break;
                    case "textStroke":
                        setTextStrokeEnabled(Boolean.parseBoolean(value));
                        break;
                    case "strokeWidth":
                        setStrokeWidth(Integer.parseInt(value));
                        break;
                    case "uiElementsOpacity":
                        setUiElementsOpacity(Integer.parseInt(value));
                        break;
                    case "immersiveClock":
                        setImmersiveClockEnabled(Boolean.parseBoolean(value));
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

package com.timetracker.overlay;

public class ActivityEntry {
    private long id;
    private String name;
    private int durationSeconds;
    private int color;
    private long startTime;
    private String date; // YYYY-MM-DD

    public ActivityEntry() {}

    public ActivityEntry(String name, int durationSeconds, long startTime, String date) {
        this.name = name;
        this.durationSeconds = durationSeconds;
        this.startTime = startTime;
        this.date = date;
        this.color = 0xFF607D8B; // default grey-blue
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int d) { this.durationSeconds = d; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long t) { this.startTime = t; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    /** Normalize for comparison: trim, collapse spaces, lowercase. "Coding  Time" = "coding time". */
    public static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.US);
    }

    /** Compact duration used across the history UI: "1h 5m 30s", or "5m 30s" under an hour. */
    public static String formatDuration(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%dh %dm %02ds", h, m, s);
        return String.format(java.util.Locale.US, "%dm %02ds", m, s);
    }

    public String getFormattedDuration() {
        return formatDuration(durationSeconds);
    }
}

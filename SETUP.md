# Time Tracker Overlay - AIDE Setup Guide

## Quick Setup in AIDE

1. **Create new project** in AIDE:
   - Open AIDE → Menu → New Project → "Android App"
   - Set package name: `com.timetracker.overlay`
   - Set app name: `Time Tracker`
   - Set minimum SDK: 26 (Android 8.0)
   - Target SDK: 33 or higher

2. **Replace files** — AIDE creates a default project structure.
   Replace each file with the ones from this archive:
   
   ```
   AndroidManifest.xml          → replace root AndroidManifest.xml
   res/layout/activity_main.xml → replace (delete default layout)
   res/layout/overlay_layout.xml         → create new
   res/layout/item_activity_entry.xml    → create new
   res/values/colors.xml        → replace
   res/values/strings.xml       → replace
   res/values/styles.xml        → replace
   src/.../ActivityEntry.java   → create in src/com/timetracker/overlay/
   src/.../DatabaseHelper.java  → create in same folder
   src/.../MainActivity.java    → replace default
   src/.../OverlayPreferences.java → create
   src/.../OverlayService.java  → create
   src/.../PieChartView.java    → create
   ```

3. **Build & Run** — Press the Play button in AIDE. It compiles and installs.

4. **Grant permissions** when prompted:
   - "Display over other apps" — tap the toggle ON
   - Notification permission — allow

## How It Works

- **Overlay**: Floating widget with timer + text field + play/pause
- **Edit text** → tap the activity name field. Timer resets & starts for the new activity.
  Previous activity is saved to history automatically.
- **Reset button** → visible only while editing. Same as changing the activity (saves current, restarts timer).
- **Drag** → drag the ⠿ handle area to reposition the overlay
- **Tap drag area** → dismisses keyboard/exits edit mode
- **App** → shows daily pie chart, history with color assignment, date navigation
- **Settings** → customize overlay background, text, accent colors, opacity, size

## File Summary

| File | Purpose |
|------|---------|
| AndroidManifest.xml | Permissions + component declarations |
| OverlayService.java | Foreground service with floating window, timer, drag, focus |
| MainActivity.java | History view, pie chart, date nav, color picker, settings |
| DatabaseHelper.java | SQLite storage for activity entries |
| ActivityEntry.java | Data model |
| PieChartView.java | Custom canvas-drawn pie chart |
| OverlayPreferences.java | SharedPreferences for overlay appearance |

## Notes

- Requires Android 8.0+ (API 26) for overlay + foreground service
- No external dependencies — pure Android SDK
- Data persists in SQLite across app restarts
- Overlay runs as foreground service (won't be killed by OS)

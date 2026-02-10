# TrackyTime

## How It Works

- **Overlay**: Floating widget with timer + text field + play/pause
- **Edit text** → tap the activity name field. Timer resets & starts for the new activity.
  Previous activity is saved to history automatically.
- **Reset button** → visible only while editing. Same as changing the activity (saves current, restarts timer).
- **Drag** → drag the ⠿ handle area to reposition the overlay
- **Tap drag area** → dismisses keyboard/exits edit mode
- **App** → shows daily pie chart, history with color assignment, date navigation
- **Settings** → customize overlay background, text, accent colors, opacity, size

## Quick Reference File Structure

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

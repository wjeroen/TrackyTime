# TrackyTime

## How It Works

### Overlay (floating pill)
- **Compact pill shape**: activity name on the left, timer on the right
- **Tap activity text** → enter edit mode (keyboard pops up, reset + close buttons appear). Type a new name and press Done — the previous activity is saved and timer restarts.
- **Tap timer** → pause/resume (timer dims when paused)
- **Drag anywhere** → reposition the pill on screen (clamped to screen bounds)
- **Tap background** → dismiss keyboard / exit edit mode
- **↺ reset** → saves current session, restarts timer with same activity name (only visible in edit mode)
- **× close** → stops the overlay service (only visible in edit mode)
- **Progress bar** → thin accent-colored line at the bottom; starts pulsing after 30 minutes, speeds up 1.5x every 30 minutes as a gentle nudge

### Timer
- **Drift-proof**: uses `virtualStartTimestamp` pattern (elapsed = now − virtualStart) instead of incrementing a counter
- **Compact format**: shows `MM:SS` under 1 hour, `H:MM:SS` at 1 hour+

### App
- Daily pie chart with color-coded slices (entries grouped by name)
- **Week view**: toggle between Day/Week; week view aggregates Mon-Sun
- History list with color dots, total duration per activity, color picker, delete
- Date navigation (prev/next day or week)
- Settings: background color, text color, accent color, background opacity, text size
- **Export**: save all data as JSON to any location (Google Drive, email, etc.)
- **Import**: restore data from a JSON backup (skips duplicates)

### Consistent colors
- Activities with the **same name always get the same color** — in pie chart, history, and across all days
- New names get a stable color auto-assigned from a palette (based on name hash)
- Changing an entry's color updates **all** entries with that name

### Grouping
- Entries with the same name are **grouped together** in both the pie chart and history
- Durations are summed — e.g., three "Coding" sessions of 30min each show as one "Coding" entry at 1h 30m
- Delete removes all sessions for that activity (with confirmation)

### Opacity
- The opacity slider in settings controls **only the background** of the overlay pill
- Text, timer, and accent elements are always fully visible (100% alpha)

## Quick Reference File Structure

| File | Purpose |
|------|---------|
| `settings.gradle` | Gradle project config — declares the `app` module and plugin repositories |
| `gradlew` / `gradlew.bat` | Gradle wrapper scripts — runs the correct Gradle version automatically |
| `gradle/wrapper/gradle-wrapper.properties` | Pins the Gradle version (currently 8.14.3) |
| `app/build.gradle` | Android build config — SDK versions, package name, build types |
| `app/proguard-rules.pro` | ProGuard rules for release builds (currently empty) |
| `app/src/main/AndroidManifest.xml` | Permissions + component declarations |
| `app/src/main/java/.../OverlayService.java` | Foreground service: floating pill overlay, drift-proof timer, drag, tap-to-pause, edit mode, progressive pulse |
| `app/src/main/java/.../MainActivity.java` | History view, pie chart, day/week toggle, date nav, color picker, entry grouping, export/import, settings |
| `app/src/main/java/.../DatabaseHelper.java` | SQLite storage + `getColorForName()` / `updateColorByName()` / date range queries / export/import |
| `app/src/main/java/.../ActivityEntry.java` | Data model |
| `app/src/main/java/.../PieChartView.java` | Custom canvas-drawn pie chart |
| `app/src/main/java/.../OverlayPreferences.java` | SharedPreferences for overlay appearance |
| `.github/workflows/android.yml` | GitHub Actions workflow — builds APK on every push |

> **Note:** Java files live under `app/src/main/java/com/timetracker/overlay/`. The `...` above abbreviates that path.

## Project Structure

```
TrackyTime/
├── .github/workflows/
│   └── android.yml              ← CI: builds APK on push
├── gradle/wrapper/
│   ├── gradle-wrapper.jar       ← Wrapper bootstrap (auto-downloads Gradle)
│   └── gradle-wrapper.properties ← Pins Gradle version
├── gradlew                      ← Gradle wrapper script (Linux/Mac)
├── gradlew.bat                  ← Gradle wrapper script (Windows)
├── app/
│   ├── build.gradle             ← SDK versions, package name
│   ├── proguard-rules.pro       ← ProGuard rules (empty for now)
│   └── src/main/
│       ├── AndroidManifest.xml  ← Permissions & components
│       ├── java/com/timetracker/overlay/
│       │   ├── ActivityEntry.java
│       │   ├── DatabaseHelper.java
│       │   ├── MainActivity.java
│       │   ├── OverlayPreferences.java
│       │   ├── OverlayService.java
│       │   └── PieChartView.java
│       └── res/
│           ├── layout/          ← XML layouts
│           └── values/          ← Strings, colors, styles
├── settings.gradle              ← Gradle project settings
├── CLAUDE.md                    ← Instructions for Claude
└── README.md                    ← This file
```

## Building

The project uses **Gradle** with the Android Gradle Plugin. There are two ways to build:

### GitHub Actions (automatic)
Every push triggers a build. The APK is uploaded as a downloadable artifact on the Actions tab.

### Locally (manual)
You need JDK 17. The Gradle wrapper handles the rest — no separate Gradle install needed:
```bash
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- Requires Android 8.0+ (API 26) for overlay + foreground service
- No external dependencies — pure Android SDK
- Data persists in SQLite across app restarts
- Overlay runs as foreground service (won't be killed by OS)
- compileSdk: 34 (Android 14) — needed for `specialUse` foreground service type
- targetSdk: 33 (Android 13)
- minSdk: 26 (Android 8.0)
- Package: `com.timetracker.overlay`

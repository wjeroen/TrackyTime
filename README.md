# TrackyTime

## How It Works

### Overlay (floating pill)
- **Compact pill shape**: activity name on the left, timer on the right, 10dp rounded corners
- **Border**: configurable color (uses accent/border color setting) and width (0–6dp, default 3dp black). Border alpha scales with background opacity.
- **Tap activity text** → enter edit mode (keyboard pops up, ↗ open-app + ✕ close buttons appear). Type a new name and press Done — the previous activity is saved and timer restarts.
- **Tap timer** → pause/resume (timer dims when paused)
- **Drag anywhere** → reposition the pill on screen (clamped to screen bounds)
- **Tap outside overlay** → saves any typed text and exits edit mode automatically
- **↗ open app** → opens the full TrackyTime app (only visible in edit mode)
- **✕ close** → stops the overlay service (only visible in edit mode)
- **Timeline bar** → thin colored bar at the bottom showing the day's activity history as proportional segments. Each activity session is a colored rectangle. The currently-running activity grows live. The live segment pulses immediately, speeding up 1.5x every 30 minutes as a gentle nudge. Not affected by the opacity slider.
- **Breathing overlay** → optional (default on): the entire background + border pulse in sync with the timeline bar, making activity tracking more noticeable. Configurable in settings.
- **Live-update**: changing any setting (colors, size, border, opacity) updates the overlay instantly — no restart needed.

### Timer
- **Drift-proof**: uses `virtualStartTimestamp` pattern (elapsed = now − virtualStart) instead of incrementing a counter
- **Compact format**: shows `MM:SS` under 1 hour, `H:MM:SS` at 1 hour+

### App
- Daily pie chart with color-coded slices (entries grouped by name)
- **Week view**: toggle between Day/Week; week view aggregates Mon-Sun
- History list shows **individual entries** with time range (e.g. "10:00 – 11:00 · 1h 00m"), color dot, color picker, delete
- **Inline rename**: tap an entry's name → it becomes editable. Press Done → saves new name and auto-assigns a matching color.
- Date navigation (prev/next day or week)
- Export/Import/Settings buttons at the top (above history) for quick access
- Settings: background color, text color, border color (accent), border width (0–6dp), background opacity, text size, breathing overlay toggle
- **Export**: save all data as JSON to any location (Google Drive, email, etc.)
- **Import**: restore data from a JSON backup (skips duplicates)

### Consistent colors
- Activities with the **same name always get the same color** — in pie chart, history, and across all days
- New names get a stable color auto-assigned from a 44-color palette (based on name hash)
- Changing an entry's color updates **all** entries with that name

### Grouping
- The **pie chart** groups entries by name: three "Coding" sessions of 30min each show as one 1h 30m slice
- The **history list** shows individual entries (not grouped) so you can rename or delete specific sessions
- Delete removes the individual entry (not all entries with the same name)

### Opacity
- The opacity slider in settings controls **only the background** of the overlay pill
- Text, timer, and timeline bar are always fully visible (100% alpha)

## Quick Reference File Structure

| File | Purpose |
|------|---------|
| `settings.gradle` | Gradle project config — declares the `app` module and plugin repositories |
| `gradlew` / `gradlew.bat` | Gradle wrapper scripts — runs the correct Gradle version automatically |
| `gradle/wrapper/gradle-wrapper.properties` | Pins the Gradle version (currently 8.14.3) |
| `app/build.gradle` | Android build config — SDK versions, package name, build types |
| `app/proguard-rules.pro` | ProGuard rules for release builds (currently empty) |
| `app/src/main/AndroidManifest.xml` | Permissions + component declarations |
| `app/src/main/java/.../OverlayService.java` | Foreground service: floating pill overlay, drift-proof timer, drag, tap-to-pause, edit mode, open-app button, timeline bar, progressive pulse, breathing overlay, border, live-update settings |
| `app/src/main/java/.../TimelineBarView.java` | Custom View: draws day timeline as proportional colored segments on a Canvas |
| `app/src/main/java/.../MainActivity.java` | History view (individual entries, inline rename), pie chart, day/week toggle, date nav, color picker, export/import, settings (border color + width) |
| `app/src/main/java/.../DatabaseHelper.java` | SQLite storage + `getColorForName()` / `updateColorByName()` / `updateEntryNameAndColor()` / date range queries / export/import |
| `app/src/main/java/.../ActivityEntry.java` | Data model |
| `app/src/main/java/.../PieChartView.java` | Custom canvas-drawn pie chart |
| `app/src/main/java/.../OverlayPreferences.java` | SharedPreferences for overlay appearance (bg/text/border colors, border width, opacity, size, overlay pulse toggle) |
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
│       │   ├── PieChartView.java
│       │   └── TimelineBarView.java
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

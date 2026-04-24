# TrackyTime

## How It Works

### Overlay (floating pill)
- **Compact pill shape**: activity name on the left, timer on the right, 10dp rounded corners
- **Border**: configurable color (uses accent/border color setting), width (0–6dp, default 2dp black), and opacity (separate from background opacity, default ~60%). Uses LayerDrawable: bottom layer = bg fill (inset by border width), top layer = stroke-only drawable (transparent fill + stroke). This avoids both the filled-rectangle-behind-bg bug and the stroke/fill overlap bug. Padding offsets content so the border doesn't overlap it.
- **Tap activity text** → expand overlay (keyboard pops up, +/➚/− icon buttons appear, quick-select rows show). Icon buttons scale with overlay text size for consistent tap targets. Type a new name and press Done — the previous activity is saved, timer restarts, and overlay collapses.
- **Tap timer** → pause/resume (timer dims to icon opacity 0x99 when paused)
- **Drag anywhere** → reposition the pill on screen (clamped to screen bounds)
- **Tap outside overlay** → releases keyboard focus (overlay stays expanded, phone becomes usable for typing elsewhere)
- **+ add shortcut** → adds a quick-select row below the timeline. Type an activity name, then tap ▶ to instantly switch to it (overlay stays expanded, focus released). Tap X icon on a row to remove it. Shortcuts persist across sessions.
- **➚ open app** → opens the full TrackyTime app (releases focus, overlay stays expanded)
- **− collapse** → collapses the expanded overlay back to the compact pill
- **Timeline bar** → 6dp colored bar at the bottom showing the day's activity history as proportional segments. Each activity session is a colored rectangle. The currently-running activity grows live. The live segment pulses immediately, speeding up 2x every 30 minutes as a gentle nudge. Not affected by the opacity slider. White tick marks (2px wide) at every hour (full height) and half-hour (bottom half). Both fully opaque. Half-hour marks are hidden once total tracked time exceeds 5 hours.
- **Background color mode** → choose between a **custom color** (the color picker) or **task color** (automatically uses the current task's assigned color). When task color mode is selected, a **brightness slider** (-50% to +50%, default -30%) adjusts the task color. The background color changes instantly whenever you switch tasks.
- **Breathing overlay** → optional (default on): the border and background pulse in sync with the timeline bar. When enabled, three sub-sliders appear:
  - **Transparency** (-50% to +50%, default +30%): controls how much the **background** opacity oscillates during breathing. Positive = more transparent at dim point, negative = more opaque. Effect is subtle (30% scaling). The **border** always breathes independently (fades from transparent → its set opacity), unaffected by this slider.
  - **Brightness** (-50% to +50%, default -25%): controls color shift direction and amount. Negative = darkens toward black, positive = brightens toward white. Slide to the opposite direction to reverse the effect — no auto-detect needed.
  - **Grayscale** (0% to 100%, default 0%): controls how much the background desaturates toward grayscale at the breathing dim point. At 100%, the background goes fully grayscale during the dim phase. Uses ITU BT.601 luminance for natural-looking desaturation.
  Values clamp silently at physical limits (can't go below 0% or above 100% opacity). Works regardless of border width (even 0). Live-updates when toggled/adjusted in settings. All pulse animations run at 30fps to reduce battery/compositor load.
- **Immersive clock** → optional (default off): when enabled, a small desaturated clock pill appears in the top-right corner whenever the phone enters immersive/fullscreen mode (e.g. gaming, video playback). The clock uses the same text size and stroke settings as the main overlay, but fully desaturated (grayscale) and borderless. Background uses the overlay's bg color (or task color if in that mode) desaturated with the same opacity. Updates every minute. Uses Android's WindowInsets API to detect when the status bar is hidden. The detection view is invisible and non-touchable.
- **Minimum activity duration** → activities shorter than 10 seconds are automatically discarded (not saved). Prevents accidental micro-entries when switching activities quickly.
- **Live-update**: changing any setting (colors, size, border, opacity) updates the overlay instantly — no restart needed. Includes quick-select row text/icon colors and sizes. Changing a task's color in the app immediately updates the timeline bar colors on the overlay.

### Timer
- **Drift-proof**: uses `virtualStartTimestamp` pattern (elapsed = now − virtualStart) instead of incrementing a counter
- **Compact format**: shows `MM:SS` under 1 hour, `H:MM:SS` at 1 hour+
- **Crash recovery**: a heartbeat writes the running activity's name, start time, and elapsed seconds to a separate SharedPreferences file (`crash_recovery`) every 5 seconds. If the app crashes, is force-stopped, or the phone reboots without calling `onDestroy()`, the next service startup detects the dangling checkpoint and saves the activity to the database with the duration up to the last heartbeat. Max data loss: 5 seconds. Activities under 10 seconds are still discarded. The checkpoint is cleared on normal save or activity switch.

### App
- Daily pie chart with color-coded slices (entries grouped by name)
- **Color bar** below pie chart: horizontal stacked bar grouping all activity time by color. Full screen width (within the 16dp page margins). Activities with the same color are lumped into one segment. Percentage labels appear on segments wide enough to fit them. Sorted by hue (similar colors grouped together), then by duration descending within each hue group.
- **Week view**: toggle between Day/Week; week view aggregates Mon-Sun
- History list shows **individual entries** with time range (e.g. "10:00 – 11:00 · 1h 00m"), color dot, color picker, delete
- **Inline rename**: tap an entry's name → it becomes editable. Press Done → saves new name and auto-assigns a matching color.
- **Inline duration edit**: tap an entry's duration/time text → dialog appears with hours/minutes/seconds fields. Save updates the duration (start time stays the same, end time recalculates).
- **Live activity indicator**: when the overlay is recording, a "● REC" entry appears at the top of today's history list showing the current activity name, start time, and elapsed duration in green.
- **Tap date to return to today**: tapping the date/week text in the header jumps back to the present day or current week.
- Date navigation (prev/next day or week)
- Export/Import/Settings buttons at the top (above history) for quick access
- Settings: background color mode (custom/task color + brightness slider), text color, border color (accent), border width (0–6dp) + border opacity, background opacity, text size, breathing overlay toggle + transparency/brightness/grayscale sliders, text stroke toggle + stroke width slider (1–10, linear scaling, proportional to text size so stroke scales with overlay size like icon stroke does; anchored at 16sp Medium where setting 4 = original default), UI elements opacity (buttons, separator, hints, paused clock), immersive clock toggle
- **Export**: save all data as JSON to any location (Google Drive, email, etc.). Also includes quick-select shortcut names.
- **Import**: restore data from a JSON backup (skips duplicates). Restores quick-select shortcuts if present. Backward-compatible with older exports that don't have shortcuts.

### Consistent colors
- Activities with the **same name always get the same color** — in pie chart, history, and across all days
- **Name matching is case- and space-insensitive**: "Coding Time", "coding time", and "CODING  TIME" are all treated as the same activity (via `normalizeName()` — trim, collapse spaces, lowercase)
- New names get a stable color auto-assigned from a 76-color palette (19 Material Design hues × 4 brightness levels, based on normalized name hash)
- Changing an entry's color updates **all** entries with that name (case/space-insensitive)

### Grouping
- The **pie chart** groups entries by normalized name: "Coding", "coding", and "CODING" all combine into one slice
- The **history list** shows individual entries (not grouped) so you can rename or delete specific sessions
- Delete removes the individual entry (not all entries with the same name)
- Import deduplication is also case/space-insensitive

### Opacity
- The **background opacity** slider sets the opacity of the overlay background (default ~60%)
- The **border opacity** slider (shown when border width > 0) sets the border's opacity independently from the background
- With breathing enabled: transparency and brightness sliders control how much the opacity and color shift during the pulse cycle
- Without breathing: everything stays at the set opacity
- Text (activity name) and timeline bar are always fully visible (100% alpha)
- The **UI elements opacity** slider controls the transparency of secondary UI elements: close/add/open-app buttons, separator dot, hint text, play buttons in quick-select rows, and the timer when paused (default ~60%, range 10–100%)

## Quick Reference File Structure

| File | Purpose |
|------|---------|
| `settings.gradle` | Gradle project config — declares the `app` module and plugin repositories |
| `gradlew` / `gradlew.bat` | Gradle wrapper scripts — runs the correct Gradle version automatically |
| `gradle/wrapper/gradle-wrapper.properties` | Pins the Gradle version (currently 8.14.3) |
| `app/build.gradle` | Android build config — SDK versions, package name, build types |
| `app/proguard-rules.pro` | ProGuard rules for release builds (currently empty) |
| `app/src/main/AndroidManifest.xml` | Permissions + component declarations |
| `app/src/main/java/.../OverlayService.java` | Foreground service: floating pill overlay, drift-proof timer, drag, tap-to-pause, expand/collapse/focus model, quick-select shortcuts with icon buttons, timeline bar, progressive pulse, breathing overlay (stroke-only border layer + bg darken/brighten), immersive clock (WindowInsets detection), live-update settings |
| `app/src/main/java/.../TimelineBarView.java` | Custom View: draws day timeline as proportional colored segments on a Canvas |
| `app/src/main/java/.../MainActivity.java` | History view (individual entries, inline rename), pie chart, color bar, day/week toggle, date nav, color picker, export/import (with shortcuts), settings (border color + width) |
| `app/src/main/java/.../ColorBarView.java` | Custom canvas-drawn horizontal stacked bar chart — groups activity time by color, sorted by hue then duration |
| `app/src/main/res/drawable/` | Vector drawable icons (add, open, remove, close) used in overlay buttons |
| `app/src/main/java/.../DatabaseHelper.java` | SQLite storage + `getColorForName()` / `updateColorByName()` / `updateEntryNameAndColor()` / date range queries / export/import — all name matching is case/space-insensitive via `LOWER(TRIM())` |
| `app/src/main/java/.../ActivityEntry.java` | Data model + `normalizeName()` helper (trim, collapse spaces, lowercase) |
| `app/src/main/java/.../PieChartView.java` | Custom canvas-drawn pie chart |
| `app/src/main/java/.../StrokeTextView.java` | Custom TextView with TV subtitle-style text stroke/outline — auto-contrast via ITU BT.601 brightness (black stroke for light text, white for dark) |
| `app/src/main/java/.../StrokeEditText.java` | Custom EditText with same stroke/outline — uses Layout.draw() directly to bypass Editor's hardware-acceleration cache |
| `app/src/main/java/.../StrokeImageView.java` | Custom ImageView with same stroke outline — draws icon at 8 offset positions in contrasting color, then normally on top (same auto-contrast as StrokeTextView) |
| `app/src/main/java/.../OverlayPreferences.java` | SharedPreferences for overlay appearance (bg/text/border colors, border width, opacity, size, overlay pulse toggle + breathing transparency/brightness/grayscale, task color bg mode + brightness, text stroke toggle + stroke width, UI elements opacity, quick-select activities, color change signal) + crash recovery checkpoint (separate `crash_recovery` file) |
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
│       │   ├── ColorBarView.java
│       │   ├── DatabaseHelper.java
│       │   ├── MainActivity.java
│       │   ├── OverlayPreferences.java
│       │   ├── OverlayService.java
│       │   ├── PieChartView.java
│       │   ├── StrokeEditText.java
│       │   ├── StrokeImageView.java
│       │   ├── StrokeTextView.java
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

**⚠️ GitHub Secrets Required:**
For consistent signing (so updates install without uninstalling), you need to configure these secrets in your repo:

| Secret | Description |
|--------|-------------|
| `DEBUG_KEYSTORE_BASE64` | Base64-encoded debug.keystore file |
| `DEBUG_KEYSTORE_PASSWORD` | Keystore password (default: `android`) |
| `DEBUG_KEY_ALIAS` | Key alias (default: `androiddebugkey`) |
| `DEBUG_KEY_PASSWORD` | Key password (default: `android`) |

**APK naming:** The build automatically names the APK with a UTC timestamp:
`TrackyTime-debug-2026-02-12-143052.apk`

### Locally (manual)
You need JDK 17. The Gradle wrapper handles the rest — no separate Gradle install needed:
```bash
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/TrackyTime-debug-<timestamp>.apk`.

## Notes

- Requires Android 8.0+ (API 26) for overlay + foreground service
- No external dependencies — pure Android SDK
- Data persists in SQLite across app restarts
- Overlay runs as foreground service (won't be killed by OS)
- compileSdk: 34 (Android 14) — needed for `specialUse` foreground service type
- targetSdk: 33 (Android 13)
- minSdk: 26 (Android 8.0)
- Package: `com.timetracker.overlay`

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
| `settings.gradle` | Gradle project config — declares the `app` module and plugin repositories |
| `gradlew` / `gradlew.bat` | Gradle wrapper scripts — runs the correct Gradle version automatically |
| `gradle/wrapper/gradle-wrapper.properties` | Pins the Gradle version (currently 8.14.3) |
| `app/build.gradle` | Android build config — SDK versions, package name, build types |
| `app/proguard-rules.pro` | ProGuard rules for release builds (currently empty) |
| `app/src/main/AndroidManifest.xml` | Permissions + component declarations |
| `app/src/main/java/.../OverlayService.java` | Foreground service with floating window, timer, drag, focus |
| `app/src/main/java/.../MainActivity.java` | History view, pie chart, date nav, color picker, settings |
| `app/src/main/java/.../DatabaseHelper.java` | SQLite storage for activity entries |
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

# Building

Whether you can build depends on where this session is running:

- **Cloud/sandbox sessions (claude.ai web, containers):** DO NOT attempt Gradle builds (`./gradlew build`, `assembleDebug`, etc.). These environments have network restrictions that prevent Gradle from downloading dependencies/plugins. Builds always fail with network errors there, this is NOT a code problem. Skip build verification and rely on GitHub Actions, which builds an APK artifact on every push.
- **The owner's local Windows machine (repo at `C:\dev\TrackyTime`):** local builds WORK. JDK 17 is installed and the Android SDK is at `C:\Users\jeroe\AppData\Local\Android\Sdk` (wired up via the gitignored `local.properties`). Two quirks:
  1. Agent shells mangle the empty `-classpath ""` argument inside `gradlew.bat`, making it fail with "-classpath requires class path specification". Invoke the wrapper jar directly instead:
     `java "-Dorg.gradle.appname=gradlew" -jar gradle\wrapper\gradle-wrapper.jar assembleDebug --no-daemon`
  2. Set `$env:DEBUG_KEYSTORE_PATH = "C:\Users\jeroe\.android\debug.keystore"` before building, otherwise signing fails (the fallback path `app/debug.keystore` only exists inside CI). Heads-up: this local key is NOT the same key as the CI keystore secret, so a locally-built APK cannot install over a CI-built install (and vice versa) without a one-time uninstall. Export data from the app first if you ever switch.

GitHub Actions builds an APK on every push regardless of environment. Use those artifacts for installing on the phone when working from the cloud.

# Testing on the phone

`adb` is available locally (`C:\Users\jeroe\AppData\Local\Android\Sdk\platform-tools\adb.exe`). When the owner's phone is connected and USB debugging is authorized, you can install and drive the app directly. Ask the owner first if a device action could interrupt what they are doing on the phone.

# Codebase map

This project's codebase map is the **Quick Reference table in `README.md`**.

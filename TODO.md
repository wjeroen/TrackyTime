# TrackyTime — TODO

## Current Sprint

### High Priority
- [ ] Verify GitHub Actions build succeeds after overlay UI redesign push

### Features to Implement
_(none right now)_

### Bug Fixes
_(none right now)_

### Performance & Optimization
_(none right now)_

### Documentation
_(none right now)_

### Testing
- [ ] Test overlay pill: drag, tap-to-edit, tap-timer-to-pause all work
- [ ] Test that opacity slider only affects background (text stays fully visible)
- [ ] Test consistent colors: create "Coding" twice on different days, verify same color
- [ ] Test color picker: changing color for one entry updates all entries with same name
- [ ] Test APK installs and runs correctly from GitHub Actions artifact

## Completed Recently
- [x] Redesign overlay as compact pill (activity left, timer right) inspired by FloatingCountdownTimer (2026-02-10)
- [x] Add drift-proof timer using virtualStartTimestamp pattern (2026-02-10)
- [x] Tap timer to pause/resume — removed separate play/pause button (2026-02-10)
- [x] Opacity now only affects background — text/timer always fully visible (2026-02-10)
- [x] Add breathing pulse animation on progress bar while timer runs (2026-02-10)
- [x] Same activity name = same color (auto-assigned from palette hash, persists across sessions) (2026-02-10)
- [x] Color picker now updates ALL entries with same name (2026-02-10)
- [x] Rename app to TrackyTime (2026-02-10)
- [x] Screen bounds clamping for overlay drag (2026-02-10)
- [x] Compact timer format: MM:SS under 1h, H:MM:SS at 1h+ (2026-02-10)
- [x] Bump compileSdk 33→34 to fix `specialUse` foregroundServiceType build error (2026-02-10)
- [x] Add Gradle wrapper (gradlew) pinned to Gradle 8.14.3 — fixes CI build failure with Gradle 9.x (2026-02-10)
- [x] Set up Gradle build files (settings.gradle, app/build.gradle) (2026-02-10)
- [x] Reorganize repo to standard Android/Gradle directory structure (2026-02-10)
- [x] Add GitHub Actions workflow to auto-build APK on push (2026-02-10)
- [x] Update README.md with new project structure and build instructions (2026-02-10)

## Future Ideas
- [ ] Add release build signing for installable release APKs
- [ ] Set up version bumping automation

## Reference
- [README.md](README.md) — Project overview, file structure, build instructions
- [CLAUDE.md](CLAUDE.md) — Instructions for Claude

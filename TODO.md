# TrackyTime — TODO

## Current Sprint

### High Priority
- [ ] Verify GitHub Actions build succeeds after latest push

### Features to Implement
- [ ] Add separate opacity slider for the timeline bar
- [ ] Live-update overlay when color/settings change (no restart needed)

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
- [ ] Test reset button (↺) appears only in edit mode, saves + restarts timer
- [ ] Test close button (×) appears only in edit mode, stops overlay service
- [ ] Test timeline bar: shows day's activity history as colored segments, live segment grows
- [ ] Test progressive pulse: starts immediately, speeds up 1.5x every 30min, only pulses live segment
- [ ] Test entry grouping: multiple sessions with same name show as one grouped entry
- [ ] Test week view: toggle Day/Week, verify Mon–Sun aggregation
- [ ] Test export: creates valid JSON file via share sheet
- [ ] Test import: restores data from JSON backup, skips duplicates
- [ ] Test APK installs and runs correctly from GitHub Actions artifact

## Completed Recently
- [x] Fix overlay sizing bug — root changed from FrameLayout to LinearLayout, bg applied via setBackground() (2026-02-10)
- [x] Fix keyboard reliability — per-child touch listeners instead of single root listener (2026-02-10)
- [x] Add reset (↺) and close (×) buttons — only visible during edit mode (2026-02-10)
- [x] Timeline bar: day history as colored segments, live segment grows in real-time (2026-02-10)
- [x] Progressive pulse: starts immediately on live segment, 1.5x faster every 30min (2026-02-10)
- [x] Group same-name entries in pie chart and history (durations summed) (2026-02-10)
- [x] Add Day/Week view toggle (week = Mon–Sun aggregation) (2026-02-10)
- [x] Add export/import via Android SAF (JSON format with duplicate detection) (2026-02-10)
- [x] Redesign overlay as compact pill (activity left, timer right) inspired by FloatingCountdownTimer (2026-02-10)
- [x] Add drift-proof timer using virtualStartTimestamp pattern (2026-02-10)
- [x] Tap timer to pause/resume — removed separate play/pause button (2026-02-10)
- [x] Opacity now only affects background — text/timer always fully visible (2026-02-10)
- [x] Same activity name = same color (auto-assigned from palette hash, persists across sessions) (2026-02-10)
- [x] Color picker now updates ALL entries with same name (2026-02-10)
- [x] Rename app to TrackyTime (2026-02-10)
- [x] Screen bounds clamping for overlay drag (2026-02-10)
- [x] Compact timer format: MM:SS under 1h, H:MM:SS at 1h+ (2026-02-10)
- [x] Bump compileSdk 33→34 to fix `specialUse` foregroundServiceType build error (2026-02-10)
- [x] Add Gradle wrapper (gradlew) pinned to Gradle 8.14.3 (2026-02-10)
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

# TrackyTime — TODO

## Current Sprint

### High Priority
- [ ] Verify GitHub Actions build succeeds after latest push

### Features to Implement
- [ ] Add separate opacity slider for the timeline bar
- [ ] Expand background/border color picker (more color options like activity picker)

### Bug Fixes
_(none right now)_

### Performance & Optimization
_(none right now)_

### Documentation
_(none right now)_

### Testing
- [ ] Test overlay pill: drag, tap-to-edit, tap-timer-to-pause all work
- [ ] Test that opacity slider affects background + border (text stays fully visible)
- [ ] Test consistent colors: create "Coding" twice on different days, verify same color
- [ ] Test color picker: 44 colors (vivid, warm, pastel, deep), changing color updates all entries with same name
- [ ] Test open-app button (➚) appears in expanded mode, opens full app (keeps overlay expanded)
- [ ] Test collapse button (−) appears in expanded mode, collapses overlay
- [ ] Test quick-select: + adds row, ▶ switches activity, ✕ removes row, persists across sessions
- [ ] Test timeline bar: colored segments, live segment grows, tick marks at hour (full) and half-hour (half, hidden >5h)
- [ ] Test progressive pulse: starts immediately, speeds up 1.5x every 30min, pulses live segment + border
- [ ] Test breathing border toggle: on = border pulses 0%→opacity, off = only timeline bar pulses
- [ ] Test border: fully outside bg (LayerDrawable, no overlap), width adjustable 0–6dp (default 2dp)
- [ ] Test expand/focus split: tap outside → releases focus but keeps expanded; − collapses; Enter on quick-select → releases focus, stays expanded
- [ ] Test live-update: changing settings in app immediately updates the overlay (no restart)
- [ ] Test inline rename: tap entry name in history → edit → press Done → name + color update
- [ ] Test individual history entries: each session shown separately with time range ("10:00 – 11:00 · 1h 00m")
- [ ] Test delete removes individual entry (not all entries with same name)
- [ ] Test pie chart still groups entries by name
- [ ] Test week view: toggle Day/Week, verify Mon–Sun aggregation
- [ ] Test export: creates valid JSON file via share sheet (includes quick-select shortcuts)
- [ ] Test import: restores data from JSON backup, skips duplicates, restores shortcuts if present
- [ ] Test color bar: shows below pie chart, groups time by color, percentages on wide segments
- [ ] Test live-update quick-select rows: changing text color/size in settings updates existing rows
- [ ] Test APK installs and runs correctly from GitHub Actions artifact

## Completed Recently
- [x] Fix quick-select row colors not live-updating when settings change (2026-02-11)
- [x] Add color bar chart below pie chart — groups time by color, shows percentages (2026-02-11)
- [x] Reverse breathing bg pulse: darkest when border is gone, normal when border visible (2026-02-11)
- [x] Export/import quick-select shortcuts (backward-compatible with older backups) (2026-02-11)
- [x] Fix border: stroke-only layer + inset bg fill; breathing darkens/brightens bg (2026-02-11)
- [x] Expand/focus split: expanded UI stays visible independently of keyboard focus (2026-02-11)
- [x] Replace ✕ close with − collapse, remove stop-service from overlay (2026-02-11)
- [x] Border on outside: LayerDrawable (outer border fill + inner bg fill), no stroke overlap (2026-02-11)
- [x] Fix quick-select ▶ not updating displayed activity text (editText.setText) (2026-02-11)
- [x] Default border width 2dp, pulse floor 0% (fully transparent → user opacity) (2026-02-11)
- [x] Tap outside overlay → release focus only (keep expanded, phone usable) (2026-02-11)
- [x] Quick-select activity shortcuts: + button adds rows with ▶ play and ✕ remove, persists in prefs (2026-02-10)
- [x] Border-only pulse: background stays static, only border breathes (2026-02-10)
- [x] Revert tap-outside-exits-edit-mode (removed FLAG_WATCH_OUTSIDE_TOUCH) (2026-02-10)
- [x] Fix pulse direction: breathes from invisible (0) up to user's opacity (ceiling), not the other way (2026-02-10)
- [x] Fix default opacity to 60% user-visible (internal 153), pulse floor at 20% (2026-02-10)
- [x] Tick marks: 2px wide, half-hour fully opaque (only height differentiates from full-hour) (2026-02-10)
- [x] Fix pulse: live-updates on toggle, respects opacity as ceiling (0→opacity) (2026-02-10)
- [x] Border grows outward (dynamic padding), default width 1dp (2026-02-10)
- [x] Open-app icon (➚) sized larger (+4sp) to match ✕ visually (2026-02-10)
- [x] Timeline bar tick marks: hour (full height) + half-hour (bottom half, hidden >5h), both 2px fully opaque (2026-02-10)
- [x] Remove reset button, add ➚ open-app button to overlay edit mode (2026-02-10)
- [x] Exit edit mode when tapping outside overlay (FLAG_WATCH_OUTSIDE_TOUCH + ACTION_OUTSIDE) (2026-02-10)
- [x] Breathing overlay: bg + border pulse in sync with timeline bar, optional setting (default on) (2026-02-10)
- [x] Expand activity color palette from 12 to 44 colors (vivid, warm, pastel, deep) with smaller swatches (2026-02-10)
- [x] Move export/import/settings above history list for quick access (2026-02-10)
- [x] Fix timeline bar color: refresh from DB every 30s + on segment reload (2026-02-10)
- [x] Fix button alignment: consistent gravity/includeFontPadding on ➚ and ✕ (2026-02-10)
- [x] Overlay visual redesign: white bg default, black text/border, 10dp corners, 5dp padding, unified text sizes (2026-02-10)
- [x] Add configurable border using accent color (default black, 3dp, adjustable 0–6dp) (2026-02-10)
- [x] Live-update overlay when settings change (SharedPreferences listener, 100ms debounce) (2026-02-10)
- [x] Reset button now saves + pauses (no longer auto-starts new timer) (2026-02-10)
- [x] History shows individual entries with time ranges + inline rename (tap name → edit → Done) (2026-02-10)
- [x] Delete removes individual entry by ID (not all entries with same name) (2026-02-10)
- [x] Settings: "Accent" → "Border Color", added border width slider, removed restart note (2026-02-10)
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
- [ ] Fix package conflicts on update (dedicated signing key setup — currently debug key mismatch between build environments)
- [ ] Set up version bumping automation

## Reference
- [README.md](README.md) — Project overview, file structure, build instructions
- [CLAUDE.md](CLAUDE.md) — Instructions for Claude

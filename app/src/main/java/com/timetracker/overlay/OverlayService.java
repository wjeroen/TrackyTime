package com.timetracker.overlay;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service {

    public static boolean isServiceRunning = false;

    // Live activity data (readable by MainActivity for showing current activity)
    public static String liveActivityName = "";
    public static long liveStartTime = 0;
    public static boolean liveIsRunning = false;
    public static int liveActivityColor = 0;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private TimelineBarView timelineBar;
    private StrokeTextView timerText, separator;
    private StrokeImageView openAppBtn, closeBtn, addBtn;
    private StrokeEditText editText;
    private LinearLayout quickSelectContainer;
    private GradientDrawable overlayBgFill;        // background fill (inset when border > 0)
    private GradientDrawable overlayBorderDrawable; // stroke-only border layer (null when border = 0)
    private boolean isExpanded = false;

    // Cached timeline segments (from DB, chronological order)
    private List<TimelineBarView.Segment> savedSegments = new ArrayList<>();
    private int currentActivityColor = 0;

    private Handler timerHandler;
    private boolean isRunning = false;

    // Drift-proof timer
    private long virtualStartTimestamp = -1;
    private long accumulatedMs = 0;

    private String currentActivityName = "";
    private long currentStartTime = 0;

    private DatabaseHelper dbHelper;

    private ValueAnimator pulseAnimator;
    private long currentPulseDuration = 0; // 0 = no pulse active

    // Cached values for overlay pulse (avoid reading prefs every animation frame)
    private boolean cachedOverlayPulseEnabled = true;
    private int cachedBgColor, cachedBgOpacity, cachedAccentColor, cachedBorderWidth;
    private float cachedDensity;

    // Throttle animation updates to 30fps (~33ms between frames)
    private static final long FRAME_INTERVAL_MS = 33;
    private long lastFrameTime = 0;

    // Crash recovery: heartbeat saves running activity state every 5s
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    // Live-update: listen for pref changes from the settings dialog
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private Runnable applyPrefsRunnable = () -> {
        applyPreferences();
        // Force pulse to pick up new values (toggle, opacity, colors)
        refreshPulseCache();
        currentPulseDuration = 0; // force restart on next tick
    };

    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "timetracker_channel";
    private static final long PULSE_INTERVAL_SECONDS = 30 * 60; // speed up every 30 min
    private static final long BASE_PULSE_MS = 3000; // 1500ms each way at start

    // Drag state
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        timerHandler = new Handler(Looper.getMainLooper());

        // Crash recovery heartbeat (saves running activity state every 5s)
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = () -> {
            if (!currentActivityName.isEmpty()) {
                new OverlayPreferences(this).updateCrashHeartbeat(getElapsedSeconds());
            }
            heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        };

        // Recover activity from previous crash (if any) — before setupOverlay
        // so the recovered entry appears in the timeline
        OverlayPreferences crashPrefs = new OverlayPreferences(this);
        if (crashPrefs.hasCrashRecovery()) {
            recoverCrashedActivity(crashPrefs);
        }

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        isServiceRunning = true;
        setupOverlay();

        // Live-update: re-apply prefs whenever settings change
        SharedPreferences sp = getSharedPreferences("overlay_prefs", MODE_PRIVATE);
        prefsListener = (sharedPreferences, key) -> {
            timerHandler.removeCallbacks(applyPrefsRunnable);
            timerHandler.postDelayed(applyPrefsRunnable, 100); // debounce
        };
        sp.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        timerText = overlayView.findViewById(R.id.timerText);
        separator = overlayView.findViewById(R.id.separator);
        editText = overlayView.findViewById(R.id.activityInput);
        addBtn = overlayView.findViewById(R.id.addBtn);
        openAppBtn = overlayView.findViewById(R.id.openAppBtn);
        closeBtn = overlayView.findViewById(R.id.closeBtn);
        timelineBar = overlayView.findViewById(R.id.timelineBar);
        quickSelectContainer = overlayView.findViewById(R.id.quickSelectContainer);

        float density = getResources().getDisplayMetrics().density;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (int) (16 * density);
        params.y = (int) (100 * density);

        applyPreferences();
        setupTouchHandlers();
        setupEditText();
        loadTodaySegments();

        windowManager.addView(overlayView, params);
    }

    // ---- Visual preferences (opacity applies to both background and border) ----

    private static final int BASE_PADDING_DP = 5;

    private void applyPreferences() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        float density = getResources().getDisplayMetrics().density;

        int bgColor = prefs.getBgColor();
        int bgOpacity = prefs.getOpacity();
        int bgWithAlpha = (bgOpacity << 24) | (bgColor & 0x00FFFFFF);

        int borderWidth = prefs.getBorderWidth();
        int borderWidthPx = (int) (borderWidth * density);
        int cornerRadiusPx = (int) (10 * density);

        // Background fill (no stroke on this drawable — avoids stroke/fill overlap)
        overlayBgFill = new GradientDrawable();
        overlayBgFill.setShape(GradientDrawable.RECTANGLE);
        overlayBgFill.setCornerRadius(cornerRadiusPx);
        overlayBgFill.setColor(bgWithAlpha);

        if (borderWidth > 0) {
            int accentColor = prefs.getAccentColor();
            int borderColor = (bgOpacity << 24) | (accentColor & 0x00FFFFFF);

            // Stroke-only layer: transparent fill, just the border line
            // Corner radius set so inner edge of stroke matches bg fill's radius
            overlayBorderDrawable = new GradientDrawable();
            overlayBorderDrawable.setShape(GradientDrawable.RECTANGLE);
            overlayBorderDrawable.setCornerRadius(cornerRadiusPx + borderWidthPx / 2f);
            overlayBorderDrawable.setColor(0x00000000);
            overlayBorderDrawable.setStroke(borderWidthPx, borderColor);

            LayerDrawable layered = new LayerDrawable(
                new GradientDrawable[]{ overlayBgFill, overlayBorderDrawable });
            // Inset bg fill by border width so it sits inside the stroke, no overlap
            layered.setLayerInset(0, borderWidthPx, borderWidthPx,
                borderWidthPx, borderWidthPx);
            overlayView.setBackground(layered);
        } else {
            overlayBorderDrawable = null;
            overlayView.setBackground(overlayBgFill);
        }

        // Border grows outward: add border width to padding so content area stays the same
        int basePad = (int) (BASE_PADDING_DP * density);
        int pad = basePad + borderWidthPx;
        overlayView.setPadding(pad, pad, pad, pad);

        // Text: always fully opaque
        int textColor = prefs.getTextColor();

        timerText.setTextColor(textColor);
        separator.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);
        editText.setTextColor(textColor);
        editText.setHintTextColor((textColor & 0x00FFFFFF) | 0x99000000);
        // Force EditText to redraw after color change (needed for stroke updates)
        editText.invalidate();

        // Icon button tints
        addBtn.setImageTintList(ColorStateList.valueOf((textColor & 0x00FFFFFF) | 0x99000000));
        openAppBtn.setImageTintList(ColorStateList.valueOf((textColor & 0x00FFFFFF) | 0x99000000));
        closeBtn.setImageTintList(ColorStateList.valueOf((textColor & 0x00FFFFFF) | 0x99000000));

        // Unified text size for text elements
        float textSize = prefs.getTextSize();

        // Main button icon sizes: baseline 24dp at large (20sp) = 1.2x multiplier
        // Perfect for large, scales down for small/medium, scales up for extra large
        int iconSize = (int) (textSize * 1.2f * density);
        LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        addBtnParams.setMargins((int) (6 * density), 0, 0, 0);
        addBtn.setLayoutParams(addBtnParams);

        LinearLayout.LayoutParams openAppBtnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        openAppBtnParams.setMargins((int) (8 * density), 0, 0, 0);
        openAppBtn.setLayoutParams(openAppBtnParams);

        LinearLayout.LayoutParams closeBtnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        closeBtnParams.setMargins((int) (6 * density), 0, 0, 0);
        closeBtn.setLayoutParams(closeBtnParams);
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);

        // Text stroke (TV subtitle style with auto-contrast)
        boolean strokeEnabled = prefs.isTextStrokeEnabled();
        timerText.setStrokeEnabled(strokeEnabled);
        editText.setStrokeEnabled(strokeEnabled);
        separator.setStrokeEnabled(strokeEnabled);
        addBtn.setStrokeEnabled(strokeEnabled);
        openAppBtn.setStrokeEnabled(strokeEnabled);
        closeBtn.setStrokeEnabled(strokeEnabled);

        // Timeline bar corner radius (not affected by opacity)
        timelineBar.setCornerRadius(2 * density);

        // Live-update quick-select row colors + sizes + stroke
        // Perfect for small, scales up for medium/large/extra large
        int quickSelectIconSize = (int) (textSize * 1.2f * density);
        for (int i = 0; i < quickSelectContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) quickSelectContainer.getChildAt(i);
            StrokeTextView playBtn = (StrokeTextView) row.getChildAt(0);
            StrokeEditText nameField = (StrokeEditText) row.getChildAt(1);
            StrokeImageView removeBtn = (StrokeImageView) row.getChildAt(2);
            playBtn.setTextColor((textColor & 0x00FFFFFF) | 0x99000000);
            playBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            playBtn.setStrokeEnabled(strokeEnabled);
            nameField.setTextColor(textColor);
            nameField.setHintTextColor((textColor & 0x00FFFFFF) | 0x99000000);
            // Force EditText to redraw after color change (needed for stroke updates)
            nameField.invalidate();
            nameField.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            nameField.setStrokeEnabled(strokeEnabled);
            removeBtn.setImageTintList(ColorStateList.valueOf((textColor & 0x00FFFFFF) | 0x99000000));
            removeBtn.setStrokeEnabled(strokeEnabled);
            // Update icon size
            LinearLayout.LayoutParams removeBtnParams = new LinearLayout.LayoutParams(
                quickSelectIconSize, quickSelectIconSize);
            removeBtnParams.setMargins((int) (6 * density), 0, 0, 0);
            removeBtn.setLayoutParams(removeBtnParams);
        }
    }

    // ---- Touch handling: each child handles drag + its own tap action ----

    private boolean handleDragTouch(MotionEvent event, Runnable onTap) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                isDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) (event.getRawX() - initialTouchX);
                int dy = (int) (event.getRawY() - initialTouchY);
                if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                    isDragging = true;
                }
                if (isDragging) {
                    params.x = initialX + dx;
                    params.y = initialY + dy;
                    clampToScreen();
                    windowManager.updateViewLayout(overlayView, params);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!isDragging && onTap != null) {
                    onTap.run();
                }
                return true;
        }
        return false;
    }

    private void setupTouchHandlers() {
        // EditText: behavior depends on expanded + focused state
        editText.setOnTouchListener((v, event) -> {
            if (isExpanded && isOverlayFocusable()) return false; // native handling
            if (isExpanded) {
                // Expanded but unfocused: tap to re-gain focus
                return handleDragTouch(event, () -> gainFocus(editText));
            }
            // Collapsed: tap to expand
            return handleDragTouch(event, this::expandOverlay);
        });

        // Timer: drag or tap-to-pause
        timerText.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::togglePause));

        // Add quick-select: drag or tap-to-add
        addBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::addQuickSelectRow));

        // Open app: release focus + open (keep expanded)
        openAppBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::openApp));

        // Collapse (−): drag or tap-to-collapse
        closeBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::collapseOverlay));

        // Background (root): outside touch releases focus; bg tap releases focus
        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                if (isOverlayFocusable()) releaseFocus();
                return true;
            }
            return handleDragTouch(event, () -> {
                if (isOverlayFocusable()) releaseFocus();
            });
        });
    }

    private void openApp() {
        releaseFocus();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void clampToScreen() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int viewW = overlayView.getWidth();
        int viewH = overlayView.getHeight();
        if (viewW > 0 && viewH > 0) {
            params.x = Math.max(0, Math.min(params.x, dm.widthPixels - viewW));
            params.y = Math.max(0, Math.min(params.y, dm.heightPixels - viewH));
        }
    }

    private void togglePause() {
        if (currentActivityName.isEmpty()) return;
        if (isRunning) pauseTimer(); else resumeTimer();
    }

    // ---- Expanded / Focus management (independent states) ----

    /** Whether the overlay currently captures keyboard input. */
    private boolean isOverlayFocusable() {
        return (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
    }

    private void setupEditText() {
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                collapseOverlay(); // Enter on main text → save + collapse
                return true;
            }
            return false;
        });
    }

    /** Expand: show buttons + quick-select, gain focus, show keyboard. */
    private void expandOverlay() {
        isExpanded = true;
        addBtn.setVisibility(View.VISIBLE);
        openAppBtn.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        rebuildQuickSelectRows();
        gainFocus(editText);
    }

    /** Give the overlay keyboard focus on the specified view. */
    private void gainFocus(View target) {
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(overlayView, params);
        target.requestFocus();
        if (target instanceof EditText) {
            ((EditText) target).setSelection(((EditText) target).getText().length());
        }
        target.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    /** Release keyboard focus but keep expanded UI visible. */
    private void releaseFocus() {
        View focused = overlayView.findFocus();
        if (focused != null) focused.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(overlayView.getWindowToken(), 0);
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(overlayView, params);
    }

    /** Collapse: save activity if changed, save quick-select, release focus, hide expanded UI. */
    private void collapseOverlay() {
        String newText = editText.getText().toString().trim();
        if (!newText.isEmpty() && !ActivityEntry.normalizeName(newText).equals(
                ActivityEntry.normalizeName(currentActivityName))) {
            startNewActivity(newText);
        }
        isExpanded = false;
        saveQuickSelectNames();
        releaseFocus();
        addBtn.setVisibility(View.GONE);
        openAppBtn.setVisibility(View.GONE);
        closeBtn.setVisibility(View.GONE);
        quickSelectContainer.setVisibility(View.GONE);
    }

    // ---- Timer (drift-proof) ----

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                updateTimerDisplay();
                updateTimelineBar();
                updatePulseSpeed();
                // Refresh segments from DB every ~30s to pick up color changes from the app
                int elapsed = getElapsedSeconds();
                if (elapsed > 0 && elapsed % 30 == 0) loadTodaySegments();
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    private int getElapsedSeconds() {
        if (isRunning && virtualStartTimestamp > 0) {
            return (int) ((System.currentTimeMillis() - virtualStartTimestamp) / 1000);
        }
        return (int) (accumulatedMs / 1000);
    }

    private void startTimer() {
        isRunning = true;
        virtualStartTimestamp = System.currentTimeMillis() - accumulatedMs;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        showTimerRunning();
    }

    private void pauseTimer() {
        accumulatedMs = System.currentTimeMillis() - virtualStartTimestamp;
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        showTimerPaused();
        updateTimerDisplay();
        updateLiveStatics();
        // Snapshot paused duration to crash recovery (don't wait for next heartbeat)
        new OverlayPreferences(this).updateCrashHeartbeat(getElapsedSeconds());
    }

    private void resumeTimer() {
        isRunning = true;
        virtualStartTimestamp = System.currentTimeMillis() - accumulatedMs;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        showTimerRunning();
        updateLiveStatics();
    }

    private void showTimerRunning() {
        timerText.setAlpha(1.0f);
        currentPulseDuration = 0; // force pulse recalculation on next tick
    }

    private void showTimerPaused() {
        timerText.setAlpha(0x99 / 255f);
        stopProgressPulse();
        currentPulseDuration = 0; // force pulse recalculation on resume
        timelineBar.setPulseAlpha(1.0f); // fully opaque when paused
        updateTimelineBar();
    }

    // ---- Progressive pulse: starts immediately, 2x faster every 30min ----
    // Pulse affects the timeline bar's live segment AND (optionally) the border.
    // Border breathes from fully transparent (0) up to the user's opacity setting.

    /** Refresh cached pulse values from current preferences (called on pref change). */
    private void refreshPulseCache() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        cachedOverlayPulseEnabled = prefs.isOverlayPulseEnabled();
        cachedBgColor = prefs.getBgColor();
        cachedBgOpacity = prefs.getOpacity();
        cachedAccentColor = prefs.getAccentColor();
        cachedBorderWidth = prefs.getBorderWidth();
        cachedDensity = getResources().getDisplayMetrics().density;
    }

    private void updatePulseSpeed() {
        if (!isRunning) return;

        int elapsed = getElapsedSeconds();
        // How many 30-min periods have passed?
        int periods = (int) (elapsed / PULSE_INTERVAL_SECONDS);
        // duration = BASE / 1.75^periods (each period 1.75x faster)
        long targetDuration = (long) (BASE_PULSE_MS / Math.pow(1.75, periods));
        if (targetDuration < 400) targetDuration = 400; // floor

        // Only restart animation if speed changed
        if (targetDuration != currentPulseDuration) {
            currentPulseDuration = targetDuration;
            stopProgressPulse();
            refreshPulseCache();

            ValueAnimator va = ValueAnimator.ofFloat(1.0f, 0.3f);
            va.setDuration(targetDuration / 2); // half-cycle
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.addUpdateListener(animation -> {
                long now = System.currentTimeMillis();
                if (now - lastFrameTime < FRAME_INTERVAL_MS) return;
                lastFrameTime = now;
                float alpha = (float) animation.getAnimatedValue();
                timelineBar.setPulseAlpha(alpha);
                applyOverlayPulse(alpha);
            });
            pulseAnimator = va;
            va.start();
        }
    }

    /** Animate border stroke + shift background (darken or brighten) in sync. */
    private void applyOverlayPulse(float pulseAlpha) {
        if (!cachedOverlayPulseEnabled) return;
        if (overlayBgFill == null) return;

        // Normalized factor: 0.0 (pulse dim/transparent) to 1.0 (pulse bright/opaque)
        float factor = (pulseAlpha - 0.3f) / 0.7f;
        if (factor < 0f) factor = 0f;
        if (factor > 1f) factor = 1f;

        // 1. Border pulse: fully transparent → user's opacity (only when border exists)
        if (cachedBorderWidth > 0 && overlayBorderDrawable != null) {
            int borderAlpha = (int) (cachedBgOpacity * factor);
            int borderWidthPx = (int) (cachedBorderWidth * cachedDensity);
            overlayBorderDrawable.setStroke(borderWidthPx,
                (borderAlpha << 24) | (cachedAccentColor & 0x00FFFFFF));
        }

        // 2. Background shift: inverted — darkest/brightest when border is gone
        float bgFactor = 1f - factor;
        float maxShift = 0.25f;
        float shift = bgFactor * maxShift;

        int r = (cachedBgColor >> 16) & 0xFF;
        int g = (cachedBgColor >> 8) & 0xFF;
        int b = cachedBgColor & 0xFF;

        // Perceived brightness (simple average, 0–255). Below ~75 → brighten instead
        int brightness = (r + g + b) / 3;
        int dr, dg, db;
        if (brightness < 75) {
            // Brighten: blend toward white
            dr = r + (int) ((255 - r) * shift);
            dg = g + (int) ((255 - g) * shift);
            db = b + (int) ((255 - b) * shift);
        } else {
            // Darken: blend toward black
            dr = (int) (r * (1 - shift));
            dg = (int) (g * (1 - shift));
            db = (int) (b * (1 - shift));
        }

        // Alpha: from user's opacity toward more opaque (30% of remaining range), inverted with bg
        int alphaBoost = (int) (bgFactor * (255 - cachedBgOpacity) * 0.3f);
        int newAlpha = Math.min(255, cachedBgOpacity + alphaBoost);

        overlayBgFill.setColor((newAlpha << 24) | (dr << 16) | (dg << 8) | db);
    }

    private void stopProgressPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        // Reset overlay bg to resting state
        if (overlayBgFill != null) {
            applyPreferences();
        }
    }

    private void updateTimerDisplay() {
        int elapsed = getElapsedSeconds();
        int h = elapsed / 3600;
        int m = (elapsed % 3600) / 60;
        int s = elapsed % 60;
        if (h > 0) {
            timerText.setText(String.format(Locale.US, "%d:%02d:%02d", h, m, s));
        } else {
            timerText.setText(String.format(Locale.US, "%02d:%02d", m, s));
        }


    }

    // ---- Timeline bar (day history as colored segments) ----

    private void loadTodaySegments() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<ActivityEntry> entries = dbHelper.getEntriesByDate(today);
        Collections.reverse(entries); // DB returns DESC; we need chronological
        savedSegments.clear();
        for (ActivityEntry e : entries) {
            savedSegments.add(new TimelineBarView.Segment(e.getColor(), e.getDurationSeconds()));
        }
        // Refresh live segment color in case the user changed it via the app
        if (!currentActivityName.isEmpty()) {
            currentActivityColor = dbHelper.getColorForName(currentActivityName);
        }
        updateTimelineBar();
    }

    private void updateTimelineBar() {
        List<TimelineBarView.Segment> all = new ArrayList<>(savedSegments);
        // Add the currently-running activity as a live segment
        if (!currentActivityName.isEmpty()) {
            int elapsed = getElapsedSeconds();
            if (elapsed > 0) {
                all.add(new TimelineBarView.Segment(currentActivityColor, elapsed));
            }
        }
        if (!all.isEmpty()) {
            timelineBar.setVisibility(View.VISIBLE);
            int pulseIdx = (isRunning && !currentActivityName.isEmpty()) ? all.size() - 1 : -1;
            timelineBar.setSegments(all, pulseIdx);
        }
    }

    // ---- Activity tracking ----

    private static final int MIN_ACTIVITY_SECONDS = 10;

    private void saveCurrentActivity() {
        int elapsed = getElapsedSeconds();
        if (currentActivityName.isEmpty() || elapsed < MIN_ACTIVITY_SECONDS) {
            new OverlayPreferences(this).clearCrashRecovery();
            return;
        }
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date(currentStartTime));

        int color = dbHelper.getColorForName(currentActivityName);

        ActivityEntry entry = new ActivityEntry(
            currentActivityName, elapsed, currentStartTime, date);
        entry.setColor(color);
        dbHelper.insertActivity(entry);
        new OverlayPreferences(this).clearCrashRecovery();
    }

    /** Recover an activity that was running when the service crashed. */
    private void recoverCrashedActivity(OverlayPreferences prefs) {
        String name = prefs.getCrashName();
        long startTime = prefs.getCrashStartTime();
        int elapsed = prefs.getCrashElapsedSeconds();
        prefs.clearCrashRecovery();
        if (name.isEmpty() || elapsed < MIN_ACTIVITY_SECONDS) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date(startTime));
        int color = dbHelper.getColorForName(name);
        ActivityEntry entry = new ActivityEntry(name, elapsed, startTime, date);
        entry.setColor(color);
        dbHelper.insertActivity(entry);
    }

    private void startNewActivity(String name) {
        saveCurrentActivity();
        loadTodaySegments(); // re-query DB now that previous activity is saved
        currentActivityName = name;
        editText.setText(name);
        currentActivityColor = dbHelper.getColorForName(name);
        currentStartTime = System.currentTimeMillis();
        accumulatedMs = 0;
        virtualStartTimestamp = -1;

        timerText.setVisibility(View.VISIBLE);
        separator.setVisibility(View.VISIBLE);

        updateTimerDisplay();
        startTimer();
        updateLiveStatics();

        // Set crash recovery checkpoint and start heartbeat
        new OverlayPreferences(this).setCrashRecovery(name, currentStartTime);
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        heartbeatHandler.post(heartbeatRunnable);
    }

    private void updateLiveStatics() {
        liveActivityName = currentActivityName;
        liveStartTime = currentStartTime;
        liveIsRunning = isRunning;
        liveActivityColor = currentActivityColor;
    }

    // ---- Notification (minimal — required by Android for foreground service) ----

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "TrackyTime", NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TrackyTime")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build();
    }

    // ---- Quick-select activity shortcuts ----

    private void addQuickSelectRow() {
        addQuickSelectRowWithName("", true);
    }

    private void addQuickSelectRowWithName(String name, boolean requestFocus) {
        quickSelectContainer.setVisibility(View.VISIBLE);

        OverlayPreferences prefs = new OverlayPreferences(this);
        float density = getResources().getDisplayMetrics().density;
        float textSize = prefs.getTextSize();
        int textColor = prefs.getTextColor();
        boolean strokeEnabled = prefs.isTextStrokeEnabled();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (3 * density), 0, 0);

        // Play button ▶
        StrokeTextView playBtn = new StrokeTextView(this);
        playBtn.setText("▶");
        playBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        playBtn.setTextColor((textColor & 0x00FFFFFF) | 0x99000000);
        playBtn.setPadding(0, 0, (int) (6 * density), 0);
        playBtn.setIncludeFontPadding(false);
        playBtn.setStrokeEnabled(strokeEnabled);

        // Activity name
        StrokeEditText nameField = new StrokeEditText(this);
        nameField.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        nameField.setTextColor(textColor);
        nameField.setHintTextColor((textColor & 0x00FFFFFF) | 0x99000000);
        nameField.setBackground(null);
        nameField.setSingleLine(true);
        nameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT);
        nameField.setPadding(0, 0, 0, 0);
        nameField.setHint("activity name");
        nameField.setMinWidth((int) (60 * density));
        nameField.setMaxWidth((int) (140 * density));
        if (!name.isEmpty()) nameField.setText(name);
        nameField.setStrokeEnabled(strokeEnabled);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameField.setLayoutParams(nameParams);

        // Remove button X icon
        StrokeImageView removeBtn = new StrokeImageView(this);
        removeBtn.setImageResource(R.drawable.ic_close);
        removeBtn.setImageTintList(ColorStateList.valueOf((textColor & 0x00FFFFFF) | 0x99000000));
        removeBtn.setStrokeEnabled(strokeEnabled);
        removeBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = (int) (textSize * 1.2f * density);
        LinearLayout.LayoutParams removeBtnParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        removeBtnParams.setMargins((int) (6 * density), 0, 0, 0);
        removeBtn.setLayoutParams(removeBtnParams);

        row.addView(playBtn);
        row.addView(nameField);
        row.addView(removeBtn);
        quickSelectContainer.addView(row);

        // Play: switch to this activity, release focus (keep expanded)
        playBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, () -> {
                String actName = nameField.getText().toString().trim();
                if (!actName.isEmpty()) {
                    saveQuickSelectNames();
                    startNewActivity(actName);
                    releaseFocus();
                }
            }));

        // Done on keyboard: save the name, release focus (keep expanded)
        nameField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveQuickSelectNames();
                releaseFocus();
                return true;
            }
            return false;
        });

        // Tap name field when expanded but unfocused: re-gain focus
        nameField.setOnTouchListener((v, event) -> {
            if (isExpanded && !isOverlayFocusable()) {
                return handleDragTouch(event, () -> gainFocus(nameField));
            }
            return false; // native EditText handling when focused
        });

        // Remove: delete this row
        removeBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, () -> {
                quickSelectContainer.removeView(row);
                saveQuickSelectNames();
                if (quickSelectContainer.getChildCount() == 0) {
                    quickSelectContainer.setVisibility(View.GONE);
                }
            }));

        if (requestFocus) {
            gainFocus(nameField);
        }
    }

    private void saveQuickSelectNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < quickSelectContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) quickSelectContainer.getChildAt(i);
            EditText nameField = (EditText) row.getChildAt(1);
            String name = nameField.getText().toString().trim();
            if (!name.isEmpty()) names.add(name);
        }
        new OverlayPreferences(this).setQuickActivities(names);
    }

    private void rebuildQuickSelectRows() {
        quickSelectContainer.removeAllViews();
        List<String> names = new OverlayPreferences(this).getQuickActivities();
        if (names.isEmpty()) {
            quickSelectContainer.setVisibility(View.GONE);
        } else {
            quickSelectContainer.setVisibility(View.VISIBLE);
            for (String name : names) {
                addQuickSelectRowWithName(name, false);
            }
        }
    }

    // ---- Lifecycle ----

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isServiceRunning = false;
        liveActivityName = "";
        liveStartTime = 0;
        liveIsRunning = false;
        liveActivityColor = 0;
        saveCurrentActivity();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.removeCallbacks(applyPrefsRunnable);
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        stopProgressPulse();
        // Unregister pref listener
        getSharedPreferences("overlay_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener);
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            windowManager.removeView(overlayView);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

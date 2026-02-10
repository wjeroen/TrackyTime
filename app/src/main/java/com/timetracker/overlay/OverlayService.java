package com.timetracker.overlay;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service {

    public static boolean isServiceRunning = false;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private TimelineBarView timelineBar;
    private TextView timerText, separator, openAppBtn, closeBtn;
    private EditText editText;
    private GradientDrawable overlayBg; // cached for pulse animation

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
    private NotificationManager notifManager;

    private ValueAnimator pulseAnimator;
    private long currentPulseDuration = 0; // 0 = no pulse active

    // Cached values for overlay pulse (avoid reading prefs every animation frame)
    private boolean cachedOverlayPulseEnabled = true;
    private int cachedBgColor, cachedBgOpacity, cachedAccentColor, cachedBorderWidth;
    private float cachedDensity;

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
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

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
        openAppBtn = overlayView.findViewById(R.id.openAppBtn);
        closeBtn = overlayView.findViewById(R.id.closeBtn);
        timelineBar = overlayView.findViewById(R.id.timelineBar);

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

        // Background: rounded rect with optional border — cached for pulse animation
        overlayBg = new GradientDrawable();
        overlayBg.setShape(GradientDrawable.RECTANGLE);
        overlayBg.setCornerRadius(10 * density); // 10dp, matching FCT
        overlayBg.setColor(bgWithAlpha);

        if (borderWidth > 0) {
            // Border follows same opacity as background
            int accentColor = prefs.getAccentColor();
            int borderColor = (bgOpacity << 24) | (accentColor & 0x00FFFFFF);
            overlayBg.setStroke(borderWidthPx, borderColor);
        }
        overlayView.setBackground(overlayBg);

        // Border grows outward: add border width to padding so content area stays the same
        int basePad = (int) (BASE_PADDING_DP * density);
        int pad = basePad + borderWidthPx;
        overlayView.setPadding(pad, pad, pad, pad);

        // Text: always fully opaque
        int textColor = prefs.getTextColor();

        timerText.setTextColor(textColor);
        separator.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);
        editText.setTextColor(textColor);
        editText.setHintTextColor((textColor & 0x00FFFFFF) | 0x55000000);
        openAppBtn.setTextColor((textColor & 0x00FFFFFF) | 0x99000000);
        closeBtn.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);

        // Unified text size for everything, open-app icon slightly larger to match ✕ visually
        float textSize = prefs.getTextSize();
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        openAppBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize + 4);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);

        // Timeline bar corner radius (not affected by opacity)
        timelineBar.setCornerRadius(2 * density);
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
        // EditText: when NOT editing, drag or tap-to-enter-edit.
        // When editing, pass through so EditText handles cursor/selection natively.
        editText.setOnTouchListener((v, event) -> {
            if (isInEditMode()) return false; // native EditText handling
            return handleDragTouch(event, this::enterEditMode);
        });

        // Timer: drag or tap-to-pause
        timerText.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::togglePause));

        // Open app: drag or tap-to-open
        openAppBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::openApp));

        // Close: drag or tap-to-close
        closeBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::stopSelf));

        // Background (root): drag, tap-to-exit-edit, or outside touch exits edit
        overlayView.setOnTouchListener((v, event) -> {
            // ACTION_OUTSIDE fires when user taps outside the overlay window
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                if (isInEditMode()) exitEditMode();
                return true;
            }
            return handleDragTouch(event, () -> {
                if (isInEditMode()) exitEditMode();
            });
        });
    }

    private void openApp() {
        closeEditingUI();
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

    // ---- EditText / focus management ----

    private boolean isInEditMode() {
        return (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
    }

    private void setupEditText() {
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                exitEditMode();
                return true;
            }
            return false;
        });
    }

    private void enterEditMode() {
        // Allow focus + detect touches outside the overlay window
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(overlayView, params);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
        // Show open-app + close buttons while editing
        openAppBtn.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        editText.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void exitEditMode() {
        String newText = editText.getText().toString().trim();
        if (!newText.isEmpty() && !newText.equals(currentActivityName)) {
            startNewActivity(newText);
        }
        closeEditingUI();
    }

    private void closeEditingUI() {
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        // Restore non-focusable, stop watching outside touches
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(overlayView, params);
        // Hide open-app + close buttons when not editing
        openAppBtn.setVisibility(View.GONE);
        closeBtn.setVisibility(View.GONE);
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
        updateNotification();
    }

    private void resumeTimer() {
        isRunning = true;
        virtualStartTimestamp = System.currentTimeMillis() - accumulatedMs;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        showTimerRunning();
        updateNotification();
    }

    private void showTimerRunning() {
        timerText.setAlpha(1.0f);
        currentPulseDuration = 0; // force pulse recalculation on next tick
    }

    private void showTimerPaused() {
        timerText.setAlpha(0.5f);
        stopProgressPulse();
        currentPulseDuration = 0; // force pulse recalculation on resume
        timelineBar.setPulseAlpha(1.0f); // fully opaque when paused
        updateTimelineBar();
    }

    // ---- Progressive pulse: starts immediately, 1.5x faster every 30min ----
    // Pulse affects the timeline bar's live segment AND (optionally) the entire overlay bg+border.
    // The user's opacity setting is the CEILING — pulse breathes from invisible (0) up to
    // the opacity setting. Lower opacity = subtler pulse. Higher opacity = more visible pulse.

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
        // duration = BASE / 1.5^periods (each period 1.5x faster)
        long targetDuration = (long) (BASE_PULSE_MS / Math.pow(1.5, periods));
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
                float alpha = (float) animation.getAnimatedValue();
                timelineBar.setPulseAlpha(alpha);
                applyOverlayPulse(alpha);
            });
            pulseAnimator = va;
            va.start();
        }
    }

    /**
     * Animate overlay bg + border in sync with the timeline bar pulse.
     * Opacity setting is the CEILING: pulse goes from 0 (invisible) up to the user's opacity.
     * This makes low opacity = subtler breathing, high opacity = more visible breathing.
     */
    private void applyOverlayPulse(float pulseAlpha) {
        if (!cachedOverlayPulseEnabled) return;
        if (overlayBg == null) return;

        // Map pulseAlpha (1.0→0.3) to bg alpha (cachedBgOpacity→0)
        // At pulseAlpha=1.0: bgAlpha=cachedBgOpacity (user's setting = ceiling)
        // At pulseAlpha=0.3: bgAlpha=0 (invisible)
        int bgAlpha = (int) (cachedBgOpacity * (pulseAlpha - 0.3f) / 0.7f);
        if (bgAlpha > cachedBgOpacity) bgAlpha = cachedBgOpacity;
        if (bgAlpha < 0) bgAlpha = 0;
        overlayBg.setColor((bgAlpha << 24) | (cachedBgColor & 0x00FFFFFF));

        if (cachedBorderWidth > 0) {
            // Border: same alpha scaling
            int borderAlpha = (int) (cachedBgOpacity * (pulseAlpha - 0.3f) / 0.7f);
            if (borderAlpha > cachedBgOpacity) borderAlpha = cachedBgOpacity;
            if (borderAlpha < 0) borderAlpha = 0;
            overlayBg.setStroke((int) (cachedBorderWidth * cachedDensity),
                (borderAlpha << 24) | (cachedAccentColor & 0x00FFFFFF));
        }
    }

    private void stopProgressPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        // Reset overlay bg to resting state
        if (overlayBg != null) {
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
        if (elapsed % 5 == 0) updateNotification();
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

    private void saveCurrentActivity() {
        int elapsed = getElapsedSeconds();
        if (currentActivityName.isEmpty() || elapsed <= 0) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date(currentStartTime));

        int color = dbHelper.getColorForName(currentActivityName);

        ActivityEntry entry = new ActivityEntry(
            currentActivityName, elapsed, currentStartTime, date);
        entry.setColor(color);
        dbHelper.insertActivity(entry);
    }

    private void startNewActivity(String name) {
        saveCurrentActivity();
        loadTodaySegments(); // re-query DB now that previous activity is saved
        currentActivityName = name;
        currentActivityColor = dbHelper.getColorForName(name);
        currentStartTime = System.currentTimeMillis();
        accumulatedMs = 0;
        virtualStartTimestamp = -1;

        timerText.setVisibility(View.VISIBLE);
        separator.setVisibility(View.VISIBLE);

        updateTimerDisplay();
        startTimer();
        updateNotification();
    }

    // ---- Notification ----

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "TrackyTime", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Active time tracking overlay");
        channel.setShowBadge(false);
        notifManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text;
        if (currentActivityName.isEmpty()) {
            text = "Ready to track";
        } else {
            int elapsed = getElapsedSeconds();
            int h = elapsed / 3600;
            int m = (elapsed % 3600) / 60;
            int s = elapsed % 60;
            String time = h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
            text = currentActivityName + " " + time + (isRunning ? "" : " (paused)");
        }

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TrackyTime")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification() {
        notifManager.notify(NOTIF_ID, buildNotification());
    }

    // ---- Lifecycle ----

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isServiceRunning = false;
        saveCurrentActivity();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.removeCallbacks(applyPrefsRunnable);
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

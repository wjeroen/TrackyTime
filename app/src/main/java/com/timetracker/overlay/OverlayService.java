package com.timetracker.overlay;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
    private TextView timerText, separator, resetBtn, closeBtn;
    private EditText editText;

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
    }

    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        timerText = overlayView.findViewById(R.id.timerText);
        separator = overlayView.findViewById(R.id.separator);
        editText = overlayView.findViewById(R.id.activityInput);
        resetBtn = overlayView.findViewById(R.id.resetBtn);
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

    // ---- Visual preferences (opacity ONLY on background) ----

    private void applyPreferences() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        float density = getResources().getDisplayMetrics().density;

        int bgColor = prefs.getBgColor();
        int alpha = prefs.getOpacity();
        int bgWithAlpha = (alpha << 24) | (bgColor & 0x00FFFFFF);

        // Background: rounded pill — drawable alpha doesn't affect children
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20 * density);
        bg.setColor(bgWithAlpha);
        overlayView.setBackground(bg);

        // Text: always fully opaque
        int textColor = prefs.getTextColor();
        int accentColor = prefs.getAccentColor();

        timerText.setTextColor(textColor);
        separator.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);
        editText.setTextColor(textColor);
        editText.setHintTextColor((textColor & 0x00FFFFFF) | 0x55000000);
        resetBtn.setTextColor((accentColor & 0x00FFFFFF) | 0x99000000);
        closeBtn.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);

        float textSize = prefs.getTextSize();
        float timerSize = prefs.getTimerTextSize();
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, timerSize);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);

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

        // Reset: drag or tap-to-reset
        resetBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::resetTimer));

        // Close: drag or tap-to-close
        closeBtn.setOnTouchListener((v, event) ->
            handleDragTouch(event, this::stopSelf));

        // Background (root): drag or tap-to-exit-edit
        overlayView.setOnTouchListener((v, event) ->
            handleDragTouch(event, () -> {
                if (isInEditMode()) exitEditMode();
            }));
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

    private void resetTimer() {
        if (currentActivityName.isEmpty()) return;
        // Save current session, start fresh with same activity name
        startNewActivity(currentActivityName);
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
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(overlayView, params);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
        // Show reset (if tracking) and close buttons while editing
        if (!currentActivityName.isEmpty()) resetBtn.setVisibility(View.VISIBLE);
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
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(overlayView, params);
        // Hide reset + close buttons when not editing
        resetBtn.setVisibility(View.GONE);
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
        timelineBar.setPulseAlpha(1.0f); // fully opaque when paused
        updateTimelineBar();
    }

    // ---- Progressive pulse: starts immediately, 1.5x faster every 30min ----

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
            ValueAnimator va = ValueAnimator.ofFloat(1.0f, 0.3f);
            va.setDuration(targetDuration / 2); // half-cycle
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.addUpdateListener(animation ->
                timelineBar.setPulseAlpha((float) animation.getAnimatedValue()));
            pulseAnimator = va;
            va.start();
        }
    }

    private void stopProgressPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
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
        stopProgressPulse();
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

package com.timetracker.overlay;

import android.animation.ObjectAnimator;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {

    public static boolean isServiceRunning = false;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private View overlayBg, progressTrack, progressBar;
    private TextView timerText, separator;
    private EditText editText;

    private Handler timerHandler;
    private boolean isRunning = false;

    // Drift-proof timer: record "virtual start" and compute elapsed = now - virtualStart
    private long virtualStartTimestamp = -1;
    private long accumulatedMs = 0;

    private String currentActivityName = "";
    private long currentStartTime = 0;

    private DatabaseHelper dbHelper;
    private NotificationManager notifManager;

    private ObjectAnimator pulseAnimator;

    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "timetracker_channel";

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
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.overlay_layout, null);

        overlayBg = overlayView.findViewById(R.id.overlayBg);
        timerText = overlayView.findViewById(R.id.timerText);
        separator = overlayView.findViewById(R.id.separator);
        editText = overlayView.findViewById(R.id.activityInput);
        progressTrack = overlayView.findViewById(R.id.progressTrack);
        progressBar = overlayView.findViewById(R.id.progressBar);

        float density = getResources().getDisplayMetrics().density;

        // WRAP_CONTENT both ways — auto-sizes like FloatingCountdownTimer
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
        setupDrag();
        setupEditText();

        windowManager.addView(overlayView, params);
    }

    // ---- Visual preferences (opacity ONLY on background) ----

    private void applyPreferences() {
        OverlayPreferences prefs = new OverlayPreferences(this);
        float density = getResources().getDisplayMetrics().density;

        int bgColor = prefs.getBgColor();
        int alpha = prefs.getOpacity();
        int bgWithAlpha = (alpha << 24) | (bgColor & 0x00FFFFFF);

        // Background: rounded pill with user-controlled opacity
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20 * density);
        bg.setColor(bgWithAlpha);
        overlayBg.setBackground(bg);

        // Text colors: always fully opaque
        int textColor = prefs.getTextColor();
        int accentColor = prefs.getAccentColor();

        timerText.setTextColor(textColor);
        separator.setTextColor((textColor & 0x00FFFFFF) | 0x66000000);
        editText.setTextColor(textColor);
        editText.setHintTextColor((textColor & 0x00FFFFFF) | 0x55000000);

        float textSize = prefs.getTextSize();
        float timerSize = prefs.getTimerTextSize();
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, timerSize);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);

        // Progress track (faint accent, ~10% opacity)
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setShape(GradientDrawable.RECTANGLE);
        trackBg.setCornerRadius(2 * density);
        trackBg.setColor((accentColor & 0x00FFFFFF) | 0x1A000000);
        progressTrack.setBackground(trackBg);

        // Progress bar (solid accent)
        GradientDrawable barBg = new GradientDrawable();
        barBg.setShape(GradientDrawable.RECTANGLE);
        barBg.setCornerRadius(2 * density);
        barBg.setColor(accentColor);
        progressBar.setBackground(barBg);
    }

    // ---- Drag handling (whole pill is draggable) ----

    private void setupDrag() {
        overlayView.setOnTouchListener((v, event) -> {
            // Let the EditText handle its own touches when in edit mode
            if (isInEditMode() && isTouchOnView(event, editText)) {
                return false;
            }

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
                    if (!isDragging) {
                        if (isTouchOnView(event, timerText)
                                && timerText.getVisibility() == View.VISIBLE) {
                            // Tap timer → pause/resume
                            togglePause();
                        } else if (isTouchOnView(event, editText)) {
                            // Tap activity text → enter edit mode
                            if (!isInEditMode()) enterEditMode();
                        } else {
                            // Tap background → exit edit mode
                            if (isInEditMode()) exitEditMode();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private boolean isTouchOnView(MotionEvent event, View target) {
        if (target.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= loc[0] && x <= loc[0] + target.getWidth()
            && y >= loc[1] && y <= loc[1] + target.getHeight();
    }

    private void clampToScreen() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int viewW = overlayView.getWidth();
        int viewH = overlayView.getHeight();
        if (viewW > 0 && viewH > 0) {
            params.x = Math.max(0, Math.min(params.x, screenW - viewW));
            params.y = Math.max(0, Math.min(params.y, screenH - viewH));
        }
    }

    private void togglePause() {
        if (currentActivityName.isEmpty()) return;
        if (isRunning) {
            pauseTimer();
        } else {
            resumeTimer();
        }
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
        editText.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
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
    }

    // ---- Timer (drift-proof using virtualStartTimestamp) ----

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                updateTimerDisplay();
                // Update twice per second for snappy display
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
        startProgressPulse();
    }

    private void showTimerPaused() {
        timerText.setAlpha(0.5f);
        stopProgressPulse();
        progressBar.setAlpha(0.3f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        progressBar.setLayoutParams(lp);
    }

    private void startProgressPulse() {
        stopProgressPulse();
        progressBar.setAlpha(1.0f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        progressBar.setLayoutParams(lp);

        // Breathing pulse while timer runs
        pulseAnimator = ObjectAnimator.ofFloat(progressBar, "alpha", 1.0f, 0.3f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.start();
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

    // ---- Activity tracking ----

    private void saveCurrentActivity() {
        int elapsed = getElapsedSeconds();
        if (currentActivityName.isEmpty() || elapsed <= 0) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date(currentStartTime));

        // Use consistent color for this activity name
        int color = dbHelper.getColorForName(currentActivityName);

        ActivityEntry entry = new ActivityEntry(
            currentActivityName, elapsed, currentStartTime, date);
        entry.setColor(color);
        dbHelper.insertActivity(entry);
    }

    private void startNewActivity(String name) {
        saveCurrentActivity();
        currentActivityName = name;
        currentStartTime = System.currentTimeMillis();
        accumulatedMs = 0;
        virtualStartTimestamp = -1;

        // Show timer + separator
        timerText.setVisibility(View.VISIBLE);
        separator.setVisibility(View.VISIBLE);

        updateTimerDisplay();
        startTimer();
        updateNotification();
    }

    // ---- Notification ----

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "TrackyTime",
            NotificationManager.IMPORTANCE_LOW);
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
            String time;
            if (h > 0) {
                time = String.format(Locale.US, "%d:%02d:%02d", h, m, s);
            } else {
                time = String.format(Locale.US, "%02d:%02d", m, s);
            }
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

package com.timetracker.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {

    public static boolean isServiceRunning = false;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private TextView timerText, dragIcon;
    private EditText editText;
    private Button playPauseBtn, resetBtn;

    private Handler timerHandler;
    private int elapsedSeconds = 0;
    private boolean isRunning = false;

    private String currentActivityName = "";
    private long currentStartTime = 0;

    private DatabaseHelper dbHelper;
    private NotificationManager notifManager;

    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "timetracker_channel";

    // Drag state
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

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

        timerText = overlayView.findViewById(R.id.timerText);
        dragIcon = overlayView.findViewById(R.id.dragIcon);
        editText = overlayView.findViewById(R.id.activityInput);
        playPauseBtn = overlayView.findViewById(R.id.playPauseBtn);
        resetBtn = overlayView.findViewById(R.id.resetBtn);
        View dragArea = overlayView.findViewById(R.id.dragArea);

        OverlayPreferences prefs = new OverlayPreferences(this);
        float density = getResources().getDisplayMetrics().density;
        int widthDp;
        switch (prefs.getSize()) {
            case 0: widthDp = 220; break;
            case 2: widthDp = 340; break;
            default: widthDp = 280; break;
        }

        params = new WindowManager.LayoutParams(
            (int) (widthDp * density),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (int) (16 * density);
        params.y = (int) (100 * density);

        applyPreferences(prefs);
        setupDrag(dragArea);
        setupEditText();
        setupButtons();

        windowManager.addView(overlayView, params);
    }

    // ---- Visual preferences ----

    private void applyPreferences(OverlayPreferences prefs) {
        int bgColor = prefs.getBgColor();
        int alpha = prefs.getOpacity();
        int bgWithAlpha = (alpha << 24) | (bgColor & 0x00FFFFFF);

        float density = getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14 * density);
        bg.setColor(bgWithAlpha);
        bg.setStroke((int)(1 * density), (prefs.getTextColor() & 0x00FFFFFF) | 0x33000000);
        overlayView.setBackground(bg);

        int textColor = prefs.getTextColor();
        int accentColor = prefs.getAccentColor();

        timerText.setTextColor(textColor);
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getTimerTextSize());
        dragIcon.setTextColor((textColor & 0x00FFFFFF) | 0x88000000);
        editText.setTextColor(textColor);
        editText.setHintTextColor((textColor & 0x00FFFFFF) | 0x55000000);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getTextSize());

        playPauseBtn.setBackgroundColor(Color.TRANSPARENT);
        playPauseBtn.setTextColor(accentColor);
        resetBtn.setBackgroundColor(Color.TRANSPARENT);
        resetBtn.setTextColor(accentColor);
    }

    // ---- Drag handling ----

    private void setupDrag(View dragArea) {
        dragArea.setOnTouchListener((v, event) -> {
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    if (isDragging) {
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(overlayView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        // Tap on drag area exits edit mode
                        if (isInEditMode()) exitEditMode();
                    }
                    return true;
            }
            return false;
        });
    }

    // ---- EditText / focus management ----

    private boolean isInEditMode() {
        return (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
    }

    private void setupEditText() {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && !isInEditMode()) {
                enterEditMode();
            }
            return false;
        });

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
        resetBtn.setVisibility(View.VISIBLE);
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
        resetBtn.setVisibility(View.GONE);
    }

    // ---- Buttons ----

    private void setupButtons() {
        playPauseBtn.setOnClickListener(v -> {
            if (currentActivityName.isEmpty()) return;
            if (isRunning) {
                pauseTimer();
            } else {
                resumeTimer();
            }
        });

        resetBtn.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) return;
            startNewActivity(text);
            closeEditingUI();
        });
    }

    // ---- Timer ----

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                elapsedSeconds++;
                updateTimerDisplay();
                if (elapsedSeconds % 5 == 0) updateNotification();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private void startTimer() {
        isRunning = true;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
        updatePlayPauseButton();
    }

    private void pauseTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        updatePlayPauseButton();
        updateNotification();
    }

    private void resumeTimer() {
        isRunning = true;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
        updatePlayPauseButton();
        updateNotification();
    }

    private void updateTimerDisplay() {
        int h = elapsedSeconds / 3600;
        int m = (elapsedSeconds % 3600) / 60;
        int s = elapsedSeconds % 60;
        timerText.setText(String.format(Locale.US, "%02d:%02d:%02d", h, m, s));
    }

    private void updatePlayPauseButton() {
        playPauseBtn.setText(isRunning ? "⏸" : "▶");
    }

    // ---- Activity tracking ----

    private void saveCurrentActivity() {
        if (currentActivityName.isEmpty() || elapsedSeconds <= 0) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(new Date(currentStartTime));
        ActivityEntry entry = new ActivityEntry(
            currentActivityName, elapsedSeconds, currentStartTime, date);
        dbHelper.insertActivity(entry);
    }

    private void startNewActivity(String name) {
        saveCurrentActivity();
        currentActivityName = name;
        currentStartTime = System.currentTimeMillis();
        elapsedSeconds = 0;
        updateTimerDisplay();
        startTimer();
        updateNotification();
    }

    // ---- Notification ----

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Time Tracker",
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
            int h = elapsedSeconds / 3600;
            int m = (elapsedSeconds % 3600) / 60;
            int s = elapsedSeconds % 60;
            String time = String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
            text = currentActivityName + " " + time + (isRunning ? "" : " (paused)");
        }

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Time Tracker")
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

package com.timetracker.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service for the immersive clock: a small time and battery pill in
 * the top-right corner while the phone is in fullscreen (videos, games).
 *
 * It is separate from OverlayService so the tracking pill and the clock never
 * affect each other. Stopping the overlay leaves the clock running. The
 * service runs while the setting is on and stops itself when it goes off.
 */
public class ClockService extends Service {

    public static boolean isRunning = false;

    // OverlayService uses 1001. A separate id, because a stopping foreground
    // service takes its notification with it.
    private static final int NOTIF_ID = 1002;

    private WindowManager windowManager;
    private View immersiveDetectorView;
    private StrokeTextView clockText;
    private GradientDrawable clockBgDrawable;
    private Handler clockHandler;
    private Runnable clockRunnable;
    private boolean isImmersiveMode = false;
    private boolean immersiveClockSetUp = false;

    // Live-update: listen for pref changes from the settings dialog
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private final Runnable applyPrefsRunnable = () -> {
        if (!new OverlayPreferences(this).isImmersiveClockEnabled()) {
            stopSelf();
            return;
        }
        applyClockPreferences();
    };

    /**
     * Starts or stops the clock so it matches the setting. Safe to call at any
     * time and from anywhere, so every place that might find the clock missing
     * (boot, the settings checkbox, opening the app, starting the overlay) can
     * simply call this.
     */
    static void sync(Context context) {
        boolean wanted = new OverlayPreferences(context).isImmersiveClockEnabled();
        if (wanted && !isRunning && Settings.canDrawOverlays(context)) {
            context.startForegroundService(new Intent(context, ClockService.class));
        } else if (!wanted && isRunning) {
            context.stopService(new Intent(context, ClockService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        OverlayService.createNotificationChannel(this);
        startForeground(NOTIF_ID, OverlayService.buildNotification(this));
        isRunning = true;

        SharedPreferences sp = getSharedPreferences("overlay_prefs", MODE_PRIVATE);
        clockHandler = new Handler(Looper.getMainLooper());
        prefsListener = (sharedPreferences, key) -> {
            clockHandler.removeCallbacks(applyPrefsRunnable);
            clockHandler.postDelayed(applyPrefsRunnable, 100); // debounce
        };
        sp.registerOnSharedPreferenceChangeListener(prefsListener);

        if (new OverlayPreferences(this).isImmersiveClockEnabled()
                && Settings.canDrawOverlays(this)) {
            setupImmersiveClock();
        } else {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        clockHandler.removeCallbacks(applyPrefsRunnable);
        getSharedPreferences("overlay_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener);
        teardownImmersiveClock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setupImmersiveClock() {
        if (immersiveClockSetUp) return;
        immersiveClockSetUp = true;

        float density = getResources().getDisplayMetrics().density;

        // 1x1 pixel view to receive system inset changes without blocking touches
        immersiveDetectorView = new View(this);
        WindowManager.LayoutParams detectorParams = new WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        immersiveDetectorView.setOnApplyWindowInsetsListener((view, insets) -> {
            boolean immersive;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // isVisible() is more accurate than checking inset height, handles translucent bars
                boolean statusHidden = !insets.isVisible(WindowInsets.Type.statusBars());
                boolean navHidden = !insets.isVisible(WindowInsets.Type.navigationBars());
                immersive = statusHidden && navHidden;
            } else {
                immersive = insets.getSystemWindowInsetTop() == 0
                         && insets.getSystemWindowInsetBottom() == 0;
            }
            if (immersive != isImmersiveMode) {
                isImmersiveMode = immersive;
                updateClockVisibility(immersive);
            }
            return insets;
        });
        windowManager.addView(immersiveDetectorView, detectorParams);

        // Clock overlay, small pill showing current time
        clockText = new StrokeTextView(this);
        int padH = (int) (8 * density);
        int padV = (int) (4 * density);
        clockText.setPadding(padH, padV, padH, padV);

        clockBgDrawable = new GradientDrawable();
        clockBgDrawable.setShape(GradientDrawable.RECTANGLE);
        clockBgDrawable.setCornerRadius(10 * density);
        clockText.setBackground(clockBgDrawable);

        applyClockPreferences();
        updateClockDisplay();

        WindowManager.LayoutParams clockParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        clockParams.gravity = Gravity.TOP | Gravity.END;
        clockParams.x = (int) (16 * density);
        clockParams.y = (int) (8 * density);

        clockText.setVisibility(View.GONE);
        windowManager.addView(clockText, clockParams);

        // Clock update handler
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                if (isImmersiveMode && immersiveClockSetUp) {
                    updateClockDisplay();
                    long delay = 60000 - (System.currentTimeMillis() % 60000);
                    clockHandler.postDelayed(this, delay);
                }
            }
        };
    }

    private void teardownImmersiveClock() {
        if (!immersiveClockSetUp) return;
        immersiveClockSetUp = false;
        isImmersiveMode = false;

        if (clockHandler != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
        if (immersiveDetectorView != null && immersiveDetectorView.isAttachedToWindow()) {
            windowManager.removeView(immersiveDetectorView);
        }
        immersiveDetectorView = null;
        if (clockText != null && clockText.isAttachedToWindow()) {
            windowManager.removeView(clockText);
        }
        clockText = null;
        clockBgDrawable = null;
    }

    private void updateClockVisibility(boolean show) {
        if (clockText == null) return;
        clockText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            updateClockDisplay();
            clockHandler.removeCallbacks(clockRunnable);
            long delay = 60000 - (System.currentTimeMillis() % 60000);
            clockHandler.postDelayed(clockRunnable, delay);
        } else {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }

    private void updateClockDisplay() {
        if (clockText == null) return;
        String pattern = android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        String time = new SimpleDateFormat(pattern, Locale.US).format(new Date());
        int battery = getBatteryLevel();
        clockText.setText(time + " · " + battery + "%");
    }

    private int getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus == null) return -1;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return (int) (level * 100f / scale);
    }

    private void applyClockPreferences() {
        if (clockText == null || clockBgDrawable == null) return;
        OverlayPreferences prefs = new OverlayPreferences(this);

        float textSize = prefs.getTextSize();
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        clockText.setTextColor(0xFFFFFFFF);
        clockText.setAlpha(1.0f);

        boolean strokeEnabled = prefs.isTextStrokeEnabled();
        int strokeWidth = prefs.getStrokeWidth();
        clockText.setStrokeEnabled(strokeEnabled);
        clockText.setStrokeWidth(strokeWidth);

        int bgOpacity = prefs.getOpacity();
        clockBgDrawable.setColor((bgOpacity << 24) | 0x00000000);
    }
}

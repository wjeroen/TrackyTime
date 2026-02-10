package com.timetracker.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERM_CODE = 100;
    private static final int NOTIF_PERM_CODE = 101;

    private static final int[] ENTRY_COLORS = {
        0xFFE53935, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
        0xFF42A5F5, 0xFF26C6DA, 0xFF26A69A, 0xFF66BB6A,
        0xFFD4E157, 0xFFFFEE58, 0xFFFFA726, 0xFF8D6E63
    };

    private static final int[] SETTINGS_COLORS = {
        0xFF1E1E2E, 0xFF2A2A3C, 0xFF313244, 0xFF45475A,
        0xFFCDD6F4, 0xFFBAC2DE, 0xFFA6ADC8, 0xFF9399B2,
        0xFF89B4FA, 0xFF74C7EC, 0xFF94E2D5, 0xFFA6E3A1,
        0xFFF9E2AF, 0xFFFAB387, 0xFFF38BA8, 0xFFCBA6F7,
        0xFFE53935, 0xFFFF6F00, 0xFF1B5E20, 0xFF0D47A1,
        0xFFFFFFFF, 0xFF000000, 0xFF424242, 0xFF757575
    };

    private Button toggleBtn, prevDayBtn, nextDayBtn, settingsBtn;
    private TextView dateText, totalTimeText;
    private PieChartView pieChart;
    private LinearLayout historyContainer;

    private DatabaseHelper dbHelper;
    private OverlayPreferences prefs;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        prefs = new OverlayPreferences(this);
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        toggleBtn = findViewById(R.id.toggleBtn);
        prevDayBtn = findViewById(R.id.prevDayBtn);
        nextDayBtn = findViewById(R.id.nextDayBtn);
        settingsBtn = findViewById(R.id.settingsBtn);
        dateText = findViewById(R.id.dateText);
        totalTimeText = findViewById(R.id.totalTimeText);
        pieChart = findViewById(R.id.pieChart);
        historyContainer = findViewById(R.id.historyContainer);

        setupToggle();
        setupDateNav();
        settingsBtn.setOnClickListener(v -> showSettingsDialog());

        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleButton();
        loadData();
    }

    // ======== Overlay toggle ========

    private void setupToggle() {
        toggleBtn.setOnClickListener(v -> {
            if (OverlayService.isServiceRunning) {
                stopService(new Intent(this, OverlayService.class));
                toggleBtn.postDelayed(this::updateToggleButton, 300);
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())),
                        OVERLAY_PERM_CODE);
                    return;
                }
                startForegroundService(new Intent(this, OverlayService.class));
                toggleBtn.postDelayed(this::updateToggleButton, 300);
            }
        });
    }

    private void updateToggleButton() {
        toggleBtn.setText(OverlayService.isServiceRunning ?
            "Stop Overlay" : "Start Overlay");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_PERM_CODE && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, OverlayService.class));
            toggleBtn.postDelayed(this::updateToggleButton, 300);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERM_CODE);
            }
        }
    }

    // ======== Date navigation ========

    private void setupDateNav() {
        updateDateDisplay();
        prevDayBtn.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            updateDateDisplay();
            loadData();
        });
        nextDayBtn.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            updateDateDisplay();
            loadData();
        });
    }

    private void updateDateDisplay() {
        String current = dateFormat.format(calendar.getTime());
        String today = dateFormat.format(new Date());
        if (current.equals(today)) {
            dateText.setText("Today");
        } else {
            SimpleDateFormat display = new SimpleDateFormat("EEE, MMM d", Locale.US);
            dateText.setText(display.format(calendar.getTime()));
        }
    }

    // ======== Data loading ========

    private void loadData() {
        String date = dateFormat.format(calendar.getTime());
        List<ActivityEntry> entries = dbHelper.getEntriesByDate(date);

        pieChart.setEntries(entries);

        int totalSec = 0;
        for (ActivityEntry e : entries) totalSec += e.getDurationSeconds();
        if (totalSec > 0) {
            int h = totalSec / 3600;
            int m = (totalSec % 3600) / 60;
            totalTimeText.setText(h > 0 ?
                String.format(Locale.US, "Total: %dh %dm", h, m) :
                String.format(Locale.US, "Total: %dm", m));
        } else {
            totalTimeText.setText("");
        }

        historyContainer.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No activities recorded");
            empty.setTextColor(0xFF888899);
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            historyContainer.addView(empty);
        } else {
            for (ActivityEntry entry : entries) {
                addHistoryItem(entry);
            }
        }
    }

    private void addHistoryItem(ActivityEntry entry) {
        View item = getLayoutInflater().inflate(
            R.layout.item_activity_entry, historyContainer, false);

        View colorDot = item.findViewById(R.id.colorDot);
        TextView nameText = item.findViewById(R.id.entryName);
        TextView durationText = item.findViewById(R.id.entryDuration);
        Button colorBtn = item.findViewById(R.id.colorBtn);
        Button deleteBtn = item.findViewById(R.id.deleteBtn);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(entry.getColor());
        colorDot.setBackground(dotBg);

        nameText.setText(entry.getName());
        durationText.setText(entry.getFormattedDuration());

        colorBtn.setOnClickListener(v -> showEntryColorPicker(entry));
        deleteBtn.setOnClickListener(v -> {
            dbHelper.deleteEntry(entry.getId());
            loadData();
        });

        historyContainer.addView(item);
    }

    // ======== Entry color picker ========

    private void showEntryColorPicker(ActivityEntry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int)(16 * d);
        grid.setPadding(pad, pad, pad, pad);

        for (int color : ENTRY_COLORS) {
            View sw = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int)(56 * d);
            lp.height = (int)(56 * d);
            lp.setMargins((int)(6*d), (int)(6*d), (int)(6*d), (int)(6*d));
            sw.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(8 * d);
            bg.setColor(color);
            if (color == entry.getColor()) bg.setStroke((int)(3*d), 0xFFFFFFFF);
            sw.setBackground(bg);

            final int c = color;
            sw.setOnClickListener(v -> {
                // Update ALL entries with this name so colors stay consistent
                dbHelper.updateColorByName(entry.getName(), c);
                loadData();
                dialog.dismiss();
            });
            grid.addView(sw);
        }

        dialog.setView(grid);
        dialog.show();
    }

    // ======== Settings dialog ========

    private interface ColorCallback {
        void onColor(int color);
    }

    private void showSettingsDialog() {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*d), (int)(12*d), (int)(20*d), (int)(8*d));

        addColorRow(layout, "Background", prefs.getBgColor(), c -> prefs.setBgColor(c));
        addColorRow(layout, "Text Color", prefs.getTextColor(), c -> prefs.setTextColor(c));
        addColorRow(layout, "Accent", prefs.getAccentColor(), c -> prefs.setAccentColor(c));

        // Opacity
        addSpacer(layout, 14);
        TextView opLabel = new TextView(this);
        opLabel.setText("Background Opacity: " + (prefs.getOpacity() * 100 / 255) + "%");
        opLabel.setTextColor(0xFFCDD6F4);
        opLabel.setTextSize(15f);
        layout.addView(opLabel);

        SeekBar opBar = new SeekBar(this);
        opBar.setMax(255);
        opBar.setMin(50);
        opBar.setProgress(prefs.getOpacity());
        opBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int val, boolean u) {
                opLabel.setText("Background Opacity: " + (val * 100 / 255) + "%");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.setOpacity(sb.getProgress());
            }
        });
        layout.addView(opBar);

        // Size
        addSpacer(layout, 14);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(0xFFCDD6F4);
        sizeLabel.setTextSize(15f);
        layout.addView(sizeLabel);

        RadioGroup sizeGroup = new RadioGroup(this);
        sizeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String[] sizes = {"Small", "Medium", "Large"};
        for (int i = 0; i < sizes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(sizes[i]);
            rb.setTextColor(0xFFCDD6F4);
            rb.setId(i);
            sizeGroup.addView(rb);
        }
        sizeGroup.check(prefs.getSize());
        sizeGroup.setOnCheckedChangeListener((g, id) -> prefs.setSize(id));
        layout.addView(sizeGroup);

        addSpacer(layout, 14);
        TextView note = new TextView(this);
        note.setText("Restart overlay to apply changes");
        note.setTextColor(0xFF888899);
        note.setTextSize(12f);
        layout.addView(note);

        new AlertDialog.Builder(this)
            .setTitle("Overlay Settings")
            .setView(layout)
            .setPositiveButton("OK", null)
            .show();
    }

    private void addColorRow(LinearLayout parent, String label, int current,
                             ColorCallback callback) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(6*d), 0, (int)(6*d));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFCDD6F4);
        tv.setTextSize(15f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tv);

        View swatch = new View(this);
        int sz = (int)(40 * d);
        swatch.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        setSwatchColor(swatch, current, d);

        swatch.setOnClickListener(v ->
            showSettingsColorPicker(current, color -> {
                callback.onColor(color);
                setSwatchColor(swatch, color, d);
            })
        );

        row.addView(swatch);
        parent.addView(row);
    }

    private void setSwatchColor(View v, int color, float density) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(6 * density);
        bg.setColor(color);
        bg.setStroke((int)(1 * density), 0x44FFFFFF);
        v.setBackground(bg);
    }

    private void showSettingsColorPicker(int current, ColorCallback callback) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        float d = getResources().getDisplayMetrics().density;

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int pad = (int)(12 * d);
        grid.setPadding(pad, pad, pad, pad);

        for (int color : SETTINGS_COLORS) {
            View sw = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int)(52 * d);
            lp.height = (int)(52 * d);
            lp.setMargins((int)(5*d), (int)(5*d), (int)(5*d), (int)(5*d));
            sw.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(6 * d);
            bg.setColor(color);
            if (color == current) bg.setStroke((int)(3*d), 0xFFFFFFFF);
            sw.setBackground(bg);

            final int c = color;
            sw.setOnClickListener(v -> {
                callback.onColor(c);
                dialog.dismiss();
            });
            grid.addView(sw);
        }

        dialog.setView(grid);
        dialog.show();
    }

    private void addSpacer(LinearLayout parent, int dp) {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int)(dp * getResources().getDisplayMetrics().density)));
        parent.addView(s);
    }
}

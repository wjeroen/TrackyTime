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
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERM_CODE = 100;
    private static final int NOTIF_PERM_CODE = 101;
    private static final int EXPORT_FILE_CODE = 200;
    private static final int IMPORT_FILE_CODE = 201;

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
    private Button dayViewBtn, weekViewBtn, exportBtn, importBtn;
    private TextView dateText, totalTimeText;
    private PieChartView pieChart;
    private LinearLayout historyContainer;

    private DatabaseHelper dbHelper;
    private OverlayPreferences prefs;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private boolean isWeekView = false;

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
        dayViewBtn = findViewById(R.id.dayViewBtn);
        weekViewBtn = findViewById(R.id.weekViewBtn);
        exportBtn = findViewById(R.id.exportBtn);
        importBtn = findViewById(R.id.importBtn);
        dateText = findViewById(R.id.dateText);
        totalTimeText = findViewById(R.id.totalTimeText);
        pieChart = findViewById(R.id.pieChart);
        historyContainer = findViewById(R.id.historyContainer);

        setupToggle();
        setupDateNav();
        setupViewToggle();
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        exportBtn.setOnClickListener(v -> exportData());
        importBtn.setOnClickListener(v -> importData());

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
        if (req == EXPORT_FILE_CODE && res == RESULT_OK && data != null) {
            writeExportToUri(data.getData());
        }
        if (req == IMPORT_FILE_CODE && res == RESULT_OK && data != null) {
            readImportFromUri(data.getData());
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

    // ======== Day / Week toggle ========

    private void setupViewToggle() {
        updateViewToggleButtons();
        dayViewBtn.setOnClickListener(v -> {
            isWeekView = false;
            updateViewToggleButtons();
            updateDateDisplay();
            loadData();
        });
        weekViewBtn.setOnClickListener(v -> {
            isWeekView = true;
            updateViewToggleButtons();
            updateDateDisplay();
            loadData();
        });
    }

    private void updateViewToggleButtons() {
        dayViewBtn.setAlpha(isWeekView ? 0.5f : 1.0f);
        weekViewBtn.setAlpha(isWeekView ? 1.0f : 0.5f);
    }

    // ======== Date navigation ========

    private void setupDateNav() {
        updateDateDisplay();
        prevDayBtn.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, isWeekView ? -7 : -1);
            updateDateDisplay();
            loadData();
        });
        nextDayBtn.setOnClickListener(v -> {
            calendar.add(Calendar.DAY_OF_MONTH, isWeekView ? 7 : 1);
            updateDateDisplay();
            loadData();
        });
    }

    private void updateDateDisplay() {
        if (isWeekView) {
            String[] range = getWeekRange();
            SimpleDateFormat shortFmt = new SimpleDateFormat("MMM d", Locale.US);
            try {
                Date start = dateFormat.parse(range[0]);
                Date end = dateFormat.parse(range[1]);
                dateText.setText(shortFmt.format(start) + " — " + shortFmt.format(end));
            } catch (Exception e) {
                dateText.setText(range[0] + " — " + range[1]);
            }
        } else {
            String current = dateFormat.format(calendar.getTime());
            String today = dateFormat.format(new Date());
            if (current.equals(today)) {
                dateText.setText("Today");
            } else {
                SimpleDateFormat display = new SimpleDateFormat("EEE, MMM d", Locale.US);
                dateText.setText(display.format(calendar.getTime()));
            }
        }
    }

    /** Returns [startDate, endDate] for the week (Mon-Sun) containing the current calendar date. */
    private String[] getWeekRange() {
        Calendar cal = (Calendar) calendar.clone();
        // Go back to Monday
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        String start = dateFormat.format(cal.getTime());
        cal.add(Calendar.DAY_OF_MONTH, 6);
        String end = dateFormat.format(cal.getTime());
        return new String[]{start, end};
    }

    // ======== Data loading (with grouping) ========

    private void loadData() {
        List<ActivityEntry> rawEntries;
        if (isWeekView) {
            String[] range = getWeekRange();
            rawEntries = dbHelper.getEntriesByDateRange(range[0], range[1]);
        } else {
            String date = dateFormat.format(calendar.getTime());
            rawEntries = dbHelper.getEntriesByDate(date);
        }

        // Group by name (case-insensitive), sum durations
        List<ActivityEntry> grouped = groupEntries(rawEntries);

        pieChart.setEntries(grouped);

        int totalSec = 0;
        for (ActivityEntry e : grouped) totalSec += e.getDurationSeconds();
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
        if (grouped.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No activities recorded");
            empty.setTextColor(0xFF888899);
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            historyContainer.addView(empty);
        } else {
            for (ActivityEntry entry : grouped) {
                addHistoryItem(entry);
            }
        }
    }

    private List<ActivityEntry> groupEntries(List<ActivityEntry> raw) {
        Map<String, ActivityEntry> map = new LinkedHashMap<>();
        for (ActivityEntry e : raw) {
            String key = e.getName().toLowerCase(Locale.US);
            if (map.containsKey(key)) {
                ActivityEntry existing = map.get(key);
                existing.setDurationSeconds(
                    existing.getDurationSeconds() + e.getDurationSeconds());
            } else {
                ActivityEntry group = new ActivityEntry();
                group.setName(e.getName());
                group.setDurationSeconds(e.getDurationSeconds());
                group.setColor(e.getColor());
                group.setStartTime(e.getStartTime());
                group.setDate(e.getDate());
                map.put(key, group);
            }
        }
        return new ArrayList<>(map.values());
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
            new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete all \"" + entry.getName() + "\" entries" +
                    (isWeekView ? " this week?" : " today?"))
                .setPositiveButton("Delete", (d, w) -> {
                    if (isWeekView) {
                        String[] range = getWeekRange();
                        dbHelper.deleteEntriesByNameInRange(
                            entry.getName(), range[0], range[1]);
                    } else {
                        String date = dateFormat.format(calendar.getTime());
                        dbHelper.deleteEntriesByNameAndDate(entry.getName(), date);
                    }
                    loadData();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                dbHelper.updateColorByName(entry.getName(), c);
                loadData();
                dialog.dismiss();
            });
            grid.addView(sw);
        }

        dialog.setView(grid);
        dialog.show();
    }

    // ======== Export / Import ========

    private void exportData() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "trackytime_backup.json");
        startActivityForResult(intent, EXPORT_FILE_CODE);
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_FILE_CODE);
    }

    private void writeExportToUri(Uri uri) {
        try {
            List<ActivityEntry> entries = dbHelper.getAllEntries();
            JSONArray arr = new JSONArray();
            for (ActivityEntry e : entries) {
                JSONObject obj = new JSONObject();
                obj.put("name", e.getName());
                obj.put("duration_seconds", e.getDurationSeconds());
                obj.put("color", e.getColor());
                obj.put("start_time", e.getStartTime());
                obj.put("date", e.getDate());
                arr.put(obj);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("entries", arr);

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(root.toString(2).getBytes("UTF-8"));
                os.close();
            }
            Toast.makeText(this,
                "Exported " + entries.size() + " entries", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void readImportFromUri(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.getJSONArray("entries");
            List<ActivityEntry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ActivityEntry e = new ActivityEntry();
                e.setName(obj.getString("name"));
                e.setDurationSeconds(obj.getInt("duration_seconds"));
                e.setColor(obj.getInt("color"));
                e.setStartTime(obj.getLong("start_time"));
                e.setDate(obj.getString("date"));
                entries.add(e);
            }

            int count = dbHelper.importEntries(entries);
            Toast.makeText(this,
                "Imported " + count + " new entries" +
                (count < entries.size() ? " (" + (entries.size() - count) + " duplicates skipped)" : ""),
                Toast.LENGTH_SHORT).show();
            loadData();
        } catch (Exception e) {
            Toast.makeText(this,
                "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

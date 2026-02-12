package com.timetracker.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.CheckBox;
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
    private static final int EXPORT_FILE_CODE = 200;
    private static final int IMPORT_FILE_CODE = 201;

    private static final int[] ENTRY_COLORS = {
        // Row 1: Vivid / saturated
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
        0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
        0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
        // Row 2: Warm + earthy
        0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
        0xFF6D4C41, 0xFF757575, 0xFF546E7A, 0xFFEC407A,
        // Row 3: Pastel / lighter
        0xFFEF9A9A, 0xFFF48FB1, 0xFFCE93D8, 0xFFB39DDB,
        0xFF9FA8DA, 0xFF90CAF9, 0xFF81D4FA, 0xFF80DEEA,
        0xFF80CBC4, 0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C,
        // Row 4: Deep / dark
        0xFFB71C1C, 0xFF880E4F, 0xFF4A148C, 0xFF1A237E,
        0xFF0D47A1, 0xFF006064, 0xFF1B5E20, 0xFF33691E,
        0xFFE65100, 0xFF3E2723, 0xFF263238, 0xFF212121
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
    private ColorBarView colorBar;
    private LinearLayout historyContainer;

    private DatabaseHelper dbHelper;
    private OverlayPreferences prefs;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private boolean isWeekView = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        prefs = new OverlayPreferences(this);
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

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
        colorBar = findViewById(R.id.colorBar);
        historyContainer = findViewById(R.id.historyContainer);

        setupToggle();
        setupDateNav();
        setupViewToggle();
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        exportBtn.setOnClickListener(v -> exportData());
        importBtn.setOnClickListener(v -> importData());
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
        // Tap date text to jump back to today / this week
        dateText.setOnClickListener(v -> {
            calendar = Calendar.getInstance(); // reset to now
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

    // ======== Data loading ========

    private void loadData() {
        List<ActivityEntry> rawEntries;
        if (isWeekView) {
            String[] range = getWeekRange();
            rawEntries = dbHelper.getEntriesByDateRange(range[0], range[1]);
        } else {
            String date = dateFormat.format(calendar.getTime());
            rawEntries = dbHelper.getEntriesByDate(date);
        }

        // Pie chart uses grouped data (same name = one slice)
        List<ActivityEntry> grouped = groupEntries(rawEntries);
        pieChart.setEntries(grouped);
        colorBar.setEntries(grouped);

        int totalSec = 0;
        for (ActivityEntry e : rawEntries) totalSec += e.getDurationSeconds();
        if (totalSec > 0) {
            int h = totalSec / 3600;
            int m = (totalSec % 3600) / 60;
            totalTimeText.setText(h > 0 ?
                String.format(Locale.US, "Total: %dh %dm", h, m) :
                String.format(Locale.US, "Total: %dm", m));
        } else {
            totalTimeText.setText("");
        }

        // History shows individual entries (not grouped) — each session editable
        historyContainer.removeAllViews();

        // Show live activity at the top if overlay is running and we're viewing today
        boolean showLive = false;
        if (OverlayService.isServiceRunning && OverlayService.liveIsRunning
                && !OverlayService.liveActivityName.isEmpty()) {
            String today = dateFormat.format(new Date());
            String viewDate = dateFormat.format(calendar.getTime());
            if (!isWeekView && viewDate.equals(today)) {
                showLive = true;
            } else if (isWeekView) {
                String[] range = getWeekRange();
                if (today.compareTo(range[0]) >= 0 && today.compareTo(range[1]) <= 0) {
                    showLive = true;
                }
            }
        }
        if (showLive) {
            addLiveHistoryItem();
        }

        if (rawEntries.isEmpty() && !showLive) {
            TextView empty = new TextView(this);
            empty.setText("No activities recorded");
            empty.setTextColor(0xFF888899);
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            historyContainer.addView(empty);
        } else {
            for (ActivityEntry entry : rawEntries) {
                addHistoryItem(entry);
            }
        }
    }

    /** Group entries by name (case-insensitive), sum durations — used for pie chart. */
    private List<ActivityEntry> groupEntries(List<ActivityEntry> raw) {
        Map<String, ActivityEntry> map = new LinkedHashMap<>();
        for (ActivityEntry e : raw) {
            String key = ActivityEntry.normalizeName(e.getName());
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
        EditText nameEdit = (EditText) item.findViewById(R.id.entryName);
        TextView durationText = item.findViewById(R.id.entryDuration);
        Button colorBtn = item.findViewById(R.id.colorBtn);
        Button deleteBtn = item.findViewById(R.id.deleteBtn);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(entry.getColor());
        colorDot.setBackground(dotBg);

        nameEdit.setText(entry.getName());

        // Time range + duration: "10:00 – 11:00 · 1h 00m"
        String startStr = timeFormat.format(new Date(entry.getStartTime()));
        long endMs = entry.getStartTime() + (entry.getDurationSeconds() * 1000L);
        String endStr = timeFormat.format(new Date(endMs));
        durationText.setText(startStr + " – " + endStr + " · " + entry.getFormattedDuration());

        // Inline rename: tap name → becomes editable, press Done → saves
        nameEdit.setOnClickListener(v -> {
            nameEdit.setFocusable(true);
            nameEdit.setFocusableInTouchMode(true);
            nameEdit.setCursorVisible(true);
            nameEdit.requestFocus();
            nameEdit.setSelection(nameEdit.getText().length());
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(nameEdit, InputMethodManager.SHOW_IMPLICIT);
        });

        nameEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String newName = nameEdit.getText().toString().trim();
                if (!newName.isEmpty() && !ActivityEntry.normalizeName(newName).equals(
                        ActivityEntry.normalizeName(entry.getName()))) {
                    int color = dbHelper.getColorForName(newName);
                    dbHelper.updateEntryNameAndColor(entry.getId(), newName, color);
                    loadData();
                }
                // Disable editing
                nameEdit.setFocusable(false);
                nameEdit.setFocusableInTouchMode(false);
                nameEdit.setCursorVisible(false);
                nameEdit.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(nameEdit.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        // Tap duration to edit it
        durationText.setOnClickListener(v -> showDurationEditor(entry));

        // Color picker (updates all entries with same name)
        colorBtn.setOnClickListener(v -> showEntryColorPicker(entry));

        // Delete this individual entry
        deleteBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete this entry?")
                .setPositiveButton("Delete", (d, w) -> {
                    dbHelper.deleteEntry(entry.getId());
                    loadData();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        historyContainer.addView(item);
    }

    // ======== Live activity entry (currently recording) ========

    private void addLiveHistoryItem() {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding((int)(12*d), (int)(12*d), (int)(12*d), (int)(12*d));
        item.setBackgroundColor(0xFF33334A); // slightly different bg to stand out

        // Color dot
        View colorDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            (int)(20*d), (int)(20*d));
        dotParams.setMarginEnd((int)(12*d));
        colorDot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(OverlayService.liveActivityColor);
        colorDot.setBackground(dotBg);
        item.addView(colorDot);

        // Text column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Activity name
        TextView nameText = new TextView(this);
        nameText.setText(OverlayService.liveActivityName);
        nameText.setTextColor(0xFFCDD6F4);
        nameText.setTextSize(15f);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(nameText);

        // Time: "15:30 – now"
        String startStr = timeFormat.format(new Date(OverlayService.liveStartTime));
        long elapsed = (System.currentTimeMillis() - OverlayService.liveStartTime) / 1000;
        int eh = (int)(elapsed / 3600);
        int em = (int)((elapsed % 3600) / 60);
        String durStr = eh > 0 ?
            String.format(Locale.US, "%dh %02dm", eh, em) :
            String.format(Locale.US, "%dm", em);

        TextView durationText = new TextView(this);
        durationText.setText(startStr + " – now · " + durStr);
        durationText.setTextColor(0xFF43A047); // green to indicate live
        durationText.setTextSize(13f);
        textCol.addView(durationText);

        item.addView(textCol);

        // "LIVE" label
        TextView liveLabel = new TextView(this);
        liveLabel.setText("● REC");
        liveLabel.setTextColor(0xFFE53935); // red
        liveLabel.setTextSize(12f);
        liveLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        item.addView(liveLabel);

        // Bottom margin
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        itemParams.setMargins(0, 0, 0, (int)(4*d));
        item.setLayoutParams(itemParams);

        historyContainer.addView(item);
    }

    // ======== Duration editor ========

    private void showDurationEditor(ActivityEntry entry) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*d), (int)(16*d), (int)(20*d), (int)(8*d));

        // Show current duration
        int curH = entry.getDurationSeconds() / 3600;
        int curM = (entry.getDurationSeconds() % 3600) / 60;
        int curS = entry.getDurationSeconds() % 60;
        TextView currentLabel = new TextView(this);
        currentLabel.setText("Current: " + entry.getFormattedDuration());
        currentLabel.setTextColor(0xFFCDD6F4);
        currentLabel.setTextSize(14f);
        layout.addView(currentLabel);

        addSpacer(layout, 12);

        // Hours input
        LinearLayout hoursRow = new LinearLayout(this);
        hoursRow.setOrientation(LinearLayout.HORIZONTAL);
        hoursRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText hoursInput = new EditText(this);
        hoursInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hoursInput.setText(String.valueOf(curH));
        hoursInput.setTextColor(0xFFCDD6F4);
        hoursInput.setTextSize(16f);
        hoursInput.setMinWidth((int)(60*d));
        hoursInput.selectAll();
        TextView hoursLabel = new TextView(this);
        hoursLabel.setText(" hours");
        hoursLabel.setTextColor(0xFF9399B2);
        hoursLabel.setTextSize(14f);
        hoursRow.addView(hoursInput);
        hoursRow.addView(hoursLabel);
        layout.addView(hoursRow);

        // Minutes input
        LinearLayout minsRow = new LinearLayout(this);
        minsRow.setOrientation(LinearLayout.HORIZONTAL);
        minsRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText minsInput = new EditText(this);
        minsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minsInput.setText(String.valueOf(curM));
        minsInput.setTextColor(0xFFCDD6F4);
        minsInput.setTextSize(16f);
        minsInput.setMinWidth((int)(60*d));
        TextView minsLabel = new TextView(this);
        minsLabel.setText(" minutes");
        minsLabel.setTextColor(0xFF9399B2);
        minsLabel.setTextSize(14f);
        minsRow.addView(minsInput);
        minsRow.addView(minsLabel);
        layout.addView(minsRow);

        // Seconds input
        LinearLayout secsRow = new LinearLayout(this);
        secsRow.setOrientation(LinearLayout.HORIZONTAL);
        secsRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText secsInput = new EditText(this);
        secsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        secsInput.setText(String.valueOf(curS));
        secsInput.setTextColor(0xFFCDD6F4);
        secsInput.setTextSize(16f);
        secsInput.setMinWidth((int)(60*d));
        TextView secsLabel = new TextView(this);
        secsLabel.setText(" seconds");
        secsLabel.setTextColor(0xFF9399B2);
        secsLabel.setTextSize(14f);
        secsRow.addView(secsInput);
        secsRow.addView(secsLabel);
        layout.addView(secsRow);

        new AlertDialog.Builder(this)
            .setTitle("Edit Duration")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    int h = Integer.parseInt(hoursInput.getText().toString().trim());
                    int m = Integer.parseInt(minsInput.getText().toString().trim());
                    int s = Integer.parseInt(secsInput.getText().toString().trim());
                    int totalSec = h * 3600 + m * 60 + s;
                    if (totalSec > 0) {
                        dbHelper.updateEntryDuration(entry.getId(), totalSec);
                        loadData();
                    } else {
                        Toast.makeText(this, "Duration must be > 0", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ======== Entry color picker ========

    private void showEntryColorPicker(ActivityEntry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * d);
        grid.setPadding(pad, pad, pad, pad);

        for (int color : ENTRY_COLORS) {
            View sw = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int)(40 * d);
            lp.height = (int)(40 * d);
            lp.setMargins((int)(4*d), (int)(4*d), (int)(4*d), (int)(4*d));
            sw.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(6 * d);
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
            // Quick-select shortcuts (backward-compatible: older imports just ignore this)
            List<String> shortcuts = new OverlayPreferences(this).getQuickActivities();
            JSONArray shortcutsArr = new JSONArray();
            for (String s : shortcuts) shortcutsArr.put(s);

            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("entries", arr);
            root.put("quick_activities", shortcutsArr);

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

            // Restore quick-select shortcuts if present (backward-compatible)
            JSONArray shortcutsArr = root.optJSONArray("quick_activities");
            if (shortcutsArr != null && shortcutsArr.length() > 0) {
                List<String> shortcuts = new ArrayList<>();
                for (int i = 0; i < shortcutsArr.length(); i++) {
                    shortcuts.add(shortcutsArr.getString(i));
                }
                new OverlayPreferences(this).setQuickActivities(shortcuts);
            }

            int count = dbHelper.importEntries(entries);
            String shortcutMsg = (shortcutsArr != null && shortcutsArr.length() > 0)
                ? ", " + shortcutsArr.length() + " shortcuts" : "";
            Toast.makeText(this,
                "Imported " + count + " new entries" + shortcutMsg +
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
        addColorRow(layout, "Border Color", prefs.getAccentColor(), c -> prefs.setAccentColor(c));

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

        // Border Width
        addSpacer(layout, 14);
        TextView borderLabel = new TextView(this);
        int bw = prefs.getBorderWidth();
        borderLabel.setText(bw == 0 ? "Border: Off" : "Border: " + bw + "dp");
        borderLabel.setTextColor(0xFFCDD6F4);
        borderLabel.setTextSize(15f);
        layout.addView(borderLabel);

        SeekBar borderBar = new SeekBar(this);
        borderBar.setMax(6);
        borderBar.setProgress(prefs.getBorderWidth());
        borderBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int val, boolean u) {
                borderLabel.setText(val == 0 ? "Border: Off" : "Border: " + val + "dp");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.setBorderWidth(sb.getProgress());
            }
        });
        layout.addView(borderBar);

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

        // Overlay pulse toggle
        addSpacer(layout, 14);
        CheckBox pulseCb = new CheckBox(this);
        pulseCb.setText("Breathing overlay (bg + border pulse)");
        pulseCb.setTextColor(0xFFCDD6F4);
        pulseCb.setTextSize(14f);
        pulseCb.setChecked(prefs.isOverlayPulseEnabled());
        pulseCb.setOnCheckedChangeListener((btn, checked) -> prefs.setOverlayPulseEnabled(checked));
        layout.addView(pulseCb);

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

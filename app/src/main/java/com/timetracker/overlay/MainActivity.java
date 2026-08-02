package com.timetracker.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

    // 4x5 grid colors (mostly 600-level + black/white)
    private static final int[] GRID_COLORS = {
        0xFF6D4C41, 0xFF546E7A, 0xFF000000, 0xFFFFFFFF,
        0xFFE53935, 0xFFF4511E, 0xFFFB8C00, 0xFFFFB300,
        0xFFFDD835, 0xFFC0CA33, 0xFF7CB342, 0xFF43A047,
        0xFF00897B, 0xFF00ACC1, 0xFF039BE5, 0xFF1E88E5,
        0xFF3949AB, 0xFF5E35B1, 0xFF8E24AA, 0xFFD81B60
    };

    // Brightness variants: 100, 300, 600, 900 for each grid color
    private static int[] brightnessVariants(int gridColor) {
        switch (gridColor) {
            // Grey (also used for black and white)
            case 0xFF000000: case 0xFFFFFFFF:
                return new int[]{0xFFFFFFFF, 0xFFE0E0E0, 0xFF757575, 0xFF000000};
            case 0xFFE53935: return new int[]{0xFFFFCDD2, 0xFFE57373, 0xFFE53935, 0xFFB71C1C};
            case 0xFFF4511E: return new int[]{0xFFFFCCBC, 0xFFFF8A65, 0xFFF4511E, 0xFFBF360C};
            case 0xFFFB8C00: return new int[]{0xFFFFE0B2, 0xFFFFB74D, 0xFFFB8C00, 0xFFE65100};
            case 0xFFFFB300: return new int[]{0xFFFFECB3, 0xFFFFD54F, 0xFFFFB300, 0xFFFF6F00};
            case 0xFFFDD835: return new int[]{0xFFFFF9C4, 0xFFFFF176, 0xFFFDD835, 0xFFF57F17};
            case 0xFFC0CA33: return new int[]{0xFFF0F4C3, 0xFFDCE775, 0xFFC0CA33, 0xFF827717};
            case 0xFF7CB342: return new int[]{0xFFDCEDC8, 0xFFAED581, 0xFF7CB342, 0xFF33691E};
            case 0xFF43A047: return new int[]{0xFFC8E6C9, 0xFF81C784, 0xFF43A047, 0xFF1B5E20};
            case 0xFF00897B: return new int[]{0xFFB2DFDB, 0xFF4DB6AC, 0xFF00897B, 0xFF004D40};
            case 0xFF00ACC1: return new int[]{0xFFB2EBF2, 0xFF4DD0E1, 0xFF00ACC1, 0xFF006064};
            case 0xFF039BE5: return new int[]{0xFFB3E5FC, 0xFF4FC3F7, 0xFF039BE5, 0xFF01579B};
            case 0xFF1E88E5: return new int[]{0xFFBBDEFB, 0xFF64B5F6, 0xFF1E88E5, 0xFF0D47A1};
            case 0xFF3949AB: return new int[]{0xFFC5CAE9, 0xFF7986CB, 0xFF3949AB, 0xFF1A237E};
            case 0xFF5E35B1: return new int[]{0xFFD1C4E9, 0xFF9575CD, 0xFF5E35B1, 0xFF311B92};
            case 0xFF8E24AA: return new int[]{0xFFE1BEE7, 0xFFBA68C8, 0xFF8E24AA, 0xFF4A148C};
            case 0xFFD81B60: return new int[]{0xFFF8BBD0, 0xFFF06292, 0xFFD81B60, 0xFF880E4F};
            case 0xFF6D4C41: return new int[]{0xFFD7CCC8, 0xFFA1887F, 0xFF6D4C41, 0xFF3E2723};
            case 0xFF546E7A: return new int[]{0xFFCFD8DC, 0xFF90A4AE, 0xFF546E7A, 0xFF263238};
            default:         return new int[]{gridColor, gridColor, gridColor, gridColor};
        }
    }

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

    // Pending export options, set by the export dialog before the file picker opens.
    // startDate == null means all time. Dates are "yyyy-MM-dd" strings.
    private String pendingExportStart = null;
    private String pendingExportEnd = null;
    private boolean pendingExportIncludeSettings = true;

    // The running activity's slice keeps growing, so the graphs reload every
    // 5 minutes while the app is open and something is being tracked. Any
    // navigation (date tap, day/week toggle) still refreshes instantly.
    private static final long GRAPH_REFRESH_MS = 5 * 60 * 1000;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable graphRefresh = new Runnable() {
        @Override
        public void run() {
            if (OverlayService.isServiceRunning
                    && !OverlayService.liveActivityName.isEmpty()) {
                loadData();
            }
            refreshHandler.postDelayed(this, GRAPH_REFRESH_MS);
        }
    };

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
        refreshHandler.removeCallbacks(graphRefresh);
        refreshHandler.postDelayed(graphRefresh, GRAPH_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(graphRefresh);
    }

    // ======== Overlay toggle ========

    private void setupToggle() {
        toggleBtn.setOnClickListener(v -> {
            if (OverlayService.isOverlayVisible) {
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
                startOverlayService();
                toggleBtn.postDelayed(this::updateToggleButton, 300);
            }
        });
    }

    private void startOverlayService() {
        Intent svc = new Intent(this, OverlayService.class);
        svc.putExtra(OverlayService.EXTRA_SHOW_OVERLAY, true);
        startForegroundService(svc);
    }

    private void updateToggleButton() {
        toggleBtn.setText(OverlayService.isOverlayVisible ?
            "Stop Overlay" : "Start Overlay");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_PERM_CODE && Settings.canDrawOverlays(this)) {
            startOverlayService();
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
                dateText.setText(shortFmt.format(start) + " - " + shortFmt.format(end));
            } catch (Exception e) {
                dateText.setText(range[0] + " - " + range[1]);
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

        // Live activity (running or paused) belonging to the viewed day/week.
        // It gets a row at the top of the history, and once it has 10 tracked
        // seconds (the same threshold that decides whether it will be saved at
        // all) it also joins the graphs and the total, so the charts reflect
        // right now rather than only what is already stored.
        boolean showLive = false;
        ActivityEntry liveEntry = null;
        if (OverlayService.isServiceRunning
                && (OverlayService.liveIsRunning || OverlayService.livePaused)
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
            if (showLive) {
                long elapsed = OverlayService.livePaused
                    ? OverlayService.liveAccumulatedMs / 1000
                    : (System.currentTimeMillis() - OverlayService.liveVirtualStart) / 1000;
                if (elapsed >= OverlayService.MIN_ACTIVITY_SECONDS) {
                    liveEntry = new ActivityEntry();
                    liveEntry.setName(OverlayService.liveActivityName);
                    liveEntry.setDurationSeconds((int) elapsed);
                    liveEntry.setColor(OverlayService.liveActivityColor);
                    liveEntry.setStartTime(OverlayService.liveStartTime);
                    liveEntry.setDate(today);
                }
            }
        }

        // Pie chart uses grouped data (same name = one slice)
        List<ActivityEntry> counted = rawEntries;
        if (liveEntry != null) {
            counted = new ArrayList<>(rawEntries);
            counted.add(liveEntry);
        }
        List<ActivityEntry> grouped = groupEntries(counted);
        pieChart.setEntries(grouped);
        colorBar.setEntries(grouped);

        int totalSec = 0;
        for (ActivityEntry e : counted) totalSec += e.getDurationSeconds();
        if (totalSec > 0) {
            int h = totalSec / 3600;
            int m = (totalSec % 3600) / 60;
            totalTimeText.setText(h > 0 ?
                String.format(Locale.US, "Total: %dh %dm", h, m) :
                String.format(Locale.US, "Total: %dm", m));
        } else {
            totalTimeText.setText("");
        }

        // History shows individual entries (not grouped), each session editable
        historyContainer.removeAllViews();

        // Live activity row at the top (showLive computed above with the graphs)
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

    /** Group entries by name (case-insensitive), sum durations, used for pie chart. */
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

        // Time range + duration: "10:00 - 11:00 · 1h 00m"
        String startStr = timeFormat.format(new Date(entry.getStartTime()));
        long endMs = entry.getStartTime() + (entry.getDurationSeconds() * 1000L);
        String endStr = timeFormat.format(new Date(endMs));
        durationText.setText(startStr + " - " + endStr + " · " + entry.getFormattedDuration());

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

        // Time: "15:30 - now" while recording, "15:30 - paused" while paused.
        // Duration is tracked time (pauses excluded), matching what gets saved.
        boolean paused = OverlayService.livePaused;
        String startStr = timeFormat.format(new Date(OverlayService.liveStartTime));
        long elapsed = paused
            ? OverlayService.liveAccumulatedMs / 1000
            : (System.currentTimeMillis() - OverlayService.liveVirtualStart) / 1000;
        int eh = (int)(elapsed / 3600);
        int em = (int)((elapsed % 3600) / 60);
        String durStr = eh > 0 ?
            String.format(Locale.US, "%dh %02dm", eh, em) :
            String.format(Locale.US, "%dm", em);

        TextView durationText = new TextView(this);
        durationText.setText(startStr + (paused ? " - paused · " : " - now · ") + durStr);
        durationText.setTextColor(paused ? 0xFFFFA726 : 0xFF43A047); // amber when paused, green when live
        durationText.setTextSize(13f);
        textCol.addView(durationText);

        item.addView(textCol);

        // Status label: red REC dot while recording, amber pause bars while paused
        TextView liveLabel = new TextView(this);
        liveLabel.setText(paused ? "❚❚ PAUSED" : "● REC");
        liveLabel.setTextColor(paused ? 0xFFFFA726 : 0xFFE53935);
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

    // ======== Shared color picker with brightness row ========

    private interface ColorCallback { void onColor(int color); }

    private void showColorPickerDialog(int currentColor, ColorCallback callback) {
        showColorPickerDialog(currentColor, callback, null);
    }

    /**
     * @param previewCallback if non-null, called on every tap for live preview.
     *                        On cancel, called with originalColor to revert.
     */
    private void showColorPickerDialog(int currentColor, ColorCallback callback,
                                        ColorCallback previewCallback) {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * d);
        int swatchSize = (int)(52 * d);
        int swatchMargin = (int)(5 * d);
        // Fixed width: 4 swatches + margins
        int gridWidth = 4 * swatchSize + 8 * swatchMargin;

        // Track selected color (mutable via array)
        final int[] selected = { currentColor };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // --- Brightness row (top, 4 columns matching grid) ---
        LinearLayout brightnessRow = new LinearLayout(this);
        brightnessRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
            gridWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        brightnessRow.setLayoutParams(brLp);
        root.addView(brightnessRow);

        // --- Divider (matches grid width) ---
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            gridWidth, (int)(1 * d));
        divLp.setMargins(0, (int)(10 * d), 0, (int)(8 * d));
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(0x44FFFFFF);
        root.addView(divider);

        // --- 4x5 grid ---
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);

        // Find which grid color the current color belongs to
        int initialGrid = findGridColorFor(currentColor);

        for (int color : GRID_COLORS) {
            View sw = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = swatchSize;
            lp.height = swatchSize;
            lp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
            sw.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(6 * d);
            bg.setColor(color);
            // Show selection on the grid color whose brightness family contains the selected color
            if (color == initialGrid) bg.setStroke((int)(3*d), 0xFFFFFFFF);
            sw.setBackground(bg);

            final int c = color;
            sw.setOnClickListener(v -> {
                int[] variants = brightnessVariants(c);
                // Black/white exception: select the actual clicked color, not 600-level
                if (c == 0xFF000000) {
                    selected[0] = 0xFF000000; // select black (index 3)
                } else if (c == 0xFFFFFFFF) {
                    selected[0] = 0xFFFFFFFF; // select white (index 0)
                } else {
                    selected[0] = variants[2]; // 600-level default
                }
                if (previewCallback != null) previewCallback.onColor(selected[0]);
                refreshColorPickerSelection(grid, brightnessRow, c, selected, d,
                    swatchSize, swatchMargin, previewCallback);
            });
            grid.addView(sw);
        }

        root.addView(grid);

        // Populate initial brightness row
        populateBrightnessRow(brightnessRow, initialGrid, selected, d,
            swatchSize, swatchMargin, grid, previewCallback);

        // --- OK / Cancel buttons ---
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, (int)(8 * d), 0, 0);
        buttonRow.setLayoutParams(btnRowLp);

        Button cancelBtn = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        cancelBtn.setText("CANCEL");
        cancelBtn.setTextColor(0xFFBBBBBB);

        Button okBtn = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        okBtn.setText("OK");
        okBtn.setTextColor(0xFF8AB4F8);

        buttonRow.addView(cancelBtn);
        buttonRow.addView(okBtn);
        root.addView(buttonRow);

        // Plain Dialog, no AlertDialog minimum-width / internal-padding nonsense
        Dialog dialog = new Dialog(this);
        dialog.setContentView(root);

        // Track whether OK was pressed (to distinguish from dismiss-by-back-button)
        final boolean[] confirmed = { false };

        cancelBtn.setOnClickListener(v -> dialog.dismiss()); // dismiss triggers revert below
        okBtn.setOnClickListener(v -> {
            confirmed[0] = true;
            callback.onColor(selected[0]);
            dialog.dismiss();
        });

        // Revert on any dismiss that isn't OK (cancel button, back button, tap outside)
        if (previewCallback != null) {
            dialog.setOnDismissListener(d2 -> {
                if (!confirmed[0]) {
                    previewCallback.onColor(currentColor); // revert to original
                }
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(0xFF303030);
            dialogBg.setCornerRadius(16 * d);
            dialog.getWindow().setBackgroundDrawable(dialogBg);
            dialog.getWindow().setLayout(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Refresh both brightness row and grid selection indicators. */
    private void refreshColorPickerSelection(GridLayout grid, LinearLayout brightnessRow,
                                              int activeGridColor, int[] selected,
                                              float d, int swatchSize, int swatchMargin,
                                              ColorCallback previewCallback) {
        // Update grid swatches, highlight the active grid color family
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int color = GRID_COLORS[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(6 * d);
            bg.setColor(color);
            if (color == activeGridColor) bg.setStroke((int)(3*d), 0xFFFFFFFF);
            child.setBackground(bg);
        }

        populateBrightnessRow(brightnessRow, activeGridColor, selected, d,
            swatchSize, swatchMargin, grid, previewCallback);
    }

    private void populateBrightnessRow(LinearLayout row, int gridColor,
                                        int[] selected, float d,
                                        int swatchSize, int swatchMargin,
                                        GridLayout grid,
                                        ColorCallback previewCallback) {
        row.removeAllViews();
        int[] variants = brightnessVariants(gridColor);
        for (int color : variants) {
            View sw = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                swatchSize, swatchSize);
            lp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
            sw.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(6 * d);
            bg.setColor(color);
            if (color == selected[0]) bg.setStroke((int)(3*d), 0xFFFFFFFF);
            sw.setBackground(bg);

            final int c = color;
            sw.setOnClickListener(v -> {
                selected[0] = c;
                if (previewCallback != null) previewCallback.onColor(c);
                // Refresh brightness row selection
                for (int i = 0; i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    int varColor = variants[i];
                    GradientDrawable vbg = new GradientDrawable();
                    vbg.setShape(GradientDrawable.RECTANGLE);
                    vbg.setCornerRadius(6 * d);
                    vbg.setColor(varColor);
                    if (varColor == c) vbg.setStroke((int)(3*d), 0xFFFFFFFF);
                    child.setBackground(vbg);
                }
            });
            row.addView(sw);
        }
    }

    private int findGridColorFor(int color) {
        for (int gridColor : GRID_COLORS) {
            int[] variants = brightnessVariants(gridColor);
            for (int v : variants) {
                if (v == color) return gridColor;
            }
        }
        // Default to first grid color if not found
        return GRID_COLORS[0];
    }

    private void showEntryColorPicker(ActivityEntry entry) {
        showColorPickerDialog(entry.getColor(), color -> {
            dbHelper.updateColorByName(entry.getName(), color);
            loadData();
            // Signal overlay to reload segment colors immediately
            prefs.notifyTaskColorChanged();
        });
    }

    // ======== Export / Import ========

    private void exportData() {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*d), (int)(12*d), (int)(20*d), (int)(8*d));

        TextView rangeLabel = new TextView(this);
        rangeLabel.setText("Date Range");
        rangeLabel.setTextColor(0xFFCDD6F4);
        rangeLabel.setTextSize(15f);
        layout.addView(rangeLabel);

        // Same id-by-index pattern as the size RadioGroup in settings
        RadioGroup rangeGroup = new RadioGroup(this);
        String[] ranges = {"Today", "Past week", "All time", "Custom"};
        for (int i = 0; i < ranges.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(ranges[i]);
            rb.setTextColor(0xFFCDD6F4);
            rb.setId(i);
            rangeGroup.addView(rb);
        }
        rangeGroup.check(2); // All time, matches the old export behavior
        layout.addView(rangeGroup);

        addSpacer(layout, 8);
        CheckBox settingsCb = new CheckBox(this);
        settingsCb.setText("Include settings");
        settingsCb.setTextColor(0xFFCDD6F4);
        settingsCb.setTextSize(14f);
        settingsCb.setChecked(true); // old exports always included settings
        layout.addView(settingsCb);

        new AlertDialog.Builder(this)
            .setTitle("Export")
            .setView(layout)
            .setPositiveButton("Next", (dialog, which) -> {
                boolean includeSettings = settingsCb.isChecked();
                int sel = rangeGroup.getCheckedRadioButtonId();
                String today = dateFormat.format(new Date());
                if (sel == 0) {
                    // Today
                    launchExportFilePicker(today, today, includeSettings);
                } else if (sel == 1) {
                    // Past week: the last 7 days including today
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -6);
                    launchExportFilePicker(dateFormat.format(cal.getTime()), today, includeSettings);
                } else if (sel == 3) {
                    // Custom: pick start and end dates
                    showCustomRangePicker(includeSettings);
                } else {
                    // All time
                    launchExportFilePicker(null, null, includeSettings);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Custom range: two chained system date pickers (start, then end). */
    private void showCustomRangePicker(boolean includeSettings) {
        Calendar now = Calendar.getInstance();
        Toast.makeText(this, "Pick the START date", Toast.LENGTH_SHORT).show();
        DatePickerDialog startDlg = new DatePickerDialog(this, (view, y, m, day) -> {
            Calendar sc = Calendar.getInstance();
            sc.set(y, m, day);
            String start = dateFormat.format(sc.getTime());
            Toast.makeText(this, "Pick the END date", Toast.LENGTH_SHORT).show();
            DatePickerDialog endDlg = new DatePickerDialog(this, (view2, y2, m2, day2) -> {
                Calendar ec = Calendar.getInstance();
                ec.set(y2, m2, day2);
                String end = dateFormat.format(ec.getTime());
                // Swap silently if picked in reverse order
                if (end.compareTo(start) < 0) {
                    launchExportFilePicker(end, start, includeSettings);
                } else {
                    launchExportFilePicker(start, end, includeSettings);
                }
            }, y, m, day);
            endDlg.setTitle("End date");
            endDlg.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        startDlg.setTitle("Start date");
        startDlg.show();
    }

    /** Stores the chosen range, then opens the system file picker for the export file. */
    private void launchExportFilePicker(String startDate, String endDate, boolean includeSettings) {
        pendingExportStart = startDate;
        pendingExportEnd = endDate;
        pendingExportIncludeSettings = includeSettings;

        String fileName;
        if (startDate == null) {
            fileName = "trackytime_backup.json";
        } else if (startDate.equals(endDate)) {
            fileName = "trackytime_backup_" + startDate + ".json";
        } else {
            fileName = "trackytime_backup_" + startDate + "_to_" + endDate + ".json";
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
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
            // Range chosen in the export dialog (null start = all time)
            List<ActivityEntry> entries = (pendingExportStart == null)
                ? dbHelper.getAllEntries()
                : dbHelper.getEntriesByDateRange(pendingExportStart, pendingExportEnd);
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
            List<String> shortcuts = prefs.getQuickActivities();
            JSONArray shortcutsArr = new JSONArray();
            for (String s : shortcuts) shortcutsArr.put(s);

            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("entries", arr);
            root.put("quick_activities", shortcutsArr);

            // Customization preferences, only when the export dialog toggle is on
            // (backward-compatible: import treats a missing field as "no settings")
            if (pendingExportIncludeSettings) {
                root.put("preferences", prefs.exportToString());
            }

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
            // Read the whole file as raw bytes. The old line-by-line reader dropped
            // newlines, which only worked because our exports are pretty-printed.
            InputStream is = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            is.close();

            JSONObject root = new JSONObject(new String(buf.toByteArray(), "UTF-8"));
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

            // Restore customization preferences if present (backward-compatible)
            String prefsData = root.optString("preferences", null);
            if (prefsData != null && !prefsData.isEmpty()) {
                new OverlayPreferences(this).importFromString(prefsData);
            }

            int count = dbHelper.importEntries(entries);
            String shortcutMsg = (shortcutsArr != null && shortcutsArr.length() > 0)
                ? ", " + shortcutsArr.length() + " shortcuts" : "";
            String prefsMsg = (prefsData != null && !prefsData.isEmpty()) ? ", settings" : "";
            Toast.makeText(this,
                "Imported " + count + " new entries" + shortcutMsg + prefsMsg +
                (count < entries.size() ? " (" + (entries.size() - count) + " duplicates skipped)" : ""),
                Toast.LENGTH_SHORT).show();
            loadData();
        } catch (Exception e) {
            Toast.makeText(this,
                "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ======== Settings dialog ========

    private void showSettingsDialog() {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*d), (int)(12*d), (int)(20*d), (int)(8*d));

        // Background color mode: Custom vs Task Color
        TextView bgModeLabel = new TextView(this);
        bgModeLabel.setText("Background Color");
        bgModeLabel.setTextColor(0xFFCDD6F4);
        bgModeLabel.setTextSize(15f);
        layout.addView(bgModeLabel);

        RadioGroup bgModeGroup = new RadioGroup(this);
        bgModeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton customBgRb = new RadioButton(this);
        customBgRb.setText("Custom");
        customBgRb.setTextColor(0xFFCDD6F4);
        customBgRb.setId(View.generateViewId());
        RadioButton taskColorBgRb = new RadioButton(this);
        taskColorBgRb.setText("Task Color");
        taskColorBgRb.setTextColor(0xFFCDD6F4);
        taskColorBgRb.setId(View.generateViewId());
        bgModeGroup.addView(customBgRb);
        bgModeGroup.addView(taskColorBgRb);
        layout.addView(bgModeGroup);

        // Custom color picker (shown only in custom mode)
        LinearLayout customBgContainer = new LinearLayout(this);
        customBgContainer.setOrientation(LinearLayout.VERTICAL);
        addColorRow(customBgContainer, "Background", () -> prefs.getBgColor(), c -> prefs.setBgColor(c));
        layout.addView(customBgContainer);

        // Task color brightness slider (shown only in task color mode)
        LinearLayout taskColorContainer = new LinearLayout(this);
        taskColorContainer.setOrientation(LinearLayout.VERTICAL);
        taskColorContainer.setPadding(0, (int)(4*d), 0, 0);
        int initTaskBright = prefs.getTaskColorBrightness();
        TextView taskBrightLabel = new TextView(this);
        taskBrightLabel.setText("Brightness: " + (initTaskBright > 0 ? "+" : "") + initTaskBright + "%");
        taskBrightLabel.setTextColor(0xFFCDD6F4);
        taskBrightLabel.setTextSize(14f);
        taskColorContainer.addView(taskBrightLabel);

        addSlider(taskColorContainer, taskBrightLabel, -100, 100,
            prefs.getTaskColorBrightness(),
            val -> "Brightness: " + (val > 0 ? "+" : "") + val + "%",
            val -> prefs.setTaskColorBrightness(val));

        TextView taskBrightHint = new TextView(this);
        taskBrightHint.setText("Background uses current task's color");
        taskBrightHint.setTextColor(0xFF9399B2);
        taskBrightHint.setTextSize(12f);
        taskColorContainer.addView(taskBrightHint);
        layout.addView(taskColorContainer);

        // Set initial visibility based on current mode
        boolean useTaskColor = prefs.isUseTaskColorBg();
        if (useTaskColor) {
            bgModeGroup.check(taskColorBgRb.getId());
            customBgContainer.setVisibility(View.GONE);
            taskColorContainer.setVisibility(View.VISIBLE);
        } else {
            bgModeGroup.check(customBgRb.getId());
            customBgContainer.setVisibility(View.VISIBLE);
            taskColorContainer.setVisibility(View.GONE);
        }

        bgModeGroup.setOnCheckedChangeListener((g, id) -> {
            if (id == taskColorBgRb.getId()) {
                prefs.setUseTaskColorBg(true);
                customBgContainer.setVisibility(View.GONE);
                taskColorContainer.setVisibility(View.VISIBLE);
            } else {
                prefs.setUseTaskColorBg(false);
                customBgContainer.setVisibility(View.VISIBLE);
                taskColorContainer.setVisibility(View.GONE);
            }
        });

        addColorRow(layout, "Text Color", () -> prefs.getTextColor(), c -> prefs.setTextColor(c));
        addColorRow(layout, "Border Color", () -> prefs.getAccentColor(), c -> prefs.setAccentColor(c));

        // Opacity
        addSpacer(layout, 14);
        TextView opLabel = new TextView(this);
        opLabel.setText("Background Opacity: " + (prefs.getOpacity() * 100 / 255) + "%");
        opLabel.setTextColor(0xFFCDD6F4);
        opLabel.setTextSize(15f);
        layout.addView(opLabel);

        addSlider(layout, opLabel, 50, 255, prefs.getOpacity(),
            val -> "Background Opacity: " + (val * 100 / 255) + "%",
            val -> prefs.setOpacity(val));

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
        layout.addView(borderBar);

        // Border Opacity
        addSpacer(layout, 8);
        TextView borderOpLabel = new TextView(this);
        borderOpLabel.setText("Border Opacity: " + (prefs.getBorderOpacity() * 100 / 255) + "%");
        borderOpLabel.setTextColor(0xFFCDD6F4);
        borderOpLabel.setTextSize(14f);
        borderOpLabel.setVisibility(prefs.getBorderWidth() > 0 ? View.VISIBLE : View.GONE);
        layout.addView(borderOpLabel);

        SeekBar borderOpBar = addSlider(layout, borderOpLabel, 10, 255,
            prefs.getBorderOpacity(),
            val -> "Border Opacity: " + (val * 100 / 255) + "%",
            val -> prefs.setBorderOpacity(val));
        borderOpBar.setVisibility(prefs.getBorderWidth() > 0 ? View.VISIBLE : View.GONE);

        // Show/hide border opacity when border width changes
        borderBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int val, boolean u) {
                borderLabel.setText(val == 0 ? "Border: Off" : "Border: " + val + "dp");
                borderOpLabel.setVisibility(val > 0 ? View.VISIBLE : View.GONE);
                borderOpBar.setVisibility(val > 0 ? View.VISIBLE : View.GONE);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.setBorderWidth(sb.getProgress());
            }
        });

        // Default Task Color
        addSpacer(layout, 14);
        TextView defColorLabel = new TextView(this);
        defColorLabel.setText("Default Task Color");
        defColorLabel.setTextColor(0xFFCDD6F4);
        defColorLabel.setTextSize(15f);
        layout.addView(defColorLabel);

        LinearLayout defColorRow = new LinearLayout(this);
        defColorRow.setOrientation(LinearLayout.HORIZONTAL);
        defColorRow.setGravity(Gravity.CENTER_VERTICAL);
        defColorRow.setPadding(0, (int)(4*d), 0, (int)(4*d));

        View defSwatch = new View(this);
        int defSwatchSize = (int)(40 * d);
        defSwatch.setLayoutParams(new LinearLayout.LayoutParams(defSwatchSize, defSwatchSize));

        TextView defStatus = new TextView(this);
        defStatus.setTextColor(0xFF9399B2);
        defStatus.setTextSize(14f);
        defStatus.setPadding((int)(10*d), 0, 0, 0);
        defStatus.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button defResetBtn = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        defResetBtn.setText("RANDOM");
        defResetBtn.setTextColor(0xFF8AB4F8);
        defResetBtn.setTextSize(12f);

        int currentDefColor = prefs.getDefaultTaskColor();
        if (currentDefColor != 0) {
            setSwatchColor(defSwatch, currentDefColor, d);
            defStatus.setText("Fixed");
            defResetBtn.setVisibility(View.VISIBLE);
        } else {
            setSwatchColor(defSwatch, 0xFF757575, d);
            defStatus.setText("Random");
            defResetBtn.setVisibility(View.GONE);
        }

        defSwatch.setOnClickListener(v -> {
            int cur = prefs.getDefaultTaskColor();
            if (cur == 0) cur = 0xFF039BE5;
            showColorPickerDialog(cur, color -> {
                prefs.setDefaultTaskColor(color);
                setSwatchColor(defSwatch, color, d);
                defStatus.setText("Fixed");
                defResetBtn.setVisibility(View.VISIBLE);
            });
        });

        defResetBtn.setOnClickListener(v -> {
            prefs.setDefaultTaskColor(0);
            setSwatchColor(defSwatch, 0xFF757575, d);
            defStatus.setText("Random");
            defResetBtn.setVisibility(View.GONE);
        });

        defColorRow.addView(defSwatch);
        defColorRow.addView(defStatus);
        defColorRow.addView(defResetBtn);
        layout.addView(defColorRow);

        // Size
        addSpacer(layout, 14);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(0xFFCDD6F4);
        sizeLabel.setTextSize(15f);
        layout.addView(sizeLabel);

        RadioGroup sizeGroup = new RadioGroup(this);
        sizeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String[] sizes = {"Small", "Medium", "Large", "Extra Large"};
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
        layout.addView(pulseCb);

        // Breathing transparency slider (-50 to +50, 0 = center)
        addSpacer(layout, 8);
        int initTrans = prefs.getBreathingTransparency();
        TextView transLabel = new TextView(this);
        transLabel.setText("Transparency: " + (initTrans > 0 ? "+" : "") + initTrans + "%");
        transLabel.setTextColor(0xFFCDD6F4);
        transLabel.setTextSize(14f);
        transLabel.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);
        layout.addView(transLabel);

        SeekBar transBar = addSlider(layout, transLabel, -50, 50,
            prefs.getBreathingTransparency(),
            val -> "Transparency: " + (val > 0 ? "+" : "") + val + "%",
            val -> prefs.setBreathingTransparency(val));
        transBar.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);

        // Breathing brightness slider (-50 to +50, 0 = center)
        addSpacer(layout, 4);
        int initBright = prefs.getBreathingBrightness();
        TextView brightLabel = new TextView(this);
        brightLabel.setText("Brightness: " + (initBright > 0 ? "+" : "") + initBright + "%");
        brightLabel.setTextColor(0xFFCDD6F4);
        brightLabel.setTextSize(14f);
        brightLabel.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);
        layout.addView(brightLabel);

        SeekBar brightBar = addSlider(layout, brightLabel, -50, 50,
            prefs.getBreathingBrightness(),
            val -> "Brightness: " + (val > 0 ? "+" : "") + val + "%",
            val -> prefs.setBreathingBrightness(val));
        brightBar.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);

        // Breathing grayscale slider (0 to 100, default 0)
        addSpacer(layout, 4);
        int initGray = prefs.getBreathingGrayscale();
        TextView grayLabel = new TextView(this);
        grayLabel.setText("Grayscale: " + initGray + "%");
        grayLabel.setTextColor(0xFFCDD6F4);
        grayLabel.setTextSize(14f);
        grayLabel.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);
        layout.addView(grayLabel);

        SeekBar grayBar = addSlider(layout, grayLabel, 0, 100,
            prefs.getBreathingGrayscale(),
            val -> "Grayscale: " + val + "%",
            val -> prefs.setBreathingGrayscale(val));
        grayBar.setVisibility(prefs.isOverlayPulseEnabled() ? View.VISIBLE : View.GONE);

        // Toggle breathing checkbox shows/hides sub-sliders
        pulseCb.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setOverlayPulseEnabled(checked);
            int vis = checked ? View.VISIBLE : View.GONE;
            transLabel.setVisibility(vis);
            transBar.setVisibility(vis);
            brightLabel.setVisibility(vis);
            brightBar.setVisibility(vis);
            grayLabel.setVisibility(vis);
            grayBar.setVisibility(vis);
        });

        // Text stroke toggle
        CheckBox strokeCb = new CheckBox(this);
        strokeCb.setText("Text stroke/outline (TV subtitle style)");
        strokeCb.setTextColor(0xFFCDD6F4);
        strokeCb.setTextSize(14f);
        strokeCb.setChecked(prefs.isTextStrokeEnabled());
        layout.addView(strokeCb);

        // Stroke width slider (only visible when stroke is enabled)
        addSpacer(layout, 8);
        TextView strokeLabel = new TextView(this);
        int sw = prefs.getStrokeWidth();
        strokeLabel.setText("Stroke Width: " + sw + "px");
        strokeLabel.setTextColor(0xFFCDD6F4);
        strokeLabel.setTextSize(14f);
        strokeLabel.setVisibility(prefs.isTextStrokeEnabled() ? View.VISIBLE : View.GONE);
        layout.addView(strokeLabel);

        // Progress is 0-9 but the stored width is 1-10 (offset by 1)
        SeekBar strokeBar = addSlider(layout, strokeLabel, 0, 9,
            prefs.getStrokeWidth() - 1,
            val -> "Stroke Width: " + (val + 1) + "px",
            val -> prefs.setStrokeWidth(val + 1));
        strokeBar.setVisibility(prefs.isTextStrokeEnabled() ? View.VISIBLE : View.GONE);

        // Toggle stroke checkbox shows/hides stroke width slider
        strokeCb.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setTextStrokeEnabled(checked);
            strokeLabel.setVisibility(checked ? View.VISIBLE : View.GONE);
            strokeBar.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        // UI elements opacity (buttons, separator, hint text, paused clock)
        addSpacer(layout, 14);
        TextView uiOpLabel = new TextView(this);
        uiOpLabel.setText("UI Elements Opacity: " + (prefs.getUiElementsOpacity() * 100 / 255) + "%");
        uiOpLabel.setTextColor(0xFFCDD6F4);
        uiOpLabel.setTextSize(15f);
        layout.addView(uiOpLabel);

        TextView uiOpHint = new TextView(this);
        uiOpHint.setText("Buttons, separator, hints, paused clock");
        uiOpHint.setTextColor(0xFF9399B2);
        uiOpHint.setTextSize(12f);
        layout.addView(uiOpHint);

        addSlider(layout, uiOpLabel, 25, 255, prefs.getUiElementsOpacity(),
            val -> "UI Elements Opacity: " + (val * 100 / 255) + "%",
            val -> prefs.setUiElementsOpacity(val));

        // Immersive clock toggle
        addSpacer(layout, 14);
        CheckBox immersiveCb = new CheckBox(this);
        immersiveCb.setText("Display a clock while playing videos or gaming");
        immersiveCb.setTextColor(0xFFCDD6F4);
        immersiveCb.setTextSize(14f);
        immersiveCb.setChecked(prefs.isImmersiveClockEnabled());
        immersiveCb.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setImmersiveClockEnabled(checked);
            // Start the service in clock-only mode if not already running
            if (checked && !OverlayService.isServiceRunning && Settings.canDrawOverlays(this)) {
                startForegroundService(new Intent(this, OverlayService.class));
            }
        });
        layout.addView(immersiveCb);

        TextView immersiveHint = new TextView(this);
        immersiveHint.setText("Shows a grayscale clock in fullscreen/immersive mode");
        immersiveHint.setTextColor(0xFF9399B2);
        immersiveHint.setTextSize(12f);
        layout.addView(immersiveHint);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        new AlertDialog.Builder(this)
            .setTitle("Overlay Settings")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show();
    }

    private interface SliderFormat { String label(int value); }

    /**
     * Creates a settings SeekBar wired exactly like the hand-written ones it replaces:
     * label text updates live while dragging, the save action runs on release.
     * Note: the border width slider is NOT built with this because its listener
     * also shows/hides the border opacity rows, which are created after it.
     */
    private SeekBar addSlider(LinearLayout parent, TextView label, int min, int max,
                              int initial, SliderFormat fmt,
                              java.util.function.IntConsumer onSave) {
        SeekBar bar = new SeekBar(this);
        if (min != 0) bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(initial);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int val, boolean u) {
                label.setText(fmt.label(val));
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                onSave.accept(sb.getProgress());
            }
        });
        parent.addView(bar);
        return bar;
    }

    private void addColorRow(LinearLayout parent, String label,
                             java.util.function.IntSupplier colorGetter,
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
        setSwatchColor(swatch, colorGetter.getAsInt(), d);

        swatch.setOnClickListener(v -> {
            int current = colorGetter.getAsInt(); // read CURRENT pref value
            // Preview callback: writes to prefs immediately so overlay updates live.
            // On cancel, reverts to the color read at open time.
            ColorCallback preview = color -> {
                callback.onColor(color);
                setSwatchColor(swatch, color, d);
            };
            showColorPickerDialog(current, color -> {
                // OK: already previewed, just ensure final save
                callback.onColor(color);
                setSwatchColor(swatch, color, d);
            }, preview);
        });

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

    private void addSpacer(LinearLayout parent, int dp) {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int)(dp * getResources().getDisplayMetrics().density)));
        parent.addView(s);
    }
}

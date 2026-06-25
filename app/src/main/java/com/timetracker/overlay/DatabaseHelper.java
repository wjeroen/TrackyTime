package com.timetracker.overlay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "timetracker.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "activities";
    private Context ctx;

    public DatabaseHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
        this.ctx = ctx;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "duration_seconds INTEGER NOT NULL, " +
            "color INTEGER NOT NULL DEFAULT -10453622, " +
            "start_time INTEGER NOT NULL, " +
            "date TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long insertActivity(ActivityEntry entry) {
        if (entry.getDurationSeconds() <= 0) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", entry.getName());
        cv.put("duration_seconds", entry.getDurationSeconds());
        cv.put("color", entry.getColor());
        cv.put("start_time", entry.getStartTime());
        cv.put("date", entry.getDate());
        long id = db.insert(TABLE, null, cv);
        db.close();
        return id;
    }

    public List<ActivityEntry> getEntriesByDate(String date) {
        List<ActivityEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT * FROM " + TABLE + " WHERE date = ? ORDER BY start_time DESC",
            new String[]{date});
        while (c.moveToNext()) {
            list.add(cursorToEntry(c));
        }
        c.close();
        db.close();
        return list;
    }

    public List<ActivityEntry> getEntriesByDateRange(String startDate, String endDate) {
        List<ActivityEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT * FROM " + TABLE +
            " WHERE date >= ? AND date <= ? ORDER BY start_time DESC",
            new String[]{startDate, endDate});
        while (c.moveToNext()) {
            list.add(cursorToEntry(c));
        }
        c.close();
        db.close();
        return list;
    }

    public List<ActivityEntry> getAllEntries() {
        List<ActivityEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT * FROM " + TABLE + " ORDER BY start_time DESC", null);
        while (c.moveToNext()) {
            list.add(cursorToEntry(c));
        }
        c.close();
        db.close();
        return list;
    }

    /** Rename a single entry and update its color to match the new name. */
    public void updateEntryNameAndColor(long id, String name, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("color", color);
        db.update(TABLE, cv, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /** Update the duration of a single entry. */
    public void updateEntryDuration(long id, int durationSeconds) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("duration_seconds", durationSeconds);
        db.update(TABLE, cv, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateColor(long id, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("color", color);
        db.update(TABLE, cv, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteEntry(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteEntriesByNameAndDate(String name, String date) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, "LOWER(TRIM(name)) = LOWER(TRIM(?)) AND date = ?",
            new String[]{name, date});
        db.close();
    }

    public void deleteEntriesByNameInRange(String name, String startDate, String endDate) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE,
            "LOWER(TRIM(name)) = LOWER(TRIM(?)) AND date >= ? AND date <= ?",
            new String[]{name, startDate, endDate});
        db.close();
    }

    public int getColorForName(String name) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT color FROM " + TABLE +
            " WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) ORDER BY id DESC LIMIT 1",
            new String[]{name});
        int color = 0;
        boolean found = false;
        if (c.moveToFirst()) {
            color = c.getInt(0);
            found = true;
        }
        c.close();
        db.close();
        if (found) return color;

        // Check if user has a default task color set
        OverlayPreferences prefs = new OverlayPreferences(ctx);
        int defaultColor = prefs.getDefaultTaskColor();
        if (defaultColor != 0) return defaultColor;

        // Interleaved palette: each group of 4 is one hue at different brightness levels,
        // but hues are spread apart so adjacent picks look different
        int[] palette = {
            // Teal → Purple → Orange → Green → Blue → Pink → Yellow → Indigo
            0xFFB2DFDB, 0xFF4DB6AC, 0xFF00897B, 0xFF004D40,
            0xFFD1C4E9, 0xFF9575CD, 0xFF5E35B1, 0xFF311B92,
            0xFFFFCCBC, 0xFFFF8A65, 0xFFF4511E, 0xFFBF360C,
            0xFFDCEDC8, 0xFFAED581, 0xFF7CB342, 0xFF33691E,
            0xFFB3E5FC, 0xFF4FC3F7, 0xFF039BE5, 0xFF01579B,
            0xFFF8BBD0, 0xFFF06292, 0xFFD81B60, 0xFF880E4F,
            0xFFFFF9C4, 0xFFFFF176, 0xFFFDD835, 0xFFF57F17,
            0xFFC5CAE9, 0xFF7986CB, 0xFF3949AB, 0xFF1A237E,
            // Cyan → DeepPurple → Amber → LightGreen → DeepBlue → Red → Lime → Brown
            0xFFB2EBF2, 0xFF4DD0E1, 0xFF00ACC1, 0xFF006064,
            0xFFE1BEE7, 0xFFBA68C8, 0xFF8E24AA, 0xFF4A148C,
            0xFFFFECB3, 0xFFFFD54F, 0xFFFFB300, 0xFFFF6F00,
            0xFFC8E6C9, 0xFF81C784, 0xFF43A047, 0xFF1B5E20,
            0xFFBBDEFB, 0xFF64B5F6, 0xFF1E88E5, 0xFF0D47A1,
            0xFFFFCDD2, 0xFFE57373, 0xFFE53935, 0xFFB71C1C,
            0xFFF0F4C3, 0xFFDCE775, 0xFFC0CA33, 0xFF827717,
            0xFFD7CCC8, 0xFFA1887F, 0xFF6D4C41, 0xFF3E2723,
            // Orange(warm) → BlueGrey
            0xFFFFE0B2, 0xFFFFB74D, 0xFFFB8C00, 0xFFE65100,
            0xFFCFD8DC, 0xFF90A4AE, 0xFF546E7A, 0xFF263238
        };
        int hash = ActivityEntry.normalizeName(name).hashCode();
        return palette[Math.abs(hash) % palette.length];
    }

    public void updateColorByName(String name, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("color", color);
        db.update(TABLE, cv, "LOWER(TRIM(name)) = LOWER(TRIM(?))", new String[]{name});
        db.close();
    }

    /** Import entries, skipping duplicates by name+start_time. Returns count imported. */
    public int importEntries(List<ActivityEntry> entries) {
        int imported = 0;
        SQLiteDatabase db = getWritableDatabase();
        for (ActivityEntry entry : entries) {
            Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE +
                " WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND start_time = ?",
                new String[]{entry.getName(), String.valueOf(entry.getStartTime())});
            c.moveToFirst();
            boolean exists = c.getInt(0) > 0;
            c.close();
            if (!exists) {
                ContentValues cv = new ContentValues();
                cv.put("name", entry.getName());
                cv.put("duration_seconds", entry.getDurationSeconds());
                cv.put("color", entry.getColor());
                cv.put("start_time", entry.getStartTime());
                cv.put("date", entry.getDate());
                db.insert(TABLE, null, cv);
                imported++;
            }
        }
        db.close();
        return imported;
    }

    private ActivityEntry cursorToEntry(Cursor c) {
        ActivityEntry e = new ActivityEntry();
        e.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        e.setName(c.getString(c.getColumnIndexOrThrow("name")));
        e.setDurationSeconds(c.getInt(c.getColumnIndexOrThrow("duration_seconds")));
        e.setColor(c.getInt(c.getColumnIndexOrThrow("color")));
        e.setStartTime(c.getLong(c.getColumnIndexOrThrow("start_time")));
        e.setDate(c.getString(c.getColumnIndexOrThrow("date")));
        return e;
    }
}

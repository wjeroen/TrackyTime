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

    public DatabaseHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
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
        db.delete(TABLE, "LOWER(name) = LOWER(?) AND date = ?",
            new String[]{name, date});
        db.close();
    }

    public void deleteEntriesByNameInRange(String name, String startDate, String endDate) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE,
            "LOWER(name) = LOWER(?) AND date >= ? AND date <= ?",
            new String[]{name, startDate, endDate});
        db.close();
    }

    public int getColorForName(String name) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT color FROM " + TABLE +
            " WHERE LOWER(name) = LOWER(?) ORDER BY id DESC LIMIT 1",
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

        int[] palette = {
            0xFFE53935, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
            0xFF42A5F5, 0xFF26C6DA, 0xFF26A69A, 0xFF66BB6A,
            0xFFD4E157, 0xFFFFEE58, 0xFFFFA726, 0xFF8D6E63
        };
        int hash = name.toLowerCase(java.util.Locale.US).hashCode();
        return palette[Math.abs(hash) % palette.length];
    }

    public void updateColorByName(String name, int color) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("color", color);
        db.update(TABLE, cv, "LOWER(name) = LOWER(?)", new String[]{name});
        db.close();
    }

    /** Import entries, skipping duplicates by name+start_time. Returns count imported. */
    public int importEntries(List<ActivityEntry> entries) {
        int imported = 0;
        SQLiteDatabase db = getWritableDatabase();
        for (ActivityEntry entry : entries) {
            Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE +
                " WHERE name = ? AND start_time = ?",
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

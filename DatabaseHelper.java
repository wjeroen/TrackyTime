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
            ActivityEntry e = cursorToEntry(c);
            list.add(e);
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

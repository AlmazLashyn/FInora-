package com.finora.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Private SQLite store for calculation history and favorites. */
public final class ScenarioStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "finora_scenarios.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "scenarios";

    public static final class Scenario {
        public final long id;
        public final String kind;
        public final String currency;
        public final String inputs;
        public final String result;
        public final long createdAt;
        public final boolean favorite;
        public Scenario(long id, String kind, String currency, String inputs, String result, long createdAt, boolean favorite) {
            this.id = id; this.kind = kind; this.currency = currency;
            this.inputs = inputs; this.result = result; this.createdAt = createdAt; this.favorite = favorite;
        }
    }

    public ScenarioStore(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, currency TEXT NOT NULL, inputs TEXT NOT NULL, result TEXT NOT NULL, created_at INTEGER NOT NULL, favorite INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0");
    }

    public long save(String kind, String currency, String inputs, String result) {
        ContentValues values = new ContentValues();
        values.put("kind", kind); values.put("currency", currency); values.put("inputs", inputs);
        values.put("result", result); values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
    }

    public List<Scenario> all(boolean favoritesOnly) {
        List<Scenario> items = new ArrayList<>();
        String selection = favoritesOnly ? "favorite=1" : null;
        try (Cursor c = getReadableDatabase().query(TABLE, null, selection, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) items.add(new Scenario(c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("kind")), c.getString(c.getColumnIndexOrThrow("currency")),
                    c.getString(c.getColumnIndexOrThrow("inputs")), c.getString(c.getColumnIndexOrThrow("result")),
                    c.getLong(c.getColumnIndexOrThrow("created_at")), c.getInt(c.getColumnIndexOrThrow("favorite")) != 0));
        }
        return items;
    }

    public void setFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);
        getWritableDatabase().update(TABLE, values, "id=?", new String[]{String.valueOf(id)});
    }

    public void remove(long id) { getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)}); }
    public void clear() { getWritableDatabase().delete(TABLE, null, null); }
}

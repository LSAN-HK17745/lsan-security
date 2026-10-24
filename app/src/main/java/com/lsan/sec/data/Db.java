package com.lsan.sec.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** 本地事件数据库（纯 SQLite，无网络）。 */
public class Db extends SQLiteOpenHelper {

    private static final String NAME = "murphy_events.db";
    private static final int VERSION = 1;

    public static final String T_EVENTS = "events";

    private static Db INSTANCE;

    private Db(Context ctx) {
        super(ctx.getApplicationContext(), NAME, null, VERSION);
    }

    public static synchronized Db get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new Db(ctx);
        return INSTANCE;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_EVENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "type TEXT NOT NULL,"
                + "src TEXT,"
                + "dst TEXT,"
                + "summary TEXT,"
                + "detail TEXT,"
                + "verdict TEXT,"
                + "level TEXT)");
        db.execSQL("CREATE INDEX idx_events_ts ON " + T_EVENTS + "(ts DESC)");
        db.execSQL("CREATE INDEX idx_events_type ON " + T_EVENTS + "(type)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1 起无迁移
    }

    public long insertEvent(long ts, String type, String src, String dst,
                            String summary, String detail, String verdict, String level) {
        ContentValues cv = new ContentValues();
        cv.put("ts", ts);
        cv.put("type", type);
        cv.put("src", src);
        cv.put("dst", dst);
        cv.put("summary", summary);
        cv.put("detail", detail);
        cv.put("verdict", verdict);
        cv.put("level", level);
        try {
            return getWritableDatabase().insert(T_EVENTS, null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public Cursor queryEvents(String type, int limit, String keyword) {
        StringBuilder where = new StringBuilder();
        java.util.List<String> args = new java.util.ArrayList<>();
        if (type != null && !type.isEmpty()) {
            where.append("type=?");
            args.add(type);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("(summary LIKE ? OR detail LIKE ? OR src LIKE ? OR dst LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        String sel = where.length() == 0 ? null : where.toString();
        return getReadableDatabase().query(T_EVENTS, null, sel,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, "ts DESC", String.valueOf(limit));
    }

    public int countSince(long since, String type, String level) {
        StringBuilder where = new StringBuilder("ts>=?");
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(since));
        if (type != null) {
            where.append(" AND type=?");
            args.add(type);
        }
        if (level != null) {
            where.append(" AND level=?");
            args.add(level);
        }
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + T_EVENTS + " WHERE " + where,
                    args.toArray(new String[0]));
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public int countByTypeSince(long since, String type) {
        return countSince(since, type, null);
    }

    public int deleteOlderThan(long ts) {
        try {
            return getWritableDatabase().delete(T_EVENTS, "ts<?", new String[]{String.valueOf(ts)});
        } catch (Exception e) {
            return 0;
        }
    }

    public void deleteAll() {
        try {
            getWritableDatabase().delete(T_EVENTS, null, null);
        } catch (Exception ignored) {
        }
    }

    /** 导出为纯文本行（本地文件用）。 */
    public String exportText(int limit) {
        StringBuilder sb = new StringBuilder();
        Cursor c = queryEvents(null, limit, null);
        try {
            while (c != null && c.moveToNext()) {
                sb.append(c.getLong(c.getColumnIndexOrThrow("ts"))).append('\t')
                        .append(nz(c.getString(c.getColumnIndexOrThrow("type")))).append('\t')
                        .append(nz(c.getString(c.getColumnIndexOrThrow("src")))).append('\t')
                        .append(nz(c.getString(c.getColumnIndexOrThrow("dst")))).append('\t')
                        .append(nz(c.getString(c.getColumnIndexOrThrow("summary")))).append('\t')
                        .append(nz(c.getString(c.getColumnIndexOrThrow("verdict")))).append('\n');
            }
        } finally {
            if (c != null) c.close();
        }
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}

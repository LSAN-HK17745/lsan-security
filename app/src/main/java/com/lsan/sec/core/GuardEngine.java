package com.lsan.sec.core;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import com.lsan.sec.data.Db;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地防护引擎：事件汇聚、运行时状态、统计。纯本地，无任何网络调用。
 * 单例，无障碍服务 / 通知服务 / UI 共用。
 */
public class GuardEngine {

    private static final GuardEngine INSTANCE = new GuardEngine();

    public static GuardEngine get() { return INSTANCE; }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Context app;

    /** 当前前台应用包名。 */
    public volatile String foregroundPkg = "";
    /** 上一次用户主动交互（点击/滑动/输入）的时间戳。 */
    public volatile long lastInteractionTs = 0L;
    /** 上一次记录过的前台应用（用于判断跳转来源）。 */
    public volatile String lastForegroundPkg = "";
    /** 无障碍服务是否已连接。 */
    public volatile boolean accessibilityConnected = false;
    /** 通知监听是否已连接。 */
    public volatile boolean notificationConnected = false;
    /** 最近观察到的窗口切换（dev 调试用，最多 30 条）。 */
    public final java.util.LinkedList<String> recentWindows = new java.util.LinkedList<>();

    public void noteWindow(String from, String to) {
        synchronized (recentWindows) {
            recentWindows.addFirst(System.currentTimeMillis() + " " + from + " -> " + to);
            while (recentWindows.size() > 30) recentWindows.removeLast();
        }
    }

    /** 开屏跳过的调试轨迹（dev 诊断用）。 */
    public final java.util.LinkedList<String> skipTrace = new java.util.LinkedList<>();

    public void noteSkip(String msg) {
        synchronized (skipTrace) {
            skipTrace.addFirst(System.currentTimeMillis() + " " + msg);
            while (skipTrace.size() > 40) skipTrace.removeLast();
        }
    }

    /** 自检结果：签名/调试/Hook/模拟器是否全部通过。 */
    public volatile boolean integrityOk = true;
    /** 最近的阻止动作时间（防抖）。 */
    public volatile long lastBlockTs = 0L;

    public interface Listener {
        void onNewEvent(Event e);
        default void onStatsChanged() {}
    }

    public void init(Context ctx) {
        if (app == null) {
            app = ctx.getApplicationContext();
            Prefs.get().init(app);
            maintenance();
        }
    }

    public Context ctx() { return app; }

    public void register(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void unregister(Listener l) {
        listeners.remove(l);
    }

    /** 记录一条事件：写库 + 分发到 UI。 */
    public void record(Event e) {
        if (e == null || app == null) return;
        if (!Modules.isEnabled(app, Modules.EVENT)) {
            // 事件中心关闭时仍保留高危事件，避免防护动作不可追溯
            if (!"high".equals(e.level)) return;
        }
        e.id = Db.get(app).insertEvent(e.ts, e.type, e.src, e.dst,
                e.summary, e.detail, e.verdict, e.level);
        final Event fe = e;
        main.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onNewEvent(fe);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void notifyStats() {
        main.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onStatsChanged();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------------- 统计 ----------------

    public static class Stats {
        public int todayEvents;
        public int jumpBlocked;
        public int jumpTotal;
        public int sensitiveAlerts;
        public int collusionAlerts;
        public int blockedTotal;
    }

    public Stats stats() {
        Stats s = new Stats();
        if (app == null) return s;
        long todayStart = startOfToday();
        Db db = Db.get(app);
        s.todayEvents = db.countSince(todayStart, null, null);
        s.jumpTotal = db.countByTypeSince(todayStart, "jump");
        s.jumpBlocked = countVerdict(todayStart, "block");
        s.sensitiveAlerts = db.countByTypeSince(todayStart, "screen")
                + db.countByTypeSince(todayStart, "clip");
        s.collusionAlerts = db.countByTypeSince(todayStart, "collusion");
        s.blockedTotal = countVerdict(0, "block");
        return s;
    }

    private int countVerdict(long since, String verdict) {
        Cursor c = null;
        try {
            c = Db.get(app).getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM events WHERE ts>=? AND verdict=?",
                    new String[]{String.valueOf(since), verdict});
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public static long startOfToday() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public List<Event> recentEvents(int limit) {
        List<Event> out = new ArrayList<>();
        if (app == null) return out;
        Cursor c = Db.get(app).queryEvents(null, limit, null);
        try {
            while (c != null && c.moveToNext()) {
                Event e = new Event(
                        c.getString(c.getColumnIndexOrThrow("type")),
                        c.getString(c.getColumnIndexOrThrow("src")),
                        c.getString(c.getColumnIndexOrThrow("dst")),
                        c.getString(c.getColumnIndexOrThrow("summary")));
                e.id = c.getLong(c.getColumnIndexOrThrow("id"));
                e.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                e.detail = c.getString(c.getColumnIndexOrThrow("detail"));
                e.verdict = c.getString(c.getColumnIndexOrThrow("verdict"));
                e.level = c.getString(c.getColumnIndexOrThrow("level"));
                out.add(e);
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 按保留策略清理历史事件。 */
    public void maintenance() {
        if (app == null) return;
        try {
            int days = Prefs.get().retentionDays();
            long cutoff = System.currentTimeMillis() - days * 86_400_000L;
            Db.get(app).deleteOlderThan(cutoff);
        } catch (Exception ignored) {
        }
    }

    public void clearAllEvents() {
        if (app == null) return;
        Db.get(app).deleteAll();
        notifyStats();
    }
}

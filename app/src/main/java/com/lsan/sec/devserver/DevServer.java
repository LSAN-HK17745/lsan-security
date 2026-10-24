package com.lsan.sec.devserver;

import android.content.Context;

import com.lsan.sec.core.AppScanner;
import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.JumpKiller;
import com.lsan.sec.core.Modules;
import com.lsan.sec.data.Db;
import com.lsan.sec.data.Event;
import com.lsan.sec.plugin.PluginLoader;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Sensitive;
import com.lsan.sec.util.Text;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发版调试服务（**仅 dev 变体启用**，正式版不启动且无 INTERNET 权限）。
 *
 * 只绑定 127.0.0.1，通过 `adb forward tcp:18888 tcp:18888` 在电脑上访问，
 * 不对外暴露、不上传任何数据。
 *
 * 接口：
 *   GET  /ping                          健康检查
 *   GET  /state                         全量状态快照（主题/模块/档位/服务/统计）
 *   GET  /events?limit=50&type=jump      事件列表
 *   GET  /apps?limit=30                  应用画像（含风险分）
 *   POST /module?id=jump_guard&on=1      开关模块
 *   POST /theme?dark=1                   切换外观
 *   POST /policy?level=0|1|2             切换拦截档位
 *   POST /setting?key=strict_jump&value=0 修改策略参数
 *   POST /plugin/import?name=xxx.mpk     从私有 incoming 目录导入功能包
 *   POST /event/clear                    清空事件
 *   POST /probe?text=...                 敏感识别自测
 */
public final class DevServer {

    public static final int PORT = 18888;

    private static final DevServer INSTANCE = new DevServer();
    private ServerSocket server;
    private Thread thread;
    private Context app;

    private DevServer() {}

    public static DevServer get() { return INSTANCE; }

    public void start(Context ctx) {
        if (thread != null) return;
        app = ctx.getApplicationContext();
        GuardEngine.get().init(app);
        thread = new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
                while (!Thread.currentThread().isInterrupted()) {
                    Socket s = server.accept();
                    new Thread(() -> handle(s), "lsan-dev-req").start();
                }
            } catch (Exception ignored) {
            }
        }, "lsan-dev-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        thread = null;
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String requestLine = in.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";
            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            StringBuilder body = new StringBuilder();
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int n = in.read(buf, 0, contentLength);
                if (n > 0) body.append(buf, 0, n);
            }

            String rawPath = path;
            String query = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                query = path.substring(q + 1);
                rawPath = path.substring(0, q);
            }
            Map<String, String> params = parseQuery(query);

            String result;
            try {
                result = route(rawPath, method, params, body.toString());
            } catch (Exception e) {
                result = "{\"ok\":false,\"error\":\"" + e.getClass().getSimpleName() + ":"
                        + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
            }

            byte[] out = result.getBytes("UTF-8");
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream os = sock.getOutputStream();
            os.write(header.getBytes("UTF-8"));
            os.write(out);
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private String route(String path, String method, Map<String, String> p, String body) throws Exception {
        switch (path) {
            case "/ping":
                return "{\"ok\":true,\"app\":\"LSAN dev\",\"ts\":" + System.currentTimeMillis() + "}";
            case "/state":
                return state();
            case "/events":
                return events(Integer.parseInt(p.getOrDefault("limit", "50")),
                        p.get("type"));
            case "/skiptrace": {
                JSONArray arr = new JSONArray();
                synchronized (GuardEngine.get().skipTrace) {
                    for (String w : GuardEngine.get().skipTrace) arr.put(w);
                }
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("trace", arr);
                return o.toString();
            }
            case "/windows": {
                JSONArray arr = new JSONArray();
                synchronized (GuardEngine.get().recentWindows) {
                    for (String w : GuardEngine.get().recentWindows) arr.put(w);
                }
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("windows", arr);
                return o.toString();
            }
            case "/apps":
                return apps(Integer.parseInt(p.getOrDefault("limit", "30")));
            case "/module": {
                String id = p.get("id");
                boolean on = truthy(p.get("on"));
                Prefs.get().setModuleEnabled(id, on);
                GuardEngine.get().record(new Event("system", app.getPackageName(), "",
                        "[dev] 模块 " + Modules.label(app, id) + " → " + (on ? "开" : "关"))
                        .verdict("info").level("info"));
                return ok("module " + id + "=" + on);
            }
            case "/theme": {
                boolean dark = truthy(p.get("dark"));
                Prefs.get().setDarkTheme(dark);
                GuardEngine.get().record(new Event("system", app.getPackageName(), "",
                        "[dev] 外观 → " + (dark ? "黑" : "白")).verdict("info").level("info"));
                return ok("dark=" + dark);
            }
            case "/policy": {
                int lv = Integer.parseInt(p.getOrDefault("level", "1"));
                Prefs.get().setPolicyLevel(lv);
                GuardEngine.get().record(new Event("system", app.getPackageName(), "",
                        "[dev] 档位 → " + Prefs.policyName(lv)).verdict("info").level("info"));
                return ok("policy=" + lv + "(" + Prefs.policyName(lv) + ")");
            }
            case "/setting": {
                String k = p.get("key");
                String v = p.get("value");
                if (k == null) return err("key required");
                switch (k) {
                    case "strict_jump": Prefs.get().setStrictJump(truthy(v)); break;
                    case "block_with_back": Prefs.get().setBlockWithBack(truthy(v)); break;
                    case "silent_ms": Prefs.get().setSilentThresholdMs(Integer.parseInt(v)); break;
                    case "countdown": Prefs.get().setPromptCountdown(Integer.parseInt(v)); break;
                    case "retention": Prefs.get().setRetentionDays(Integer.parseInt(v)); break;
                    case "boot": Prefs.get().setBootStart(truthy(v)); break;
                    case "master": Prefs.get().setMasterEnabled(truthy(v)); break;
                    default: return err("unknown key " + k);
                }
                GuardEngine.get().record(new Event("system", app.getPackageName(), "",
                        "[dev] 设置 " + k + "=" + v).verdict("info").level("info"));
                return ok(k + "=" + v);
            }
            case "/plugin/import": {
                String name = p.get("name");
                File f = new File(PluginLoader.incomingDir(app), name);
                PluginLoader.ImportResult r = PluginLoader.importFromFile(app, f);
                return "{\"ok\":" + r.ok + ",\"msg\":\"" + r.message + "\",\"rules\":" + r.ruleCount + "}";
            }
            case "/event/clear":
                GuardEngine.get().clearAllEvents();
                return ok("cleared");
            case "/probe": {
                String text = p.getOrDefault("text", body);
                List<Sensitive.Hit> hits = Sensitive.scan(text, true, true, true, true, true, true, true);
                JSONArray arr = new JSONArray();
                for (Sensitive.Hit h : hits) {
                    JSONObject o = new JSONObject();
                    o.put("type", h.type);
                    o.put("masked", h.masked);
                    o.put("verified", h.verified);
                    arr.put(o);
                }
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("hits", arr);
                return o.toString();
            }
            default:
                return err("unknown path " + path);
        }
    }

    private String state() throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("theme", Prefs.get().darkTheme() ? "black" : "white");
        o.put("policyLevel", Prefs.get().policyLevel());
        o.put("policyName", Prefs.policyName(Prefs.get().policyLevel()));
        o.put("master", Prefs.get().masterEnabled());
        o.put("strictJump", Prefs.get().strictJump());
        o.put("blockWithBack", Prefs.get().blockWithBack());
        o.put("silentMs", Prefs.get().silentThresholdMs());
        o.put("countdown", Prefs.get().promptCountdown());
        o.put("retentionDays", Prefs.get().retentionDays());
        o.put("accessibility", GuardEngine.get().accessibilityConnected);
        o.put("notification", GuardEngine.get().notificationConnected);
        o.put("foreground", GuardEngine.get().foregroundPkg);
        o.put("integrityOk", GuardEngine.get().integrityOk);
        o.put("terminateCapability", JumpKiller.capabilityLabel(app));
        GuardEngine.Stats st = GuardEngine.get().stats();
        JSONObject s = new JSONObject();
        s.put("todayEvents", st.todayEvents);
        s.put("jumpBlocked", st.jumpBlocked);
        s.put("jumpTotal", st.jumpTotal);
        s.put("sensitiveAlerts", st.sensitiveAlerts);
        s.put("blockedTotal", st.blockedTotal);
        o.put("stats", s);

        JSONArray mods = new JSONArray();
        for (Modules.Module m : Modules.all(app)) {
            JSONObject mo = new JSONObject();
            mo.put("id", m.id);
            mo.put("name", m.name);
            mo.put("enabled", Prefs.get().moduleEnabled(m.id, m.defaultOn));
            mo.put("builtin", m.builtin);
            mo.put("category", m.category);
            mods.put(mo);
        }
        o.put("modules", mods);
        return o.toString();
    }

    private String events(int limit, String type) throws Exception {
        JSONArray arr = new JSONArray();
        android.database.Cursor c = Db.get(app).queryEvents(type, limit, null);
        try {
            while (c != null && c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("ts", c.getLong(c.getColumnIndexOrThrow("ts")));
                o.put("type", c.getString(c.getColumnIndexOrThrow("type")));
                o.put("src", c.getString(c.getColumnIndexOrThrow("src")));
                o.put("dst", c.getString(c.getColumnIndexOrThrow("dst")));
                o.put("summary", c.getString(c.getColumnIndexOrThrow("summary")));
                o.put("verdict", c.getString(c.getColumnIndexOrThrow("verdict")));
                o.put("level", c.getString(c.getColumnIndexOrThrow("level")));
                arr.put(o);
            }
        } finally {
            if (c != null) c.close();
        }
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("count", arr.length());
        out.put("events", arr);
        return out.toString();
    }

    private String apps(int limit) throws Exception {
        AppScanner.Result r = AppScanner.scan(app);
        JSONArray arr = new JSONArray();
        int n = 0;
        for (AppScanner.App a : r.apps) {
            if (n++ >= limit) break;
            JSONObject o = new JSONObject();
            o.put("pkg", a.pkg);
            o.put("label", a.label);
            o.put("score", a.score);
            o.put("accessibility", a.accessibilityEnabled);
            o.put("internet", a.hasInternet);
            o.put("sms", a.hasSmsRead);
            o.put("risks", new JSONArray(a.risks));
            arr.put(o);
        }
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("total", r.apps.size());
        out.put("accessibilityCount", r.accessibilityCount);
        out.put("highRisk", r.highRiskCount);
        out.put("apps", arr);
        return out.toString();
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            try {
                if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"),
                        URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
                else m.put(URLDecoder.decode(kv, "UTF-8"), "");
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    private static boolean truthy(String v) {
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on"));
    }

    private static String ok(String msg) throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("msg", msg);
        return o.toString();
    }

    private static String err(String msg) throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", false);
        o.put("error", msg);
        return o.toString();
    }
}

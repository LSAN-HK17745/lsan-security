package com.lsan.sec.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 本地配置存储（SharedPreferences）。所有数据留在本机。 */
public class Prefs {

    private static final String FILE = "murphy_sec";
    private static final Prefs INSTANCE = new Prefs();

    private SharedPreferences sp;

    private Prefs() {}

    public static Prefs get() { return INSTANCE; }

    public void init(Context ctx) {
        if (sp == null) {
            sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
    }

    private SharedPreferences p() {
        if (sp == null) throw new IllegalStateException("Prefs not initialized");
        return sp;
    }

    // ---------------- 外观 ----------------
    /** true = 终端黑，false = 极简白（默认白）。 */
    public boolean darkTheme() { return p().getBoolean("ui.dark", false); }

    public void setDarkTheme(boolean dark) { p().edit().putBoolean("ui.dark", dark).apply(); }

    /** 桌面图标已同步到的外观：-1 未初始化 / 0 白 / 1 黑。 */
    public int iconSyncedState() { return p().getInt("ui.icon_synced", -1); }

    public void setIconSyncedState(int state) { p().edit().putInt("ui.icon_synced", state).apply(); }

    // ---------------- 总开关 ----------------
    public boolean masterEnabled() { return p().getBoolean("master", true); }
    public void setMasterEnabled(boolean v) { p().edit().putBoolean("master", v).apply(); }

    // ---------------- 模块开关 ----------------
    public boolean moduleEnabled(String id, boolean def) {
        return p().getBoolean("module." + id, def);
    }

    public void setModuleEnabled(String id, boolean v) {
        p().edit().putBoolean("module." + id, v).apply();
    }

    // ---------------- 拦截档位 ----------------
    /** 0=宽松 1=中等 2=严格 */
    public int policyLevel() { return p().getInt("policy_level", 1); }

    public void setPolicyLevel(int level) {
        p().edit().putInt("policy_level", Math.max(0, Math.min(2, level))).apply();
    }

    public static String policyName(int level) {
        switch (level) {
            case 0: return "宽松";
            case 2: return "严格";
            default: return "中等";
        }
    }

    public static String policyDesc(int level) {
        switch (level) {
            case 0:
                return "只拦静默跳转（无任何操作的自动唤起）；你手动触发的跳转直接放行，拒绝时仅撤回界面。";
            case 2:
                return "所有跨应用唤起都要确认，包括你自己点击触发的；不记忆批准，拒绝即撤回并终止目标进程。";
            default:
                return "静默跳转必须确认；你手动触发的跳转首次确认后可记忆；拒绝时撤回并终止目标进程。";
        }
    }

    // ---------------- 策略参数 ----------------
    /** 严格模式：任何跨应用跳转都要求确认。 */
    public boolean strictJump() { return p().getBoolean("strict_jump", true); }
    public void setStrictJump(boolean v) { p().edit().putBoolean("strict_jump", v).apply(); }

    /** 静默跳转判定阈值（毫秒）：超过该时间无用户交互的窗口切换视为静默跳转。 */
    public int silentThresholdMs() { return p().getInt("silent_ms", 1500); }
    public void setSilentThresholdMs(int ms) { p().edit().putInt("silent_ms", ms).apply(); }

    /** 弹窗自动阻止倒计时（秒）。 */
    public int promptCountdown() { return p().getInt("prompt_countdown", 5); }
    public void setPromptCountdown(int s) { p().edit().putInt("prompt_countdown", Math.max(1, s)).apply(); }

    /** 阻止方式：true = 直接按返回键撤销跳转。 */
    public boolean blockWithBack() { return p().getBoolean("block_with_back", true); }
    public void setBlockWithBack(boolean v) { p().edit().putBoolean("block_with_back", v).apply(); }

    // ---------------- 敏感类型 ----------------
    public boolean sens(String type) { return p().getBoolean("sens." + type, true); }
    public void setSens(String type, boolean v) { p().edit().putBoolean("sens." + type, v).apply(); }

    // ---------------- 白名单 / 已批准 ----------------
    public Set<String> whitelist() {
        String raw = p().getString("whitelist", "");
        Set<String> s = new LinkedHashSet<>();
        if (!raw.isEmpty()) {
            for (String x : raw.split(",")) {
                if (!x.trim().isEmpty()) s.add(x.trim());
            }
        }
        return s;
    }

    public void addWhitelist(String pkg) {
        Set<String> s = whitelist();
        s.add(pkg);
        p().edit().putString("whitelist", join(s)).apply();
    }

    public void removeWhitelist(String pkg) {
        Set<String> s = whitelist();
        s.remove(pkg);
        p().edit().putString("whitelist", join(s)).apply();
    }

    public boolean isWhitelisted(String pkg) {
        return pkg != null && whitelist().contains(pkg);
    }

    /** 已永久批准的跳转：src>dst。 */
    public boolean jumpApproved(String src, String dst) {
        return p().getBoolean("jump." + src + ">" + dst, false);
    }

    public void approveJump(String src, String dst) {
        p().edit().putBoolean("jump." + src + ">" + dst, true).apply();
    }

    public void revokeJumpApprovals() {
        SharedPreferences.Editor e = p().edit();
        for (String k : p().getAll().keySet()) {
            if (k.startsWith("jump.")) e.remove(k);
        }
        e.apply();
    }

    public int jumpApprovalCount() {
        int n = 0;
        for (String k : p().getAll().keySet()) if (k.startsWith("jump.")) n++;
        return n;
    }

    // ---------------- 开屏跳过 ----------------
    /** 单应用"跳过"开关（记录被用户关闭跳过的应用）。 */
    public boolean isSkipDisabled(String pkg) {
        return p().getBoolean("skip.off." + pkg, false);
    }

    public void setSkipDisabled(String pkg, boolean disabled) {
        p().edit().putBoolean("skip.off." + pkg, disabled).apply();
    }

    /** 跳过尝试窗口（毫秒）。 */
    public int skipWindowMs() { return p().getInt("skip_window", 3000); }

    public void setSkipWindowMs(int ms) { p().edit().putInt("skip_window", Math.max(800, ms)).apply(); }

    // ---------------- 数据保留 ----------------
    public int retentionDays() { return p().getInt("retention_days", 14); }
    public void setRetentionDays(int d) { p().edit().putInt("retention_days", Math.max(1, d)).apply(); }

    public boolean notifyOnAlert() { return p().getBoolean("notify_alert", true); }
    public void setNotifyOnAlert(boolean v) { p().edit().putBoolean("notify_alert", v).apply(); }

    public boolean bootStart() { return p().getBoolean("boot_start", true); }
    public void setBootStart(boolean v) { p().edit().putBoolean("boot_start", v).apply(); }

    // ---------------- 插件 ----------------
    public String pluginsJson() { return p().getString("plugins", "[]"); }
    public void setPluginsJson(String json) { p().edit().putString("plugins", json).apply(); }

    public List<String> stringList(String key) {
        String raw = p().getString(key, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String x : raw.split("\u0001")) if (!x.isEmpty()) out.add(x);
        }
        return out;
    }

    public void setStringList(String key, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(list.get(i));
        }
        p().edit().putString(key, sb.toString()).apply();
    }

    private static String join(Set<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        return sb.toString();
    }

    public void wipeAll() {
        p().edit().clear().apply();
    }
}

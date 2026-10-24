package com.lsan.sec.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 厂商适配层。
 *
 * 不同 ROM 的差异集中在四处：
 *  1) 系统组件包名不同 → 跳转白名单必须按厂商给，否则疯狂误报或漏报；
 *  2) 自启动/后台查杀策略不同 → 需要把用户引导到对应开关，否则服务被清；
 *  3) "应用列表"权限在部分国产 ROM 上是独立开关 → 审计功能会只读到自己；
 *  4) 无障碍服务可能被"守护"类功能拦截 → 需要引导关闭。
 *
 * 本类按 Build.MANUFACTURER / BRAND / ROM 特征给出对应的包名与引导入口。
 */
public final class VendorAdapter {

    public static final String VIVO = "vivo";
    public static final String OPPO = "oppo";
    public static final String XIAOMI = "xiaomi";
    public static final String HUAWEI = "huawei";
    public static final String HONOR = "honor";
    public static final String SAMSUNG = "samsung";
    public static final String OTHER = "other";

    private VendorAdapter() {}

    /** 当前厂商标识。 */
    public static String vendor() {
        String brand = nz(Build.BRAND).toLowerCase(Locale.ROOT);
        String manu = nz(Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        if (brand.contains("vivo") || manu.contains("vivo") || brand.contains("iqoo")) return VIVO;
        if (brand.contains("oppo") || manu.contains("oppo") || brand.contains("realme")
                || brand.contains("oneplus") || manu.contains("oneplus")) return OPPO;
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) return XIAOMI;
        if (brand.contains("huawei") || manu.contains("huawei")) return HUAWEI;
        if (brand.contains("honor") || manu.contains("honor")) return HONOR;
        if (brand.contains("samsung")) return SAMSUNG;
        return OTHER;
    }

    public static String vendorLabel() {
        switch (vendor()) {
            case VIVO: return "vivo / iQOO（OriginOS · Funtouch）";
            case OPPO: return "OPPO / realme / 一加（ColorOS）";
            case XIAOMI: return "小米 / Redmi（HyperOS · MIUI）";
            case HUAWEI: return "华为（HarmonyOS · EMUI）";
            case HONOR: return "荣耀（MagicOS）";
            case SAMSUNG: return "三星（One UI）";
            default: return "类原生 Android";
        }
    }

    /** 设备摘要，用于"设备适配"卡片。 */
    public static String deviceSummary() {
        return Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE
                + "（API " + Build.VERSION.SDK_INT + "）"
                + " · " + Build.SUPPORTED_ABIS[0];
    }

    /** 系统/厂商组件包名集合：这些应用之间的跳转属于系统行为，不打扰用户。 */
    public static Set<String> systemPackages() {
        Set<String> s = new HashSet<>();
        // AOSP / 通用
        String[] common = {
                "android", "com.android.systemui", "com.android.settings",
                "com.android.packageinstaller", "com.google.android.packageinstaller",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.providers.settings", "com.android.documentsui",
                "com.google.android.documentsui", "com.android.intentresolver",
                "com.android.externalstorage", "com.android.incallui", "com.android.dialer",
                "com.android.phone", "com.android.bluetooth", "com.android.certinstaller",
                "com.android.keychain", "com.android.credentialmanager",
                "com.google.android.gms", "com.google.android.gsf",
                "com.android.vending", "com.android.capture",
        };
        java.util.Collections.addAll(s, common);

        switch (vendor()) {
            case VIVO:
                addPrefix(s, "com.vivo.", "com.bbk.", "com.iqoo.", "com.android.bbk",
                        "com.vivo.abe", "com.vivo.appfilter", "com.vivo.permissionmanager",
                        "com.vivo.smartmultiwindow", "com.vivo.gamewatch",
                        "com.vivo.assistant", "com.vivo.easyshare", "com.vivo.finance");
                break;
            case OPPO:
                addPrefix(s, "com.coloros.", "com.oplus.", "com.oppo.", "com.heytap.",
                        "com.realme.", "com.nearme.", "com.oneplus.");
                break;
            case XIAOMI:
                addPrefix(s, "com.miui.", "com.xiaomi.", "com.mi.", "com.android.thememanager");
                break;
            case HUAWEI:
                addPrefix(s, "com.huawei.", "com.hihonor.", "com.huawei.android.");
                break;
            case HONOR:
                addPrefix(s, "com.hihonor.", "com.honor.");
                break;
            case SAMSUNG:
                addPrefix(s, "com.samsung.", "com.sec.android.");
                break;
            default:
                break;
        }
        return s;
    }

    private static void addPrefix(Set<String> s, String... prefixes) {
        // 前缀形式由调用方按 startsWith 匹配，这里只登记精确前缀标记
        for (String p : prefixes) {
            if (p.endsWith(".")) s.add(p + "*");
            else s.add(p);
        }
    }

    /** 判断是否系统/厂商组件（支持 "前缀*" 通配）。 */
    public static boolean isSystemPackage(Set<String> rules, String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        for (String r : rules) {
            if (r.endsWith("*")) {
                if (pkg.startsWith(r.substring(0, r.length() - 1))) return true;
            } else if (r.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** 引导项：标题 / 说明 / 跳转 Intent（可能为空）。 */
    public static class Guide {
        public final String title;
        public final String desc;
        public final Intent intent;

        Guide(String title, String desc, Intent intent) {
            this.title = title;
            this.desc = desc;
            this.intent = intent;
        }
    }

    /** 本机型需要的"保活与权限"引导清单。 */
    public static List<Guide> guides(Context ctx) {
        List<Guide> out = new ArrayList<>();
        String v = vendor();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
            if (!ignoring) {
                Intent i = null;
                try {
                    i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + ctx.getPackageName()));
                } catch (Exception ignored) {
                }
                out.add(new Guide("电池优化白名单",
                        "允许后台常驻，避免防护引擎被系统休眠清理", i));
            }
        }

        switch (v) {
            case VIVO:
                out.add(new Guide("自启动（i管家）",
                        "i管家 → 应用管理 → 自启动 → 允许 LSAN 自启动", vivoIntent("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")));
                out.add(new Guide("后台高耗电",
                        "设置 → 电池 → 后台高耗电 → 允许 LSAN", null));
                out.add(new Guide("应用列表权限",
                        "设置 → 权限管理 → 权限 → 应用列表 → LSAN → 允许", appDetails(ctx)));
                break;
            case OPPO:
                out.add(new Guide("自启动",
                        "设置 → 应用管理 → 自启动管理 → 允许 LSAN", oppoStartupIntent()));
                out.add(new Guide("耗电保护",
                        "设置 → 电池 → 应用耗电管理 → LSAN → 允许完全后台行为", appDetails(ctx)));
                break;
            case XIAOMI:
                out.add(new Guide("自启动",
                        "安全中心 → 授权管理 → 自启动管理 → 允许 LSAN", miuiIntent()));
                out.add(new Guide("省电策略",
                        "设置 → 应用设置 → LSAN → 省电策略 → 无限制", appDetails(ctx)));
                out.add(new Guide("后台弹出界面",
                        "设置 → 应用设置 → LSAN → 权限管理 → 后台弹出界面 → 允许", appDetails(ctx)));
                break;
            case HUAWEI:
            case HONOR:
                out.add(new Guide("应用启动管理",
                        "设置 → 应用 → 应用启动管理 → LSAN → 手动管理（全部允许）", appDetails(ctx)));
                out.add(new Guide("忽略电池优化", "设置 → 电池 → 更多电池设置 → 忽略优化 → LSAN", null));
                break;
            default:
                out.add(new Guide("应用详情", "检查权限与电池设置", appDetails(ctx)));
                break;
        }

        out.add(new Guide("无障碍服务",
                "设置 → 无障碍 → 已下载的应用 → LSAN 信息防护 → 开启"
                        + ("vivo".equals(v) ? "（若被\"无障碍守护\"拦截，请先关闭守护）" : ""),
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        out.add(new Guide("通知使用权",
                "设置 → 通知 → 通知使用权 → LSAN 信息防护 → 开启",
                new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        return out;
    }

    private static Intent appDetails(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + ctx.getPackageName()));
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static Intent vivoIntent(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, cls));
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static Intent oppoStartupIntent() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static Intent miuiIntent() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    /** 该机型是否把"应用列表"作为独立权限（需要额外引导）。 */
    public static boolean hasAppListPermissionGate() {
        String v = vendor();
        return VIVO.equals(v) || OPPO.equals(v) || XIAOMI.equals(v);
    }

    /** 是否有能力打开某个 Intent（避免跳转失败）。 */
    public static boolean canResolve(Context ctx, Intent i) {
        if (i == null) return false;
        try {
            return ctx.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

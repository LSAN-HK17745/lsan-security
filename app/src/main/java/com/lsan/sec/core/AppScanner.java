package com.lsan.sec.core;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.view.accessibility.AccessibilityManager;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应用行为扫描器：读取本机应用清单、权限、签名、无障碍/通知监听声明，
 * 并计算本地风险分。全部离线完成，不访问任何服务器。
 */
public final class AppScanner {

    public static final String P_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE";
    public static final String P_INTERNET = "android.permission.INTERNET";
    public static final String P_READ_SMS = "android.permission.READ_SMS";
    public static final String P_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    public static final String P_SEND_SMS = "android.permission.SEND_SMS";
    public static final String P_READ_CONTACTS = "android.permission.READ_CONTACTS";
    public static final String P_READ_CALL_LOG = "android.permission.READ_CALL_LOG";
    public static final String P_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    public static final String P_CAMERA = "android.permission.CAMERA";
    public static final String P_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    public static final String P_STORAGE_NEW = "android.permission.READ_MEDIA_IMAGES";
    public static final String P_QUERY_ALL = "android.permission.QUERY_ALL_PACKAGES";
    public static final String P_OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW";

    private AppScanner() {}

    /** 一个应用的本地画像。 */
    public static class App {
        public String pkg = "";
        public String label = "";
        public int uid;
        public boolean system;
        public boolean updatedSystem;
        public String sig = "";
        public String installer = "";
        public long firstInstall;
        public long lastUpdate;

        public boolean hasInternet;
        public boolean hasSmsRead;
        public boolean hasSmsSend;
        public boolean hasContacts;
        public boolean hasCallLog;
        public boolean hasAudio;
        public boolean hasCamera;
        public boolean hasStorage;
        public boolean hasQueryAll;
        public boolean hasOverlay;
        public boolean declaresAccessibility;
        public boolean declaresNotifListener;
        public boolean accessibilityEnabled;

        public int score;
        public List<String> risks = new ArrayList<>();
        public Set<String> perms = new HashSet<>();

        public boolean canRead() {
            return accessRead();
        }

        private boolean accessRead() {
            return accessibilityEnabled || declaresAccessibility
                    || hasSmsRead || hasContacts || hasCallLog || hasAudio || hasCamera;
        }

        public boolean canSend() {
            return hasInternet || hasSmsSend;
        }
    }

    /** 联合风险：两个及以上应用构成的数据通道。 */
    public static class Collusion {
        public String kind;       // signature / accessibility / listener / field
        public String title;
        public String detail;
        public List<String> pkgs = new ArrayList<>();
        public String level = "warn"; // warn / high
    }

    public static class Result {
        public List<App> apps = new ArrayList<>();
        public List<Collusion> collusions = new ArrayList<>();
        public int accessibilityCount;
        public int notifCount;
        public int internetCount;
        public int highRiskCount;
        public long scannedAt;
    }

    public static Result scan(Context ctx) {
        Result r = new Result();
        r.scannedAt = System.currentTimeMillis();
        PackageManager pm = ctx.getPackageManager();

        Set<String> accPkgs = new HashSet<>();
        Set<String> notifPkgs = new HashSet<>();
        try {
            Intent acc = new Intent("android.accessibilityservice.AccessibilityService");
            for (ResolveInfo ri : pm.queryIntentServices(acc, 0)) {
                ServiceInfo si = ri.serviceInfo;
                if (si != null && si.packageName != null) accPkgs.add(si.packageName);
            }
        } catch (Exception ignored) {
        }
        try {
            Intent nl = new Intent("android.service.notification.NotificationListenerService");
            for (ResolveInfo ri : pm.queryIntentServices(nl, 0)) {
                ServiceInfo si = ri.serviceInfo;
                if (si != null && si.packageName != null) notifPkgs.add(si.packageName);
            }
        } catch (Exception ignored) {
        }

        Set<String> enabledAcc = enabledAccessibilityPackages(ctx);

        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_SIGNATURES | PackageManager.GET_META_DATA);
        } catch (Exception e) {
            packages = new ArrayList<>();
        }
        // 某些定制系统会限制"读取应用列表"（即使声明了 QUERY_ALL_PACKAGES），
        // 此时退化为按启动器入口 + 无障碍/通知服务声明来枚举用户可见应用。
        if (packages.size() < 5) {
            Set<String> fallbackPkgs = new HashSet<>();
            try {
                Intent launcher = new Intent(Intent.ACTION_MAIN);
                launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        fallbackPkgs.add(ri.activityInfo.packageName);
                    }
                }
            } catch (Exception ignored) {
            }
            fallbackPkgs.addAll(accPkgs);
            fallbackPkgs.addAll(notifPkgs);
            List<PackageInfo> alt = new ArrayList<>();
            for (String pk : fallbackPkgs) {
                try {
                    alt.add(pm.getPackageInfo(pk, PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_SIGNATURES | PackageManager.GET_META_DATA));
                } catch (Exception ignored) {
                }
            }
            if (alt.size() > packages.size()) packages = alt;
        }
        final String selfPkg = ctx.getPackageName();

        for (PackageInfo pi : packages) {
            if (pi == null || pi.packageName == null) continue;
            if (pi.packageName.equals(selfPkg)) continue;   // 不把自身计入审计对象
            App a = new App();
            a.pkg = pi.packageName;
            try {
                ApplicationInfo ai = pi.applicationInfo;
                if (ai != null) {
                    a.label = String.valueOf(pm.getApplicationLabel(ai));
                    a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    a.updatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    a.uid = ai.uid;
                }
            } catch (Exception ignored) {
            }
            if (a.label == null || a.label.isEmpty()) a.label = a.pkg;

            a.firstInstall = pi.firstInstallTime;
            a.lastUpdate = pi.lastUpdateTime;
            a.sig = signatureOf(pi);
            a.installer = installerOf(pm, a.pkg);

            if (pi.requestedPermissions != null) {
                Collections.addAll(a.perms, pi.requestedPermissions);
            }
            a.hasInternet = a.perms.contains(P_INTERNET);
            a.hasSmsRead = a.perms.contains(P_READ_SMS) || a.perms.contains(P_RECEIVE_SMS);
            a.hasSmsSend = a.perms.contains(P_SEND_SMS);
            a.hasContacts = a.perms.contains(P_READ_CONTACTS);
            a.hasCallLog = a.perms.contains(P_READ_CALL_LOG);
            a.hasAudio = a.perms.contains(P_RECORD_AUDIO);
            a.hasCamera = a.perms.contains(P_CAMERA);
            a.hasStorage = a.perms.contains(P_STORAGE) || a.perms.contains(P_STORAGE_NEW);
            a.hasQueryAll = a.perms.contains(P_QUERY_ALL);
            a.hasOverlay = a.perms.contains(P_OVERLAY);
            a.declaresAccessibility = accPkgs.contains(a.pkg) || a.perms.contains(P_ACCESSIBILITY);
            a.declaresNotifListener = notifPkgs.contains(a.pkg);
            a.accessibilityEnabled = enabledAcc.contains(a.pkg);

            score(a);
            r.apps.add(a);
        }

        // 统计
        for (App a : r.apps) {
            if (a.declaresAccessibility) r.accessibilityCount++;
            if (a.declaresNotifListener) r.notifCount++;
            if (a.hasInternet) r.internetCount++;
            if (a.score >= 60) r.highRiskCount++;
        }

        r.collusions = analyzeCollusion(r.apps);
        r.apps.sort((x, y) -> Integer.compare(y.score, x.score));
        return r;
    }

    /** 本地风险评分（0-100）。 */
    private static void score(App a) {
        int s = 0;
        List<String> risks = a.risks;

        if (a.accessibilityEnabled) {
            s += 30;
            risks.add("无障碍服务已启用（可读取全屏内容）");
        } else if (a.declaresAccessibility) {
            s += 10;
            risks.add("声明了无障碍服务（一旦被开启即可读屏）");
        }
        if (a.hasSmsRead) {
            s += 12;
            risks.add("可读取短信（含验证码）");
        }
        if (a.hasContacts) {
            s += 8;
            risks.add("可读取通讯录");
        }
        if (a.hasCallLog) {
            s += 6;
            risks.add("可读取通话记录");
        }
        if (a.hasAudio) {
            s += 8;
            risks.add("可录音");
        }
        if (a.hasCamera) {
            s += 5;
            risks.add("可使用摄像头");
        }
        if (a.hasStorage) {
            s += 4;
            risks.add("可读取存储文件");
        }
        if (a.hasQueryAll) {
            s += 3;
            risks.add("可枚举本机全部应用");
        }
        if (a.declaresNotifListener) {
            s += 10;
            risks.add("可监听通知栏（含验证码通知）");
        }
        if (a.hasOverlay) {
            s += 4;
            risks.add("可悬浮覆盖界面");
        }
        if (a.hasInternet) {
            s += a.canRead() ? 12 : 5;
            risks.add("具备联网外发能力");
        }
        if (a.hasSmsSend) {
            s += 6;
            risks.add("可发送短信");
        }
        if (a.system) {
            s = (int) Math.round(s * 0.6);
        }
        if (s > 100) s = 100;
        a.score = s;
    }

/** 联合逃逸分析：找出"能读 + 能传"的组合。 */
    private static List<Collusion> analyzeCollusion(List<App> apps) {
        List<Collusion> out = new ArrayList<>();

        // 1) 同签名组内的"读 + 传"合谋
        Map<String, List<App>> bySig = new HashMap<>();
        for (App a : apps) {
            if (a.sig == null || a.sig.isEmpty() || a.system) continue;
            bySig.computeIfAbsent(a.sig, k -> new ArrayList<>()).add(a);
        }
        for (Map.Entry<String, List<App>> e : bySig.entrySet()) {
            List<App> group = e.getValue();
            if (group.size() < 2) continue;
            List<App> readers = new ArrayList<>();
            List<App> senders = new ArrayList<>();
            for (App a : group) {
                if (a.canRead()) readers.add(a);
                if (a.hasInternet || a.hasSmsSend) senders.add(a);
            }
            if (!readers.isEmpty() && !senders.isEmpty()) {
                Collusion c = new Collusion();
                c.kind = "signature";
                c.title = "同签名应用的读写合谋风险";
                c.level = "high";
                StringBuilder sb = new StringBuilder("同一签名的 " + group.size() + " 个应用共享信任关系：");
                sb.append("读取方 ");
                for (App a : readers) sb.append(a.label).append(" ");
                sb.append("；外发方 ");
                for (App a : senders) sb.append(a.label).append(" ");
                c.detail = sb.toString();
                for (App a : group) c.pkgs.add(a.pkg);
                out.add(c);
            }
        }

        // 2) 无障碍已启用 + 联网 = 可直接读屏外传
        for (App a : apps) {
            if (a.accessibilityEnabled && a.hasInternet) {
                Collusion c = new Collusion();
                c.kind = "accessibility";
                c.title = "读屏服务与联网同体：" + a.label;
                c.level = "high";
                c.detail = a.label + "（" + a.pkg + "）已启用无障碍服务且具备联网能力，"
                        + "可在读取屏幕内容后直接外发，属于单应用即完成的数据窃取链。";
                c.pkgs.add(a.pkg);
                out.add(c);
            }
        }

        // 3) 已启用无障碍与已启用通知监听并存
        List<App> accOn = new ArrayList<>();
        List<App> notifOn = new ArrayList<>();
        for (App a : apps) {
            if (a.accessibilityEnabled) accOn.add(a);
            if (a.declaresNotifListener) notifOn.add(a);
        }
        if (!accOn.isEmpty() && !notifOn.isEmpty()) {
            Collusion c = new Collusion();
            c.kind = "listener";
            c.title = "读屏 + 通知监听 双通道并存";
            c.level = "warn";
            StringBuilder sb = new StringBuilder("当前存在读屏通道与通知监听通道，可组合还原短信验证码与界面操作：");
            sb.append("读屏 ");
            for (App a : accOn) sb.append(a.label).append(" ");
            sb.append("；通知 ");
            for (App a : notifOn) sb.append(a.label).append(" ");
            c.detail = sb.toString();
            for (App a : accOn) c.pkgs.add(a.pkg);
            for (App a : notifOn) c.pkgs.add(a.pkg);
            out.add(c);
        }

        return out;
    }

    private static Set<String> enabledAccessibilityPackages(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            AccessibilityManager am =
                    (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<AccessibilityServiceInfo> list =
                        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                for (AccessibilityServiceInfo info : list) {
                    if (info != null && info.getResolveInfo() != null
                            && info.getResolveInfo().serviceInfo != null) {
                        out.add(info.getResolveInfo().serviceInfo.packageName);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String signatureOf(PackageInfo pi) {
        try {
            if (pi.signatures == null || pi.signatures.length == 0) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(pi.signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, d.length); i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String installerOf(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                String s = pm.getInstallSourceInfo(pkg).getInstallingPackageName();
                return s == null ? "" : s;
            }
            String s = pm.getInstallerPackageName(pkg);
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    public static String installerLabel(String installer) {
        if (installer == null || installer.isEmpty()) return "未知来源";
        switch (installer) {
            case "com.android.vending": return "应用商店";
            case "com.google.android.packageinstaller": return "系统安装器";
            case "com.android.packageinstaller": return "系统安装器";
            case "com.coloros.packageinstaller": return "系统安装器";
            case "com.oplus.packageinstaller": return "系统安装器";
            case "com.tencent.mm": return "微信传输";
            case "com.android.shell": return "ADB 安装";
            default: return installer;
        }
    }
}

package com.lsan.sec.core;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 跳转终止器：把"被跳转到的目标应用"真正终止，而不只是撤回到上一层。
 *
 * 能力分三档（按可用性从高到低）：
 *  1) root / su 可用 → am force-stop（最强，连前台进程一起结束）
 *  2) 有 KILL_BACKGROUND_PROCESSES 权限 → killBackgroundProcesses（公开 API，杀后台进程）
 *  3) 以上都不可用 → 仅记录"已撤回"，由无障碍 BACK 完成界面层撤回
 *
 * 结果会写进事件详情，界面上显示"已撤回 / 已终止进程"，避免用户以为只是退回。
 */
public final class JumpKiller {

    public static class Result {
        public String method = "back";   // force-stop / kill-background / back
        public boolean terminated;        // 是否真的执行了进程终止
        public String detail = "";

        public String label() {
            if ("force-stop".equals(method)) return "已强制终止";
            if ("kill-background".equals(method)) return "已终止后台进程";
            return "已撤回界面";
        }
    }

    private JumpKiller() {}

    /** 终止目标应用。调用前通常已执行 BACK 撤回界面。 */
    public static Result terminate(Context ctx, String pkg) {
        Result r = new Result();
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) {
            return r;
        }

        // 1) root / su
        if (hasSu()) {
            if (runSu("am force-stop " + pkg)) {
                r.method = "force-stop";
                r.terminated = true;
                r.detail = "通过 su 执行 am force-stop " + pkg;
                return r;
            }
        }

        // 2) 公开 API：多次杀后台进程（部分 ROM 首次调用会被保活服务拉回）
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                int rounds = 0;
                for (int i = 0; i < 3; i++) {
                    am.killBackgroundProcesses(pkg);
                    rounds++;
                    try {
                        Thread.sleep(180);
                    } catch (InterruptedException ignored) {
                    }
                }
                r.method = "kill-background";
                r.terminated = true;
                r.detail = "连续 " + rounds + " 次 killBackgroundProcesses(" + pkg + ")"
                        + "（界面已通过 BACK 撤回；非 root 无法强制停止前台进程，"
                        + "root / Shizuku 环境下会自动改用 force-stop）";
                return r;
            }
        } catch (SecurityException se) {
            r.detail = "缺少 KILL_BACKGROUND_PROCESSES 权限，仅撤回界面";
        } catch (Exception e) {
            r.detail = "终止失败：" + e.getClass().getSimpleName() + "，仅撤回界面";
        }

        r.method = "back";
        r.detail = "无可用终止能力（未 root），仅撤回界面";
        return r;
    }

    private static Boolean suAvailable;

    /** 是否存在可用的 su。缓存一次，避免频繁 fork。 */
    public static boolean hasSu() {
        if (suAvailable != null) return suAvailable;
        boolean ok = false;
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/su/bin/su", "/vendor/bin/su", "/system/sbin/su"};
        for (String p : paths) {
            try {
                if (new java.io.File(p).exists()) {
                    ok = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (!ok) {
            try {
                Process pr = Runtime.getRuntime().exec(new String[]{"which", "su"});
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(pr.getInputStream()))) {
                    ok = br.readLine() != null;
                }
                pr.destroy();
            } catch (Exception ignored) {
            }
        }
        suAvailable = ok;
        return ok;
    }

    private static boolean runSu(String cmd) {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int code = pr.waitFor();
            pr.destroy();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 当前设备的终止能力说明（展示在设置页）。 */
    public static String capabilityLabel(Context ctx) {
        if (hasSu()) return "强制终止（root 可用）";
        return "终止后台进程（非 root 上限）";
    }

    public static String sdkTag() {
        return "API " + Build.VERSION.SDK_INT;
    }
}

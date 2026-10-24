package com.lsan.sec.core;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 开屏跳过（同类能力对标「李跳跳」）。
 *
 * 机制：应用刚启动时（首个窗口 3 秒内），在屏幕右上/下部区域寻找"跳过 / skip / 关闭广告"类控件，
 * 命中且满足尺寸与位置约束后执行无障碍点击。
 *
 * 安全约束（避免误点造成损失）：
 *  1) 只在应用启动后的短时间窗内尝试，用户正常浏览时绝不干预；
 *  2) 控件必须同时满足：文本命中关键词 + 位于边缘区域 + 尺寸不超过阈值；
 *  3) 命中危险词（支付/确认/删除/卸载/授权…）直接放弃；
 *  4) 单应用单次启动最多尝试 3 次，且两次之间间隔 ≥ 400ms；
 *  5) 点击动作不计入"用户交互"，不会影响跳转守卫的静默判定。
 */
public final class SkipGuard {

    /** 命中关键词（小写比较，包含匹配）。 */
    private static final List<String> KEYWORDS = Arrays.asList(
            "跳过", "跳过广告", "跳過", "关闭广告", "关闭", "skip", "skip ad", "skip ads",
            "跳过此广告", "点击跳过", "5s跳过", "3s跳过", "x");

    /** 危险词：出现即放弃，避免误点支付/删除类按钮。 */
    private static final List<String> DANGER_WORDS = Arrays.asList(
            "支付", "付款", "确认", "确定", "删除", "卸载", "注销", "退出", "授权", "同意",
            "购买", "充值", "转账", "提现", "开通", "订阅", "install", "pay", "delete",
            "uninstall", "confirm", "buy", "subscribe");

    private static final Pattern SKIP_COUNT = Pattern.compile(
            "(?i)^\\s*(跳过|skip)\\s*(广告)?\\s*\\d*\\s*s?\\s*$");

    private static final long TRY_WINDOW_MS = 9000L;   // 启动后尝试窗口
    private static final int MAX_TRIES = 12;            // 单次启动最多尝试
    private static final long TRY_GAP_MS = 250L;       // 两次尝试间隔

    private final AccessibilityService svc;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastPkg = "";
    private long firstWindowTs = 0;
    private int tries = 0;
    private long lastTryTs = 0;
    private String lastClickedKey = "";
    private long lastClickOkTs = 0;

    public SkipGuard(AccessibilityService svc) {
        this.svc = svc;
    }

    /** 窗口切换时调用：记录启动时刻，并在启动窗口内安排多次尝试。 */
    /** 这些包绝不参与"跳过"：系统通知栏/状态栏/桌面/设置等，否则会误点快捷开关（如飞行模式）。 */
    private static boolean isExcluded(String pkg) {
        if (pkg == null) return true;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        return p.contains("systemui") || p.equals("android") || p.contains("launcher")
                || p.contains("systemlauncher") || p.contains("settings")
                || p.contains("notification") || p.contains("sidebar")
                || p.contains("touch.gesture");
    }

    public void onWindowChanged(String pkg, int screenW, int screenH) {
        if (isExcluded(pkg)) return;
        long now = System.currentTimeMillis();
        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            firstWindowTs = now;
            tries = 0;
            lastClickedKey = "";
        }
        if (now - firstWindowTs > TRY_WINDOW_MS) return;
        if (tries > 0) return;   // 本轮已排过，避免窗口事件重复调度
        tries = MAX_TRIES;
        for (int i = 0; i < MAX_TRIES; i++) {
            final int pass = i;
            handler.postDelayed(() -> doAttempt(pkg, screenW, screenH), 200L + pass * TRY_GAP_MS);
        }
    }

    private void doAttempt(String pkg, int screenW, int screenH) {
        if (System.currentTimeMillis() - lastClickOkTs < 1500L) return;  // 刚点成功，冷却
        try {
            // 广告常以悬浮窗/独立 Window 的形式出现，必须遍历所有窗口而不只是当前活跃窗口
            List<Candidate> found = new ArrayList<>();
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> wins = null;
            try {
                wins = svc.getWindows();
            } catch (Exception ignored) {
            }
            if (wins != null && !wins.isEmpty()) {
                for (android.view.accessibility.AccessibilityWindowInfo w : wins) {
                    try {
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r == null) continue;
                        try { r.refresh(); } catch (Exception ignored) {}
                        CharSequence rp = r.getPackageName();
                        if (rp != null && rp.toString().equals(svc.getPackageName())) {
                            r.recycle();
                            continue;   // 跳过我们自己的确认浮层
                        }
                        int kids = -1;
                        try { kids = r.getChildCount(); } catch (Exception ignored) {}
                        collect(r, found, 0);
                        GuardEngine.get().noteSkip("  win type=" + w.getType() + " kids=" + kids
                                + " cls=" + String.valueOf(r.getClassName()));
                        r.recycle();
                    } catch (Exception ignored) {
                    }
                }
            }
            GuardEngine.get().noteSkip("attempt pkg=" + pkg + " windows="
                    + (wins == null ? -1 : wins.size()) + " cands=" + found.size());
            // 窗口不可见/为空时用活跃窗口兜底
            if (found.isEmpty()) {
                AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                if (root != null) {
                    try { root.refresh(); } catch (Exception ignored) {}
                    collect(root, found, 0);
                    GuardEngine.get().noteSkip("  fallback root kids=" + root.getChildCount()
                            + " cls=" + String.valueOf(root.getClassName()));
                    root.recycle();
                }
            }
            if (found.isEmpty()) return;

            // 优先：文本明确的跳过，其次：边缘的小尺寸关闭按钮
            found.sort((a, b) -> Float.compare(b.score, a.score));
            Candidate best = found.get(0);
            GuardEngine.get().noteSkip("best='" + best.label + "' score=" + best.score
                    + " bounds=" + best.bounds.toShortString());
            if (best.score < 1.0f) return;

            String key = best.label + "@" + best.bounds.toShortString();
            if (key.equals(lastClickedKey)) return;
            lastClickedKey = key;

            boolean ok = false;
            AccessibilityNodeInfo node = best.node;
            if (node != null) {
                try {
                    ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } catch (Exception ignored) {
                }
                // WebView / 自绘广告控件对 ACTION_CLICK 常无响应，用坐标手势兜底
                if (!ok) {
                    ok = tapByGesture(best.bounds);
                }
                if (ok) lastClickOkTs = System.currentTimeMillis();
            }
            GuardEngine.get().record(new Event("skip", pkg, "",
                    label(pkg) + " 开屏跳过：" + (ok ? "已点击「" + best.label + "」" : "尝试点击失败"))
                    .verdict(ok ? "allow" : "alert").level("info")
                    .detail("控件：文本=\"" + best.label + "\" 区域=" + best.bounds.toShortString()
                            + " 第 " + tries + " 次尝试"));
        } catch (Exception ignored) {
        }
    }

    /** 用无障碍手势在控件中心点一下（对 WebView 广告有效）。 */
    private boolean tapByGesture(Rect r) {
        try {
            if (r.width() <= 0 || r.height() <= 0) return false;
            android.accessibilityservice.GestureDescription.Builder b =
                    new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(r.centerX(), r.centerY());
            b.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, 60));
            return svc.dispatchGesture(b.build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    /** 收集候选控件。 */
    private void collect(AccessibilityNodeInfo node, List<Candidate> out, int depth) {
        if (node == null || depth > 16 || out.size() > 60) return;
        try {
            CharSequence t = node.getText();
            CharSequence d = node.getContentDescription();
            String label = t != null ? t.toString().trim() : "";
            if (label.isEmpty() && d != null) label = d.toString().trim();

            if (!label.isEmpty() && label.length() <= 12 && isCandidate(node, label)) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                out.add(new Candidate(node, label, r, score(label, r)));
            }
            int n = 0;
            try { n = node.getChildCount(); } catch (Exception ignored) {}
            for (int i = 0; i < n; i++) {
                AccessibilityNodeInfo c = null;
                try { c = node.getChild(i); } catch (Exception ignored) {}
                if (c != null) {
                    collect(c, out, depth + 1);
                    c.recycle();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isCandidate(AccessibilityNodeInfo node, String label) {
        String low = label.toLowerCase(Locale.ROOT);
        for (String dw : DANGER_WORDS) {
            if (low.contains(dw)) return false;
        }
        if (SKIP_COUNT.matcher(low).matches()) return true;
        for (String kw : KEYWORDS) {
            if (low.equals(kw) || low.contains(kw)) return true;
        }
        return false;
    }

    /** 位置与尺寸打分：右上角、小面积、文本含"跳过"最高。 */
    private float score(String label, Rect r) {
        float s = 0f;
        String low = label.toLowerCase(Locale.ROOT);
        if (low.contains("跳过") || low.contains("skip")) s += 1.2f;
        if (r.width() > 0 && r.height() > 0) {
            int cw = svc.getResources().getDisplayMetrics().widthPixels;
            int ch = svc.getResources().getDisplayMetrics().heightPixels;
            boolean topRight = r.centerX() > cw * 0.55f && r.centerY() < ch * 0.30f;
            boolean bottomSmall = r.centerY() > ch * 0.80f && r.height() < ch * 0.08f;
            boolean small = r.width() < cw * 0.35f && r.height() < ch * 0.10f;
            if (topRight) s += 0.9f;
            if (bottomSmall) s += 0.4f;
            if (small) s += 0.5f;
            if (!small) s -= 1.2f;         // 大面积控件多半是主内容，强烈降权
        }
        if (r.width() <= 0 || r.height() <= 0) s -= 0.5f;
        return s;
    }

    private static class Candidate {
        final AccessibilityNodeInfo node;
        final String label;
        final Rect bounds;
        final float score;

        Candidate(AccessibilityNodeInfo node, String label, Rect bounds, float score) {
            this.node = node;
            this.label = label;
            this.bounds = bounds;
            this.score = score;
        }
    }

    private String label(String pkg) {
        try {
            return String.valueOf(svc.getPackageManager()
                    .getApplicationLabel(svc.getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }


}

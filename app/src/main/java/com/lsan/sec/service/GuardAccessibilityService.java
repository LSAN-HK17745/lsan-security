package com.lsan.sec.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.JumpKiller;
import com.lsan.sec.core.SkipGuard;
import com.lsan.sec.core.VendorAdapter;
import com.lsan.sec.core.Modules;
import com.lsan.sec.core.Rules;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Sensitive;
import com.lsan.sec.util.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 核心守卫：无障碍服务。
 *
 * 职责：
 *  1) 应用间跳转拦截（无交互的自动跳转 → 弹窗确认，拒绝则立即撤回）
 *  2) 剪贴板敏感内容监控
 *  3) 屏幕内容敏感信息扫描（防读屏窃取）
 *  4) 通知层敏感内容关联
 *
 * 全部本地处理，无网络访问。
 */
public class GuardAccessibilityService extends AccessibilityService {

    public static volatile GuardAccessibilityService INSTANCE;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastTopPkg = "";
    private JumpPrompt prompt;
    private SkipGuard skipGuard;

    private ClipboardManager clipboard;
    private String lastClipDigest = "";
    private long lastClipTs = 0;

    private String lastScanDigest = "";
    private long lastScanTs = 0;
    private static final long SCREEN_SCAN_INTERVAL = 700;

    private final Set<String> systemAllow = new HashSet<>();
    private String launcherPkg = "";
    private String imePkg = "";
    private volatile long lastBlockTs = 0;
    private long lastConnectLogTs = 0;
    private long lastClipNoteTs = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        GuardEngine.get().init(this);
        GuardEngine.get().accessibilityConnected = true;

        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                        | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                info.notificationTimeout = 50;
                setServiceInfo(info);
            }
        } catch (Exception ignored) {
        }

        buildAllowLists();
        skipGuard = new SkipGuard(this);
        startClipboardWatcher();

        try {
            Intent svc = new Intent(this, GuardEngineService.class);
            startForegroundService(svc);
        } catch (Exception ignored) {
        }

        long now = System.currentTimeMillis();
        if (now - lastConnectLogTs > 10_000) {
            lastConnectLogTs = now;
            GuardEngine.get().record(new Event("system", getPackageName(), "",
                    "无障碍守卫已连接，跳转拦截与敏感监控开始工作").verdict("info").level("info"));
        }
    }

    private void buildAllowLists() {
        systemAllow.clear();
        // 厂商适配：系统组件包名按 ROM 给，避免误报（vivo 的 com.vivo.*、ColorOS 的 com.coloros.* 等）
        systemAllow.addAll(VendorAdapter.systemPackages());

        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> rs = getPackageManager().queryIntentActivities(home,
                    PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : rs) {
                if (ri.activityInfo != null) launcherPkg = ri.activityInfo.packageName;
            }
        } catch (Exception ignored) {
        }
        try {
            String ime = android.provider.Settings.Secure.getString(
                    getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) imePkg = ime.contains("/") ? ime.substring(0, ime.indexOf('/')) : ime;
        } catch (Exception ignored) {
        }
    }

    private boolean isSystemOrAllowed(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (pkg.equals(getPackageName())) return true;
        if (VendorAdapter.isSystemPackage(systemAllow, pkg)) return true;
        if (!launcherPkg.isEmpty() && pkg.equals(launcherPkg)) return true;
        if (!imePkg.isEmpty() && pkg.equals(imePkg)) return true;
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        GuardEngine.get().init(this);
        if (!Prefs.get().masterEnabled()) return;

        int type = event.getEventType();
        long now = System.currentTimeMillis();

        switch (type) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                GuardEngine.get().lastInteractionTs = now;
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                handleWindowChange(event, now);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleContentChange(event, now);
                break;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                handleNotificationEvent(event);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // 1) 应用间跳转拦截
    // ------------------------------------------------------------------

    private void handleWindowChange(AccessibilityEvent event, long now) {
        CharSequence pcs = event.getPackageName();
        if (pcs == null) return;
        String pkg = pcs.toString();
        if (pkg.equals(getPackageName())) return;

        String prev = lastTopPkg;
        if (pkg.equals(prev)) return; // 同应用内窗口变化
        lastTopPkg = pkg;
        GuardEngine.get().foregroundPkg = pkg;
        GuardEngine.get().lastForegroundPkg = prev;

        // 开屏跳过：仅对"刚启动的应用"生效，与跳转拦截互不影响
        if (skipGuard != null && Modules.isEnabled(this, Modules.SKIP)
                && !Prefs.get().isSkipDisabled(pkg)) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            skipGuard.onWindowChanged(pkg, dm.widthPixels, dm.heightPixels);
        }

        if (prev.isEmpty()) return;                // 首次建立基线
        if (isSystemOrAllowed(pkg)) return;        // 目标是系统组件
        if (Prefs.get().isWhitelisted(pkg)) return;
        // 源是系统 UI（桌面 / 侧边栏 / 通知栏 / 最近任务）→ 视为用户主动启动，放行
        if (isSystemOrAllowed(prev)) {
            GuardEngine.get().record(new Event("jump", prev, pkg,
                    appLabel(prev) + " → " + appLabel(pkg) + "（系统入口，用户主动）")
                    .verdict("allow").level("info"));
            return;
        }
        if (!Modules.isEnabled(this, Modules.JUMP)) return;

        boolean silent = (now - GuardEngine.get().lastInteractionTs) > Prefs.get().silentThresholdMs();
        int level = Prefs.get().policyLevel();   // 0=宽松 1=中等 2=严格

        // 宽松：你主动触发的跳转直接放行，只在"静默跳转"时介入
        if (level == 0 && !silent) {
            GuardEngine.get().record(new Event("jump", prev, pkg,
                    appLabel(prev) + " → " + appLabel(pkg) + "（宽松档放行）")
                    .verdict("allow").level("info"));
            return;
        }

        // 严格：忽略"永久批准"的记忆，每次都要确认
        if (level < 2 && Prefs.get().jumpApproved(prev, pkg)) {
            GuardEngine.get().record(new Event("jump", prev, pkg,
                    appLabel(prev) + " → " + appLabel(pkg) + "（已批准）")
                    .verdict("allow").level("info"));
            return;
        }

        Rules.Match m = Rules.evaluate(this, Rules.SCOPE_JUMP, prev, pkg, null);
        if (m != null) {
            if (Rules.ACT_BLOCK.equals(m.action)) {
                blockJump(prev, pkg, "规则阻止：" + m.reason);
                return;
            }
            if (Rules.ACT_ALLOW.equals(m.action)) {
                GuardEngine.get().record(new Event("jump", prev, pkg,
                        appLabel(prev) + " → " + appLabel(pkg) + "（规则放行）")
                        .verdict("allow").level("info").detail(m.reason));
                return;
            }
        }

        if (!silent && !Prefs.get().strictJump()) {
            GuardEngine.get().record(new Event("jump", prev, pkg,
                    appLabel(prev) + " → " + appLabel(pkg) + "（用户主动）")
                    .verdict("allow").level("info"));
            return;
        }

        int blocked = blockedCount.getOrDefault(pkg, 0);
        int autoBlockAt = Prefs.get().policyLevel() == 2 ? 2 : 3;
        if (blocked >= autoBlockAt) {
            blockJump(prev, pkg, "该目标已被连续拦截 " + blocked + " 次，自动终止");
            return;
        }

        askJump(prev, pkg, silent);
    }

    private void askJump(String src, String dst, boolean silent) {
        if (prompt != null && prompt.isShowing()) return;
        if (System.currentTimeMillis() - lastBlockTs < 500) return;

        prompt = new JumpPrompt(this, new JumpPrompt.Callback() {
            @Override
            public void onAllowOnce() {
                GuardEngine.get().record(new Event("jump", src, dst,
                        appLabel(src) + " → " + appLabel(dst) + "（本次允许）")
                        .verdict("allow").level("warn"));
                prompt = null;
            }

            @Override
            public void onAllowForever() {
                Prefs.get().approveJump(src, dst);
                GuardEngine.get().record(new Event("jump", src, dst,
                        appLabel(src) + " → " + appLabel(dst) + "（永久允许）")
                        .verdict("allow").level("info"));
                prompt = null;
            }

            @Override
            public void onBlock() {
                prompt = null;
                blockJump(src, dst, silent ? "静默跳转被拒绝" : "用户拒绝跳转");
            }
        });
        prompt.show(appLabel(src), appLabel(dst), src, dst, Prefs.get().promptCountdown(), silent);
    }

    private void blockJump(String src, String dst, String reason) {
        lastBlockTs = System.currentTimeMillis();
        GuardEngine.get().lastBlockTs = lastBlockTs;

        if (Prefs.get().blockWithBack()) {
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 120);
        }

        // 关键：撤回界面之后，把"被跳转到的目标应用"在后端真正终止
        final String target = dst;
        final boolean hardTerminate = Prefs.get().policyLevel() > 0;
        handler.postDelayed(() -> {
            JumpKiller.Result kill = hardTerminate
                    ? JumpKiller.terminate(getApplicationContext(), target)
                    : new JumpKiller.Result();
            GuardEngine.get().record(new Event("jump", src, target,
                    appLabel(src) + " → " + appLabel(target) + "（" + kill.label() + "）")
                    .verdict("block").level("high")
                    .detail(reason + "；" + kill.label() + "：" + kill.detail));
            GuardEngine.get().notifyStats();
        }, 420);

        blockedCount.put(dst, blockedCount.getOrDefault(dst, 0) + 1);

        handler.postDelayed(() -> {
            String cur = GuardEngine.get().foregroundPkg;
            lastTopPkg = cur == null ? "" : cur;
        }, 700);
    }

    /** 同一目标应用被拦截次数（用于自动静默阻止）。 */
    private final java.util.Map<String, Integer> blockedCount = new java.util.HashMap<>();

    // ------------------------------------------------------------------
    // 2) 屏幕内容扫描
    // ------------------------------------------------------------------

    private void handleContentChange(AccessibilityEvent event, long now) {
        if (!Modules.isEnabled(this, Modules.SCREEN)) return;
        if (now - lastScanTs < SCREEN_SCAN_INTERVAL) return;
        CharSequence pcs = event.getPackageName();
        if (pcs == null) return;
        String pkg = pcs.toString();
        if (pkg.equals(getPackageName())) return;
        if (isSystemOrAllowed(pkg)) return;
        lastScanTs = now;

        String text = collectText();
        if (text.isEmpty()) return;
        String digest = Text.mask(text) + "|" + text.length();
        if (digest.equals(lastScanDigest)) return;
        lastScanDigest = digest;

        List<Sensitive.Hit> hits = scanSensitive(text);
        if (hits.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        boolean allVerified = true;
        for (Sensitive.Hit h : hits) {
            if (sb.length() > 0) sb.append("、");
            sb.append(Sensitive.label(h.type)).append(' ').append(h.masked);
            if (!h.verified) {
                sb.append("(疑似)");
                allVerified = false;
            }
        }
        GuardEngine.get().record(new Event("screen", pkg, "",
                appLabel(pkg) + " 界面出现敏感信息：" + sb)
                .verdict("alert").level(allVerified ? "high" : "warn")
                .detail("窗口：" + Text.clip(String.valueOf(event.getClassName()), 80)
                        + "；命中：" + hits.size() + " 项"
                        + (allVerified ? "" : "；其中含未通过强校验的疑似项")));

        if (Modules.isEnabled(this, Modules.COLLUSION)) {
            GuardEngine.get().record(new Event("collusion", pkg, "",
                    "敏感界面与潜在读屏通道并存：" + appLabel(pkg))
                    .verdict("alert").level("warn")
                    .detail("当前界面出现 " + hits.size() + " 项敏感数据，"
                            + "若同时存在已启用的读屏类无障碍服务或通知监听，存在被组合窃取的可能。"));
        }
    }

    private String collectText() {
        StringBuilder sb = new StringBuilder();
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "";
            collectNode(root, sb, 0);
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void collectNode(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 14 || sb.length() > 12000) return;
        try {
            CharSequence t = node.getText();
            if (t != null && t.length() > 0) sb.append(t).append('\n');
            CharSequence d = node.getContentDescription();
            if (d != null && d.length() > 0 && (t == null || !d.toString().equals(t.toString()))) {
                sb.append(d).append('\n');
            }
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                AccessibilityNodeInfo c = node.getChild(i);
                if (c != null) {
                    collectNode(c, sb, depth + 1);
                    c.recycle();
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 3) 剪贴板监控
    // ------------------------------------------------------------------

    private void startClipboardWatcher() {
        try {
            clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.addPrimaryClipChangedListener(this::onClipChanged);
        } catch (Exception ignored) {
        }
    }

    private void onClipChanged() {
        if (clipboard == null) return;
        if (!Modules.isEnabled(this, Modules.CLIP)) return;
        try {
            ClipData clip = clipboard.getPrimaryClip();
            String text = "";
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(this);
                if (t != null) text = t.toString();
            }
            if (text.isEmpty()) {
                // Android 10+ 对后台读取剪贴板有系统级限制：内容不可见，仅记录变更事实
                long n = System.currentTimeMillis();
                if (n - lastClipNoteTs > 8000) {
                    lastClipNoteTs = n;
                    String fg2 = GuardEngine.get().foregroundPkg;
                    GuardEngine.get().record(new Event("clip", fg2, "",
                            "剪贴板内容发生变化（系统限制：后台不可读取内容）")
                            .verdict("alert").level("warn")
                            .detail("Android 10+ 仅在应用处于前台时允许读取剪贴板内容；"
                                    + "当前前台应用：" + appLabel(fg2)
                                    + "。可在「设置 → 剪贴板体检」中主动检查当前剪贴板。"));
                }
                return;
            }
            String digest = Text.mask(text) + "|" + text.length();
            long now = System.currentTimeMillis();
            if (digest.equals(lastClipDigest) && now - lastClipTs < 1500) return;
            lastClipDigest = digest;
            lastClipTs = now;

            String fg = GuardEngine.get().foregroundPkg;
            List<Sensitive.Hit> hits = scanSensitive(text);
            if (hits.isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            boolean verified = true;
            for (Sensitive.Hit h : hits) {
                if (sb.length() > 0) sb.append("、");
                sb.append(Sensitive.label(h.type)).append(' ').append(h.masked);
                if (!h.verified) {
                    sb.append("(疑似)");
                    verified = false;
                }
            }
            GuardEngine.get().record(new Event("clip", fg, "", "剪贴板出现敏感信息：" + sb)
                    .verdict("alert").level(verified ? "high" : "warn")
                    .detail("来源应用：" + appLabel(fg) + "；长度 " + text.length() + " 字符"));

            Rules.Match m = Rules.evaluate(this, Rules.SCOPE_CLIP, fg, null, text);
            if (m != null && Rules.ACT_BLOCK.equals(m.action)) {
                try {
                    clipboard.clearPrimaryClip();
                } catch (Exception ignored) {
                }
                GuardEngine.get().record(new Event("clip", fg, "",
                        "按规则清空剪贴板：" + m.reason).verdict("block").level("high"));
            }
            GuardEngine.get().notifyStats();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 4) 通知事件
    // ------------------------------------------------------------------

    private void handleNotificationEvent(AccessibilityEvent event) {
        if (!Modules.isEnabled(this, Modules.NOTIF)) return;
        CharSequence pcs = event.getPackageName();
        String pkg = pcs == null ? "" : pcs.toString();
        if (pkg.equals(getPackageName())) return;
        List<CharSequence> texts = event.getText();
        if (texts == null || texts.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (CharSequence c : texts) {
            if (c != null) sb.append(c).append('\n');
        }
        List<Sensitive.Hit> hits = scanSensitive(sb.toString());
        if (hits.isEmpty()) return;
        StringBuilder desc = new StringBuilder();
        for (Sensitive.Hit h : hits) {
            if (desc.length() > 0) desc.append("、");
            desc.append(Sensitive.label(h.type)).append(' ').append(h.masked);
        }
        GuardEngine.get().record(new Event("notif", pkg, "",
                appLabel(pkg) + " 通知中含敏感信息：" + desc)
                .verdict("alert").level("warn")
                .detail("通知栏内容可被具备通知使用权的应用读取。"));
    }

    private List<Sensitive.Hit> scanSensitive(String text) {
        if (!Sensitive.maybeSensitive(text)) return new ArrayList<>();
        Prefs p = Prefs.get();
        return Sensitive.scan(text,
                p.sens(Sensitive.TYPE_ID_CARD),
                p.sens(Sensitive.TYPE_BANK_CARD),
                p.sens(Sensitive.TYPE_PHONE),
                p.sens(Sensitive.TYPE_OTP),
                p.sens(Sensitive.TYPE_EMAIL),
                p.sens(Sensitive.TYPE_PASSWORD),
                p.sens(Sensitive.TYPE_ADDRESS));
    }

    private String appLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "系统";
        try {
            PackageManager pm = getPackageManager();
            String s = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            return s == null || s.equals("null") ? pkg : s;
        } catch (Exception e) {
            return pkg;
        }
    }

    @Override
    public void onInterrupt() {
        GuardEngine.get().accessibilityConnected = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        GuardEngine.get().accessibilityConnected = false;
        INSTANCE = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        GuardEngine.get().accessibilityConnected = false;
        INSTANCE = null;
        if (prompt != null) {
            prompt.dismiss();
            prompt = null;
        }
        super.onDestroy();
    }
}

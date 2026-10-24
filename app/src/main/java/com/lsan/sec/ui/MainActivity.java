package com.lsan.sec.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.Security;
import com.lsan.sec.data.Event;
import com.lsan.sec.service.GuardEngineService;
import com.lsan.sec.util.Prefs;

/**
 * LSAN-信息防护终端 主界面。
 *
 * 双外观：
 *  - 白（极简）：大字、留白、细线、丝滑入场动画
 *  - 黑（终端像素）：等宽字体、荧光绿、扫描线、无动画
 */
public class MainActivity extends Activity implements GuardEngine.Listener {

    public static final String ALIAS_WHITE = "com.lsan.sec.ui.LauncherWhite";
    public static final String ALIAS_BLACK = "com.lsan.sec.ui.LauncherBlack";

    private FrameLayout container;
    private LinearLayout nav;
    private TextView headerState;
    private LinearLayout securityBar;
    private Page[] pages;
    private View[] pageViews;
    private int current = 0;
    private boolean builtDark = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.get().init(this);
        GuardEngine.get().init(this);
        UiKit.attach(this);
        builtDark = Theme.dark(this);
        requestNotifPermission();

        Theme.Palette pal = UiKit.p();
        LinearLayout root = UiKit.vertical(this);
        root.setBackground(UiKit.background(this));

        root.addView(buildHeader(pal));

        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildNav(pal));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        FrameLayout frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (pal.mono) {
            // 终端扫描线覆盖层：不参与布局、不拦截触摸
            View scan = buildScanline();
            scan.setClickable(false);
            scan.setFocusable(false);
            scan.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(scan, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(frame);

        pages = new Page[]{
                new HomePage(this),
                new ModulesPage(this),
                new EventsPage(this),
                new AuditPage(this),
                new SettingsPage(this)
        };
        pageViews = new View[pages.length];
        select(0);

        if (Prefs.get().masterEnabled()) startEngineService();
        syncLauncherIconIfNeeded();
        runSelfCheck();
    }

    // ---------------------------------------------------------------- 顶栏

    private View buildHeader(Theme.Palette pal) {
        LinearLayout wrap = UiKit.vertical(this);

        if (pal.mono) {
            // ===== 窗口标题栏：○ ○ ○   LSAN :: SECURITY   [白] =====
            wrap.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8),
                    UiKit.dp(this, 12), UiKit.dp(this, 0));
            LinearLayout bar = UiKit.horizontal(this);
            bar.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 6));
            TextView dots = UiKit.text(this, "○  ○  ○", 11, pal.textFaint, false);
            dots.setLetterSpacing(0.2f);
            bar.addView(dots, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView path = UiKit.text(this, "LSAN :: SECURITY", 10, pal.textDim, false);
            path.setLetterSpacing(0.28f);
            path.setGravity(Gravity.CENTER);
            bar.addView(path, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView themeBtn = UiKit.text(this, "[白]", 11, pal.text, true);
            themeBtn.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 2), 0, UiKit.dp(this, 2));
            themeBtn.setOnClickListener(v -> v.postDelayed(this::toggleTheme, 0));
            bar.addView(themeBtn);
            wrap.addView(bar);

            View line0 = new View(this);
            line0.setBackgroundColor(pal.stroke);
            wrap.addView(line0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 1))));

            // ===== 标题区：LSAN_SEC▌ + 版本行 + 命令行状态 =====
            LinearLayout titleBox = UiKit.vertical(this);
            titleBox.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 14),
                    UiKit.dp(this, 4), UiKit.dp(this, 10));

            LinearLayout brandRow = UiKit.horizontal(this);
            TextView brand = UiKit.text(this, "LSAN_SEC", 24, pal.text, true);
            brand.setLetterSpacing(0.04f);
            brandRow.addView(brand);
            TextView caret = UiKit.text(this, "▌", 22, pal.text, false);
            caret.setPadding(UiKit.dp(this, 2), 0, 0, 0);
            brandRow.addView(caret);
            titleBox.addView(brandRow);

            TextView sub = UiKit.text(this, "2026 :: 本地信息防护 :: KERNEL v" + com.lsan.sec.BuildConfig.VERSION_NAME,
                    10, pal.textFaint, false);
            sub.setLetterSpacing(0.12f);
            sub.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 10));
            titleBox.addView(sub);

            boolean acc = HomePage.accessibilityEnabled(this);
            boolean ntf = HomePage.notificationListenerEnabled(this);
            boolean on = Prefs.get().masterEnabled();
            String cmd = "> 防护:" + (on ? "运行中" : "已暂停")
                    + " | 无障碍:" + (acc ? "连接" : "未连接")
                    + " | 通知:" + (ntf ? "连接" : "未连接");
            TextView cmdLine = UiKit.text(this, cmd, 11,
                    on ? pal.textDim : pal.warn, false);
            cmdLine.setLetterSpacing(0.04f);
            titleBox.addView(cmdLine);
            wrap.addView(titleBox);

            View line1 = new View(this);
            line1.setBackgroundColor(pal.stroke);
            wrap.addView(line1, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 1))));

            headerState = null;
            securityBar = UiKit.vertical(this);
            securityBar.setVisibility(View.GONE);
            wrap.addView(securityBar);
            return wrap;
        }

        // ===== 白色：保持原极简设计 =====
        wrap.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 22),
                UiKit.dp(this, 24), UiKit.dp(this, 10));
        LinearLayout row = UiKit.horizontal(this);
        LinearLayout left = UiKit.vertical(this);
        TextView brand = UiKit.text(this, "LSAN", 32, pal.text, true);
        brand.setLetterSpacing(0.04f);
        left.addView(brand);
        TextView sub = UiKit.text(this, "信息防护终端 · 全程离线", 12, pal.textFaint, false);
        sub.setPadding(UiKit.dp(this, 2), UiKit.dp(this, 4), 0, 0);
        left.addView(sub);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = UiKit.horizontal(this);
        TextView themeBtn = UiKit.text(this, "◐", 18, pal.text, true);
        themeBtn.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 8), UiKit.dp(this, 10), UiKit.dp(this, 8));
        themeBtn.setOnClickListener(v -> {
            Anim.press(v, UiKit.animated());
            v.postDelayed(this::toggleTheme, UiKit.animated() ? 150 : 0);
        });
        right.addView(themeBtn);

        headerState = UiKit.pill(this, Prefs.get().masterEnabled() ? "防护中" : "已暂停",
                Prefs.get().masterEnabled() ? pal.accent2 : pal.warn);
        headerState.setOnClickListener(v -> toggleMaster());
        right.addView(headerState);
        row.addView(right);
        wrap.addView(row);

        securityBar = UiKit.vertical(this);
        securityBar.setVisibility(View.GONE);
        wrap.addView(securityBar);
        return wrap;
    }

    /** 终端风细网格覆盖层（对齐官网的工程图纸质感）。 */
    private View buildScanline() {
        return new View(this) {
            private final Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(0x0AFFFFFF);
                paint.setStrokeWidth(1f);
                int step = Math.max(8, UiKit.dp(getContext(), 24));
                for (int y = 0; y < getHeight(); y += step) {
                    canvas.drawLine(0, y, getWidth(), y, paint);
                }
                for (int x = 0; x < getWidth(); x += step) {
                    canvas.drawLine(x, 0, x, getHeight(), paint);
                }
            }
        };
    }

    private View buildNav(Theme.Palette pal) {
        LinearLayout wrapper = UiKit.vertical(this);
        View line = new View(this);
        line.setBackgroundColor(pal.stroke);
        wrapper.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 1))));

        nav = UiKit.horizontal(this);
        nav.setBackgroundColor(pal.surface);
        nav.setPadding(0, UiKit.dp(this, pal.mono ? 4 : 8), 0, UiKit.dp(this, pal.mono ? 4 : 8));
        String[] names = {"总览", "模块", "事件", "审计", "设置"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout item = UiKit.vertical(this);
            item.setGravity(Gravity.CENTER);
            TextView tv = UiKit.text(this, names[i], pal.mono ? pal.smallSp : 12,
                    pal.textFaint, pal.mono);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, pal.mono ? 4 : 8));
            item.addView(tv);

            View bar = new View(this);
            bar.setLayoutParams(new LinearLayout.LayoutParams(
                    UiKit.dp(this, pal.mono ? 22 : 16), UiKit.dp(this, 2)));
            bar.setBackgroundColor(Color.TRANSPARENT);
            item.addView(bar);

            item.setTag(new Object[]{tv, bar});
            item.setOnClickListener(v -> {
                Anim.press(v, pal.animated);
                v.postDelayed(() -> select(idx), pal.animated ? 90 : 0);
            });
            nav.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrapper.addView(nav);
        return wrapper;
    }

    // ---------------------------------------------------------------- 行为

    private void toggleTheme() {
        boolean toDark = !Theme.dark(this);
        Theme.setDark(this, toDark);
        GuardEngine.get().record(new Event("system", getPackageName(), "",
                "外观已切换为" + (toDark ? "终端黑" : "极简白")).verdict("info").level("info"));
        recreate();
    }

    private static final String ALIAS_WHITE_C = "com.lsan.sec.ui.LauncherWhite";
    private static final String ALIAS_BLACK_C = "com.lsan.sec.ui.LauncherBlack";

    private ComponentName aliasOf(boolean dark) {
        return new ComponentName(this, dark ? ALIAS_BLACK_C : ALIAS_WHITE_C);
    }

    private int aliasState(boolean dark) {
        try {
            return getPackageManager().getComponentEnabledSetting(aliasOf(dark));
        } catch (Exception e) {
            return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        }
    }

    /** 启动时校正"图标已同步"的记录（不做切换，避免重启任务）。 */
    private void syncLauncherIconIfNeeded() {
        boolean dark = Theme.dark(this);
        if (aliasState(dark) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            Prefs.get().setIconSyncedState(dark ? 1 : 0);
        }
    }

    /**
     * 手动同步桌面图标（设置页按钮）：启用目标别名 → 结束当前界面 → 稍后以目标别名重启。
     * 因为禁用"承载当前任务"的别名会结束任务，所以这一步必须显式触发并允许一次重建。
     */
    public void applyLauncherIconNow() {
        final boolean dark = Theme.dark(this);
        try {
            getPackageManager().setComponentEnabledSetting(aliasOf(dark),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Prefs.get().setIconSyncedState(dark ? 1 : 0);
        } catch (Exception ignored) {
        }
        final Intent relaunch = new Intent();
        relaunch.setComponent(aliasOf(dark));
        relaunch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        finish();
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            try {
                getPackageManager().setComponentEnabledSetting(aliasOf(!dark),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Exception ignored) {
            }
            try {
                startActivity(relaunch);
            } catch (Exception ignored) {
            }
        }, 350);
    }

    private void toggleMaster() {
        boolean on = !Prefs.get().masterEnabled();
        Prefs.get().setMasterEnabled(on);
        updateHeader();
        if (on) {
            startEngineService();
        } else {
            try {
                Intent stop = new Intent(this, GuardEngineService.class);
                stop.setAction("stop");
                startService(stop);
            } catch (Exception ignored) {
            }
        }
        GuardEngine.get().record(new Event("system", getPackageName(), "",
                on ? "防护总开关已开启" : "防护总开关已关闭").verdict("info").level("info"));
        refreshCurrent();
    }

    private void updateHeader() {
        if (headerState == null) return;
        Theme.Palette pal = UiKit.p();
        boolean on = Prefs.get().masterEnabled();
        headerState.setText(pal.mono ? (on ? "[防护中]" : "[已暂停]") : (on ? "防护中" : "已暂停"));
        headerState.setTextColor(on ? pal.accent : pal.warn);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(pal.soft(on ? pal.accent : pal.warn, 0x18));
        bg.setCornerRadius(UiKit.dp(this, pal.mono ? 3 : 20));
        bg.setStroke(UiKit.dp(this, 1), pal.soft(on ? pal.accent : pal.warn, 0x66));
        headerState.setBackground(bg);
    }

    private void select(int idx) {
        current = idx;
        container.removeAllViews();
        if (pageViews[idx] == null) pageViews[idx] = pages[idx].build();
        View v = pageViews[idx];
        container.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pages[idx].refresh();
        Anim.fadeUp(v, UiKit.animated(), 0);

        Theme.Palette pal = UiKit.p();
        for (int i = 0; i < nav.getChildCount(); i++) {
            View item = nav.getChildAt(i);
            Object tag = item.getTag();
            if (tag instanceof Object[]) {
                Object[] arr = (Object[]) tag;
                TextView tv = (TextView) arr[0];
                View bar = (View) arr[1];
                boolean on = i == idx;
                String base = ((String) tv.getText()).replace("[", "").replace("]", "");
                if (pal.mono) {
                    tv.setText(base);
                    tv.setTextColor(on ? pal.accentInk : pal.textFaint);
                    GradientDrawable bgd = new GradientDrawable();
                    bgd.setColor(on ? pal.accent : 0x00000000);
                    bgd.setCornerRadius(0);
                    tv.setBackground(bgd);
                    tv.setPadding(UiKit.dp(MainActivity.this, 8), UiKit.dp(MainActivity.this, 2),
                            UiKit.dp(MainActivity.this, 8), UiKit.dp(MainActivity.this, 2));
                    bar.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    tv.setText(base);
                    tv.setTextColor(on ? pal.accent : pal.textFaint);
                    bar.setBackgroundColor(on ? pal.accent : Color.TRANSPARENT);
                }
            }
        }
        updateHeader();
    }

    private void refreshCurrent() {
        if (pages != null && current < pages.length) pages[current].refresh();
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void startEngineService() {
        try {
            Intent svc = new Intent(this, GuardEngineService.class);
            startForegroundService(svc);
        } catch (Exception ignored) {
        }
    }

    /** 启动自检：签名 / 调试 / Hook / 模拟器。异常时顶部显示红色告警条。 */
    private void runSelfCheck() {
        new Thread(() -> {
            Security.Report r = Security.audit(this);
            runOnUiThread(() -> {
                if (securityBar == null) return;
                securityBar.removeAllViews();
                Theme.Palette pal = UiKit.p();
                if (!r.risky()) {
                    securityBar.setVisibility(View.GONE);
                    GuardEngine.get().integrityOk = true;
                    return;
                }
                securityBar.setVisibility(View.VISIBLE);
                securityBar.addView(UiKit.divider(this));
                TextView tv = UiKit.text(this, "⚠ " + r.summary(), pal.smallSp, pal.danger, true);
                tv.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
                securityBar.addView(tv);
                GuardEngine.get().integrityOk = false;
                GuardEngine.get().record(new Event("system", getPackageName(), "",
                        "完整性自检异常：" + r.summary())
                        .verdict("alert").level("high")
                        .detail(android.text.TextUtils.join("；", r.notes)));
            });
        }, "lsan-selfcheck").start();
    }

    public void openAccessibilitySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    public void openNotificationListenerSettings() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            for (Page p : pages) {
                if (p instanceof ModulesPage) ((ModulesPage) p).onFilePicked(data.getData());
            }
        }
    }

    @Override
    public void onNewEvent(Event e) {
        runOnUiThread(() -> {
            if (pages != null && current == 2) pages[2].refresh();
            if (pages != null && current == 0) pages[0].refresh();
        });
    }

    @Override
    public void onStatsChanged() {
        runOnUiThread(this::refreshCurrent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 外观被外部改动（调试接口 / 其它入口）时，重建界面保证配色与组件一致
        if (Theme.dark(this) != builtDark) {
            recreate();
            return;
        }
        GuardEngine.get().register(this);
        updateHeader();
        refreshCurrent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        GuardEngine.get().unregister(this);
    }
}

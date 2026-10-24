package com.lsan.sec.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lsan.sec.ui.Theme;

/**
 * 跳转确认浮层（TYPE_ACCESSIBILITY_OVERLAY，无需悬浮窗权限）。
 *
 * 视觉与主界面同源：
 *  - 终端黑：窗口标题栏 + 等宽字体 + 1px 灰线 + 方角 + 白底黑字反色主按钮
 *  - 极简白：大字标题 + 宽留白 + 细线 + 黑底白字主按钮
 */
public class JumpPrompt {

    public interface Callback {
        void onAllowOnce();
        void onAllowForever();
        void onBlock();
    }

    private final AccessibilityService svc;
    private final Callback cb;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private View root;
    private volatile boolean showing = false;
    private Runnable tick;
    private int remain;
    private boolean dismissed = false;

    public JumpPrompt(AccessibilityService svc, Callback cb) {
        this.svc = svc;
        this.cb = cb;
    }

    public boolean isShowing() {
        return showing;
    }

    public void show(String srcLabel, String dstLabel, String srcPkg, String dstPkg,
                     int seconds, boolean silent) {
        if (showing) return;
        showing = true;
        dismissed = false;
        remain = Math.max(1, seconds);

        Theme.Palette pal = Theme.p(svc);
        boolean mono = pal.mono;

        try {
            wm = (WindowManager) svc.getSystemService(AccessibilityService.WINDOW_SERVICE);

            LinearLayout box = new LinearLayout(svc);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(pal.surface);
            bg.setCornerRadius(dp(pal.cardRadiusDp));
            bg.setStroke(dp(1), pal.stroke);
            box.setBackground(bg);
            int pad = mono ? dp(14) : dp(20);
            box.setPadding(pad, pad, pad, mono ? dp(12) : dp(16));

            // ---------- 标题区 ----------
            if (mono) {
                LinearLayout bar = new LinearLayout(svc);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL);
                TextView dots = tv("○  ○  ○", pal.smallSp - 1, pal.textFaint, false, true);
                dots.setLetterSpacing(0.2f);
                bar.addView(dots);
                TextView path = tv("LSAN :: INTERCEPT", pal.smallSp - 1, pal.textDim, false, true);
                path.setLetterSpacing(0.24f);
                path.setGravity(Gravity.CENTER);
                bar.addView(path, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                TextView flag = tv(silent ? "[SILENT]" : "[ASK]", pal.smallSp - 1,
                        silent ? pal.danger : pal.text, true, true);
                bar.addView(flag);
                box.addView(bar);

                View line = new View(svc);
                line.setBackgroundColor(pal.stroke);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                lp.setMargins(0, dp(8), 0, dp(10));
                box.addView(line, lp);

                TextView head = tv("> 应用间跳转被拦截", pal.bodySp + 2, pal.text, true, true);
                box.addView(head);

                TextView pair = tv(srcLabel + "  →  " + dstLabel, pal.bodySp, pal.text, false, true);
                pair.setPadding(0, dp(6), 0, 0);
                box.addView(pair);

                TextView pkgs = tv(srcPkg + "\n→ " + dstPkg, pal.smallSp - 1, pal.textFaint, false, true);
                pkgs.setPadding(0, dp(4), 0, dp(8));
                box.addView(pkgs);

                TextView hint = tv(silent
                                ? "// 无用户操作，可能是后台唤起 → 建议终止"
                                : "// 你刚点过页面，若确实要访问则放行",
                        pal.smallSp, silent ? pal.warn : pal.textDim, false, true);
                box.addView(hint);
            } else {
                TextView tag = tv(silent ? "静默跳转" : "应用间跳转", 12,
                        silent ? pal.danger : pal.textFaint, true, false);
                box.addView(tag);

                TextView pair = tv(srcLabel + " → " + dstLabel, 22, pal.text, true, false);
                pair.setPadding(0, dp(8), 0, dp(4));
                box.addView(pair);

                TextView pkgs = tv(srcPkg + "  →  " + dstPkg, 11, pal.textFaint, false, false);
                box.addView(pkgs);

                TextView hint = tv(silent
                                ? "没有任何操作就发生了唤起。终止会立即返回上一层。"
                                : "这是你自己操作触发的跳转，可以直接放行。",
                        13, pal.textDim, false, false);
                hint.setPadding(0, dp(14), 0, dp(6));
                hint.setLineSpacing(dp(4), 1f);
                box.addView(hint);
            }

            // ---------- 倒计时 ----------
            final TextView count = tv(mono ? "// " + remain + "s 后自动终止" : remain + " 秒后自动终止",
                    pal.smallSp + (mono ? 0 : 1), pal.warn, mono, mono);
            count.setPadding(0, dp(mono ? 8 : 10), 0, dp(mono ? 10 : 14));
            box.addView(count);

            // ---------- 按钮 ----------
            String blockText = mono ? "终止" : "终止跳转";
            String onceText = mono ? "仅本次" : "仅本次允许";
            String foreverText = mono ? "永久允许" : "永久放行";

            Button block = mkButton(blockText, pal.accent, pal.accentInk, true, mono, pal);
            Button once = mkButton(onceText, pal.surfaceAlt, pal.text, false, mono, pal);
            Button forever = mkButton(foreverText, pal.surfaceAlt, pal.text, false, mono, pal);

            LinearLayout row = new LinearLayout(svc);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
            blp.setMargins(0, 0, dp(mono ? 6 : 10), 0);
            row.addView(block, blp);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            olp.setMargins(0, 0, dp(mono ? 6 : 10), 0);
            row.addView(once, olp);
            row.addView(forever, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);

            block.setOnClickListener(v -> {
                dismiss();
                cb.onBlock();
            });
            once.setOnClickListener(v -> {
                dismiss();
                cb.onAllowOnce();
            });
            forever.setOnClickListener(v -> {
                dismiss();
                cb.onAllowForever();
            });

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            // 状态栏高度内会裁掉卡片圆角，统一放到安全区下方
            int statusBar = 0;
            try {
                int id = svc.getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (id > 0) statusBar = svc.getResources().getDimensionPixelSize(id);
            } catch (Exception ignored) {
            }
            params.y = statusBar + (mono ? dp(8) : dp(16));
            params.horizontalMargin = mono ? 0.03f : 0.06f;

            root = box;
            wm.addView(root, params);

            startCountdown(count);
        } catch (Exception e) {
            showing = false;
            dismissed = true;
            cb.onBlock();
        }
    }

    private TextView tv(String s, float sp, int color, boolean bold, boolean mono) {
        TextView t = new TextView(svc);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (mono) {
            try {
                t.setTypeface(svc.getResources().getFont(
                        bold ? com.lsan.sec.R.font.jb_mono_bold : com.lsan.sec.R.font.jb_mono));
            } catch (Exception e) {
                t.setTypeface(Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL));
            }
            t.setLetterSpacing(0.02f);
        } else {
            t.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        }
        return t;
    }

    private Button mkButton(String text, int bg, int fg, boolean primary, boolean mono,
                            Theme.Palette pal) {
        Button b = new Button(svc);
        b.setText(mono ? (primary ? "▸ " + text : text) : text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, mono ? pal.smallSp + 1 : 14);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(mono ? 0 : 14));
        if (!primary) d.setStroke(dp(1), mono ? pal.stroke : 0xFFDDDDE2);
        b.setBackground(d);
        b.setPadding(dp(mono ? 8 : 12), dp(mono ? 9 : 12), dp(mono ? 8 : 12), dp(mono ? 9 : 12));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(dp(mono ? 38 : 46));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        if (mono) {
            try {
                b.setTypeface(svc.getResources().getFont(com.lsan.sec.R.font.jb_mono));
            } catch (Exception ignored) {
            }
        }
        return b;
    }

    private void startCountdown(TextView count) {
        final boolean mono = Theme.p(svc).mono;
        tick = new Runnable() {
            @Override public void run() {
                if (dismissed) return;
                remain--;
                if (remain <= 0) {
                    dismiss();
                    cb.onBlock();
                    return;
                }
                count.setText(mono ? "// " + remain + "s 后自动终止" : remain + " 秒后自动终止");
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tick, 1000);
    }

    public void dismiss() {
        dismissed = true;
        showing = false;
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
        try {
            if (wm != null && root != null) wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                svc.getResources().getDisplayMetrics());
    }
}

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

/**
 * 跳转确认弹窗：以无障碍浮层（TYPE_ACCESSIBILITY_OVERLAY）呈现，
 * 不需要"显示在其他应用上层"权限，也不会被普通应用覆盖。
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

        try {
            wm = (WindowManager) svc.getSystemService(AccessibilityService.WINDOW_SERVICE);

            LinearLayout box = new LinearLayout(svc);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF111A2B);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), 0xFF2B3B57);
            box.setBackground(bg);
            int pad = dp(18);
            box.setPadding(pad, pad, pad, dp(12));

            TextView tag = new TextView(svc);
            tag.setText(silent ? "静默跳转拦截   ·   无用户操作" : "应用间跳转确认");
            tag.setTextColor(silent ? 0xFFFF5C6C : 0xFF38E1B0);
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tag.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(tag);

            TextView path = new TextView(svc);
            path.setText(srcLabel + "  →  " + dstLabel);
            path.setTextColor(0xFFE8EFF9);
            path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            path.setTypeface(Typeface.DEFAULT_BOLD);
            path.setPadding(0, dp(8), 0, dp(2));
            box.addView(path);

            TextView pkgs = new TextView(svc);
            pkgs.setText(srcPkg + "  →  " + dstPkg);
            pkgs.setTextColor(0xFF8FA2BE);
            pkgs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            box.addView(pkgs);

            TextView hint = new TextView(svc);
            hint.setText("应用之间正在相互唤起。拒绝将立即返回上一层界面；"
                    + "允许则放行本次访问。");
            hint.setTextColor(0xFF8FA2BE);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setPadding(0, dp(10), 0, dp(10));
            box.addView(hint);

            final TextView count = new TextView(svc);
            count.setText(remain + " 秒后自动终止");
            count.setTextColor(0xFFFFB020);
            count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            count.setPadding(0, 0, 0, dp(10));
            box.addView(count);

            LinearLayout row = new LinearLayout(svc);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button block = makeButton("终止跳转", 0xFF2A1A22, 0xFFFF5C6C);
            Button once = makeButton("仅本次", 0xFF1B2840, 0xFFE8EFF9);
            Button forever = makeButton("永久允许", 0xFF1E8F71, 0xFF04150F);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(block, lp);
            row.addView(once, lp);
            row.addView(forever, lp);
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
            params.y = dp(48);
            params.horizontalMargin = 0.06f;
            params.windowAnimations = android.R.style.Animation_Dialog;

            root = box;
            wm.addView(root, params);

            startCountdown(count);
        } catch (Exception e) {
            showing = false;
            dismissed = true;
            cb.onBlock();
        }
    }

    private void startCountdown(TextView count) {
        tick = new Runnable() {
            @Override public void run() {
                if (dismissed) return;
                remain--;
                if (remain <= 0) {
                    dismiss();
                    cb.onBlock();
                    return;
                }
                count.setText(remain + " 秒后自动终止");
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tick, 1000);
    }

    private Button makeButton(String text, int bg, int fg) {
        Button b = new Button(svc);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), 0x22FFFFFF);
        b.setBackground(d);
        b.setPadding(dp(6), dp(10), dp(6), dp(10));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
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

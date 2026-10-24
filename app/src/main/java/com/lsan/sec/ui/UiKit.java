package com.lsan.sec.ui;

import android.content.Context;

import com.lsan.sec.R;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 组件工厂：主题感知。
 *  - 白（极简）：大字号、宽留白、1px 细线、无阴影、黑底白字按钮
 *  - 黑（终端像素）：等宽字体、方角、荧光绿描边、命令行符号、扫描线
 */
public final class UiKit {

    private static Context app;

    private UiKit() {}

    public static void attach(Context c) {
        app = c.getApplicationContext();
    }

    public static Theme.Palette p() {
        return app == null ? Theme.white() : Theme.p(app);
    }

    public static boolean animated() {
        return p().animated;
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    private static Typeface monoRegular;
    private static Typeface monoBold;

    private static Typeface face(boolean bold) {
        Theme.Palette pal = p();
        if (pal.mono) {
            // 官网同款等宽：JetBrains Mono（已子集化内嵌）
            try {
                if (bold) {
                    if (monoBold == null) monoBold = app.getResources().getFont(R.font.jb_mono_bold);
                    return monoBold;
                }
                if (monoRegular == null) monoRegular = app.getResources().getFont(R.font.jb_mono);
                return monoRegular;
            } catch (Exception e) {
                return bold ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) : Typeface.MONOSPACE;
            }
        }
        return bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) : Typeface.SANS_SERIF;
    }

    // ---------------- 文本 ----------------

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face(bold));
        Theme.Palette pal = p();
        t.setLineSpacing(dp(c, pal.mono ? 2 : 4), 1f);
        if (pal.mono) t.setLetterSpacing(bold ? 0.06f : 0.02f);
        return t;
    }

    public static TextView label(Context c, String s) {
        return text(c, s, p().smallSp + 1, p().textDim, false);
    }

    public static TextView title(Context c, String s) {
        return text(c, s, p().titleSp, p().text, true);
    }

    /** 分组标签：白=灰小字大写，黑=// 注释风。 */
    public static TextView sectionLabel(Context c, String s) {
        Theme.Palette pal = p();
        TextView t = text(c, pal.mono ? "// " + s : s.toUpperCase(),
                pal.smallSp, pal.textFaint, pal.mono);
        if (pal.mono) t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(c, 10), 0, dp(c, 8));
        return t;
    }

    // ---------------- 容器 ----------------

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** 卡片。白=白底细线大圆角；黑=深底方角+顶部荧光条。 */
    public static LinearLayout card(Context c) {
        Theme.Palette pal = p();
        LinearLayout inner = vertical(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surface);
        bg.setCornerRadius(dp(c, pal.cardRadiusDp));
        bg.setStroke(dp(c, 1), pal.stroke);
        inner.setBackground(bg);
        int pad = pal.mono ? dp(c, 14) : dp(c, 20);
        inner.setPadding(pad, pad, pad, pad);
        return inner;
    }

    public static LinearLayout.LayoutParams cardLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, p().mono ? 10 : 16));
        return lp;
    }

    public static TextView pill(Context c, String s, int color) {
        Theme.Palette pal = p();
        String label = pal.mono ? "[" + s + "]" : s;
        TextView t = text(c, label, pal.smallSp - 1, pal.mono ? pal.text : color, pal.mono);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.mono ? 0x00000000 : pal.soft(color, 0x14));
        bg.setCornerRadius(dp(c, pal.mono ? 0 : 20));
        bg.setStroke(dp(c, 1), pal.mono ? pal.stroke : pal.soft(color, 0x44));
        t.setBackground(bg);
        int ph = pal.mono ? dp(c, 6) : dp(c, 9);
        t.setPadding(ph, dp(c, 3), ph, dp(c, 3));
        return t;
    }

    public static Button button(Context c, String s, int bgColor, int textColor, boolean filled) {
        Theme.Palette pal = p();
        Button b = new Button(c);
        b.setText(pal.mono ? (filled ? "▸ " + s : s) : s);
        b.setAllCaps(!pal.mono);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, pal.mono ? pal.smallSp + 1 : 14);
        b.setTextColor(pal.mono && filled ? pal.accentInk : textColor);
        b.setTypeface(face(pal.mono));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(c, pal.mono ? 0 : 14));
        if (!filled || pal.mono) d.setStroke(dp(c, 1), pal.mono ? pal.stroke : pal.stroke);
        b.setBackground(d);
        int px = pal.mono ? dp(c, 10) : dp(c, 16);
        int py = pal.mono ? dp(c, 8) : dp(c, 11);
        b.setPadding(px, py, px, py);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(dp(c, pal.mono ? 36 : 44));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setLetterSpacing(pal.mono ? 0.04f : 0f);
        return b;
    }

    public static Button primary(Context c, String s) {
        Theme.Palette pal = p();
        return button(c, s, pal.accent, pal.accentInk, true);
    }

    public static Button ghost(Context c, String s) {
        Theme.Palette pal = p();
        return button(c, s, pal.surfaceAlt, pal.text, false);
    }

    public static Button danger(Context c, String s) {
        Theme.Palette pal = p();
        return button(c, s, pal.soft(pal.danger, pal.mono ? 0x1A : 0x12), pal.danger, false);
    }

    public static View divider(Context c) {
        Theme.Palette pal = p();
        View v = new View(c);
        v.setBackgroundColor(pal.mono ? pal.soft(pal.accent, 0x22) : pal.stroke);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.setMargins(0, dp(c, pal.mono ? 8 : 14), 0, dp(c, pal.mono ? 8 : 14));
        v.setLayoutParams(lp);
        return v;
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    // ---------------- 组合控件 ----------------

    public static LinearLayout switchRow(Context c, String title, String desc,
                                         boolean checked,
                                         CompoundButton.OnCheckedChangeListener l) {
        Theme.Palette pal = p();
        LinearLayout row = horizontal(c);
        LinearLayout left = vertical(c);

        TextView t = text(c, title, pal.mono ? pal.bodySp : 16, pal.text, pal.mono);
        left.addView(t);
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(c, desc, pal.smallSp, pal.textFaint, false);
            d.setPadding(0, dp(c, pal.mono ? 3 : 5), 0, 0);
            left.addView(d);
        }
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(c);
        sw.setChecked(checked);
        applySwitch(sw, checked);
        if (l != null) sw.setOnCheckedChangeListener(l);
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, dp(c, pal.mono ? 8 : 12), 0, dp(c, pal.mono ? 4 : 8));
        return row;
    }

    public static void setSwitchVisual(CompoundButton cb, boolean checked) {
        if (cb instanceof Switch) applySwitch((Switch) cb, checked);
    }

    private static void applySwitch(Switch sw, boolean checked) {
        Theme.Palette pal = p();
        sw.setThumbTintList(ColorStateList.valueOf(checked ? pal.accent : pal.textFaint));
        sw.setTrackTintList(ColorStateList.valueOf(
                checked ? pal.soft(pal.accent, 0x55) : pal.soft(pal.textFaint, 0x33)));
    }

    /** 状态行：圆点 + 标题/状态 + 右侧操作。 */
    public static LinearLayout statusRow(Context c, String name, String state,
                                         boolean ok, String action, Runnable onAction) {
        Theme.Palette pal = p();
        LinearLayout row = horizontal(c);
        row.setPadding(0, dp(c, pal.mono ? 7 : 12), 0, dp(c, pal.mono ? 7 : 12));

        if (pal.mono) {
            TextView dot = text(c, ok ? "[●]" : "[○]", pal.bodySp,
                    ok ? pal.accent : pal.danger, true);
            dot.setPadding(0, 0, dp(c, 8), 0);
            row.addView(dot);
        } else {
            View dot = new View(c);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(ok ? pal.accent : pal.danger);
            dot.setBackground(d);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(c, 9), dp(c, 9));
            dlp.setMargins(0, 0, dp(c, 14), 0);
            row.addView(dot, dlp);
        }

        LinearLayout col = vertical(c);
        col.addView(text(c, name, pal.mono ? pal.bodySp : 16, pal.text, !pal.mono));
        col.addView(text(c, state, pal.smallSp,
                ok ? (pal.mono ? pal.textDim : pal.textDim) : pal.danger, false));
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (action != null && onAction != null) {
            Button b = ghost(c, action);
            b.setOnClickListener(v -> {
                Anim.press(v, pal.animated);
                onAction.run();
            });
            row.addView(b);
        }
        return row;
    }

    /** 统计单元：数值 + 名称。 */
    public static LinearLayout statCell(Context c, String value, String name, int color) {
        Theme.Palette pal = p();
        LinearLayout cell = vertical(c);
        cell.setGravity(Gravity.CENTER);
        TextView v = text(c, value, pal.mono ? 26 : 34, color, true);
        v.setGravity(Gravity.CENTER);
        cell.addView(v);
        TextView n = text(c, name, pal.smallSp, pal.textFaint, false);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, dp(c, pal.mono ? 2 : 6), 0, 0);
        cell.addView(n);
        return cell;
    }

    /** 三档分段选择器（宽松 / 中等 / 严格）。 */
    public static LinearLayout segmented(Context c, String[] labels, int selected,
                                         android.view.View.OnClickListener dummy,
                                         java.util.function.IntConsumer onPick) {
        Theme.Palette pal = p();
        LinearLayout outer = horizontal(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surfaceAlt);
        bg.setCornerRadius(dp(c, pal.mono ? 0 : 14));
        bg.setStroke(dp(c, 1), pal.stroke);
        outer.setBackground(bg);
        outer.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView seg = text(c, labels[i], pal.mono ? pal.smallSp + 1 : 14,
                    on ? pal.accentInk : pal.textDim, on);
            seg.setGravity(Gravity.CENTER);
            seg.setPadding(0, dp(c, pal.mono ? 7 : 10), 0, dp(c, pal.mono ? 7 : 10));
            GradientDrawable sg = new GradientDrawable();
            sg.setColor(on ? pal.accent : 0x00000000);
            sg.setCornerRadius(dp(c, pal.mono ? 0 : 11));
            seg.setBackground(sg);
            seg.setOnClickListener(v -> {
                Anim.press(v, pal.animated);
                onPick.accept(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            outer.addView(seg, lp);
        }
        return outer;
    }

    /** 背景：白=纯白，黑=深色 + 扫描线质感。 */
    public static GradientDrawable background(Context c) {
        Theme.Palette pal = p();
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{pal.bgGradTop, pal.bg});
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return d;
    }
}

package com.lsan.sec.ui;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.List;

/**
 * 动效工具：白主题走"丝滑"（缓入缓出、错峰入场、数字滚动、按压回弹），
 * 黑主题走"干脆"（几乎不动，仅 60ms 状态切换）。
 */
public final class Anim {

    /** 白主题用减速曲线；黑主题用线性且极短。 */
    private static TimeInterpolator curve(boolean animated) {
        return animated ? new DecelerateInterpolator(2.2f) : new LinearInterpolator();
    }

    private static long dur(boolean animated, long normal) {
        return animated ? normal : Math.max(0, normal / 6);
    }

    /** 页面/卡片入场：淡入 + 上移。 */
    public static void fadeUp(View v, boolean animated, int index) {
        if (v == null) return;
        if (!animated) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        float dy = dip(v, 14f);
        v.setAlpha(0f);
        v.setTranslationY(dy);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(160L, index * 38L))
                .setDuration(320L)
                .setInterpolator(curve(true))
                .start();
    }

    /** 批量错峰入场。 */
    public static void stagger(List<? extends View> views, boolean animated, int maxCount) {
        if (views == null) return;
        for (int i = 0; i < views.size() && i < maxCount; i++) {
            fadeUp(views.get(i), animated, i);
        }
    }

    /** 按压反馈：白主题缩放 + 阴影，黑主题只改透明度。 */
    public static void press(View v, boolean animated) {
        if (v == null) return;
        if (animated) {
            v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(90L)
                    .setInterpolator(curve(true)).withEndAction(() ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(220L)
                                    .setInterpolator(curve(true)).start()).start();
        } else {
            v.animate().alpha(0.72f).setDuration(60L).withEndAction(() ->
                    v.animate().alpha(1f).setDuration(60L).start()).start();
        }
    }

    /** 数字滚动（统计值变化）。 */
    public static void countTo(android.widget.TextView tv, int from, int to, boolean animated) {
        if (tv == null) return;
        if (!animated || from == to) {
            tv.setText(String.valueOf(to));
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(dur(true, 520L));
        a.setInterpolator(curve(true));
        a.addUpdateListener(x -> tv.setText(String.valueOf(x.getAnimatedValue())));
        a.start();
    }

    /** 深呼吸（防护中状态徽标）。 */
    public static void breathe(View v, boolean animated) {
        if (v == null || !animated) return;
        v.animate().alpha(0.55f).setDuration(1100L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> v.animate().alpha(1f).setDuration(1100L)
                        .setInterpolator(new LinearInterpolator())
                        .withEndAction(() -> breathe(v, true)).start())
                .start();
    }

    private static float dip(View v, float dp) {
        return dp * v.getResources().getDisplayMetrics().density;
    }
}

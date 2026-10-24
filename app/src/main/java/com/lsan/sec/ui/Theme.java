package com.lsan.sec.ui;

import android.content.Context;
import android.graphics.Color;

import com.lsan.sec.util.Prefs;

/**
 * 双外观系统：
 *  - WHITE：极简白，大字留白 + 细线 + 丝滑动效（参考极简系统版本信息页的排版语言）
 *  - BLACK：终端风，等宽字体 + 荧光绿 + 扫描线，干脆利落无动画
 */
public final class Theme {

    public enum Mode { WHITE, BLACK }

    /** 一套配色 + 排版参数。 */
    public static final class Palette {
        public final Mode mode;
        public final int bg;
        public final int bgGradTop;
        public final int surface;
        public final int surfaceAlt;
        public final int stroke;
        public final int text;
        public final int textDim;
        public final int textFaint;
        public final int accent;       // 主强调色
        public final int accentInk;    // 强调色上的文字色
        public final int accentSoft;   // 强调色淡背景
        public final int accent2;      // 次强调（信息/链接）
        public final int danger;
        public final int warn;
        public final int cardRadiusDp;
        public final float titleSp;
        public final float bodySp;
        public final float smallSp;
        public final boolean mono;     // 是否等宽字体
        public final boolean animated; // 是否有丝滑动效

        Palette(Mode mode, int bg, int bgGradTop, int surface, int surfaceAlt, int stroke,
                int text, int textDim, int textFaint, int accent, int accentInk, int accentSoft,
                int accent2, int danger, int warn, int cardRadiusDp,
                float titleSp, float bodySp, float smallSp, boolean mono, boolean animated) {
            this.mode = mode;
            this.bg = bg;
            this.bgGradTop = bgGradTop;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.stroke = stroke;
            this.text = text;
            this.textDim = textDim;
            this.textFaint = textFaint;
            this.accent = accent;
            this.accentInk = accentInk;
            this.accentSoft = accentSoft;
            this.accent2 = accent2;
            this.danger = danger;
            this.warn = warn;
            this.cardRadiusDp = cardRadiusDp;
            this.titleSp = titleSp;
            this.bodySp = bodySp;
            this.smallSp = smallSp;
            this.mono = mono;
            this.animated = animated;
        }

        public int soft(int color, int alpha) {
            return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
        }
    }

    /** 白：极简、留白、细线、丝滑。 */
    private static final Palette WHITE = new Palette(
            Mode.WHITE,
            0xFFFFFFFF,   // bg
            0xFFFAFAFA,   // bgGradTop
            0xFFFFFFFF,   // surface
            0xFFF6F6F7,   // surfaceAlt
            0xFFE9E9EC,   // stroke
            0xFF0B0B0C,   // text
            0xFF6E6E73,   // textDim
            0xFFA6A6AC,   // textFaint
            0xFF0B0B0C,   // accent（黑白主色）
            0xFFFFFFFF,   // accentInk
            0x0D000000,   // accentSoft
            0xFF1D6BFF,   // accent2
            0xFFD92D20,   // danger
            0xFFB54708,   // warn
            20, 30f, 15f, 12f, false, true);

    /** 黑：对齐官网（纯黑底 · 灰白字 · 1px 灰线 · 方角 · 等宽 · 仅状态点用绿）。 */
    private static final Palette BLACK = new Palette(
            Mode.BLACK,
            0xFF000000,   // bg
            0xFF000000,   // bgGradTop
            0xFF0A0A0A,   // surface
            0xFF101010,   // surfaceAlt
            0xFF333333,   // stroke
            0xFFFFFFFF,   // text
            0xFFAAAAAA,   // textDim
            0xFF666666,   // textFaint
            0xFFFFFFFF,   // accent：白色反色强调
            0xFF000000,   // accentInk
            0x14FFFFFF,   // accentSoft
            0xFF7FFF7F,   // accent2：官网荧光绿，仅用于状态点
            0xFFFF6B6B,   // danger
            0xFFFFD166,   // warn
            0,            // cardRadius：方角
            22f, 13f, 11f, true, false);

    private Theme() {}

    public static Palette white() { return WHITE; }

    public static Palette black() { return BLACK; }

    public static Mode currentMode(Context ctx) {
        Prefs.get().init(ctx);
        return Prefs.get().darkTheme() ? Mode.BLACK : Mode.WHITE;
    }

    public static Palette p(Context ctx) {
        return dark(ctx) ? BLACK : WHITE;
    }

    public static boolean dark(Context ctx) {
        return currentMode(ctx) == Mode.BLACK;
    }

    public static void setDark(Context ctx, boolean dark) {
        Prefs.get().init(ctx);
        Prefs.get().setDarkTheme(dark);
    }

    public static void toggle(Context ctx) {
        setDark(ctx, !dark(ctx));
    }

    public static Palette of(Mode mode) {
        return mode == Mode.BLACK ? BLACK : WHITE;
    }

    /** 终端风扫描线强度（白主题为 0）。 */
    public static int scanLineAlpha(Context ctx) {
        return dark(ctx) ? 0x0FFFFFFF : 0;
    }

    public static int mix(int a, int b, float t) {
        float r = Color.red(a) + (Color.red(b) - Color.red(a)) * t;
        float g = Color.green(a) + (Color.green(b) - Color.green(a)) * t;
        float bl = Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t;
        float al = Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t;
        return Color.argb((int) al, (int) r, (int) g, (int) bl);
    }
}

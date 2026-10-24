package com.lsan.sec.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 通用文本工具：脱敏、截断、时间格式化。 */
public final class Text {

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DAY_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private Text() {}

    public static String time(long ts) {
        return TIME_FMT.format(new Date(ts));
    }

    public static String day(long ts) {
        return DAY_FMT.format(new Date(ts));
    }

    public static String clip(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    /** 中间打码，保留两端。 */
    public static String mask(String s) {
        if (s == null) return "";
        int n = s.length();
        if (n <= 2) return repeat("*", n);
        if (n <= 6) return s.charAt(0) + repeat("*", n - 2) + s.charAt(n - 1);
        int head = Math.max(2, n / 6);
        int tail = Math.max(2, n / 6);
        return s.substring(0, head) + repeat("*", Math.min(12, n - head - tail))
                + s.substring(n - tail);
    }

    public static String repeat(String s, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }

    public static String human(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1000000) return String.format(Locale.US, "%.1fk", n / 1000.0);
        return String.format(Locale.US, "%.1fM", n / 1000000.0);
    }

    public static String ago(long ts) {
        long d = System.currentTimeMillis() - ts;
        if (d < 60_000) return "刚刚";
        if (d < 3_600_000) return (d / 60_000) + " 分钟前";
        if (d < 86_400_000L) return (d / 3_600_000) + " 小时前";
        return (d / 86_400_000L) + " 天前";
    }
}

package com.lsan.sec.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地敏感信息识别器（纯规则引擎，无网络、无第三方库）。
 * 识别：身份证 / 银行卡(Luhn) / 手机号 / 验证码 / 邮箱 / 密码字段 / 详细住址。
 */
public final class Sensitive {

    public static final String TYPE_ID_CARD = "id_card";
    public static final String TYPE_BANK_CARD = "bank_card";
    public static final String TYPE_PHONE = "phone";
    public static final String TYPE_OTP = "otp";
    public static final String TYPE_EMAIL = "email";
    public static final String TYPE_PASSWORD = "password";
    public static final String TYPE_ADDRESS = "address";

    private static final Pattern ID_CARD = Pattern.compile(
            "(?<![0-9])[1-9][0-9]{5}(?:19|20)[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])(?:62|4[0-9]|5[1-5])[0-9]{12,17}(?![0-9])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private static final Pattern OTP_CTX = Pattern.compile(
            "(?i)(?:验证码|校验码|动态码|确认码|verification\\s*code|security\\s*code|otp|code)[^0-9]{0,12}([0-9]{4,8})");
    private static final Pattern OTP_STANDALONE = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)(?:password|passwd|pwd|密码|口令)\\s*[:：=]\\s*(\\S{4,64})");
    private static final Pattern ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,8}(?:省|自治区)[\\u4e00-\\u9fa5]{2,10}?(?:市|州)[\\u4e00-\\u9fa5]{2,10}?(?:区|县|市)"
                    + "[\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:路|街|道|巷|号|栋|楼|室|小区)");

    private Sensitive() {}

    public static final class Hit {
        public final String type;
        public final String raw;
        public final String masked;
        /** 是否通过强校验（身份证校验位 / 银行卡 Luhn）。false 表示"格式疑似"。 */
        public final boolean verified;

        Hit(String type, String raw) {
            this(type, raw, true);
        }

        Hit(String type, String raw, boolean verified) {
            this.type = type;
            this.raw = raw;
            this.verified = verified;
            this.masked = Text.mask(raw);
        }

        @Override public String toString() {
            return type + ":" + masked;
        }
    }

    public static String label(String type) {
        switch (type) {
            case TYPE_ID_CARD: return "身份证号";
            case TYPE_BANK_CARD: return "银行卡号";
            case TYPE_PHONE: return "手机号";
            case TYPE_OTP: return "验证码";
            case TYPE_EMAIL: return "邮箱";
            case TYPE_PASSWORD: return "口令字段";
            case TYPE_ADDRESS: return "详细住址";
            default: return "敏感信息";
        }
    }

    /** 扫描文本，返回命中的敏感信息（同类型去重）。 */
    public static List<Hit> scan(String text, boolean idCard, boolean bankCard, boolean phone,
                                 boolean otp, boolean email, boolean password, boolean address) {
        List<Hit> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        if (text.length() > 20000) text = text.substring(0, 20000);

        if (idCard) {
            Matcher m = ID_CARD.matcher(text);
            while (m.find()) {
                String v = m.group();
                // 校验位通过 = 强命中；格式对但校验失败 = 疑似命中（仍告警，标注置信度）
                addOnce(out, TYPE_ID_CARD, v, idCardChecksum(v));
            }
        }
        if (bankCard) {
            Matcher m = BANK_CARD.matcher(text);
            while (m.find()) {
                String v = m.group();
                if (v.length() >= 15 && v.length() <= 19) {
                    addOnce(out, TYPE_BANK_CARD, v, luhn(v));
                }
            }
        }
        if (phone) {
            Matcher m = PHONE.matcher(text);
            while (m.find()) addOnce(out, TYPE_PHONE, m.group());
        }
        if (otp) {
            Matcher m = OTP_CTX.matcher(text);
            while (m.find()) addOnce(out, TYPE_OTP, m.group(1));
            if (out.isEmpty()) {
                // 无上下文时，仅当文本很短（通知/弹窗）且含 6 位纯数字才视为验证码候选
                String t = text.trim();
                if (t.length() <= 80) {
                    Matcher m2 = OTP_STANDALONE.matcher(t);
                    while (m2.find()) addOnce(out, TYPE_OTP, m2.group(1));
                }
            }
        }
        if (email) {
            Matcher m = EMAIL.matcher(text);
            while (m.find()) addOnce(out, TYPE_EMAIL, m.group());
        }
        if (password) {
            Matcher m = PASSWORD.matcher(text);
            while (m.find()) addOnce(out, TYPE_PASSWORD, m.group(1));
        }
        if (address) {
            Matcher m = ADDRESS.matcher(text);
            while (m.find()) addOnce(out, TYPE_ADDRESS, m.group());
        }
        return out;
    }

    private static void addOnce(List<Hit> out, String type, String raw) {
        addOnce(out, type, raw, true);
    }

    private static void addOnce(List<Hit> out, String type, String raw, boolean verified) {
        for (Hit h : out) {
            if (h.type.equals(type) && h.raw.equals(raw)) {
                return;
            }
        }
        out.add(new Hit(type, raw, verified));
    }

    /** 身份证 18 位校验位。 */
    public static boolean idCardChecksum(String id) {
        if (id == null || id.length() != 18) return false;
        int[] w = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] c = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        try {
            for (int i = 0; i < 17; i++) sum += (id.charAt(i) - '0') * w[i];
        } catch (Exception e) {
            return false;
        }
        char expect = c[sum % 11];
        char got = Character.toUpperCase(id.charAt(17));
        return expect == got;
    }

    /** Luhn 校验（银行卡）。 */
    public static boolean luhn(String digits) {
        if (digits == null || digits.length() < 12) return false;
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char ch = digits.charAt(i);
            if (ch < '0' || ch > '9') return false;
            int d = ch - '0';
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    /** 判断文本是否值得做敏感扫描（快速预筛，降低功耗）。 */
    public static boolean maybeSensitive(String text) {
        if (text == null || text.length() < 4) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') return true;
            if (c == '@' || c == '密' || c == '码') return true;
        }
        return text.contains("验证") || text.contains("银行") || text.contains("身份证");
    }
}

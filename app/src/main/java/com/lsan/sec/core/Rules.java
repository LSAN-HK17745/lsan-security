package com.lsan.sec.core;

import android.content.Context;

import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Text;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 本地规则引擎：执行内置策略 + 已导入功能包里的声明式规则。
 * 规则只描述"匹配条件 + 动作"，不承载可执行代码，避免功能包带来新的风险。
 */
public final class Rules {

    public static final String SCOPE_JUMP = "jump";
    public static final String SCOPE_CLIP = "clip";
    public static final String SCOPE_SCREEN = "screen";
    public static final String SCOPE_NOTIF = "notif";

    public static final String ACT_BLOCK = "block";
    public static final String ACT_ALERT = "alert";
    public static final String ACT_ALLOW = "allow";

    private Rules() {}

    public static class Rule {
        public String id = "";
        public String scope = "";
        public String action = ACT_ALERT;
        public String src = "";      // 源包名（支持 * 通配后缀）
        public String dst = "";      // 目标包名
        public String keyword = "";  // 文本包含
        public String regex = "";    // 正则
        public List<String> sensitive = new ArrayList<>();
        public String note = "";
        public String pluginId = ""; // 来源功能包

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (!src.isEmpty()) sb.append("源=").append(src).append(' ');
            if (!dst.isEmpty()) sb.append("目标=").append(dst).append(' ');
            if (!keyword.isEmpty()) sb.append("含\"").append(keyword).append("\" ");
            if (!regex.isEmpty()) sb.append("正则=").append(regex).append(' ');
            if (!sensitive.isEmpty()) sb.append("敏感=").append(sensitive).append(' ');
            if (!note.isEmpty()) sb.append("说明=").append(note);
            return sb.toString().trim();
        }
    }

    public static class Match {
        public final Rule rule;
        public final String action;
        public final String reason;

        Match(Rule rule, String action, String reason) {
            this.rule = rule;
            this.action = action;
            this.reason = reason;
        }
    }

    /** 读取全部规则（含功能包规则）。 */
    public static List<Rule> all(Context ctx) {
        Prefs.get().init(ctx);
        List<Rule> out = new ArrayList<>();
        String raw = Prefs.get().pluginsJson();
        try {
            JSONArray plugins = new JSONArray(raw);
            for (int i = 0; i < plugins.length(); i++) {
                JSONObject p = plugins.optJSONObject(i);
                if (p == null) continue;
                String pid = p.optString("id");
                JSONArray arr = p.optJSONArray("rules");
                if (arr == null) continue;
                for (int j = 0; j < arr.length(); j++) {
                    JSONObject o = arr.optJSONObject(j);
                    if (o == null) continue;
                    Rule r = parse(o);
                    if (r == null) continue;
                    r.pluginId = pid;
                    out.add(r);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Rule parse(JSONObject o) {
        try {
            Rule r = new Rule();
            r.id = Text.nz(o.optString("id"));
            r.scope = normalizeScope(o.optString("scope", SCOPE_JUMP));
            if (r.scope.isEmpty()) return null;
            String act = o.optString("action", ACT_ALERT);
            r.action = ACT_BLOCK.equals(act) || ACT_ALLOW.equals(act) ? act : ACT_ALERT;
            JSONObject m = o.optJSONObject("match");
            if (m != null) {
                r.src = normalizePkg(m.optString("src", ""));
                r.dst = normalizePkg(m.optString("dst", ""));
                r.keyword = Text.nz(m.optString("keyword", ""));
                r.regex = Text.nz(m.optString("regex", ""));
                JSONArray s = m.optJSONArray("sensitive");
                if (s != null) {
                    for (int k = 0; k < s.length(); k++) {
                        String v = s.optString(k, "");
                        if (!v.isEmpty()) r.sensitive.add(v);
                    }
                }
            }
            r.note = Text.nz(o.optString("note", ""));
            if (!r.regex.isEmpty()) {
                Pattern.compile(r.regex); // 编译失败会抛异常，由调用方拒绝
            }
            if (r.src.isEmpty() && r.dst.isEmpty() && r.keyword.isEmpty()
                    && r.regex.isEmpty() && r.sensitive.isEmpty()) {
                return null; // 空规则不接受
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public static String normalizeScope(String s) {
        String v = Text.nz(s).trim().toLowerCase();
        switch (v) {
            case SCOPE_JUMP:
            case SCOPE_CLIP:
            case SCOPE_SCREEN:
            case SCOPE_NOTIF:
                return v;
            default:
                return "";
        }
    }

    private static String normalizePkg(String s) {
        String v = Text.nz(s).trim();
        if (v.equals("*") || v.equalsIgnoreCase("any")) return "";
        return v;
    }

    /** 在给定作用域下评估规则。返回 null 表示无规则命中。 */
    public static Match evaluate(Context ctx, String scope, String src, String dst, String text) {
        for (Rule r : all(ctx)) {
            if (!r.scope.equals(scope)) continue;
            if (!matchPkg(r.src, src) || !matchPkg(r.dst, dst)) continue;
            if (!r.keyword.isEmpty()) {
                if (text == null || !text.contains(r.keyword)) continue;
            }
            if (!r.regex.isEmpty()) {
                if (text == null) continue;
                try {
                    if (!Pattern.compile(r.regex).matcher(text).find()) continue;
                } catch (Exception e) {
                    continue;
                }
            }
            if (!r.sensitive.isEmpty()) {
                if (text == null) continue;
                boolean any = false;
                for (String t : r.sensitive) {
                    if (text.toLowerCase().contains(t.toLowerCase())) {
                        any = true;
                        break;
                    }
                }
                if (!any) continue;
            }
            String reason = r.note.isEmpty()
                    ? ("命中功能包规则 " + (r.pluginId.isEmpty() ? r.id : r.pluginId + "/" + r.id))
                    : r.note;
            return new Match(r, r.action, reason);
        }
        return null;
    }

    private static boolean matchPkg(String pattern, String actual) {
        if (pattern == null || pattern.isEmpty()) return true;
        if (actual == null) return false;
        if (pattern.endsWith("*")) {
            return actual.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(actual);
    }
}

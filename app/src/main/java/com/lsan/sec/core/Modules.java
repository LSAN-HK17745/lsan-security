package com.lsan.sec.core;

import android.content.Context;

import com.lsan.sec.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 功能模块注册表：内建模块 + 已导入的功能包。 */
public final class Modules {

    public static final String CORE = "engine_core";
    public static final String JUMP = "jump_guard";
    public static final String CLIP = "clip_guard";
    public static final String SCREEN = "screen_guard";
    public static final String COLLUSION = "collusion_guard";
    public static final String SKIP = "skip_guard";
    public static final String NOTIF = "notif_guard";
    public static final String AUDIT = "perm_audit";
    public static final String EVENT = "event_center";

    private Modules() {}

    public static class Module {
        public String id;
        public String name;
        public String desc;
        public String category;   // 防护 / 监控 / 审计 / 扩展
        public boolean defaultOn;
        public boolean builtin;
        public String requires;   // 依赖的系统能力说明
        public String version;    // 插件版本

        public Module(String id, String name, String desc, String category,
                      boolean defaultOn, boolean builtin, String requires) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.category = category;
            this.defaultOn = defaultOn;
            this.builtin = builtin;
            this.requires = requires;
            this.version = builtin ? "内置" : "";
        }
    }

    public static List<Module> builtin() {
        List<Module> out = new ArrayList<>();
        out.add(new Module(CORE, "本地防护引擎", "事件汇聚、规则裁决与本地存储，纯离线运行，无联网能力。",
                "核心", true, true, "前台服务"));
        out.add(new Module(JUMP, "跳转守卫", "拦截应用之间的自动跳转：任何跨应用唤起都会弹窗询问，未同意则立即撤回。",
                "防护", true, true, "无障碍服务"));
        out.add(new Module(CLIP, "剪贴板守卫", "监控剪贴板内容，识别身份证、银行卡、验证码等敏感数据，并对读取行为告警。",
                "防护", true, true, "无障碍服务"));
        out.add(new Module(SCREEN, "屏幕内容守卫", "扫描当前界面中的敏感信息，识别被读屏类应用截取的风险。",
                "监控", true, true, "无障碍服务"));
        out.add(new Module(COLLUSION, "联合逃逸守卫", "分析已安装应用的\"读取能力 + 外发能力\"组合，识别 A 应用偷、B 应用传的合谋风险。",
                "监控", true, true, "应用清单权限"));
        out.add(new Module(SKIP, "开屏跳过",
                "应用启动时自动点击\"跳过 / 关闭广告\"控件。注意：部分应用的\"跳过\"本身就是广告位，"
                        + "点击可能触发跳转或下载；自绘控件（部分广告 SDK）也不可见。建议配合下方说明按需关闭。",
                "防护", true, true, "无障碍服务"));
        out.add(new Module(NOTIF, "通知泄露守卫", "监控通知栏中的验证码与敏感通知，识别被第三方监听读取。",
                "监控", false, true, "通知使用权"));
        out.add(new Module(AUDIT, "应用行为审计", "逐条评估应用权限、签名与安装来源，输出风险排序与处置建议。",
                "审计", true, true, "应用清单权限"));
        out.add(new Module(EVENT, "事件中心", "本地时间线记录全部防护动作，可检索、可导出。",
                "审计", true, true, "本地数据库"));
        return out;
    }

    /** 全部模块（内建 + 导入的功能包）。 */
    public static List<Module> all(Context ctx) {
        List<Module> out = new ArrayList<>(builtin());
        out.addAll(plugins(ctx));
        return out;
    }

    /** 已导入的功能包模块。 */
    public static List<Module> plugins(Context ctx) {
        Prefs.get().init(ctx);
        List<Module> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get().pluginsJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Module m = new Module(
                        o.optString("id"),
                        o.optString("name", "未命名功能包"),
                        o.optString("desc", ""),
                        "扩展",
                        o.optBoolean("defaultOn", true),
                        false,
                        o.optString("requires", "自定义规则"));
                m.version = o.optString("version", "1.0");
                if (m.id.isEmpty()) continue;
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Module find(Context ctx, String id) {
        for (Module m : all(ctx)) if (m.id.equals(id)) return m;
        return null;
    }

    public static boolean isEnabled(Context ctx, String id) {
        Prefs.get().init(ctx);
        Module m = find(ctx, id);
        boolean def = m == null || m.defaultOn;
        return Prefs.get().moduleEnabled(id, def);
    }

    public static String label(Context ctx, String id) {
        Module m = find(ctx, id);
        return m == null ? id : m.name;
    }
}

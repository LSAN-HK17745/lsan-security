package com.lsan.sec.data;

/** 一条安全事件。 */
public class Event {

    public long id;
    public long ts;
    public String type;       // jump / clip / screen / collusion / notif / flow / system
    public String src;        // 源应用包名
    public String dst;        // 目标应用包名
    public String summary;    // 一行摘要
    public String detail;     // 详情（已脱敏）
    public String verdict;    // allow / block / alert / info
    public String level;      // info / warn / high

    public Event(String type, String src, String dst, String summary) {
        this.ts = System.currentTimeMillis();
        this.type = type;
        this.src = src;
        this.dst = dst;
        this.summary = summary;
        this.verdict = "info";
        this.level = "info";
    }

    public Event verdict(String v) { this.verdict = v; return this; }
    public Event level(String l) { this.level = l; return this; }
    public Event detail(String d) { this.detail = d; return this; }

    public static String typeLabel(String type) {
        switch (type) {
            case "jump": return "跳转拦截";
            case "clip": return "剪贴板";
            case "screen": return "屏幕内容";
            case "collusion": return "联合风险";
            case "notif": return "通知泄露";
            case "flow": return "数据流向";
            case "skip": return "开屏跳过";
            case "system": return "系统";
            default: return "事件";
        }
    }

    public static String verdictLabel(String verdict) {
        switch (verdict) {
            case "allow": return "已放行";
            case "block": return "已阻止";
            case "alert": return "已告警";
            default: return "记录";
        }
    }
}

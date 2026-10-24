package com.lsan.sec.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.Modules;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Text;

import java.util.List;

/** 总览页：组件状态、今日统计、快捷开关、最新事件。 */
public class HomePage implements Page {

    private final MainActivity act;
    private LinearLayout content;

    public HomePage(MainActivity act) {
        this.act = act;
    }

    @Override
    public String title() {
        return "总览";
    }

    @Override
    public View build() {
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        content = UiKit.vertical(act);
        int p = UiKit.dp(act, 16);
        content.setPadding(p, p, p, p);
        sv.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();

        boolean acc = accessibilityEnabled(act);
        boolean notif = notificationListenerEnabled(act);
        boolean engineRunning = Prefs.get().masterEnabled();

        // ---------- 组件状态 ----------
        LinearLayout statusCard = UiKit.card(act);
        LinearLayout titleRow = UiKit.horizontal(act);
        titleRow.addView(UiKit.text(act, "防护组件", 16, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusCard.addView(titleRow);

        TextView tip = UiKit.text(act,
                "所有判定均在本机完成，应用不具备联网能力。", 11, UiKit.p().textFaint, false);
        tip.setPadding(0, UiKit.dp(act, 4), 0, UiKit.dp(act, 6));
        statusCard.addView(tip);

        statusCard.addView(UiKit.statusRow(act, "无障碍守卫",
                acc ? "运行中 · 可拦截应用跳转" : "未开启 · 跳转无法拦截",
                acc, acc ? null : "去开启", acc ? null : act::openAccessibilitySettings));

        statusCard.addView(UiKit.statusRow(act, "通知监控",
                notif ? "运行中 · 验证码通知可见" : "未开启 · 通知泄露不可见",
                notif, notif ? null : "去开启", notif ? null : act::openNotificationListenerSettings));

        statusCard.addView(UiKit.statusRow(act, "本地防护引擎",
                engineRunning ? "运行中 · 事件持续落库" : "已暂停 · 仅记录高危事件",
                engineRunning, null, null));

        content.addView(statusCard, UiKit.cardLp(act));

        // ---------- 今日统计 ----------
        GuardEngine.Stats s = GuardEngine.get().stats();
        LinearLayout statCard = UiKit.card(act);
        statCard.addView(UiKit.text(act, "今日态势", 16, UiKit.p().text, true));
        LinearLayout statRow = UiKit.horizontal(act);
        statRow.setPadding(0, UiKit.dp(act, 12), 0, 0);
        statRow.addView(UiKit.statCell(act, String.valueOf(s.jumpBlocked), "拦截跳转", UiKit.p().danger),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statRow.addView(UiKit.statCell(act, String.valueOf(s.sensitiveAlerts), "敏感事件", UiKit.p().warn),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statRow.addView(UiKit.statCell(act, String.valueOf(s.todayEvents), "今日记录", UiKit.p().accent),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statCard.addView(statRow);

        TextView sum = UiKit.text(act,
                "累计阻止 " + s.blockedTotal + " 次 · 跳转拦截共 " + s.jumpTotal + " 次",
                11, UiKit.p().textFaint, false);
        sum.setPadding(0, UiKit.dp(act, 10), 0, 0);
        statCard.addView(sum);
        content.addView(statCard, UiKit.cardLp(act));

        // ---------- 拦截档位 ----------
        LinearLayout levelCard = UiKit.card(act);
        levelCard.addView(UiKit.text(act, "拦截档位", 16, UiKit.p().text, true));
        TextView levelNow = UiKit.text(act,
                "当前：" + Prefs.policyName(Prefs.get().policyLevel()),
                UiKit.p().smallSp, UiKit.p().textFaint, false);
        levelNow.setPadding(0, UiKit.dp(act, 4), 0, UiKit.dp(act, 10));
        levelCard.addView(levelNow);
        levelCard.addView(UiKit.segmented(act,
                new String[]{"宽松", "中等", "严格"}, Prefs.get().policyLevel(), null, i -> {
                    Prefs.get().setPolicyLevel(i);
                    GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                            "拦截档位切换为「" + Prefs.policyName(i) + "」")
                            .verdict("info").level("info"));
                    refresh();
                }));
        TextView levelDesc = UiKit.text(act, Prefs.policyDesc(Prefs.get().policyLevel()),
                UiKit.p().smallSp, UiKit.p().textDim, false);
        levelDesc.setPadding(0, UiKit.dp(act, 10), 0, 0);
        levelCard.addView(levelDesc);
        content.addView(levelCard, UiKit.cardLp(act));

        // ---------- 快捷开关 ----------
        LinearLayout quick = UiKit.card(act);
        quick.addView(UiKit.text(act, "核心防护", 16, UiKit.p().text, true));
        addQuickSwitch(quick, Modules.JUMP, "跳转守卫", "跨应用唤起必须经你同意");
        addQuickSwitch(quick, Modules.CLIP, "剪贴板守卫", "识别身份证 / 银行卡 / 验证码");
        addQuickSwitch(quick, Modules.SCREEN, "屏幕内容守卫", "识别界面中的敏感数据");
        addQuickSwitch(quick, Modules.COLLUSION, "联合逃逸守卫", "识别 A 偷 B 传的合谋风险");
        addQuickSwitch(quick, Modules.SKIP, "开屏跳过", "自动点掉应用启动广告，带误点防护");
        content.addView(quick, UiKit.cardLp(act));

        // ---------- 最新事件 ----------
        List<Event> recent = GuardEngine.get().recentEvents(3);
        LinearLayout events = UiKit.card(act);
        LinearLayout evTitle = UiKit.horizontal(act);
        evTitle.addView(UiKit.text(act, "最新事件", 16, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        events.addView(evTitle);

        if (recent.isEmpty()) {
            TextView empty = UiKit.text(act, "暂无事件。开启防护后，跳转与敏感行为会记录在此。",
                    12, UiKit.p().textFaint, false);
            empty.setPadding(0, UiKit.dp(act, 10), 0, 0);
            events.addView(empty);
        } else {
            for (Event e : recent) {
                events.addView(UiKit.divider(act));
                events.addView(eventRow(e));
            }
        }
        content.addView(events, UiKit.cardLp(act));

        content.addView(UiKit.spacer(act, 12));
    }

    private void addQuickSwitch(LinearLayout parent, String id, String name, String desc) {
        Modules.Module def = Modules.find(act, id);
        boolean on = Prefs.get().moduleEnabled(id, def == null || def.defaultOn);
        Switch[] holder = new Switch[1];
        LinearLayout row = UiKit.switchRow(act, name, desc, on, (b, checked) -> {
            Prefs.get().setModuleEnabled(id, checked);
            UiKit.setSwitchVisual(b, checked);
            GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                    "模块「" + name + "」已" + (checked ? "开启" : "关闭"))
                    .verdict("info").level("info"));
        });
        holder[0] = (Switch) ((ViewGroup) row).getChildAt(1);
        row.setPadding(0, UiKit.dp(act, 8), 0, 0);
        parent.addView(row);
    }

    private View eventRow(Event e) {
        LinearLayout row = UiKit.vertical(act);
        row.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 6));
        LinearLayout head = UiKit.horizontal(act);
        int color = levelColor(e.level);
        head.addView(UiKit.pill(act, Event.typeLabel(e.type), color));
        TextView time = UiKit.text(act, Text.ago(e.ts), 11, UiKit.p().textFaint, false);
        time.setPadding(UiKit.dp(act, 8), 0, 0, 0);
        head.addView(time);
        row.addView(head);
        TextView sum = UiKit.text(act, Text.clip(e.summary, 90), 13, UiKit.p().text, false);
        sum.setPadding(0, UiKit.dp(act, 6), 0, 0);
        row.addView(sum);
        return row;
    }

    public static int levelColor(String level) {
        if ("high".equals(level)) return UiKit.p().danger;
        if ("warn".equals(level)) return UiKit.p().warn;
        return UiKit.p().accent;
    }

    // ---------------- 状态检测 ----------------

    public static boolean accessibilityEnabled(Context c) {
        try {
            AccessibilityManager am = (AccessibilityManager) c.getSystemService(
                    Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : list) {
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                        && c.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean notificationListenerEnabled(Context c) {
        try {
            String flat = Settings.Secure.getString(c.getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(c.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }
}

package com.lsan.sec.ui;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.data.Db;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Sensitive;
import com.lsan.sec.util.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

/** 设置页：策略参数、敏感类型、白名单、数据与关于。 */
public class SettingsPage implements Page {

    private final MainActivity act;
    private LinearLayout content;

    public SettingsPage(MainActivity act) {
        this.act = act;
    }

    @Override
    public String title() {
        return "设置";
    }

    @Override
    public View build() {
        ScrollView sv = new ScrollView(act);
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

        // ---------- 开屏跳过：适用范围与风险 ----------
        LinearLayout sk = UiKit.card(act);
        sk.addView(UiKit.text(act, "开屏跳过：适用范围", 16, UiKit.p().text, true));

        TextView ok1 = UiKit.text(act,
                "✓ 节点可见的「跳过 / 关闭广告」按钮：有效（已验证）",
                UiKit.p().smallSp, UiKit.p().textDim, false);
        ok1.setPadding(0, UiKit.dp(act, 8), 0, 0);
        sk.addView(ok1);

        TextView no1 = UiKit.text(act,
                "✗ 广告 SDK 自绘控件（节点树里没有该按钮）：无法点击，属平台限制",
                UiKit.p().smallSp, UiKit.p().textDim, false);
        no1.setPadding(0, UiKit.dp(act, 6), 0, 0);
        sk.addView(no1);

        TextView warn = UiKit.text(act,
                "⚠ 部分软件的「跳过」本身就是广告位——点击可能触发跳转、下载或打开第三方页面。"
                        + "本功能只负责点按，无法判断该按钮的真伪，因此对该类应用请单独关闭跳过。",
                UiKit.p().smallSp + 1, UiKit.p().warn, false);
        warn.setPadding(0, UiKit.dp(act, 10), 0, UiKit.dp(act, 6));
        sk.addView(warn);

        TextView skSwitch = UiKit.text(act,
                "当前「开屏跳过」总开关：" + (Prefs.get().moduleEnabled(com.lsan.sec.core.Modules.SKIP,
                        com.lsan.sec.core.Modules.find(act, com.lsan.sec.core.Modules.SKIP).defaultOn) ? "开启" : "关闭")
                        + "（可在「模块」页切换）",
                UiKit.p().smallSp, UiKit.p().textFaint, false);
        skSwitch.setPadding(0, UiKit.dp(act, 4), 0, UiKit.dp(act, 8));
        sk.addView(skSwitch);

        // 已禁用的应用列表
        java.util.Set<String> skipOff = Prefs.get().skipDisabledPackages();
        TextView listTitle = UiKit.text(act,
                "已单独关闭跳过的应用（" + skipOff.size() + "）",
                UiKit.p().smallSp, UiKit.p().textDim, false);
        sk.addView(listTitle);
        if (skipOff.isEmpty()) {
            TextView empty = UiKit.text(act, "暂无。可在「审计」页点开某个应用 → 关闭跳过。",
                    UiKit.p().smallSp, UiKit.p().textFaint, false);
            empty.setPadding(0, UiKit.dp(act, 4), 0, 0);
            sk.addView(empty);
        } else {
            for (String pkg : skipOff) {
                LinearLayout row = UiKit.horizontal(act);
                row.setPadding(0, UiKit.dp(act, 5), 0, UiKit.dp(act, 5));
                row.addView(UiKit.text(act, pkg, UiKit.p().smallSp, UiKit.p().textDim, false),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                android.widget.Button en = UiKit.ghost(act, "恢复");
                en.setOnClickListener(v -> {
                    Prefs.get().setSkipDisabled(pkg, false);
                    refresh();
                });
                row.addView(en);
                sk.addView(row);
            }
        }
        content.addView(sk, UiKit.cardLp(act));

        // ---------- 拦截档位 ----------
        LinearLayout lvCard = UiKit.card(act);
        lvCard.addView(UiKit.text(act, "拦截档位", 16, UiKit.p().text, true));
        TextView lvHint = UiKit.text(act, Prefs.policyDesc(Prefs.get().policyLevel()),
                UiKit.p().smallSp, UiKit.p().textFaint, false);
        lvHint.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 10));
        lvCard.addView(lvHint);
        lvCard.addView(UiKit.segmented(act,
                new String[]{"宽松", "中等", "严格"}, Prefs.get().policyLevel(), null, i -> {
                    Prefs.get().setPolicyLevel(i);
                    GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                            "拦截档位切换为「" + Prefs.policyName(i) + "」")
                            .verdict("info").level("info"));
                    refresh();
                }));
        android.widget.Button syncIcon = UiKit.ghost(act,
                "同步桌面图标为当前外观（会重开界面）");
        syncIcon.setOnClickListener(v -> {
            Toast.makeText(act, "正在切换桌面图标…", Toast.LENGTH_SHORT).show();
            act.applyLauncherIconNow();
        });
        lvCard.addView(syncIcon);
        content.addView(lvCard, UiKit.cardLp(act));

        // ---------- 行为策略 ----------
        LinearLayout policy = UiKit.card(act);
        policy.addView(UiKit.text(act, "跳转策略", 16, UiKit.p().text, true));

        policy.addView(UiKit.switchRow(act, "严格模式",
                "关闭后，你主动点击引发的跳转将直接放行（静默跳转仍会拦截）",
                Prefs.get().strictJump(), (b, checked) -> {
                    Prefs.get().setStrictJump(checked);
                    UiKit.setSwitchVisual(b, checked);
                }));

        policy.addView(UiKit.switchRow(act, "阻止时返回上一层",
                "拒绝跳转时自动执行返回，回到原来的应用界面",
                Prefs.get().blockWithBack(), (b, checked) -> {
                    Prefs.get().setBlockWithBack(checked);
                    UiKit.setSwitchVisual(b, checked);
                }));

        policy.addView(valueRow("静默跳转判定阈值",
                Prefs.get().silentThresholdMs() + " ms",
                "无操作超过该时长后发生的跳转视为静默跳转", v -> {
                    int cur = Prefs.get().silentThresholdMs();
                    int next = cur < 1000 ? 1500 : cur < 2000 ? 3000 : 800;
                    Prefs.get().setSilentThresholdMs(next);
                    refresh();
                }));

        policy.addView(valueRow("确认弹窗倒计时",
                Prefs.get().promptCountdown() + " 秒", "超时自动终止跳转", v -> {
                    int cur = Prefs.get().promptCountdown();
                    int next = cur >= 10 ? 3 : cur + 1;
                    Prefs.get().setPromptCountdown(next);
                    refresh();
                }));

        policy.addView(UiKit.switchRow(act, "开机自动启动防护",
                "开机后自动恢复本地防护引擎",
                Prefs.get().bootStart(), (b, checked) -> {
                    Prefs.get().setBootStart(checked);
                    UiKit.setSwitchVisual(b, checked);
                }));

        content.addView(policy, UiKit.cardLp(act));

        // ---------- 敏感类型 ----------
        LinearLayout sens = UiKit.card(act);
        sens.addView(UiKit.text(act, "敏感信息识别", 16, UiKit.p().text, true));
        TextView tip = UiKit.text(act, "关闭后不再扫描对应类型，可降低误报与耗电。",
                11, UiKit.p().textFaint, false);
        tip.setPadding(0, UiKit.dp(act, 4), 0, UiKit.dp(act, 4));
        sens.addView(tip);
        addSensSwitch(sens, Sensitive.TYPE_ID_CARD, "身份证号");
        addSensSwitch(sens, Sensitive.TYPE_BANK_CARD, "银行卡号");
        addSensSwitch(sens, Sensitive.TYPE_PHONE, "手机号");
        addSensSwitch(sens, Sensitive.TYPE_OTP, "验证码");
        addSensSwitch(sens, Sensitive.TYPE_EMAIL, "邮箱");
        addSensSwitch(sens, Sensitive.TYPE_PASSWORD, "口令字段");
        addSensSwitch(sens, Sensitive.TYPE_ADDRESS, "详细住址");
        content.addView(sens, UiKit.cardLp(act));

        // ---------- 白名单 ----------
        LinearLayout white = UiKit.card(act);
        LinearLayout wh = UiKit.horizontal(act);
        wh.addView(UiKit.text(act, "应用白名单", 16, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Set<String> wl = Prefs.get().whitelist();
        wh.addView(UiKit.pill(act, wl.size() + " 个", UiKit.p().textDim));
        white.addView(wh);

        TextView wd = UiKit.text(act, "白名单应用之间的跳转不再弹窗询问。",
                11, UiKit.p().textFaint, false);
        wd.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 6));
        white.addView(wd);

        if (wl.isEmpty()) {
            white.addView(UiKit.text(act, "暂无白名单应用。", 12, UiKit.p().textDim, false));
        } else {
            for (String pkg : wl) {
                LinearLayout row = UiKit.horizontal(act);
                row.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 6));
                row.addView(UiKit.text(act, pkg, 12, UiKit.p().textDim, false),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                android.widget.Button del = UiKit.danger(act, "移除");
                del.setOnClickListener(v -> {
                    Prefs.get().removeWhitelist(pkg);
                    refresh();
                });
                row.addView(del);
                white.addView(row);
            }
        }

        android.widget.Button revoke = UiKit.ghost(act, "清除全部跳转批准记录");
        revoke.setOnClickListener(v -> {
            Prefs.get().revokeJumpApprovals();
            Toast.makeText(act, "已清除，下次跳转将重新询问", Toast.LENGTH_SHORT).show();
            refresh();
        });
        revoke.setPadding(0, UiKit.dp(act, 8), 0, UiKit.dp(act, 8));
        white.addView(revoke);
        content.addView(white, UiKit.cardLp(act));

        // ---------- 设备适配 ----------
        LinearLayout dev = UiKit.card(act);
        dev.addView(UiKit.text(act, "设备适配", 16, UiKit.p().text, true));
        TextView dv = UiKit.text(act,
                UiKit.p().mono
                        ? "> 机型: " + com.lsan.sec.core.VendorAdapter.deviceSummary()
                        : com.lsan.sec.core.VendorAdapter.deviceSummary(),
                UiKit.p().smallSp, UiKit.p().textDim, false);
        dv.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 2));
        dev.addView(dv);
        TextView vd = UiKit.text(act, "ROM：" + com.lsan.sec.core.VendorAdapter.vendorLabel(),
                UiKit.p().smallSp, UiKit.p().textFaint, false);
        dev.addView(vd);
        TextView kc = UiKit.text(act, "终止能力：" + com.lsan.sec.core.JumpKiller.capabilityLabel(act),
                UiKit.p().smallSp, UiKit.p().textFaint, false);
        kc.setPadding(0, UiKit.dp(act, 2), 0, UiKit.dp(act, 8));
        dev.addView(kc);

        TextView guideTitle = UiKit.text(act, "本机型需要开启：", UiKit.p().smallSp,
                UiKit.p().textDim, false);
        guideTitle.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 4));
        dev.addView(guideTitle);
        for (com.lsan.sec.core.VendorAdapter.Guide g
                : com.lsan.sec.core.VendorAdapter.guides(act)) {
            LinearLayout row = UiKit.horizontal(act);
            row.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 6));
            LinearLayout col = UiKit.vertical(act);
            col.addView(UiKit.text(act, g.title, UiKit.p().smallSp + 2, UiKit.p().text,
                    UiKit.p().mono));
            TextView desc = UiKit.text(act, g.desc, UiKit.p().smallSp, UiKit.p().textFaint, false);
            desc.setPadding(0, UiKit.dp(act, 2), 0, 0);
            col.addView(desc);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (g.intent != null) {
                android.widget.Button go = UiKit.ghost(act, "去设置");
                go.setOnClickListener(v -> {
                    try {
                        act.startActivity(g.intent);
                    } catch (Exception e) {
                        Toast.makeText(act, "无法打开该系统页面", Toast.LENGTH_SHORT).show();
                    }
                });
                row.addView(go);
            }
            dev.addView(row);
        }
        content.addView(dev, UiKit.cardLp(act));

        // ---------- 主动体检 ----------
        LinearLayout check = UiKit.card(act);
        check.addView(UiKit.text(act, "主动体检", 16, UiKit.p().text, true));
        TextView cd = UiKit.text(act,
                "Android 10+ 限制后台应用读取剪贴板内容。需要时可在本页主动读取一次当前剪贴板并做敏感扫描。",
                11, UiKit.p().textFaint, false);
        cd.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 8));
        check.addView(cd);

        android.widget.Button clipCheck = UiKit.primary(act, "检查当前剪贴板");
        clipCheck.setOnClickListener(v -> checkClipboard());
        check.addView(clipCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.Button scanNow = UiKit.ghost(act, "立即重扫屏幕与风险");
        scanNow.setPadding(0, UiKit.dp(act, 8), 0, 0);
        scanNow.setOnClickListener(v -> {
            GuardEngine.get().notifyStats();
            Toast.makeText(act, "已刷新统计；屏幕扫描由无障碍服务实时进行", Toast.LENGTH_SHORT).show();
        });
        check.addView(scanNow);
        content.addView(check, UiKit.cardLp(act));

        // ---------- 数据 ----------
        LinearLayout data = UiKit.card(act);
        data.addView(UiKit.text(act, "本地数据", 16, UiKit.p().text, true));

        data.addView(valueRow("事件保留天数",
                Prefs.get().retentionDays() + " 天", "超期自动清理，仅保留本机", v -> {
                    int cur = Prefs.get().retentionDays();
                    int next = cur >= 90 ? 7 : cur * 2;
                    Prefs.get().setRetentionDays(next);
                    GuardEngine.get().maintenance();
                    refresh();
                }));

        LinearLayout dataRow = UiKit.horizontal(act);
        dataRow.setPadding(0, UiKit.dp(act, 8), 0, 0);
        android.widget.Button export = UiKit.ghost(act, "导出本地日志");
        export.setOnClickListener(v -> exportLog());
        android.widget.Button clear = UiKit.danger(act, "清空事件");
        clear.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dlp.setMargins(0, 0, UiKit.dp(act, 8), 0);
        dataRow.addView(export, dlp);
        dataRow.addView(clear, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        data.addView(dataRow);
        content.addView(data, UiKit.cardLp(act));

        // ---------- 关于 ----------
        LinearLayout about = UiKit.card(act);
        about.addView(UiKit.text(act, "关于 LSAN", 16, UiKit.p().text, true));
        TextView info = UiKit.text(act,
                "版本 " + com.lsan.sec.BuildConfig.VERSION_NAME
                        + (com.lsan.sec.BuildConfig.DEV_SERVER ? "（DEV · 调试通道开启）" : "")
                        + " · 纯本地个人信息防护\n\n"
                        + "本应用未声明 INTERNET 权限，在系统层面不具备联网能力；"
                        + "所有规则判定、事件记录与数据存储均在本机完成，不采集、不上传任何信息。\n\n"
                        + "功能：应用间跳转拦截（三档策略，拒绝时终止目标）、剪贴板与屏幕敏感信息监控、"
                        + "应用联合逃逸分析、通知泄露监控、应用权限审计、开屏跳过、声明式功能包导入。\n\n"
                        + "数据位置：本机私有目录 " + act.getApplicationInfo().dataDir,
                11, UiKit.p().textDim, false);
        info.setPadding(0, UiKit.dp(act, 8), 0, 0);
        about.addView(info);

        android.widget.Button refreshStats = UiKit.ghost(act, "重算统计与清理");
        refreshStats.setOnClickListener(v -> {
            GuardEngine.get().maintenance();
            GuardEngine.get().notifyStats();
            Toast.makeText(act, "已清理过期事件", Toast.LENGTH_SHORT).show();
        });
        refreshStats.setPadding(0, UiKit.dp(act, 10), 0, UiKit.dp(act, 4));
        about.addView(refreshStats);
        content.addView(about, UiKit.cardLp(act));

        content.addView(UiKit.spacer(act, 16));
    }

    private void addSensSwitch(LinearLayout parent, String type, String name) {
        boolean on = Prefs.get().sens(type);
        LinearLayout row = UiKit.switchRow(act, name, "", on, (b, checked) -> {
            Prefs.get().setSens(type, checked);
            UiKit.setSwitchVisual(b, checked);
        });
        row.setPadding(0, UiKit.dp(act, 6), 0, 0);
        parent.addView(row);
    }

    private LinearLayout valueRow(String title, String value, String desc, View.OnClickListener onClick) {
        LinearLayout row = UiKit.horizontal(act);
        row.setPadding(0, UiKit.dp(act, 10), 0, UiKit.dp(act, 4));
        LinearLayout left = UiKit.vertical(act);
        left.addView(UiKit.text(act, title, 14, UiKit.p().text, false));
        TextView d = UiKit.text(act, desc, 10, UiKit.p().textFaint, false);
        d.setPadding(0, UiKit.dp(act, 2), 0, 0);
        left.addView(d);
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button btn = UiKit.ghost(act, value);
        btn.setOnClickListener(onClick);
        row.addView(btn);
        return row;
    }

    private void exportLog() {
        try {
            File dir = new File(act.getFilesDir(), "exports");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "murphy_events_" + System.currentTimeMillis() + ".txt");
            String text = Db.get(act).exportText(2000);
            if (text.isEmpty()) text = "（当前没有事件记录）\n";
            String header = "墨菲安全 本地事件导出\n生成时间：" + Text.time(System.currentTimeMillis())
                    + "\n说明：本文件仅存在于本机，未上传。\n\n";
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write((header + text).getBytes("UTF-8"));
            }
            new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("已导出")
                    .setMessage("文件已保存到本机：\n" + f.getAbsolutePath()
                            + "\n\n大小 " + f.length() + " 字节。整个过程未联网。")
                    .setPositiveButton("好", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(act, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("清空事件")
                .setMessage("将删除本机全部事件记录，不可恢复。确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    GuardEngine.get().clearAllEvents();
                    GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                            "事件记录已被清空").verdict("info").level("info"));
                    Toast.makeText(act, "已清空", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    private void checkClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) {
                Toast.makeText(act, "剪贴板为空", Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipData clip = cm.getPrimaryClip();
            CharSequence t = clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(act) : null;
            String text = t == null ? "" : t.toString();
            if (text.isEmpty()) {
                Toast.makeText(act, "剪贴板内容不可读（可能为图片或受系统限制）",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs p = Prefs.get();
            java.util.List<Sensitive.Hit> hits = Sensitive.scan(text,
                    p.sens(Sensitive.TYPE_ID_CARD), p.sens(Sensitive.TYPE_BANK_CARD),
                    p.sens(Sensitive.TYPE_PHONE), p.sens(Sensitive.TYPE_OTP),
                    p.sens(Sensitive.TYPE_EMAIL), p.sens(Sensitive.TYPE_PASSWORD),
                    p.sens(Sensitive.TYPE_ADDRESS));

            StringBuilder sb = new StringBuilder();
            sb.append("长度：").append(text.length()).append(" 字符\n\n");
            if (hits.isEmpty()) {
                sb.append("未发现敏感信息。");
            } else {
                sb.append("命中 ").append(hits.size()).append(" 项：\n");
                for (Sensitive.Hit h : hits) {
                    sb.append("· ").append(Sensitive.label(h.type)).append(' ')
                            .append(h.masked);
                    if (!h.verified) sb.append("（疑似，未通过强校验）");
                    sb.append('\n');
                }
            }
            sb.append("\n（本次读取仅在你主动点击时发生，内容不落库、不上传）");

            new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("剪贴板体检结果")
                    .setMessage(sb.toString())
                    .setPositiveButton("好", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(act, "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

}

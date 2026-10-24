package com.lsan.sec.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lsan.sec.core.AppScanner;
import com.lsan.sec.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** 审计页：应用权限画像、风险评分、联合逃逸分析。 */
public class AuditPage implements Page {

    private static AppScanner.Result cache;

    private final MainActivity act;
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout header;
    private ListView list;
    private AppAdapter adapter;
    private boolean scanning = false;

    public AuditPage(MainActivity act) {
        this.act = act;
    }

    @Override
    public String title() {
        return "审计";
    }

    @Override
    public View build() {
        root = UiKit.vertical(act);
        root.setBackgroundColor(UiKit.p().bg);

        android.widget.ScrollView headScroll = new android.widget.ScrollView(act);
        header = UiKit.vertical(act);
        int p = UiKit.dp(act, 16);
        header.setPadding(p, p, p, 0);
        headScroll.addView(header, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(headScroll);

        list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(UiKit.dp(act, 12), UiKit.dp(act, 6), UiKit.dp(act, 12), UiKit.dp(act, 12));
        list.setClipToPadding(false);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    @Override
    public void refresh() {
        if (root == null) return;
        if (cache == null) {
            startScan();
        } else {
            render();
        }
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        renderHeaderLoading();
        new Thread(() -> {
            AppScanner.Result r;
            try {
                r = AppScanner.scan(act);
            } catch (Exception e) {
                r = new AppScanner.Result();
            }
            final AppScanner.Result fr = r;
            main.post(() -> {
                cache = fr;
                scanning = false;
                render();
            });
        }, "murphy-scan").start();
    }

    private void renderHeaderLoading() {
        if (header == null) return;
        header.removeAllViews();
        LinearLayout card = UiKit.card(act);
        card.addView(UiKit.text(act, "正在扫描本机应用…", 15, UiKit.p().text, true));
        card.addView(UiKit.text(act, "读取应用清单、权限与签名，全部在本机完成。",
                11, UiKit.p().textFaint, false));
        header.addView(card, UiKit.cardLp(act));
    }

    private void render() {
        if (header == null || cache == null) return;
        header.removeAllViews();

        // 统计
        LinearLayout stat = UiKit.card(act);
        LinearLayout titleRow = UiKit.horizontal(act);
        titleRow.addView(UiKit.text(act, "本机应用画像", 16, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button rescan = UiKit.ghost(act, "重新扫描");
        rescan.setOnClickListener(v -> {
            cache = null;
            startScan();
        });
        titleRow.addView(rescan);
        stat.addView(titleRow);

        LinearLayout row = UiKit.horizontal(act);
        row.setPadding(0, UiKit.dp(act, 12), 0, 0);
        row.addView(UiKit.statCell(act, String.valueOf(cache.apps.size()), "应用", UiKit.p().text),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(UiKit.statCell(act, String.valueOf(cache.accessibilityCount), "无障碍服务", UiKit.p().danger),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(UiKit.statCell(act, String.valueOf(cache.highRiskCount), "高风险", UiKit.p().warn),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stat.addView(row);

        TextView when = UiKit.text(act, "扫描时间 " + com.lsan.sec.util.Text.time(cache.scannedAt),
                10, UiKit.p().textFaint, false);
        when.setPadding(0, UiKit.dp(act, 8), 0, 0);
        stat.addView(when);
        header.addView(stat, UiKit.cardLp(act));

        if (cache.apps.size() <= 1) {
            LinearLayout tip = UiKit.card(act);
            tip.addView(UiKit.pill(act, "权限受限", UiKit.p().warn));
            TextView tt = UiKit.text(act,
                    "只读取到 " + cache.apps.size() + " 个应用。部分定制系统"
                            + "（如 ColorOS / realme UI）把「读取应用列表」作为独立权限管控，"
                            + "请在系统设置中允许本应用读取应用列表后重新扫描。",
                    12, UiKit.p().textDim, false);
            tt.setPadding(0, UiKit.dp(act, 8), 0, UiKit.dp(act, 8));
            tip.addView(tt);
            android.widget.Button go = UiKit.ghost(act, "打开应用权限设置");
            go.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + act.getPackageName()));
                    act.startActivity(i);
                } catch (Exception ignored) {
                }
            });
            tip.addView(go);
            header.addView(tip, UiKit.cardLp(act));
        }

        // 联合风险
        if (!cache.collusions.isEmpty()) {
            for (AppScanner.Collusion c : cache.collusions) {
                LinearLayout card = UiKit.card(act);
                LinearLayout hr = UiKit.horizontal(act);
                hr.addView(UiKit.pill(act, "联合风险",
                        "high".equals(c.level) ? UiKit.p().danger : UiKit.p().warn));
                hr.addView(UiKit.text(act, c.title, 14, UiKit.p().text, true),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(hr);

                TextView d = UiKit.text(act, c.detail, 12, UiKit.p().textDim, false);
                d.setPadding(0, UiKit.dp(act, 8), 0, 0);
                card.addView(d);
                header.addView(card, UiKit.cardLp(act));
            }
        }

        adapter.setData(cache.apps);
    }

    private class AppAdapter extends BaseAdapter {
        private List<AppScanner.App> data = new ArrayList<>();

        void setData(List<AppScanner.App> d) {
            this.data = d;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }

        @Override public Object getItem(int i) { return data.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            AppScanner.App a = data.get(i);
            LinearLayout card = UiKit.card(act);

            LinearLayout head = UiKit.horizontal(act);
            LinearLayout left = UiKit.vertical(act);
            left.addView(UiKit.text(act, a.label, 15, UiKit.p().text, true));
            TextView pkg = UiKit.text(act, a.pkg, 10, UiKit.p().textFaint, false);
            pkg.setPadding(0, UiKit.dp(act, 2), 0, 0);
            left.addView(pkg);
            head.addView(left, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            int color = a.score >= 60 ? UiKit.p().danger : a.score >= 35 ? UiKit.p().warn : UiKit.p().accent;
            head.addView(UiKit.pill(act, "风险 " + a.score, color));
            card.addView(head);

            if (!a.risks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int n = Math.min(3, a.risks.size());
                for (int k = 0; k < n; k++) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(a.risks.get(k));
                }
                if (a.risks.size() > n) sb.append(" 等 ").append(a.risks.size()).append(" 项");
                TextView r = UiKit.text(act, sb.toString(), 11, UiKit.p().textDim, false);
                r.setPadding(0, UiKit.dp(act, 8), 0, 0);
                card.addView(r);
            }

            card.setOnClickListener(v -> showDetail(a));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, UiKit.dp(act, 10));
            card.setLayoutParams(lp);
            return card;
        }
    }

    private void showDetail(AppScanner.App a) {
        StringBuilder sb = new StringBuilder();
        sb.append("包名：").append(a.pkg).append('\n');
        sb.append("风险分：").append(a.score).append(" / 100\n");
        sb.append("签名：").append(a.sig.isEmpty() ? "未知" : a.sig).append('\n');
        sb.append("安装来源：").append(AppScanner.installerLabel(a.installer)).append('\n');
        sb.append("系统应用：").append(a.system ? "是" : "否").append('\n');
        sb.append("联网能力：").append(a.hasInternet ? "有（INTERNET）" : "无").append('\n');
        sb.append("无障碍服务：")
                .append(a.accessibilityEnabled ? "已启用（可读屏）"
                        : a.declaresAccessibility ? "已声明未启用" : "无")
                .append('\n');
        sb.append("通知监听：").append(a.declaresNotifListener ? "已声明" : "无").append('\n');
        sb.append('\n');
        if (a.risks.isEmpty()) {
            sb.append("未发现明显风险项。");
        } else {
            for (String r : a.risks) sb.append("· ").append(r).append('\n');
        }

        AlertDialog.Builder b = new AlertDialog.Builder(act,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(a.label)
                .setMessage(sb.toString())
                .setNegativeButton("关闭", null);

        boolean skipOff = Prefs.get().isSkipDisabled(a.pkg);
        b.setPositiveButton(skipOff ? "恢复对该应用跳过" : "对该应用关闭跳过", (d, w) -> {
            Prefs.get().setSkipDisabled(a.pkg, !skipOff);
            Toast.makeText(act, skipOff ? "已恢复跳过" : "已对该应用关闭跳过",
                    Toast.LENGTH_SHORT).show();
        });

        boolean white = Prefs.get().isWhitelisted(a.pkg);
        b.setNeutralButton(white ? "移出白名单" : "加入白名单", (d, w) -> {
            if (white) Prefs.get().removeWhitelist(a.pkg);
            else Prefs.get().addWhitelist(a.pkg);
            Toast.makeText(act, white ? "已移出白名单" : "已加入白名单", Toast.LENGTH_SHORT).show();
        });

        if (a.declaresAccessibility || a.declaresNotifListener) {
            b.setNeutralButton("去权限设置", (d, w) -> {
                try {
                    act.startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception ignored) {
                }
            });
        }
        b.show();
    }
}

package com.lsan.sec.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.Modules;
import com.lsan.sec.data.Event;
import com.lsan.sec.plugin.PluginLoader;
import com.lsan.sec.util.Prefs;

import org.json.JSONObject;

import java.util.List;

/** 模块页：逐项开关 + 功能包导入/导出。 */
public class ModulesPage implements Page {

    private static final int REQ_IMPORT = 2001;

    private final MainActivity act;
    private LinearLayout content;

    public ModulesPage(MainActivity act) {
        this.act = act;
    }

    @Override
    public String title() {
        return "模块";
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

        // 导入区
        LinearLayout importCard = UiKit.card(act);
        importCard.addView(UiKit.text(act, "功能导入", 16, UiKit.p().text, true));
        TextView desc = UiKit.text(act,
                "功能包（.mpk）只包含声明式规则与说明，不执行任何外来代码。"
                        + "导入后作为独立模块出现，可随时关闭或移除。全部在本机解析，不上传。",
                11, UiKit.p().textFaint, false);
        desc.setPadding(0, UiKit.dp(act, 6), 0, UiKit.dp(act, 8));
        importCard.addView(desc);

        LinearLayout btnRow = UiKit.horizontal(act);
        android.widget.Button imp = UiKit.primary(act, "导入功能包");
        imp.setOnClickListener(v -> pickFile());
        android.widget.Button sample = UiKit.ghost(act, "查看格式示例");
        sample.setOnClickListener(v -> showSample());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(0, 0, UiKit.dp(act, 8), 0);
        btnRow.addView(imp, blp);
        btnRow.addView(sample, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        importCard.addView(btnRow);

        android.widget.Button demo = UiKit.ghost(act, "一键导入示例功能包（验证链路）");
        demo.setOnClickListener(v -> {
            PluginLoader.ImportResult r = PluginLoader.importBuiltinSample(act);
            Toast.makeText(act, r.message, Toast.LENGTH_LONG).show();
            if (r.ok) refresh();
        });
        demo.setPadding(0, UiKit.dp(act, 6), 0, 0);
        importCard.addView(demo);

        android.widget.Button scanLocal = UiKit.ghost(act, "扫描待导入目录（免权限）");
        scanLocal.setOnClickListener(v -> scanPending());
        scanLocal.setPadding(0, UiKit.dp(act, 6), 0, 0);
        importCard.addView(scanLocal);
        content.addView(importCard, UiKit.cardLp(act));

        // 内建模块
        content.addView(sectionTitle("内建模块"));
        for (Modules.Module m : Modules.builtin()) {
            content.addView(moduleCard(m), UiKit.cardLp(act));
        }

        // 已导入功能包
        List<JSONObject> plugins = PluginLoader.list(act);
        content.addView(sectionTitle("已导入功能包（" + plugins.size() + "）"));
        if (plugins.isEmpty()) {
            LinearLayout empty = UiKit.card(act);
            empty.addView(UiKit.text(act, "还没有导入任何功能包。", 13, UiKit.p().textFaint, false));
            content.addView(empty, UiKit.cardLp(act));
        } else {
            for (JSONObject o : plugins) {
                content.addView(pluginCard(o), UiKit.cardLp(act));
            }
        }
        content.addView(UiKit.spacer(act, 16));
    }

    private TextView sectionTitle(String s) {
        TextView t = UiKit.text(act, s, 12, UiKit.p().textFaint, true);
        t.setPadding(UiKit.dp(act, 4), UiKit.dp(act, 6), 0, UiKit.dp(act, 8));
        return t;
    }

    private View moduleCard(Modules.Module m) {
        LinearLayout card = UiKit.card(act);
        LinearLayout head = UiKit.horizontal(act);
        head.addView(UiKit.text(act, m.name, 15, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(UiKit.pill(act, m.category,
                "防护".equals(m.category) ? UiKit.p().accent
                        : "监控".equals(m.category) ? UiKit.p().accent2
                        : "审计".equals(m.category) ? UiKit.p().warn : UiKit.p().textDim));
        card.addView(head);

        TextView d = UiKit.text(act, m.desc, 12, UiKit.p().textDim, false);
        d.setPadding(0, UiKit.dp(act, 8), 0, 0);
        card.addView(d);

        TextView req = UiKit.text(act, "依赖：" + m.requires, 10, UiKit.p().textFaint, false);
        req.setPadding(0, UiKit.dp(act, 6), 0, 0);
        card.addView(req);

        boolean on = Prefs.get().moduleEnabled(m.id, m.defaultOn);
        LinearLayout sw = UiKit.switchRow(act, "启用", "", on, (b, checked) -> {
            Prefs.get().setModuleEnabled(m.id, checked);
            UiKit.setSwitchVisual(b, checked);
            GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                    "模块「" + m.name + "」已" + (checked ? "开启" : "关闭"))
                    .verdict("info").level("info"));
        });
        sw.setPadding(0, UiKit.dp(act, 4), 0, 0);
        card.addView(sw);
        return card;
    }

    private View pluginCard(JSONObject o) {
        String id = o.optString("id");
        String name = o.optString("name", id);
        String version = o.optString("version", "1.0");
        String author = o.optString("author", "");
        String desc = o.optString("desc", "");
        int ruleCount = o.optJSONArray("rules") == null ? 0 : o.optJSONArray("rules").length();

        LinearLayout card = UiKit.card(act);
        LinearLayout head = UiKit.horizontal(act);
        head.addView(UiKit.text(act, name, 15, UiKit.p().text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(UiKit.pill(act, "扩展 v" + version, UiKit.p().accent2));
        card.addView(head);

        TextView meta = UiKit.text(act, "规则 " + ruleCount + " 条"
                + (author.isEmpty() ? "" : " · " + author) + " · " + id,
                10, UiKit.p().textFaint, false);
        meta.setPadding(0, UiKit.dp(act, 6), 0, 0);
        card.addView(meta);

        if (!desc.isEmpty()) {
            TextView d = UiKit.text(act, desc, 12, UiKit.p().textDim, false);
            d.setPadding(0, UiKit.dp(act, 8), 0, 0);
            card.addView(d);
        }

        boolean on = Prefs.get().moduleEnabled(id, true);
        LinearLayout sw = UiKit.switchRow(act, "启用", "", on, (b, checked) -> {
            Prefs.get().setModuleEnabled(id, checked);
            UiKit.setSwitchVisual(b, checked);
        });
        sw.setPadding(0, UiKit.dp(act, 4), 0, 0);
        card.addView(sw);

        LinearLayout btns = UiKit.horizontal(act);
        btns.setPadding(0, UiKit.dp(act, 8), 0, 0);
        android.widget.Button del = UiKit.danger(act, "移除");
        del.setOnClickListener(v -> confirmRemove(id, name));
        btns.addView(del, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(btns);
        return card;
    }

    private void confirmRemove(String id, String name) {
        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("移除功能包")
                .setMessage("确定移除「" + name + "」？其规则将立即失效。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (d, w) -> {
                    PluginLoader.remove(act, id);
                    Toast.makeText(act, "已移除", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    /** 扫描应用私有外部目录 incoming/ 下的 .mpk 并导入。 */
    private void scanPending() {
        java.util.List<java.io.File> files = PluginLoader.pendingPacks(act);
        String dirPath = PluginLoader.incomingDir(act).getAbsolutePath();
        if (files.isEmpty()) {
            new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("没有待导入的功能包")
                    .setMessage("请把 .mpk 文件放到这个目录（无需任何存储权限）：\n\n"
                            + dirPath
                            + "\n\n放好后回到这里重新点击该按钮。")
                    .setPositiveButton("好", null)
                    .show();
            return;
        }
        final String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("选择要导入的功能包")
                .setItems(names, (d, which) -> {
                    PluginLoader.ImportResult r =
                            PluginLoader.importFromFile(act, files.get(which));
                    Toast.makeText(act, r.message, Toast.LENGTH_LONG).show();
                    if (r.ok) {
                        GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                                "从待导入目录导入功能包：" + r.pluginName
                                        + "（" + r.ruleCount + " 条规则）")
                                .verdict("info").level("warn"));
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            act.startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Toast.makeText(act, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    /** 由 MainActivity 在收到文件选择结果后调用。 */
    public void onFilePicked(Uri uri) {
        if (uri == null) return;
        PluginLoader.ImportResult r = PluginLoader.importFrom(act, uri);
        Toast.makeText(act, r.message, Toast.LENGTH_LONG).show();
        if (r.ok) {
            GuardEngine.get().record(new Event("system", act.getPackageName(), "",
                    "导入功能包：" + r.pluginName + "（" + r.ruleCount + " 条规则）")
                    .verdict("info").level("warn"));
            refresh();
        }
    }

    private void showSample() {
        String sample = "manifest.json\n" + PluginLoader.sampleManifest()
                + "\n\nrules.json\n" + PluginLoader.sampleRules()
                + "\n\n打包为 zip 后把扩展名改成 .mpk 即可导入。";
        new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("功能包格式")
                .setMessage(sample)
                .setPositiveButton("好", null)
                .show();
    }
}

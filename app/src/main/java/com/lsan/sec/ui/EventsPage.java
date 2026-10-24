package com.lsan.sec.ui;

import android.app.AlertDialog;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.data.Db;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Text;

import java.util.ArrayList;
import java.util.List;

/** 事件页：本地时间线，可筛选、可查看详情、可处置。 */
public class EventsPage implements Page {

    private final MainActivity act;
    private LinearLayout root;
    private ListView list;
    private EventAdapter adapter;
    private LinearLayout filterBar;
    private String filter = null;   // null = 全部

    private static final String[] TYPES = {"jump", "clip", "screen", "collusion", "notif", "system"};
    private static final String[] LABELS = {"全部", "跳转", "剪贴板", "屏幕", "联合", "通知", "系统"};

    public EventsPage(MainActivity act) {
        this.act = act;
    }

    @Override
    public String title() {
        return "事件";
    }

    @Override
    public View build() {
        root = UiKit.vertical(act);
        root.setBackgroundColor(UiKit.p().bg);

        android.widget.HorizontalScrollView filterScroll =
                new android.widget.HorizontalScrollView(act);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterBar = UiKit.horizontal(act);
        filterBar.setPadding(UiKit.dp(act, 12), UiKit.dp(act, 10),
                UiKit.dp(act, 12), UiKit.dp(act, 6));
        buildFilters();
        filterScroll.addView(filterBar, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(filterScroll);

        list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(UiKit.dp(act, 12), 0, UiKit.dp(act, 12), UiKit.dp(act, 12));
        list.setClipToPadding(false);
        adapter = new EventAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void buildFilters() {
        filterBar.removeAllViews();
        for (int i = 0; i < LABELS.length; i++) {
            final String type = i == 0 ? null : TYPES[i - 1];
            boolean on = (filter == null && type == null) || (type != null && type.equals(filter));
            TextView chip = UiKit.pill(act, LABELS[i], on ? UiKit.p().accent : UiKit.p().textFaint);
            chip.setPadding(UiKit.dp(act, 10), UiKit.dp(act, 5),
                    UiKit.dp(act, 10), UiKit.dp(act, 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, UiKit.dp(act, 6), 0);
            chip.setOnClickListener(v -> {
                filter = type;
                buildFilters();
                refresh();
            });
            filterBar.addView(chip, lp);
        }
    }

    @Override
    public void refresh() {
        if (list == null) return;
        List<Event> data = new ArrayList<>();
        Cursor c = Db.get(act).queryEvents(filter, 300, null);
        try {
            while (c != null && c.moveToNext()) {
                Event e = new Event(
                        c.getString(c.getColumnIndexOrThrow("type")),
                        c.getString(c.getColumnIndexOrThrow("src")),
                        c.getString(c.getColumnIndexOrThrow("dst")),
                        c.getString(c.getColumnIndexOrThrow("summary")));
                e.id = c.getLong(c.getColumnIndexOrThrow("id"));
                e.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                e.detail = c.getString(c.getColumnIndexOrThrow("detail"));
                e.verdict = c.getString(c.getColumnIndexOrThrow("verdict"));
                e.level = c.getString(c.getColumnIndexOrThrow("level"));
                data.add(e);
            }
        } finally {
            if (c != null) c.close();
        }
        adapter.setData(data);
    }

    private class EventAdapter extends BaseAdapter {
        private List<Event> data = new ArrayList<>();

        void setData(List<Event> d) {
            this.data = d;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }

        @Override public Object getItem(int i) { return data.get(i); }

        @Override public long getItemId(int i) { return data.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Event e = data.get(i);
            LinearLayout card = UiKit.card(act);
            LinearLayout head = UiKit.horizontal(act);
            head.addView(UiKit.pill(act, Event.typeLabel(e.type), HomePage.levelColor(e.level)));
            TextView time = UiKit.text(act, Text.time(e.ts) + " · " + Text.ago(e.ts),
                    10, UiKit.p().textFaint, false);
            time.setPadding(UiKit.dp(act, 8), 0, 0, 0);
            head.addView(time, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(UiKit.pill(act,
                    Event.verdictLabel(e.verdict),
                    "block".equals(e.verdict) ? UiKit.p().danger
                            : "alert".equals(e.verdict) ? UiKit.p().warn : UiKit.p().textDim));
            card.addView(head);

            TextView sum = UiKit.text(act, Text.clip(e.summary, 120), 13, UiKit.p().text, false);
            sum.setPadding(0, UiKit.dp(act, 8), 0, 0);
            card.addView(sum);

            card.setOnClickListener(v -> showDetail(e));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, UiKit.dp(act, 10));
            card.setLayoutParams(lp);
            return card;
        }
    }

    private void showDetail(Event e) {
        StringBuilder sb = new StringBuilder();
        sb.append("时间：").append(Text.time(e.ts)).append('\n');
        sb.append("类型：").append(Event.typeLabel(e.type)).append('\n');
        sb.append("裁决：").append(Event.verdictLabel(e.verdict)).append('\n');
        if (e.src != null && !e.src.isEmpty()) sb.append("来源：").append(e.src).append('\n');
        if (e.dst != null && !e.dst.isEmpty()) sb.append("目标：").append(e.dst).append('\n');
        sb.append('\n').append(e.summary).append('\n');
        if (e.detail != null && !e.detail.isEmpty()) {
            sb.append('\n').append(e.detail);
        }
        sb.append("\n\n所有内容均为本地记录，未上传。");

        AlertDialog.Builder b = new AlertDialog.Builder(act,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("事件详情")
                .setMessage(sb.toString())
                .setNegativeButton("关闭", null);

        if (e.dst != null && !e.dst.isEmpty() && "jump".equals(e.type)) {
            b.setNeutralButton("白名单该应用", (d, w) -> {
                Prefs.get().addWhitelist(e.dst);
                Toast.makeText(act, "已加入白名单：" + e.dst, Toast.LENGTH_SHORT).show();
            });
        } else if (e.src != null && !e.src.isEmpty()) {
            b.setNeutralButton("白名单该应用", (d, w) -> {
                Prefs.get().addWhitelist(e.src);
                Toast.makeText(act, "已加入白名单：" + e.src, Toast.LENGTH_SHORT).show();
            });
        }
        b.show();
    }

    /** 清空全部事件（供设置页调用）。 */
    public void clearAll() {
        GuardEngine.get().clearAllEvents();
        refresh();
    }
}

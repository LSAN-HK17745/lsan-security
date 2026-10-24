package com.lsan.sec.service;

import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.core.Modules;
import com.lsan.sec.data.Event;
import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Sensitive;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知监听：识别通知栏中的验证码 / 银行卡 / 身份证等敏感内容，
 * 提示其可能被具备通知使用权的第三方应用读取。
 * 仅做本地判定与记录，不修改、不上传任何通知内容。
 */
public class GuardNotificationService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        GuardEngine.get().init(this);
        GuardEngine.get().notificationConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        GuardEngine.get().notificationConnected = false;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        GuardEngine.get().init(this);
        if (!Prefs.get().masterEnabled()) return;
        if (!Modules.isEnabled(this, Modules.NOTIF)) return;

        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;

        CharSequence title = null;
        CharSequence text = null;
        try {
            title = sbn.getNotification().extras.getCharSequence("android.title");
            text = sbn.getNotification().extras.getCharSequence("android.text");
            if (text == null) {
                text = sbn.getNotification().extras.getCharSequence("android.bigText");
            }
        } catch (Exception ignored) {
        }
        String body = (title == null ? "" : title) + "\n" + (text == null ? "" : text);
        if (body.trim().isEmpty()) return;

        Prefs p = Prefs.get();
        List<Sensitive.Hit> hits = Sensitive.scan(body,
                p.sens(Sensitive.TYPE_ID_CARD),
                p.sens(Sensitive.TYPE_BANK_CARD),
                p.sens(Sensitive.TYPE_PHONE),
                p.sens(Sensitive.TYPE_OTP),
                p.sens(Sensitive.TYPE_EMAIL),
                p.sens(Sensitive.TYPE_PASSWORD),
                p.sens(Sensitive.TYPE_ADDRESS));
        if (hits.isEmpty()) return;

        StringBuilder desc = new StringBuilder();
        for (Sensitive.Hit h : hits) {
            if (desc.length() > 0) desc.append("、");
            desc.append(Sensitive.label(h.type)).append(' ').append(h.masked);
        }

        int listeners = countListenerApps();
        GuardEngine.get().record(new Event("notif", pkg, "",
                label(pkg) + " 通知含敏感信息：" + desc)
                .verdict("alert").level("high")
                .detail("当前有 " + listeners + " 个应用具备通知使用权，可读取该通知内容。"));
        GuardEngine.get().notifyStats();
    }

    private int countListenerApps() {
        try {
            List<android.content.pm.ResolveInfo> rs = getPackageManager()
                    .queryIntentServices(new android.content.Intent(
                            "android.service.notification.NotificationListenerService"), 0);
            List<String> pkgs = new ArrayList<>();
            for (android.content.pm.ResolveInfo ri : rs) {
                if (ri.serviceInfo != null && ri.serviceInfo.packageName != null) {
                    pkgs.add(ri.serviceInfo.packageName);
                }
            }
            return pkgs.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }
}

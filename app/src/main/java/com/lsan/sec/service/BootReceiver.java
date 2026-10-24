package com.lsan.sec.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.util.Prefs;

/** 开机自启：仅拉起本地防护引擎（可在设置中关闭）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        GuardEngine.get().init(context);
        if (!Prefs.get().bootStart()) return;
        if (!Prefs.get().masterEnabled()) return;
        try {
            Intent svc = new Intent(context, GuardEngineService.class);
            context.startForegroundService(svc);
        } catch (Exception ignored) {
        }
    }
}

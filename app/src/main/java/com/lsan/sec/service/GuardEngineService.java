package com.lsan.sec.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.lsan.sec.core.GuardEngine;
import com.lsan.sec.ui.MainActivity;

/**
 * 本地防护引擎前台服务：常驻、维持防护引擎存活、定时维护本地数据。
 * 该服务无网络访问能力（应用未声明 INTERNET 权限）。
 */
public class GuardEngineService extends Service {

    public static final String CHANNEL_ID = "murphy_guard";
    public static final int NOTI_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable loop;

    @Override
    public void onCreate() {
        super.onCreate();
        GuardEngine.get().init(this);
        createChannel();
        startForeground(NOTI_ID, buildNotification());
        startLoop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void createChannel() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "本地防护引擎", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("墨菲安全本地防护引擎运行状态（纯离线）");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("墨菲安全 · 本地防护中")
                .setContentText("跳转拦截与敏感信息监控运行中，数据仅存本机")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setContentIntent(pi)
                .setShowWhen(false);
        return b.build();
    }

    private void startLoop() {
        loop = new Runnable() {
            @Override public void run() {
                try {
                    GuardEngine.get().maintenance();
                    GuardEngine.get().notifyStats();
                } catch (Exception ignored) {
                }
                handler.postDelayed(this, 10 * 60 * 1000L);
            }
        };
        handler.postDelayed(loop, 30_000L);
    }

    @Override
    public void onDestroy() {
        if (loop != null) handler.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

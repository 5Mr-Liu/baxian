package com.example.networkcontrolapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

public class LocalVpnService extends VpnService {

    private ParcelFileDescriptor tun;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable stopRunnable = this::stopNow;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (VpnContract.ACTION_STOP.equals(action)) {
            stopNow();
            return START_NOT_STICKY;
        }

        // 默认视为 BLOCK
        startForeground(VpnContract.NOTIF_ID_VPN, buildNotification());

        String targetPkg = intent != null ? intent.getStringExtra(VpnContract.EXTRA_TARGET_PKG) : null;
        long duration = intent != null ? intent.getLongExtra(VpnContract.EXTRA_DURATION_MS, 1000L) : 1000L;
        if (targetPkg == null) {
            stopNow();
            return START_NOT_STICKY;
        }

        mainHandler.removeCallbacks(stopRunnable);
        closeTunQuietly();

        try {
            Builder b = new Builder()
                    .setSession("NC Short Block")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addAllowedApplication(targetPkg)
                    .addDnsServer("8.8.8.8");

            tun = b.establish();
            if (tun == null) {
                stopNow();
                return START_NOT_STICKY;
            }
        } catch (Exception e) {
            stopNow();
            return START_NOT_STICKY;
        }

        setBlockingState(true);

        if (duration < 200) duration = 200;
        mainHandler.postDelayed(stopRunnable, duration);

        return START_NOT_STICKY;
    }

    @Override
    public void onRevoke() {
        stopNow();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(stopRunnable);
        closeTunQuietly();
        super.onDestroy();
    }

    private void stopNow() {
        mainHandler.removeCallbacks(stopRunnable);
        closeTunQuietly();

        setBlockingState(false);

        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    private void setBlockingState(boolean blocking) {
        Prefs.setBlocking(this, blocking);

        Intent it = new Intent(VpnContract.ACTION_STATE);
        it.setPackage(getPackageName());
        it.putExtra(VpnContract.EXTRA_STATE, blocking ? VpnContract.STATE_BLOCKING : VpnContract.STATE_IDLE);
        sendBroadcast(it);
    }

    private Notification buildNotification() {
        ensureChannel(VpnContract.CH_VPN, "短时断网VPN");
        return new NotificationCompat.Builder(this, VpnContract.CH_VPN)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("拔线：正在断网")
                .setContentText("仅影响已选目标应用（可再次点击悬浮窗恢复）")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void ensureChannel(String id, String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(id) != null) return;
        nm.createNotificationChannel(new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW));
    }

    private void closeTunQuietly() {
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) {}
            tun = null;
        }
    }
}

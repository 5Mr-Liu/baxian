package com.example.networkcontrolapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {

    private WindowManager wm;
    private View floatingView;
    private WindowManager.LayoutParams lp;
    private ImageButton btn;

    private int startX, startY;
    private float downRawX, downRawY;
    private boolean dragging;
    private int touchSlop;

    private boolean blocking;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!VpnContract.ACTION_STATE.equals(intent.getAction())) return;
            int state = intent.getIntExtra(VpnContract.EXTRA_STATE, VpnContract.STATE_IDLE);
            blocking = (state == VpnContract.STATE_BLOCKING);
            updateButtonUi();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.ensureDefaultTarget(this);
        startForeground(VpnContract.NOTIF_ID_FLOAT, buildNotification());

        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 100;
        lp.y = 200;

        wm.addView(floatingView, lp);

        btn = floatingView.findViewById(R.id.btn_disconnect);
        btn.setOnTouchListener(this::handleTouch);

        // 用持久化状态作为初始化，进程重启也能恢复UI
        blocking = Prefs.isBlocking(this);
        updateButtonUi();

        // 注册状态广播
        IntentFilter f = new IntentFilter(VpnContract.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, f);
    }

    private void updateButtonUi() {
        if (btn == null) return;
        btn.setBackgroundResource(blocking ? R.drawable.circle_background : R.drawable.circle_background_idle);
    }

    private boolean handleTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                startX = lp.x;
                startY = lp.y;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    lp.x = startX + (int) dx;
                    lp.y = startY + (int) dy;
                    wm.updateViewLayout(floatingView, lp);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging) onClickFloat();
                return true;
        }
        return false;
    }

    private void onClickFloat() {
        String pkg = Prefs.getTargetPkg(this);
        if (pkg == null) {
            Toast.makeText(this, "请先选择目标应用", Toast.LENGTH_SHORT).show();
            Intent it = new Intent(this, AppSelectorActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            return;
        }

        if (blocking) {
            // 断链中 -> 点击立即恢复
            Intent stop = new Intent(this, LocalVpnService.class);
            stop.setAction(VpnContract.ACTION_STOP);
            startServiceCompat(stop);
            Toast.makeText(this, "已恢复网络", Toast.LENGTH_SHORT).show();
            return;
        }

        // 未断链 -> 开始断网 1 秒
        String name = Prefs.getTargetName(this);
        Toast.makeText(this, "断网 1 秒：" + (name == null ? pkg : name), Toast.LENGTH_SHORT).show();

        Intent block = new Intent(this, LocalVpnService.class);
        block.setAction(VpnContract.ACTION_BLOCK);
        block.putExtra(VpnContract.EXTRA_TARGET_PKG, pkg);
        block.putExtra(VpnContract.EXTRA_DURATION_MS, 1000L);
        startServiceCompat(block);
    }

    private void startServiceCompat(Intent it) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(it);
        else startService(it);
    }

    private Notification buildNotification() {
        ensureChannel(VpnContract.CH_FLOAT, "悬浮窗");
        return new NotificationCompat.Builder(this, VpnContract.CH_FLOAT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("拔线")
                .setContentText("绿=正常 红=断链中；点击切换")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void ensureChannel(String id, String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(id) != null) return;
        nm.createNotificationChannel(new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
        if (floatingView != null && wm != null) wm.removeView(floatingView);
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}

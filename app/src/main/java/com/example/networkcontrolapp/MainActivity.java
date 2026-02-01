package com.example.networkcontrolapp;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_VPN = 1002;

    private TextView tvTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaultTarget(this);
        setContentView(R.layout.activity_main);

        tvTarget = findViewById(R.id.tv_target);
        Button btnChoose = findViewById(R.id.btn_choose_target);
        Button btnStartFloat = findViewById(R.id.btn_start);

        btnChoose.setOnClickListener(v -> startChooseTarget());
        btnStartFloat.setOnClickListener(v -> ensurePermissionsThenStartFloat());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = Prefs.getTargetName(this);
        String pkg = Prefs.getTargetPkg(this);
        tvTarget.setText(pkg == null ? "目标应用：未设置" : ("目标应用：" + name + "\n" + pkg));
    }

    private void startChooseTarget() {
        startActivity(new Intent(this, AppSelectorActivity.class));
    }

    private void ensurePermissionsThenStartFloat() {
        // 悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(it, REQ_OVERLAY);
            return;
        }

        // VPN 权限
        Intent vpnPrepare = VpnService.prepare(this);
        if (vpnPrepare != null) {
            startActivityForResult(vpnPrepare, REQ_VPN);
            return;
        }

        startFloatingService();
    }

    private void startFloatingService() {
        Intent it = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(it);
        else startService(it);

        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                ensurePermissionsThenStartFloat();
            } else {
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_VPN) {
            if (resultCode == Activity.RESULT_OK) {
                startFloatingService();
            } else {
                Toast.makeText(this, "未授予 VPN 权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

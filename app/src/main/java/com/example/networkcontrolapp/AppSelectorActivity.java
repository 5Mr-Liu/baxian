package com.example.networkcontrolapp;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppSelectorActivity extends AppCompatActivity {

    static class Item {
        final String pkg;
        final String name;
        Item(String pkg, String name) { this.pkg = pkg; this.name = name; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        List<Item> items = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            // 过滤系统应用
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            // 过滤不可启动的包（更符合“这是哪个软件”的直觉）
            Intent launch = pm.getLaunchIntentForPackage(app.packageName);
            if (launch == null) continue;

            items.add(new Item(app.packageName, app.loadLabel(pm).toString()));
        }

        Collections.sort(items, (a, b) -> a.name.compareToIgnoreCase(b.name));

        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) names[i] = items.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("选择目标应用（仅此应用会被断网）")
                .setItems(names, (d, which) -> {
                    Item it = items.get(which);
                    Prefs.setTarget(this, it.pkg, it.name);
                    finish();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }
}

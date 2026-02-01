package com.example.networkcontrolapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public final class Prefs {
    private Prefs() {}

    private static final String SP = "nc_prefs";
    private static final String KEY_TARGET_PKG = "target_pkg";
    private static final String KEY_TARGET_NAME = "target_name";
    private static final String KEY_BLOCKING = "blocking";

    // 炉石传说：候选包名（按优先级排序：国服优先 -> 其他）
    private static final String[] DEFAULT_HS_PKGS = new String[] {
            "com.blizzard.wtcg.hearthstone.cn.dashen", // 国服（你提供）
            "com.blizzard.wtcg.hearthstone.cn",        // 兼容候选（部分渠道/历史可能存在）
            "com.blizzard.wtcg.hearthstone"            // 国际服常见
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static void setTarget(Context c, String pkg, String name) {
        sp(c).edit()
                .putString(KEY_TARGET_PKG, pkg)
                .putString(KEY_TARGET_NAME, name)
                .apply();
    }

    public static String getTargetPkg(Context c) {
        return sp(c).getString(KEY_TARGET_PKG, null);
    }

    public static String getTargetName(Context c) {
        return sp(c).getString(KEY_TARGET_NAME, null);
    }

    public static void setBlocking(Context c, boolean blocking) {
        sp(c).edit().putBoolean(KEY_BLOCKING, blocking).apply();
    }

    public static boolean isBlocking(Context c) {
        return sp(c).getBoolean(KEY_BLOCKING, false);
    }

    /**
     * 若用户未选择过目标应用，则尝试自动把“炉石传说”设为默认目标。
     * 不弹窗、不影响UI；仅当检测到候选包名确实已安装时生效。
     */
    public static void ensureDefaultTarget(Context c) {
        if (getTargetPkg(c) != null) return;

        PackageManager pm = c.getPackageManager();
        for (String pkg : DEFAULT_HS_PKGS) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                String name = pm.getApplicationLabel(ai).toString();
                setTarget(c, pkg, name);
                return;
            } catch (PackageManager.NameNotFoundException ignored) {
                // 没装这个包，继续尝试下一个
            } catch (Exception ignored) {
                // 其他异常（极少见），继续尝试下一个
            }
        }
    }
}

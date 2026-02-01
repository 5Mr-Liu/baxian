package com.example.networkcontrolapp;

public final class VpnContract {
    private VpnContract() {}

    // Actions
    public static final String ACTION_BLOCK = "com.example.networkcontrolapp.action.BLOCK";
    public static final String ACTION_STOP  = "com.example.networkcontrolapp.action.STOP";
    public static final String ACTION_STATE = "com.example.networkcontrolapp.action.STATE";

    // Extras
    public static final String EXTRA_TARGET_PKG   = "target_pkg";
    public static final String EXTRA_DURATION_MS  = "duration_ms";
    public static final String EXTRA_STATE        = "state";

    // States
    public static final int STATE_IDLE = 0;
    public static final int STATE_BLOCKING = 1;

    // Notifications / channels
    public static final int NOTIF_ID_FLOAT = 1;
    public static final int NOTIF_ID_VPN   = 2;

    public static final String CH_FLOAT = "float";
    public static final String CH_VPN   = "vpn";
}

package com.promouse;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class ActivationStore {
    private static final String PREF = "promouse_activation";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_METHOD = "method";
    private static final String KEY_CODE = "bshell_code";
    private static final String KEY_ADB_STATE = "adb_wifi_state";
    private static final String KEY_ADB_PAIR_CODE = "adb_wifi_pair_code";

    private ActivationStore() {}

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static String method(Context context) {
        return prefs(context).getString(KEY_METHOD, "Nenhum");
    }

    public static void activate(Context context, String method) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, true).putString(KEY_METHOD, method).apply();
    }

    public static void deactivate(Context context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putString(KEY_METHOD, "Nenhum").apply();
    }

    public static void beginAdbWifi(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, false)
                .putString(KEY_METHOD, "ADB Wi-Fi")
                .putString(KEY_ADB_STATE, "Aguardando código")
                .remove(KEY_ADB_PAIR_CODE)
                .apply();
    }

    public static void setAdbPairingCode(Context context, String code) {
        prefs(context).edit()
                .putString(KEY_ADB_PAIR_CODE, code)
                .putString(KEY_ADB_STATE, "Código recebido")
                .apply();
    }

    public static String adbPairingCode(Context context) {
        return prefs(context).getString(KEY_ADB_PAIR_CODE, "");
    }

    public static void setAdbWifiState(Context context, String state) {
        prefs(context).edit().putString(KEY_ADB_STATE, state).apply();
    }

    public static String adbWifiState(Context context) {
        return prefs(context).getString(KEY_ADB_STATE, "Não iniciado");
    }

    public static String bshellCode(Context context) {
        SharedPreferences p = prefs(context);
        String code = p.getString(KEY_CODE, null);
        if (code == null || code.length() != 6) {
            code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            p.edit().putString(KEY_CODE, code).apply();
        }
        return code;
    }

    public static String regenerateBShellCode(Context context) {
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        prefs(context).edit().putString(KEY_CODE, code).apply();
        return code;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}

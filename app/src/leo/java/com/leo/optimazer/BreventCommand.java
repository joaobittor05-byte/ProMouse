package com.leo.optimazer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.List;

public final class BreventCommand {
    public static final String ACTION = "me.piebridge.brevent.intent.action.COMMAND";
    public static final String EXTRA = "me.piebridge.brevent.intent.extra.COMMAND";

    private BreventCommand() {}

    public static boolean isAvailable(Context context) {
        Intent intent = new Intent(ACTION);
        List<ResolveInfo> handlers = context.getPackageManager().queryIntentActivities(intent, 0);
        return handlers != null && !handlers.isEmpty();
    }

    public static boolean execute(Activity activity, String command) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(EXTRA, command);
        List<ResolveInfo> handlers = activity.getPackageManager().queryIntentActivities(intent, 0);
        if (handlers == null || handlers.isEmpty()) return false;
        activity.startActivity(intent);
        return true;
    }

    public static String ramCleanupCommand() {
        return "am kill-all";
    }
}

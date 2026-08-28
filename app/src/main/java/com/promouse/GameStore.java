package com.promouse;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GameStore {
    private static final String PREF = "promouse_games";
    private static final String KEY = "packages";

    private GameStore() {}

    public static List<String> list(Context context) {
        String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void add(Context context, String packageName) {
        Set<String> values = new LinkedHashSet<>(list(context));
        values.add(packageName);
        save(context, values);
    }

    public static void remove(Context context, String packageName) {
        Set<String> values = new LinkedHashSet<>(list(context));
        values.remove(packageName);
        save(context, values);
    }

    private static void save(Context context, Set<String> packages) {
        JSONArray arr = new JSONArray();
        for (String p : packages) arr.put(p);
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putString(KEY, arr.toString()).apply();
    }
}

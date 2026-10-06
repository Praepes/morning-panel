package com.morningpanel.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class AlarmStore {
    private static final String PREFS = "local_alarm_config";
    private static final String KEY_ALARMS = "alarms";
    private static final String KEY_NEXT_ID = "next_id";

    private AlarmStore() { }

    static List<AlarmItem> get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String source = prefs.getString(KEY_ALARMS, "[]");
        ArrayList<AlarmItem> alarms = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(source);
            for (int i = 0; i < array.length(); i++) alarms.add(AlarmItem.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) { }
        Collections.sort(alarms, Comparator.comparingInt((AlarmItem a) -> a.hour * 60 + a.minute));
        return alarms;
    }

    static void save(Context context, List<AlarmItem> alarms) {
        JSONArray array = new JSONArray();
        int maxId = 0;
        for (AlarmItem alarm : alarms) {
            try { array.put(alarm.toJson()); }
            catch (JSONException error) { throw new IllegalStateException("Unable to encode alarm", error); }
            maxId = Math.max(maxId, alarm.id);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ALARMS, array.toString());
        editor.putInt(KEY_NEXT_ID, Math.max(prefs.getInt(KEY_NEXT_ID, 1), Math.max(maxId + 1, 1)));
        editor.apply();
        AlarmScheduler.scheduleAll(context);
    }

    static int nextId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int id = prefs.getInt(KEY_NEXT_ID, 1);
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply();
        return id;
    }

    static String exportJson(Context context) {
        JSONArray array = new JSONArray();
        for (AlarmItem alarm : get(context)) {
            try { array.put(alarm.toJson()); }
            catch (JSONException error) { throw new IllegalStateException("Unable to export alarm", error); }
        }
        return "{\"version\":2,\"alarms\":" + array + "}";
    }

    static List<AlarmItem> importJson(String source) throws JSONException {
        JSONArray array = new org.json.JSONObject(source).getJSONArray("alarms");
        ArrayList<AlarmItem> alarms = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) alarms.add(AlarmItem.fromJson(array.getJSONObject(i)));
        return alarms;
    }
}

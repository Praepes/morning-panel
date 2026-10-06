package com.morningpanel.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class HolidayCalendarStore {
    private static final String PREFS = "holiday_calendar_cache";

    static final class CalendarData {
        final int year;
        final Set<String> restDays;
        final Set<String> workDays;
        final long fetchedAt;

        CalendarData(int year, Set<String> restDays, Set<String> workDays, long fetchedAt) {
            this.year = year;
            this.restDays = Collections.unmodifiableSet(restDays);
            this.workDays = Collections.unmodifiableSet(workDays);
            this.fetchedAt = fetchedAt;
        }

        static CalendarData empty(int year) {
            return new CalendarData(year, new HashSet<>(), new HashSet<>(), 0L);
        }
    }

    private HolidayCalendarStore() { }

    static CalendarData get(Context context, int year) {
        String json = prefs(context).getString(key(year), "");
        if (json.isEmpty()) return CalendarData.empty(year);
        try {
            JSONObject saved = new JSONObject(json);
            if (saved.optInt("year", -1) != year) return CalendarData.empty(year);
            return new CalendarData(year, readDates(saved.optJSONArray("restDays"), year),
                    readDates(saved.optJSONArray("workDays"), year), saved.optLong("fetchedAt", 0L));
        } catch (JSONException ignored) {
            return CalendarData.empty(year);
        }
    }

    static long lastAttempt(Context context, int year) {
        return prefs(context).getLong("ailcc_attempt_" + year, 0L);
    }

    static void markAttempt(Context context, int year, long time) {
        prefs(context).edit().putLong("ailcc_attempt_" + year, time).apply();
    }

    static void save(Context context, int year, Set<String> restDays, Set<String> workDays, long fetchedAt) {
        try {
            JSONObject saved = new JSONObject();
            saved.put("year", year);
            saved.put("fetchedAt", fetchedAt);
            saved.put("source", "holiday.ailcc.com");
            saved.put("restDays", toJson(restDays));
            saved.put("workDays", toJson(workDays));
            prefs(context).edit().putString(key(year), saved.toString()).apply();
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to save holiday calendar", error);
        }
    }

    static String status(Context context) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        CalendarData data = get(context, year);
        long attemptedAt = lastAttempt(context, year);
        String current;
        if (data.fetchedAt == 0L) current = attemptedAt > 0L
                ? year + " 年日历暂不可用；暂按周一至周五判断。"
                : year + " 年日历尚未同步；暂按周一至周五判断。";
        else {
            String updated = new SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
                    .format(data.fetchedAt);
            current = attemptedAt > data.fetchedAt
                    ? year + " 年日历服务暂不可用 · 使用缓存（更新于 " + updated + "）"
                    : year + " 年日历已缓存 · 更新于 " + updated;
        }
        CalendarData next = get(context, year + 1);
        if (next.fetchedAt == 0L) current += "；" + (year + 1) + " 年安排尚无有效缓存，暂按周一至周五判断";
        return current;
    }

    static String dateKey(Calendar calendar) {
        return String.format(Locale.US, "%04d-%02d-%02d", calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static JSONArray toJson(Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    private static Set<String> readDates(JSONArray values, int year) {
        HashSet<String> result = new HashSet<>();
        if (values == null) return result;
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (value.matches("\\d{4}-\\d{2}-\\d{2}") && value.startsWith(year + "-")) result.add(value);
        }
        return result;
    }

    private static String key(int year) { return "calendar_" + year; }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

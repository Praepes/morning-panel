package com.morningpanel.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HolidayCalendarClient {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static boolean syncing;
    private static final ArrayList<Runnable> completions = new ArrayList<>();

    private HolidayCalendarClient() { }

    static void synchronizeAsync(Context context, boolean force, Runnable completion) {
        synchronized (HolidayCalendarClient.class) {
            if (syncing) {
                if (completion != null) completions.add(completion);
                return;
            }
            syncing = true;
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                synchronize(app, force);
            } finally {
                try { AlarmScheduler.scheduleAll(app); } catch (RuntimeException ignored) { }
                ArrayList<Runnable> ready;
                synchronized (HolidayCalendarClient.class) {
                    syncing = false;
                    ready = new ArrayList<>(completions);
                    completions.clear();
                }
                if (completion != null) completion.run();
                for (Runnable callback : ready) callback.run();
            }
        });
    }

    private static void synchronize(Context context, boolean force) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        refreshYear(context, year, force);
        refreshYear(context, year + 1, force);
    }

    private static void refreshYear(Context context, int year, boolean force) {
        long now = System.currentTimeMillis();
        HolidayCalendarStore.CalendarData cached = HolidayCalendarStore.get(context, year);
        if (!force && cached.fetchedAt > 0L && now - cached.fetchedAt < DAY) return;
        if (!force && now - HolidayCalendarStore.lastAttempt(context, year) < 12L * 60L * 60L * 1000L) return;
        HolidayCalendarStore.markAttempt(context, year, now);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "https://holiday.ailcc.com/api/holiday/allyear/" + year).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MorningPanel/0.4");
            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                    throw new IOException("日历服务返回 HTTP " + connection.getResponseCode());
                byte[] bytes = readLimited(connection.getInputStream(), 1024 * 1024);
                JSONObject response = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (response.optInt("code", -1) != 0 || !String.valueOf(year).equals(response.optString("year", "")))
                    throw new IOException(year + " 年日历暂不可用");
                JSONArray days = response.optJSONArray("data");
                Calendar expected = new java.util.GregorianCalendar(year, Calendar.JANUARY, 1);
                int expectedCount = expected.getActualMaximum(Calendar.DAY_OF_YEAR);
                if (days == null || response.optInt("count", -1) != expectedCount || days.length() != expectedCount)
                    throw new IOException(year + " 年日历数据不完整");
                HashSet<String> rests = new HashSet<>();
                HashSet<String> workdays = new HashSet<>();
                int officialHolidayDays = 0;
                HashSet<String> seen = new HashSet<>();
                for (int i = 0; i < days.length(); i++) {
                    JSONObject day = days.optJSONObject(i);
                    if (day == null) throw new IOException("日历条目无效");
                    String date = day.optString("date", "");
                    validateDate(date, year);
                    if (!seen.add(date)) throw new IOException("日历包含重复日期");
                    int type = day.optInt("type", -1);
                    if (type < 0 || type > 4) throw new IOException("日历类型无效");
                    if (day.optInt("is_holiday", 0) == 1) rests.add(date);
                    if (type == 2 || type == 3) officialHolidayDays++;
                    if (type == 4) workdays.add(date);
                }
                if (officialHolidayDays == 0 || rests.size() > 125 || workdays.size() > 40)
                    throw new IOException("日历数据不完整，已保留上次缓存");
                HolidayCalendarStore.save(context, year, rests, workdays, System.currentTimeMillis());
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            // A failed or unpublished year never replaces the last valid local copy.
        }
    }

    private static void validateDate(String value, int year) throws IOException {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition position = new ParsePosition(0);
        if (value.length() != 10 || parser.parse(value, position) == null || position.getIndex() != 10
                || !value.startsWith(year + "-")) throw new IOException("日历日期格式无效");
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("日历响应过大");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}

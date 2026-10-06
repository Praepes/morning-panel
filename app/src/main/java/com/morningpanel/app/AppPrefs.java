package com.morningpanel.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

final class AppPrefs {
    static final int CLOCK_STACKED = 0;
    static final int CLOCK_DIAL = 1;
    static final int CLOCK_HORIZONTAL = 3;
    static int[] clockStyles() { return new int[]{CLOCK_STACKED, CLOCK_DIAL, CLOCK_HORIZONTAL}; }
    static int normalizeClockStyle(int style) {
        return style == CLOCK_DIAL || style == CLOCK_HORIZONTAL ? style : CLOCK_STACKED;
    }
    private static final String FILE = "xperia_touch_home";
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private AppPrefs() { }

    static String city(Context c) { return preferences(c).getString("weather_city", "香港"); }
    static double latitude(Context c) { return Double.longBitsToDouble(preferences(c).getLong("weather_lat", Double.doubleToLongBits(22.3193))); }
    static double longitude(Context c) { return Double.longBitsToDouble(preferences(c).getLong("weather_lon", Double.doubleToLongBits(114.1694))); }
    static void setWeather(Context c, String city, double lat, double lon) {
        preferences(c).edit().putString("weather_city", city).putLong("weather_lat", Double.doubleToLongBits(lat))
                .putLong("weather_lon", Double.doubleToLongBits(lon)).apply();
    }

    static String haUrl(Context c) { return preferences(c).getString("ha_url", ""); }
    static String companionLinkDeviceId(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.trim().isEmpty() ? "android-device" : id;
    }
    static String haProjector(Context c) { return preferences(c).getString("ha_projector", "switch.companion_link_projector"); }
    static String haLight(Context c) { return preferences(c).getString("ha_light", "light.companion_link_projection"); }
    static String haPower(Context c) { return preferences(c).getString("ha_power", "sensor.companion_link_power_state"); }
    static String haNextAlarm(Context c) { return preferences(c).getString("ha_next_alarm", "sensor.companion_link_next_alarm"); }
    static boolean haAllowHttp(Context c) { return preferences(c).getBoolean("ha_allow_http", false); }
    static void setHomeAssistant(Context c, String url, String projector, String light, String power,
                                 String nextAlarm, boolean allowHttp) {
        preferences(c).edit().putString("ha_url", url.trim()).putString("ha_projector", projector.trim())
                .putString("ha_light", light.trim()).putString("ha_power", power.trim())
                .putString("ha_next_alarm", nextAlarm.trim()).putBoolean("ha_allow_http", allowHttp).apply();
    }

    static final class RssSource {
        final String url;
        final String note;

        RssSource(String url, String note) {
            this.url = url == null ? "" : url.trim();
            this.note = note == null ? "" : note.trim();
        }
    }

    static ArrayList<RssSource> rssSources(Context c) {
        SharedPreferences prefs = preferences(c);
        String encoded = prefs.getString("rss_sources", "");
        ArrayList<RssSource> result = new ArrayList<>();
        if (!encoded.isEmpty()) {
            try {
                JSONArray sources = new JSONArray(encoded);
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject source = sources.optJSONObject(i);
                    if (source != null) result.add(new RssSource(source.optString("url", ""), source.optString("note", "")));
                }
                return result;
            } catch (JSONException ignored) { }
        }
        String legacy = prefs.getString("rss_url", DEFAULT_RSS);
        for (String line : legacy.split("[\\r\\n,]+")) {
            String url = line.trim();
            if (!url.isEmpty()) result.add(new RssSource(url, ""));
        }
        return result;
    }

    static String rssUrl(Context c) {
        StringBuilder result = new StringBuilder();
        for (RssSource source : rssSources(c)) {
            if (source.url.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(source.url);
        }
        return result.toString();
    }

    static void setRssUrl(Context c, String url) {
        ArrayList<RssSource> sources = new ArrayList<>();
        for (String line : url.split("[\\r\\n,]+")) if (!line.trim().isEmpty()) sources.add(new RssSource(line, ""));
        setRssSources(c, sources);
    }

    static void setRssSources(Context c, ArrayList<RssSource> sources) {
        JSONArray encoded = new JSONArray();
        StringBuilder legacy = new StringBuilder();
        for (RssSource source : sources) {
            if (source.url.isEmpty()) continue;
            JSONObject item = new JSONObject();
            try { item.put("url", source.url).put("note", source.note); }
            catch (JSONException ignored) { continue; }
            encoded.put(item);
            if (legacy.length() > 0) legacy.append('\n');
            legacy.append(source.url);
        }
        preferences(c).edit().putString("rss_sources", encoded.toString())
                .putString("rss_url", legacy.toString()).apply();
    }
    private static final String DEFAULT_RSS = "https://sspai.com/feed\nhttps://www.ifanr.com/feed/\nhttps://www.ithome.com/rss/";
    static String haHomeEntities(Context c) { return preferences(c).getString("ha_home_entities", ""); }
    static void setHaHomeEntities(Context c, String entities) { preferences(c).edit().putString("ha_home_entities", entities.trim()).apply(); }
    static boolean keepScreenOn(Context c) { return preferences(c).getBoolean("keep_screen_on", true); }
    static void setKeepScreenOn(Context c, boolean value) { preferences(c).edit().putBoolean("keep_screen_on", value).apply(); }
    static int clockStyle(Context c) {
        int style = preferences(c).getInt("clock_style", CLOCK_STACKED);
        return normalizeClockStyle(style);
    }
    static void setClockStyle(Context c, int style) {
        preferences(c).edit().putInt("clock_style", normalizeClockStyle(style)).apply();
    }
    static void cacheWeather(Context c, String json, long at) {
        preferences(c).edit().putString("weather_cache", json).putLong("weather_cache_at", at).apply();
    }
    static String weatherCache(Context c) { return preferences(c).getString("weather_cache", ""); }
    static long weatherCacheAt(Context c) { return preferences(c).getLong("weather_cache_at", 0L); }
    static String holidayRegion(Context c) { return preferences(c).getString("holiday_region", "CN"); }
    static void setHolidayRegion(Context c, String region) {
        preferences(c).edit().putString("holiday_region", "CN".equals(region) ? "CN" : "CN").apply();
    }
}

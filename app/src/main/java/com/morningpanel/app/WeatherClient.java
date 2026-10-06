package com.morningpanel.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WeatherClient {
    static final int FORECAST_DAYS = 6;

    static final class Weather {
        final String temperature;
        final String apparent;
        final String description;
        final String humidity;
        final String rainChance;
        final String low;
        final String high;
        final String observedAt;
        final int weatherCode;
        final List<ForecastHour> hours;
        final List<ForecastDay> days;

        Weather(String temperature, String apparent, String description, String humidity, String rainChance,
                String low, String high, String observedAt, int weatherCode,
                List<ForecastHour> hours, List<ForecastDay> days) {
            this.temperature = temperature;
            this.apparent = apparent;
            this.description = description;
            this.humidity = humidity;
            this.rainChance = rainChance;
            this.low = low;
            this.high = high;
            this.observedAt = observedAt;
            this.weatherCode = weatherCode;
            this.hours = hours;
            this.days = days;
        }

        String display(String city) {
            return temperature + "°  " + description
                    + "\n体感 " + apparent + "°   湿度 " + humidity + "%   降雨 " + rainChance + "%"
                    + "\n今日 " + low + "° / " + high + "°";
        }
    }

    static final class ForecastHour {
        final String time;
        final String temperature;
        final String rainChance;
        final int weatherCode;
        ForecastHour(String time, String temperature, String rainChance, int weatherCode) {
            this.time = time;
            this.temperature = temperature;
            this.rainChance = rainChance;
            this.weatherCode = weatherCode;
        }
    }

    static final class ForecastDay {
        final String date;
        final String description;
        final String low;
        final String high;
        final String rainChance;
        final String uv;
        final String sunrise;
        final String sunset;
        final int weatherCode;
        ForecastDay(String date, String description, String low, String high) {
            this(date, description, low, high, "—", "—", "", "", -1);
        }
        ForecastDay(String date, String description, String low, String high, String rainChance,
                    String uv, String sunrise, String sunset, int weatherCode) {
            this.date = date;
            this.description = description;
            this.low = low;
            this.high = high;
            this.rainChance = rainChance;
            this.uv = uv;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.weatherCode = weatherCode;
        }
    }

    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;
        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
        String label() {
            String location = detail.isEmpty()
                    ? String.format(Locale.US, "%.3f, %.3f", latitude, longitude) : detail;
            return name + " · " + location;
        }
    }

    private WeatherClient() { }

    static Weather fetch(double latitude, double longitude) throws Exception {
        String query = String.format(Locale.US,
                "latitude=%.5f&longitude=%.5f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&hourly=temperature_2m,weather_code,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset&timezone=auto&forecast_days=%d",
                latitude, longitude, FORECAST_DAYS);
        URL url = new URL("https://api.open-meteo.com/v1/forecast?" + URLEncoderSafe(query));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("Weather service returned HTTP " + status);
            String json = read(connection.getInputStream());
            JSONObject root = new JSONObject(json);
            JSONObject current = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time");
            JSONArray probabilities = hourly.getJSONArray("precipitation_probability");
            JSONArray hourlyTemps = hourly.getJSONArray("temperature_2m");
            JSONArray hourlyCodes = hourly.getJSONArray("weather_code");
            String hour = current.optString("time", "");
            int currentIndex = 0;
            for (int i = 0; i < times.length(); i++) {
                if (times.optString(i, "").startsWith(hour.length() >= 13 ? hour.substring(0, 13) : hour)) {
                    currentIndex = i;
                    break;
                }
            }
            int chance = probabilities.optInt(currentIndex, 0);
            ArrayList<ForecastHour> nextHours = new ArrayList<>();
            for (int i = currentIndex; i < times.length() && nextHours.size() < 24; i++) {
                String time = times.optString(i, "");
                nextHours.add(new ForecastHour(time.length() >= 16 ? time.substring(11, 16) : "--:--",
                        number(hourlyTemps, i), String.valueOf(probabilities.optInt(i, 0)), hourlyCodes.optInt(i, -1)));
            }
            JSONArray dates = daily.getJSONArray("time");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray mins = daily.getJSONArray("temperature_2m_min");
            JSONArray maxs = daily.getJSONArray("temperature_2m_max");
            JSONArray rainMax = daily.getJSONArray("precipitation_probability_max");
            JSONArray uvMax = daily.getJSONArray("uv_index_max");
            JSONArray sunrises = daily.getJSONArray("sunrise");
            JSONArray sunsets = daily.getJSONArray("sunset");
            ArrayList<ForecastDay> nextDays = new ArrayList<>();
            for (int i = 0; i < Math.min(FORECAST_DAYS, dates.length()); i++) {
                nextDays.add(new ForecastDay(i == 0 ? "今天" : i == 1 ? "明天" : i == 2 ? "后天" : dateLabel(dates.optString(i, "")),
                        condition(codes.optInt(i, -1)), mins.optString(i, "—"), maxs.optString(i, "—"),
                        rainMax.optString(i, "—"), rainMax.optString(i, "—").equals("—") ? "—" : number(uvMax, i),
                        shortTime(sunrises.optString(i, "")), shortTime(sunsets.optString(i, "")), codes.optInt(i, -1)));
            }
            return new Weather(number(current, "temperature_2m"), number(current, "apparent_temperature"),
                    condition(current.optInt("weather_code", -1)), number(current, "relative_humidity_2m"),
                    String.valueOf(chance), mins.optString(0, "—"), maxs.optString(0, "—"), current.optString("time", ""),
                    current.optInt("weather_code", -1), nextHours, nextDays);
        } finally {
            connection.disconnect();
        }
    }

    static List<Place> searchPlaces(String query) throws Exception {
        String original = query.trim();
        String normalized = normalizePlaceQuery(original);
        List<Place> places = searchPlaceName(normalized);
        // Keep the original spelling as a fallback for names whose 市 is part
        // of the indexed name, or a qualifier that requires the original alias.
        if (places.isEmpty() && !normalized.equals(original)) return searchPlaceName(original);
        return places;
    }

    private static String normalizePlaceQuery(String query) {
        String value = query.replace('，', ',');
        int comma = value.indexOf(',');
        String location = (comma < 0 ? value : value.substring(0, comma)).trim();
        String qualifier = comma < 0 ? "" : value.substring(comma);
        String spelling = location.toLowerCase(Locale.ROOT).replace('\'', '’').replace(" ", "");
        // GeoNames indexes the Shaanxi capital as Xi’an (a curly apostrophe).
        // ASCII Xi'an matches other settlements and airports but misses the city.
        if ("xian".equals(spelling) || "xi’an".equals(spelling)) location = "Xi’an";
        else if (location.matches("[\\p{IsHan}]{2,}市")) location = location.substring(0, location.length() - 1);
        return location + qualifier;
    }

    private static List<Place> searchPlaceName(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded
                + "&count=12&language=zh&format=json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("地点搜索服务返回 HTTP " + status);
            JSONObject root = new JSONObject(read(connection.getInputStream()));
            JSONArray results = root.optJSONArray("results");
            ArrayList<Place> places = new ArrayList<>();
            if (results == null) return places;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                ArrayList<String> details = new ArrayList<>();
                String admin = item.optString("admin1", "");
                String country = item.optString("country", "");
                if (!admin.isEmpty() && !admin.equals(item.optString("name", ""))) details.add(admin);
                if (!country.isEmpty()) details.add(country);
                places.add(new Place(item.optString("name", ""), android.text.TextUtils.join(" · ", details),
                        item.optDouble("latitude"), item.optDouble("longitude")));
            }
            return places;
        } finally {
            connection.disconnect();
        }
    }

    private static String number(JSONObject object, String key) {
        return object.isNull(key) ? "—" : String.valueOf(Math.round(object.optDouble(key)));
    }

    private static String number(JSONArray values, int index) {
        Object value = values.opt(index);
        if (value == null || JSONObject.NULL.equals(value)) return "—";
        return String.valueOf(Math.round(((Number) value).doubleValue()));
    }

    private static String dateLabel(String date) {
        return date.length() >= 10 ? date.substring(5).replace('-', '/') : date;
    }

    private static String shortTime(String value) {
        return value.length() >= 16 ? value.substring(11, 16) : "--:--";
    }

    static Weather parseCached(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        int chance = 0;
        JSONArray times = root.getJSONObject("hourly").getJSONArray("time");
        JSONArray probabilities = root.getJSONObject("hourly").getJSONArray("precipitation_probability");
        String hour = current.optString("time", "");
        for (int i = 0; i < times.length(); i++) {
            if (times.optString(i, "").startsWith(hour.length() >= 13 ? hour.substring(0, 13) : hour)) {
                chance = probabilities.optInt(i, 0);
                break;
            }
        }
        return new Weather(number(current, "temperature_2m"), number(current, "apparent_temperature"),
                condition(current.optInt("weather_code", -1)), number(current, "relative_humidity_2m"),
                String.valueOf(chance), daily.getJSONArray("temperature_2m_min").optString(0, "—"),
                daily.getJSONArray("temperature_2m_max").optString(0, "—"), current.optString("time", ""),
                current.optInt("weather_code", -1),
                new ArrayList<>(), new ArrayList<>());
    }

    private static String condition(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "大致晴朗";
        if (code == 2) return "局部多云";
        if (code == 3) return "阴天";
        if (code == 45 || code == 48) return "有雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "下雨";
        if (code >= 71 && code <= 77) return "下雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 85 && code <= 86) return "阵雪";
        if (code >= 95) return "雷雨";
        return "天气数据待更新";
    }

    private static String URLEncoderSafe(String query) {
        return query.replace(" ", "%20");
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}

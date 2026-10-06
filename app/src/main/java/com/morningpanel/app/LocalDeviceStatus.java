package com.morningpanel.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class LocalDeviceStatus {
    private LocalDeviceStatus() { }

    static JSONObject capture(Context context) {
        JSONObject status = new JSONObject();
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery == null ? 100 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int percent = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
            int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            boolean charging = plugged != 0;
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = connectivity == null ? null : connectivity.getActiveNetworkInfo();
            boolean networkConnected = network != null && network.isConnected();
            String networkType = !networkConnected ? "offline" : network.getType() == ConnectivityManager.TYPE_WIFI ? "wifi" : "mobile";
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean screenAwake = power != null && power.isInteractive();
            int brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
            int brightnessPercent = brightness < 0 ? -1 : Math.round(brightness * 100f / 255f);
            boolean canWriteBrightness = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context);
            boolean rootAvailable = LocalDeviceControl.hasRoot();
            DisplayMetrics display = context.getResources().getDisplayMetrics();

            status.put("battery_percent", percent);
            status.put("charging", charging);
            status.put("network_connected", networkConnected);
            status.put("network_type", networkType);
            status.put("screen_awake", screenAwake);
            status.put("screen_brightness", brightnessPercent);
            status.put("app_version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            status.put("android_version", Build.VERSION.RELEASE);
            status.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            status.put("root_available", rootAvailable);
            status.put("brightness_control_available", canWriteBrightness);
            status.put("screen_wake_available", true);
            status.put("screen_sleep_available", rootAvailable);
            status.put("keep_screen_on", AppPrefs.keepScreenOn(context));
            status.put("display_resolution", display.widthPixels + "x" + display.heightPixels);
            status.put("display_density_dpi", display.densityDpi);
            status.put("uptime_seconds", SystemClock.elapsedRealtime() / 1000L);
            status.put("alarm_ringing", AlarmService.isAlarmActive());
            AlarmItem nextAlarm = null;
            long nextAlarmAt = Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            List<AlarmItem> alarms = AlarmStore.get(context);
            for (AlarmItem alarm : alarms) {
                if (!alarm.enabled) continue;
                long trigger = AlarmScheduler.nextTrigger(context, alarm, now);
                if (trigger > 0 && trigger < nextAlarmAt) {
                    nextAlarm = alarm;
                    nextAlarmAt = trigger;
                }
            }
            if (nextAlarm == null) {
                status.put("next_alarm", JSONObject.NULL);
                status.put("next_alarm_label", "");
            } else {
                status.put("next_alarm", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                        .format(new Date(nextAlarmAt)));
                status.put("next_alarm_label", nextAlarm.label);
            }
            JSONArray capabilities = new JSONArray();
            capabilities.put("screen_wake");
            if (rootAvailable) capabilities.put("screen_sleep");
            if (canWriteBrightness) capabilities.put("screen_brightness");
            capabilities.put("alarm_snooze");
            capabilities.put("alarm_stop");
            status.put("capabilities", capabilities);
            status.put("updated_at", System.currentTimeMillis());
        } catch (Exception ignored) { }
        return status;
    }
}

package com.morningpanel.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class LocalDeviceControl {
    private static volatile Boolean rootResult;
    private static volatile String grantedSuBinary;
    private static final String[] SU_BINARIES = {"/system/xbin/su", "/system/bin/su", "/sbin/su", "su"};
    private LocalDeviceControl() { }

    static boolean hasRoot() {
        if (rootResult != null) return rootResult;
        synchronized (LocalDeviceControl.class) {
            if (rootResult != null) return rootResult;
            rootResult = probeRoot();
            return rootResult;
        }
    }

    static boolean requestRootAccess() {
        synchronized (LocalDeviceControl.class) { rootResult = null; }
        boolean available = probeRoot();
        synchronized (LocalDeviceControl.class) { rootResult = available; }
        return available;
    }

    private static boolean probeRoot() {
        for (String binary : SU_BINARIES) {
            try {
                Process process = new ProcessBuilder("/system/bin/sh", "-c", binary + " -c id")
                        .redirectErrorStream(true).start();
                if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("uid=0")) { grantedSuBinary = binary; return true; }
                    android.util.Log.i("MorningPanelRoot", binary + ": " + line);
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    static JSONObject handle(Context context, JSONObject command) {
        String name = command.optString("command", "");
        boolean success = false;
        String message = "不支持的命令";
        JSONObject resultData = null;
        if ("wake".equals(name)) {
            wake(context);
            success = true;
            message = "已请求唤醒屏幕";
        } else if ("sleep".equals(name)) {
            success = sleep(context);
            message = success ? "已请求屏幕休眠" : "屏幕休眠需要应用 Root 授权";
        } else if ("brightness".equals(name)) {
            success = setBrightness(context, command.optInt("value", -1));
            message = success ? "亮度已设置" : "无法修改系统亮度，请检查修改系统设置授权";
        } else if ("snooze_alarm".equals(name) || "stop_alarm".equals(name)) {
            boolean snooze = "snooze_alarm".equals(name);
            success = controlAlarm(context, snooze);
            message = success ? (snooze ? "闹钟已贪睡 10 分钟" : "闹钟已停止") : "当前没有正在响铃的闹钟";
        } else if ("get_alarms".equals(name) || "add_alarm".equals(name)
                || "update_alarm".equals(name) || "delete_alarm".equals(name)) {
            try {
                if ("add_alarm".equals(name)) {
                    AlarmItem item = alarmFromCommand(context, command, AlarmStore.nextId(context), null);
                    java.util.ArrayList<AlarmItem> alarms = new java.util.ArrayList<>(AlarmStore.get(context));
                    alarms.add(item);
                    AlarmStore.save(context, alarms);
                } else if ("update_alarm".equals(name)) {
                    int id = command.optInt("id", -1);
                    java.util.ArrayList<AlarmItem> alarms = new java.util.ArrayList<>(AlarmStore.get(context));
                    boolean found = false;
                    for (int i = 0; i < alarms.size(); i++) if (alarms.get(i).id == id) {
                        alarms.set(i, alarmFromCommand(context, command, id, alarms.get(i)));
                        found = true;
                        break;
                    }
                    if (!found) throw new IllegalArgumentException("找不到闹钟 ID " + id);
                    AlarmStore.save(context, alarms);
                } else if ("delete_alarm".equals(name)) {
                    int id = command.optInt("id", -1);
                    java.util.ArrayList<AlarmItem> alarms = new java.util.ArrayList<>(AlarmStore.get(context));
                    boolean removed = false;
                    for (int i = alarms.size() - 1; i >= 0; i--) if (alarms.get(i).id == id) {
                        alarms.remove(i);
                        removed = true;
                    }
                    if (!removed) throw new IllegalArgumentException("找不到闹钟 ID " + id);
                    AlarmStore.save(context, alarms);
                }
                resultData = alarmList(context);
                success = true;
                message = "get_alarms".equals(name) ? "已读取闹钟" : "闹钟已更新";
            } catch (Exception error) { message = error.getMessage() == null ? "闹钟操作失败" : error.getMessage(); }
        } else if ("get_rss_config".equals(name) || "set_rss_config".equals(name)
                || "refresh_rss".equals(name)) {
            try {
                if ("set_rss_config".equals(name)) {
                    JSONArray sources = command.optJSONArray("sources");
                    if (sources == null) throw new IllegalArgumentException("sources 必须是 URL 列表");
                    StringBuilder urls = new StringBuilder();
                    for (int i = 0; i < sources.length(); i++) {
                        String url = sources.optString(i, "").trim();
                        if (url.isEmpty()) continue;
                        if (!url.startsWith("http://") && !url.startsWith("https://"))
                            throw new IllegalArgumentException("RSS 地址必须使用 http 或 https");
                        if (urls.length() > 0) urls.append('\n');
                        urls.append(url);
                    }
                    AppPrefs.setRssUrl(context, urls.toString());
                }
                if ("refresh_rss".equals(name) || "set_rss_config".equals(name)) MainActivity.requestRssRefresh(context);
                resultData = rssConfig(context);
                success = true;
                message = "refresh_rss".equals(name) ? "已请求 RSS 刷新" : "已读取 RSS 配置";
            } catch (Exception error) { message = error.getMessage() == null ? "RSS 操作失败" : error.getMessage(); }
        }
        JSONObject result = new JSONObject();
        try {
            result.put("command", name);
            result.put("success", success);
            result.put("message", message);
            result.put("request_id", command.optString("request_id", ""));
            result.put("updated_at", System.currentTimeMillis());
            if (resultData != null) result.put("data", resultData);
        } catch (org.json.JSONException ignored) { }
        return result;
    }

    private static AlarmItem alarmFromCommand(Context context, JSONObject command, int id, AlarmItem old) {
        int hour = command.has("hour") ? command.optInt("hour", -1) : old == null ? -1 : old.hour;
        int minute = command.has("minute") ? command.optInt("minute", -1) : old == null ? -1 : old.minute;
        int repeatMask = command.has("repeat_mask") ? command.optInt("repeat_mask", 0) : old == null ? 0 : old.repeatMask;
        int scheduleMode = command.has("schedule_mode") ? command.optInt("schedule_mode", 0) : old == null ? 0 : old.scheduleMode;
        String label = command.has("label") ? command.optString("label", "闹钟") : old == null ? "闹钟" : old.label;
        boolean enabled = command.has("enabled") ? command.optBoolean("enabled", true) : old == null || old.enabled;
        boolean makeup = command.has("ring_on_makeup_workdays") ? command.optBoolean("ring_on_makeup_workdays", false) : old != null && old.ringOnMakeupWorkdays;
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("请提供有效的 hour (0–23) 和 minute (0–59)");
        if (repeatMask < 0 || repeatMask > 127) throw new IllegalArgumentException("repeat_mask 必须在 0–127 之间");
        return new AlarmItem(id, hour, minute, label, repeatMask, enabled, scheduleMode, makeup);
    }

    private static JSONObject alarmList(Context context) throws Exception {
        JSONObject data = new JSONObject();
        JSONArray alarms = new JSONArray();
        for (AlarmItem alarm : AlarmStore.get(context)) {
            JSONObject item = new JSONObject();
            item.put("id", alarm.id).put("hour", alarm.hour).put("minute", alarm.minute)
                    .put("label", alarm.label).put("repeat_mask", alarm.repeatMask)
                    .put("enabled", alarm.enabled).put("schedule_mode", alarm.scheduleMode)
                    .put("ring_on_makeup_workdays", alarm.ringOnMakeupWorkdays);
            alarms.put(item);
        }
        data.put("alarms", alarms);
        return data;
    }

    private static JSONObject rssConfig(Context context) throws Exception {
        JSONObject data = new JSONObject();
        JSONArray sources = new JSONArray();
        for (AppPrefs.RssSource source : AppPrefs.rssSources(context)) if (!source.url.isEmpty()) sources.put(source.url);
        data.put("rss_sources", sources);
        return data;
    }

    static boolean wake(Context context) {
        AppPrefs.setKeepScreenOn(context, true);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            PowerManager.WakeLock lock = power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "MorningPanel:Wake");
            lock.acquire(10000L);
        }
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(MainActivity.ACTION_WAKE_SCREEN);
        context.startActivity(intent);
        return true;
    }

    static boolean sleep(Context context) {
        AppPrefs.setKeepScreenOn(context, false);
        MainActivity.setKeepScreenAwake(false);
        if (!hasRoot()) return false;
        try {
            String binary = grantedSuBinary == null ? SU_BINARIES[0] : grantedSuBinary;
            Process process = new ProcessBuilder("/system/bin/sh", "-c", binary + " -c 'input keyevent 223'")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) { return false; }
    }

    private static boolean controlAlarm(Context context, boolean snooze) {
        if (!AlarmService.isAlarmActive()) return false;
        AlarmService.markAlarmControlRequested();
        Intent intent = new Intent(context, AlarmService.class)
                .setAction(snooze ? AlarmService.ACTION_SNOOZE : AlarmService.ACTION_STOP);
        context.startService(intent);
        return true;
    }

    static boolean setBrightness(Context context, int percent) {
        int bounded = Math.max(1, Math.min(100, percent));
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) return false;
        int raw = Math.round(bounded * 255f / 100f);
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, raw);
            MainActivity.setWindowBrightness(bounded / 100f);
            return true;
        } catch (Exception ignored) { return false; }
    }
}

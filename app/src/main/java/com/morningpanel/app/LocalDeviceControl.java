package com.morningpanel.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

import org.json.JSONObject;

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
        }
        JSONObject result = new JSONObject();
        try {
            result.put("command", name);
            result.put("success", success);
            result.put("message", message);
            result.put("request_id", command.optString("request_id", ""));
            result.put("updated_at", System.currentTimeMillis());
        } catch (org.json.JSONException ignored) { }
        return result;
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

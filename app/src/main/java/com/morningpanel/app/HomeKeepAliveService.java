package com.morningpanel.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.net.wifi.WifiManager;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps the Home Assistant event connection and device reporting alive independently of the UI. */
public class HomeKeepAliveService extends Service {
    private static final String CHANNEL_ID = "home_companion";
    private static final int NOTIFICATION_ID = 72;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService reporter = Executors.newSingleThreadExecutor();
    private HomeAssistantEventClient eventClient;
    private PowerManager.WakeLock networkWakeLock;
    private WifiManager.WifiLock wifiLock;

    private final Runnable report = new Runnable() {
        @Override public void run() {
            if (isConnectedToHomeAssistant()) {
                reporter.execute(() -> {
                    try {
                        HomeAssistantClient client = new HomeAssistantClient(AppPrefs.haUrl(thisService()),
                                SecretStore.homeAssistantToken(thisService()), AppPrefs.haAllowHttp(thisService()));
                        client.fireEvent(HomeAssistantProtocol.EVENT_UPDATE, LocalDeviceStatus.capture(thisService()));
                    } catch (Exception error) {
                        android.util.Log.w("MorningPanelHA", "Unable to report device state", error);
                    }
                });
            }
            handler.postDelayed(this, 30000);
        }
    };

    private final Runnable refreshHolidayCalendar = new Runnable() {
        @Override public void run() {
            HolidayCalendarClient.synchronizeAsync(HomeKeepAliveService.this, false, null);
            handler.postDelayed(this, 6L * 60L * 60L * 1000L);
        }
    };

    private Context thisService() { return HomeKeepAliveService.this; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        eventClient = new HomeAssistantEventClient(this);
        eventClient.start();
        handler.post(report);
        HolidayCalendarClient.synchronizeAsync(this, false, null);
        handler.postDelayed(refreshHolidayCalendar, 6L * 60L * 60L * 1000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        updateNetworkLocks(isConnectedToHomeAssistant());
        if (eventClient != null) eventClient.reconnect();
        return START_STICKY;
    }

    private void updateNetworkLocks(boolean enabled) {
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (enabled) {
            if (networkWakeLock == null && power != null) {
                networkWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MorningPanel:HaConnection");
                networkWakeLock.setReferenceCounted(false);
            }
            if (networkWakeLock != null && !networkWakeLock.isHeld()) networkWakeLock.acquire();
            if (wifiLock == null && wifi != null) {
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MorningPanel:HaWifi");
                wifiLock.setReferenceCounted(false);
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } else {
            if (networkWakeLock != null && networkWakeLock.isHeld()) networkWakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        }
    }

    static void start(Context context) {
        Intent service = new Intent(context, HomeKeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }

    private boolean isConnectedToHomeAssistant() {
        return !AppPrefs.haUrl(this).isEmpty() && SecretStore.hasHomeAssistantToken(this);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 72, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("晨间面板运行中")
                .setContentText("闹钟、设备状态上报与 HA 控制服务保持运行")
                .setContentIntent(pending)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "桌面常驻服务", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持本机状态上报、Home Assistant 控制与桌面闹钟可用");
        manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(report);
        handler.removeCallbacks(refreshHolidayCalendar);
        if (eventClient != null) eventClient.stop();
        updateNetworkLocks(false);
        reporter.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

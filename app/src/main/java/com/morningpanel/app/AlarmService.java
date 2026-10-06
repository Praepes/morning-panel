package com.morningpanel.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class AlarmService extends Service {
    static final String ACTION_STOP = "com.morningpanel.app.STOP";
    static final String ACTION_SNOOZE = "com.morningpanel.app.SNOOZE";
    private static final String CHANNEL_ID = "alarm_alerts";
    private static final int NOTIFICATION_ID = 71;
    private static volatile boolean alarmActive;
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private String currentLabel = "起床时间到了";

    static boolean isAlarmActive() { return alarmActive; }
    static void markAlarmControlRequested() { alarmActive = false; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            AlarmScheduler.cancelSnooze(this);
            stopAlarm();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            AlarmScheduler.scheduleSnooze(this, currentLabel);
            stopAlarm();
            return START_NOT_STICKY;
        }
        if (intent != null) currentLabel = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) == null
                ? currentLabel : intent.getStringExtra(AlarmScheduler.EXTRA_LABEL);
        startForeground(NOTIFICATION_ID, buildNotification());
        alarmActive = true;
        if (player == null) {
            acquireWakeLock();
            playAlarm();
            try {
                startActivity(alarmActivityIntent());
            } catch (RuntimeException ignored) {
                // The high-priority full-screen intent remains available on locked or restricted devices.
            }
        }
        return START_NOT_STICKY;
    }

    private Intent alarmActivityIntent() {
        return new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_SHOW_ALARM)
                .putExtra(AlarmScheduler.EXTRA_LABEL, currentLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    private Notification buildNotification() {
        PendingIntent fullScreen = PendingIntent.getActivity(this, 71, alarmActivityIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(currentLabel)
                .setContentText("闹钟正在响铃 · 返回桌面滑动处理")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreen, true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "闹钟", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("本機闹钟响鈴与全螢幕提示");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private void playAlarm() {
        try {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, sound);
            player.setLooping(true);
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception ignored) {
            stopSelf();
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MorningPanel:Alarm");
        wakeLock.acquire(15 * 60 * 1000L);
    }

    private void stopAlarm() {
        alarmActive = false;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        alarmActive = false;
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

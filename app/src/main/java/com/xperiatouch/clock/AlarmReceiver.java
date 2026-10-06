package com.xperiatouch.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1);
        String label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL);
        if (id >= 0) {
            List<AlarmItem> alarms = AlarmStore.get(context);
            ArrayList<AlarmItem> changed = new ArrayList<>();
            for (AlarmItem alarm : alarms) {
                if (alarm.id == id) {
                    label = alarm.label;
                    changed.add(alarm.scheduleMode == AlarmItem.SCHEDULE_WEEKLY && alarm.repeatMask == 0
                            ? alarm.withEnabled(false) : alarm);
                } else changed.add(alarm);
            }
            AlarmStore.save(context, changed);
        }
        if (label == null) label = "起床时间到了";
        Intent service = new Intent(context, AlarmService.class)
                .putExtra(AlarmScheduler.EXTRA_LABEL, label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }
}

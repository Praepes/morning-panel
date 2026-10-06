package com.xperiatouch.clock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

final class AlarmScheduler {
    static final String ACTION_FIRE = "com.xperiatouch.clock.FIRE_ALARM";
    static final String EXTRA_ID = "alarm_id";
    static final String EXTRA_LABEL = "alarm_label";
    private static final int SNOOZE_ID = 900001;

    private AlarmScheduler() { }

    static void scheduleAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        List<AlarmItem> alarms = AlarmStore.get(context);
        for (AlarmItem alarm : alarms) {
            PendingIntent operation = alarmPendingIntent(context, alarm.id, alarm.label);
            manager.cancel(operation);
            if (!alarm.enabled) continue;
            long next = nextTrigger(context, alarm, System.currentTimeMillis());
            if (next > 0) manager.setAlarmClock(new AlarmManager.AlarmClockInfo(next, showIntent(context)), operation);
        }
    }

    static void scheduleSnooze(Context context, String label) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent operation = alarmPendingIntent(context, SNOOZE_ID, label);
        manager.cancel(operation);
        long trigger = System.currentTimeMillis() + 10 * 60 * 1000L;
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(trigger, showIntent(context)), operation);
    }

    static void cancelSnooze(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent operation = alarmPendingIntent(context, SNOOZE_ID, "");
        manager.cancel(operation);
        operation.cancel();
    }

    private static PendingIntent alarmPendingIntent(Context context, int id, String label) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(ACTION_FIRE)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_LABEL, label);
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static long nextTrigger(Context context, AlarmItem alarm, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        int horizon = alarm.scheduleMode == AlarmItem.SCHEDULE_WORKDAYS || alarm.repeatMask != 0 ? 370 : 0;
        java.util.HashMap<Integer, HolidayCalendarStore.CalendarData> calendars = new java.util.HashMap<>();
        for (int offset = 0; offset <= horizon; offset++) {
            Calendar candidate = (Calendar) now.clone();
            candidate.add(Calendar.DAY_OF_YEAR, offset);
            candidate.set(Calendar.HOUR_OF_DAY, alarm.hour);
            candidate.set(Calendar.MINUTE, alarm.minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            int day = candidate.get(Calendar.DAY_OF_WEEK);
            int mondayFirstBit = 1 << ((day + 5) % 7);
            boolean dayMatches;
            if (alarm.scheduleMode == AlarmItem.SCHEDULE_WORKDAYS) {
                int year = candidate.get(Calendar.YEAR);
                HolidayCalendarStore.CalendarData calendar = calendars.get(year);
                if (calendar == null) {
                    calendar = HolidayCalendarStore.get(context, year);
                    calendars.put(year, calendar);
                }
                String date = HolidayCalendarStore.dateKey(candidate);
                boolean makeUpWorkday = calendar.workDays.contains(date);
                boolean restDay = calendar.restDays.contains(date);
                boolean weekday = day != Calendar.SATURDAY && day != Calendar.SUNDAY;
                dayMatches = makeUpWorkday ? alarm.ringOnMakeupWorkdays
                        : restDay ? false : weekday;
            } else {
                dayMatches = alarm.repeatMask == 0 ? offset == 0 : (alarm.repeatMask & mondayFirstBit) != 0;
            }
            if (dayMatches && candidate.getTimeInMillis() > nowMillis) return candidate.getTimeInMillis();
        }
        return -1;
    }
}

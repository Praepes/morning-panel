package com.morningpanel.app;

import org.json.JSONException;
import org.json.JSONObject;

final class AlarmItem {
    static final int SCHEDULE_WEEKLY = 0;
    static final int SCHEDULE_WORKDAYS = 1;

    final int id;
    final int hour;
    final int minute;
    final String label;
    final int repeatMask;
    final boolean enabled;
    final int scheduleMode;
    final boolean ringOnMakeupWorkdays;

    AlarmItem(int id, int hour, int minute, String label, int repeatMask, boolean enabled) {
        this(id, hour, minute, label, repeatMask, enabled, SCHEDULE_WEEKLY, false);
    }

    AlarmItem(int id, int hour, int minute, String label, int repeatMask, boolean enabled,
              int scheduleMode, boolean ringOnMakeupWorkdays) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.label = label == null || label.trim().isEmpty() ? "闹钟" : label.trim();
        this.repeatMask = repeatMask;
        this.enabled = enabled;
        this.scheduleMode = scheduleMode == SCHEDULE_WORKDAYS ? SCHEDULE_WORKDAYS : SCHEDULE_WEEKLY;
        this.ringOnMakeupWorkdays = ringOnMakeupWorkdays;
    }

    AlarmItem withEnabled(boolean value) {
        return new AlarmItem(id, hour, minute, label, repeatMask, value, scheduleMode, ringOnMakeupWorkdays);
    }

    JSONObject toJson() throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("hour", hour);
        item.put("minute", minute);
        item.put("label", label);
        item.put("repeatMask", repeatMask);
        item.put("enabled", enabled);
        item.put("scheduleMode", scheduleMode);
        item.put("ringOnMakeupWorkdays", ringOnMakeupWorkdays);
        return item;
    }

    static AlarmItem fromJson(JSONObject item) throws JSONException {
        return new AlarmItem(item.getInt("id"), item.getInt("hour"), item.getInt("minute"),
                item.optString("label", "闹钟"), item.optInt("repeatMask", 0),
                item.optBoolean("enabled", true), item.optInt("scheduleMode", SCHEDULE_WEEKLY),
                item.optBoolean("ringOnMakeupWorkdays", false));
    }
}

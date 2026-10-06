package com.morningpanel.app;

/** Shared Home Assistant event names. Keep these in sync with custom_components/morning_panel/const.py. */
final class HomeAssistantProtocol {
    static final String EVENT_UPDATE = "morning_panel_update";
    static final String EVENT_COMMAND = "morning_panel_command";
    static final String EVENT_COMMAND_RESULT = "morning_panel_command_result";

    private HomeAssistantProtocol() {}
}
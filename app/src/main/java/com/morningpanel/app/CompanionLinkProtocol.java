package com.morningpanel.app;

/** Shared Home Assistant event names. Keep these in sync with custom_components/companion_link/const.py. */
final class CompanionLinkProtocol {
    static final String DEVICE_ID_KEY = "device_id";
    static final String EVENT_UPDATE = "companion_link_update";
    static final String EVENT_COMMAND = "companion_link_command";
    static final String EVENT_COMMAND_RESULT = "companion_link_command_result";

    private CompanionLinkProtocol() {}
}

package com.firstbus.auotnfc.hook

internal object ModuleStatusProtocol {
    const val ACTION_GET_SETTINGS = "com.firstbus.auotnfc.action.GET_SETTINGS"

    // Ordered broadcast resultData
    const val RESULT_SETTINGS_PREFIX = "settings:"

    // Receiver class (explicit broadcast)
    const val RECEIVER_CLASS = "com.firstbus.auotnfc.ipc.ModuleStatusReceiver"

    // SharedPreferences
    const val PREFS_NAME = "autonfc_settings"
    const val KEY_STRATEGY = "strategy"
    const val KEY_LAST_ROOT_OK = "last_root_ok"
}

package com.firstbus.auotnfc.hook

internal object ModuleRootProtocol {

    const val ACTION_TOGGLE_NFC = "com.firstbus.auotnfc.action.TOGGLE_NFC"

    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_REQUEST_ID = "request_id"

    // Ordered broadcast resultData
    const val RESULT_OK = "ok"
    const val RESULT_ERR_PREFIX = "err:"

    // Receiver class (explicit broadcast)
    const val RECEIVER_CLASS = "com.firstbus.auotnfc.root.RootToggleReceiver"
}

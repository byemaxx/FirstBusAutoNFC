package com.firstbus.auotnfc.hook

import android.content.Context
import android.nfc.NfcAdapter

/**
 * Host-side NFC helper.
 *
 * IMPORTANT: This code runs inside the host app process (FirstBus).
 * It must NOT rely on root, because root authorization is package/UID scoped.
 */
internal object NfcToggler {
    fun isEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }
}

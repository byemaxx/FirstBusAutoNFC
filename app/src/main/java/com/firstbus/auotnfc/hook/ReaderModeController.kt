package com.firstbus.auotnfc.hook

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle

internal object ReaderModeController {

    data class Result(val ok: Boolean, val error: String? = null)

    fun enable(activity: Activity): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return Result(ok = false, error = "ReaderMode requires API 19+")
        }

        val adapter = NfcAdapter.getDefaultAdapter(activity)
            ?: return Result(ok = false, error = "NFC not supported")

        return try {
            val flags =
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

            val extras = Bundle().apply {
                // Lower presence check delay reduces repeated callbacks on some devices.
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }

            adapter.enableReaderMode(
                activity,
                { /* no-op */ },
                flags,
                extras
            )
            Logx.i("[reader] enabled")
            Result(ok = true)
        } catch (se: SecurityException) {
            // Likely host app lacks android.permission.NFC in its manifest.
            Logx.w("[reader] SecurityException: ${se.message}")
            Result(ok = false, error = "SecurityException: ${se.message}")
        } catch (t: Throwable) {
            Logx.e("[reader] enable failed", t)
            Result(ok = false, error = t.message ?: t.javaClass.name)
        }
    }

    fun disable(activity: Activity): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return Result(ok = true)
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return Result(ok = true)

        return try {
            adapter.disableReaderMode(activity)
            Logx.i("[reader] disabled")
            Result(ok = true)
        } catch (t: Throwable) {
            Logx.e("[reader] disable failed", t)
            Result(ok = false, error = t.message ?: t.javaClass.name)
        }
    }
}

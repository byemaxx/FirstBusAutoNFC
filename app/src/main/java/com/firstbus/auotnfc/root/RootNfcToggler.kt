package com.firstbus.auotnfc.root

import android.content.Context
import android.nfc.NfcAdapter
import com.firstbus.auotnfc.hook.Logx
import com.firstbus.auotnfc.hook.RootShell

internal object RootNfcToggler {

    data class ToggleResult(
        val ok: Boolean,
        val error: String? = null
    )

    fun setEnabled(context: Context, enabled: Boolean): ToggleResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return ToggleResult(ok = false, error = "NFC is not supported on this device")

        // Quick no-op
        val before = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (before == enabled) return ToggleResult(ok = true)

        // Root check in module app process
        val probe = RootShell.exec("id", timeoutMs = 2_000L)
            ?: return ToggleResult(ok = false, error = "su not found in module process")
        if (!probe.isSuccess) {
            return ToggleResult(
                ok = false,
                error = "su execution failed: ${probe.stderr.trim().ifEmpty { probe.stdout.trim() }}"
            )
        }
        if (!probe.stdout.contains("uid=0")) return ToggleResult(ok = false, error = "Root not granted to the module app")

        val commands = if (enabled) {
            listOf(
                "cmd nfc enable-nfc",
                "svc nfc enable",

            )
        } else {
            listOf(
                "cmd nfc disable-nfc",
                "svc nfc disable",

            )
        }

        var lastErr: String? = null

        // Probe list each time (no caching/prefs)
        for (cmd in commands) {
            Logx.i("[root] probing cmd: $cmd")
            val r = RootShell.exec(cmd, timeoutMs = 4_000L)
            Logx.d("[root] cmd='$cmd' exit=${r?.exitCode} out='${r?.stdout?.trim()}' err='${r?.stderr?.trim()}'")
            if (r?.isSuccess == true && waitUntilState(adapter, enabled)) {
                Logx.i("[root] success enabled=$enabled")
                return ToggleResult(ok = true)
            }
            if (r != null && !r.isSuccess) {
                lastErr = r.stderr.trim().ifEmpty { r.stdout.trim() }.ifEmpty { "exit=${r.exitCode}" }
            }
        }

        val ok = waitUntilState(adapter, enabled)
        return if (ok) ToggleResult(ok = true) else ToggleResult(ok = false, error = lastErr ?: "Command did not take effect (ROM restriction)")
    }

    private fun waitUntilState(adapter: NfcAdapter, enabled: Boolean): Boolean {
        repeat(12) {
            val current = runCatching { adapter.isEnabled }.getOrDefault(false)
            if (current == enabled) return true
            runCatching { Thread.sleep(120) }
        }
        return runCatching { adapter.isEnabled }.getOrDefault(false) == enabled
    }
}

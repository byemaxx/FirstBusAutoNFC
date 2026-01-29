package com.firstbus.auotnfc.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import com.firstbus.auotnfc.hook.Logx
import com.firstbus.auotnfc.hook.ModuleRootProtocol

class RootToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        val callingUid = runCatching { Binder.getCallingUid() }.getOrDefault(-1)
        val callingPkgs = runCatching { context.packageManager.getPackagesForUid(callingUid)?.toList() ?: emptyList() }
            .getOrDefault(emptyList())

        val enabled = intent.getBooleanExtra(ModuleRootProtocol.EXTRA_ENABLED, false)
        val requestId = intent.getStringExtra(ModuleRootProtocol.EXTRA_REQUEST_ID) ?: "-"

        Logx.i("[ipc] recv toggle enabled=$enabled req=$requestId uid=$callingUid pkgs=$callingPkgs")

        // Basic allowlist: only accept from FirstBus or self
        val allowed = callingPkgs.contains("com.firstgroup.first.bus") || callingPkgs.contains(context.packageName)
        if (!allowed) {
            pending.resultCode = 1
            pending.resultData = ModuleRootProtocol.RESULT_ERR_PREFIX + "caller_not_allowed"
            Logx.w("[ipc] reject caller uid=$callingUid pkgs=$callingPkgs")
            pending.finish()
            return
        }

        Thread {
            try {
                val result = RootNfcToggler.setEnabled(appContext, enabled)
                if (result.ok) {
                    Logx.i("[ipc] completed enabled=$enabled ok")
                    pending.resultCode = 0
                    pending.resultData = ModuleRootProtocol.RESULT_OK
                } else {
                    Logx.w("[ipc] completed enabled=$enabled err=${result.error}")
                    pending.resultCode = 1
                    pending.resultData = ModuleRootProtocol.RESULT_ERR_PREFIX + (result.error ?: "unknown")
                }
                Logx.i("[ipc] done enabled=$enabled req=$requestId ok=${result.ok}")
            } catch (t: Throwable) {
                pending.resultCode = 3
                pending.resultData = ModuleRootProtocol.RESULT_ERR_PREFIX + (t.message ?: t.javaClass.name)
                Logx.e("[ipc] crash enabled=$enabled req=$requestId", t)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

package com.firstbus.auotnfc.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import com.firstbus.auotnfc.hook.Logx
import com.firstbus.auotnfc.hook.ModuleSettingsStore
import com.firstbus.auotnfc.hook.ModuleStatusProtocol

class ModuleStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()

        val callingUid = runCatching { Binder.getCallingUid() }.getOrDefault(-1)
        val callingPkgs = runCatching { context.packageManager.getPackagesForUid(callingUid)?.toList() ?: emptyList() }
            .getOrDefault(emptyList())

        // Basic allowlist: only accept from FirstBus or self
        val allowed = callingPkgs.contains("com.firstgroup.first.bus") || callingPkgs.contains(context.packageName)
        if (!allowed) {
            pending.resultCode = 1
            pending.resultData = ModuleStatusProtocol.RESULT_SETTINGS_PREFIX + "rejected"
            Logx.w("[ipc] status reject caller uid=$callingUid pkgs=$callingPkgs")
            pending.finish()
            return
        }

        Thread {
            try {
                when (intent.action) {
                    ModuleStatusProtocol.ACTION_GET_SETTINGS -> {
                        val strategy = ModuleSettingsStore.getStrategy(appContext)
                        pending.resultCode = 0
                        pending.resultData = ModuleStatusProtocol.RESULT_SETTINGS_PREFIX + strategy.wireValue
                        Logx.d("[ipc] get settings strategy=${strategy.wireValue}")
                    }

                    else -> {
                        pending.resultCode = 2
                        pending.resultData = ModuleStatusProtocol.RESULT_SETTINGS_PREFIX + "unknown_action"
                    }
                }
            } catch (t: Throwable) {
                pending.resultCode = 3
                pending.resultData = ModuleStatusProtocol.RESULT_SETTINGS_PREFIX + (t.message ?: t.javaClass.name)
                Logx.e("[ipc] status receiver crash", t)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

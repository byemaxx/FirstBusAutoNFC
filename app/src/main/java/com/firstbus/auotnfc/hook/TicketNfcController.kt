package com.firstbus.auotnfc.hook

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.firstbus.auotnfc.BuildConfig
import java.util.WeakHashMap

internal object TicketNfcController {

    private data class Session(
        val originalEnabled: Boolean,
        var disabledByModule: Boolean = false,
        var readerModeEnabled: Boolean = false,
        var restored: Boolean = false,
        var isToggling: Boolean = false,
        var keepOffToastShown: Boolean = false
    )

    private val sessions = WeakHashMap<Activity, Session>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val enterToastToken = Any()

    fun onTicketResume(activity: Activity) {
        Logx.i("[ticket] onResume activity=${activity.javaClass.name}")
        cancelPendingEnterToasts()
        val session = sessions[activity] ?: Session(
            originalEnabled = NfcToggler.isEnabled(activity)
        ).also { sessions[activity] = it }

        Logx.d("[ticket] session originalEnabled=${session.originalEnabled} disabledByModule=${session.disabledByModule} readerModeEnabled=${session.readerModeEnabled} restored=${session.restored}")

        // If NFC was originally OFF, keep it OFF and do nothing.
        if (!session.originalEnabled) {
            if (!session.keepOffToastShown) {
                session.keepOffToastShown = true
                toastOnce(activity, "AutoNFC: NFC already OFF (keeping OFF)")
            }
            return
        }

        // If we've already applied protection for this Activity session, don't spam.
        if (session.disabledByModule || session.readerModeEnabled || session.isToggling) return
        session.isToggling = true


        // Strategy is stored in module app prefs; host must query it via IPC.
        requestModuleStrategy(activity) { strategy ->
            when (strategy) {
                NfcProtectionStrategy.ROOT -> {
                    requestModuleToggle(activity, enabled = false) { ok, err ->
                        if (ok) {
                            session.isToggling = false
                            session.disabledByModule = true
                            toastEnterStatus(activity, "AutoNFC: NFC OFF (Root)")
                        } else {
                            Logx.w("[ticket] root toggle failed, fallback to ReaderMode err=$err")
                            enableReaderModeFallback(activity, session, err)
                        }
                    }
                }

                NfcProtectionStrategy.READER_MODE -> {
                    enableReaderModeFallback(activity, session, "forced_reader_mode")
                }
            }
        }
    }

    fun onTicketStop(activity: Activity) {
        Logx.i("[ticket] onStop/onPause activity=${activity.javaClass.name}")
        cancelPendingEnterToasts()
        val session = sessions[activity] ?: return
        if (session.restored) return
        session.restored = true

        // Restore whichever protection we used.
        if (session.originalEnabled) {
            if (session.disabledByModule) {
                if (!session.isToggling) {
                    session.isToggling = true
                    requestModuleToggle(activity, enabled = true) { ok, err ->
                        session.isToggling = false
                        if (ok) toastOnce(activity, "AutoNFC: NFC ON restored (Root)")
                        else toastOnce(activity, "AutoNFC: restore failed: ${err ?: "unknown"}")
                    }
                }
            }

            if (session.readerModeEnabled) {
                if (!session.isToggling) {
                    session.isToggling = true
                    val r = ReaderModeController.disable(activity)
                    mainHandler.post {
                        session.isToggling = false
                        if (r.ok) toastOnce(activity, "AutoNFC: ReaderMode disabled")
                        else toastOnce(activity, "AutoNFC: ReaderMode disable failed: ${r.error ?: "unknown"}")
                    }
                }
            }
        }

        sessions.remove(activity)
    }

    private fun enableReaderModeFallback(activity: Activity, session: Session, reason: String?) {
        val r = ReaderModeController.enable(activity)
        mainHandler.post {
            session.isToggling = false
            if (r.ok) {
                session.readerModeEnabled = true
                toastEnterStatus(activity, "AutoNFC: ReaderMode enabled")
            } else {
                val msg = r.error ?: "Failed to enable NFC ReaderMode"
                toastOnce(activity, "AutoNFC: ReaderMode enable failed: $msg")
                Logx.w("[ticket] reader mode failed reason=$reason err=$msg")
            }
        }
    }

    private fun requestModuleToggle(activity: Activity, enabled: Boolean, callback: (ok: Boolean, err: String?) -> Unit) {
        val intent = Intent(ModuleRootProtocol.ACTION_TOGGLE_NFC).apply {
            setClassName(BuildConfig.APPLICATION_ID, ModuleRootProtocol.RECEIVER_CLASS)
            putExtra(ModuleRootProtocol.EXTRA_ENABLED, enabled)
            putExtra(ModuleRootProtocol.EXTRA_REQUEST_ID, "-")
        }

        runCatching {
            activity.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        val code = resultCode
                        val data = resultData
                        val ok = code == 0 && data == ModuleRootProtocol.RESULT_OK
                        val err = data?.removePrefix(ModuleRootProtocol.RESULT_ERR_PREFIX)
                        callback(ok, err)
                    }
                },
                mainHandler,
                0,
                null,
                null
            )
        }.onFailure {
            callback(false, it.message ?: it.javaClass.name)
        }
    }

    private fun requestModuleStrategy(activity: Activity, callback: (NfcProtectionStrategy) -> Unit) {
        // Default: ReaderMode is safest on non-root devices
        val defaultStrategy = NfcProtectionStrategy.READER_MODE
        val intent = Intent(ModuleStatusProtocol.ACTION_GET_SETTINGS).apply {
            setClassName(BuildConfig.APPLICATION_ID, ModuleStatusProtocol.RECEIVER_CLASS)
        }

        runCatching {
            activity.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        val data = resultData
                        val strategyWire = data?.removePrefix(ModuleStatusProtocol.RESULT_SETTINGS_PREFIX)
                        val parsed = NfcProtectionStrategy.fromWireValue(strategyWire)
                        callback(parsed ?: defaultStrategy)
                    }
                },
                mainHandler,
                0,
                null,
                null
            )
        }.onFailure {
            callback(defaultStrategy)
        }
    }


    private fun toastEnterStatus(activity: Activity, text: String) {
        // Extended visibility on entering ticket page
        cancelPendingEnterToasts()
        toastRepeated(activity, text, Toast.LENGTH_LONG, totalMs = 6_000L, intervalMs = 3_000L)
    }

    private fun cancelPendingEnterToasts() {
        mainHandler.removeCallbacksAndMessages(enterToastToken)
    }

    private fun toastOnce(activity: Activity, text: String) {
        val ctx = activity.applicationContext
        mainHandler.post { runCatching { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() } }
    }

    private fun toastRepeated(
        activity: Activity,
        text: String,
        duration: Int,
        totalMs: Long,
        intervalMs: Long
    ) {
        val ctx = activity.applicationContext
        val times = ((totalMs + intervalMs - 1) / intervalMs).toInt().coerceAtLeast(1)
        val startAt = SystemClock.uptimeMillis()
        for (i in 0 until times) {
            val runnable = Runnable {
                runCatching { Toast.makeText(ctx, text, duration).show() }
            }
            mainHandler.postAtTime(runnable, enterToastToken, startAt + i * intervalMs)
        }
    }

}

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

    private const val TICKET_ACTIVITY_CLASS = "com.firstgroup.main.tabs.mtickets.ticket.mvp.TicketActivity"

    private data class Session(
        val originalEnabled: Boolean,
        var disabledByModule: Boolean = false,
        var readerModeRequested: Boolean = false,
        var readerModeActiveAssumed: Boolean = false,
        var restored: Boolean = false,
        var isToggling: Boolean = false,
        var keepOffToastShown: Boolean = false,
        var enterToastShown: Boolean = false,
        var lastReaderEnableAtMs: Long = 0L,
        var lastDisableRequestedAtMs: Long = 0L,
        var lastDisableRequestedBy: String? = null,
        var lastDisableRequestedStack: String? = null,
        var lastDisableExecutedAtMs: Long = 0L,
        var lastDisableExecutedBy: String? = null,
        var pendingDisable: Runnable? = null
    )

    private val sessions = WeakHashMap<Activity, Session>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val enterToastToken = Any()

    private const val IPC_STRATEGY_TIMEOUT_MS = 2_500L
    private const val IPC_TOGGLE_TIMEOUT_MS = 4_000L

    private const val PAUSE_DISABLE_DELAY_MS = 650L
    private const val REAPPLY_MIN_INTERVAL_MS = 500L

    @Volatile
    private var debugLogEnabled: Boolean = false

    @Volatile
    private var debugStackTraceEnabled: Boolean = false

    private val bypassDisableReaderHook = ThreadLocal<Boolean>()

    fun isDebugStackTraceEnabled(): Boolean = debugStackTraceEnabled

    internal fun shouldBypassDisableReaderModeHook(): Boolean = bypassDisableReaderHook.get() == true

    private inline fun <T> withBypassDisableReaderModeHook(block: () -> T): T {
        val prev = bypassDisableReaderHook.get() == true
        bypassDisableReaderHook.set(true)
        return try {
            block()
        } finally {
            if (prev) bypassDisableReaderHook.set(true) else bypassDisableReaderHook.remove()
        }
    }

    fun isTicketActivity(activity: Activity): Boolean {
        return activity.javaClass.name == TICKET_ACTIVITY_CLASS
    }

    fun onTicketPause(activity: Activity) {
        // Do NOT restore onPause. Ticket screen may still be effectively visible (dialogs/overlays).
        cancelPendingEnterToasts()
        val session = sessions[activity] ?: return
        if (session.restored) return
        Logx.d("[ticket] onPause activity=${activity.javaClass.name}")

        // If using ReaderMode, schedule a delayed disable. If focus returns quickly, we cancel.
        // This reduces the chance of a short-lived pause dropping protection.
        if (session.readerModeActiveAssumed) {
            // If framework/app already requested a disable very recently (or we already have one pending),
            // don't cancel/reschedule again from onPause; it just adds churn and can extend the window.
            val now = SystemClock.uptimeMillis()
            val recentlyRequested = session.lastDisableRequestedAtMs > 0 && now - session.lastDisableRequestedAtMs < 250L
            if (session.pendingDisable == null && !recentlyRequested) {
                scheduleDelayedReaderDisable(activity, session, "pause")
            }
        }
    }

    fun onTicketPostResume(activity: Activity) {
        val session = sessions[activity] ?: return
        if (session.restored) return
        Logx.d("[ticket] onPostResume activity=${activity.javaClass.name}")
        cancelDelayedReaderDisable(session)
        if (!session.originalEnabled) return
        ensureProtection(activity, session, reason = "post_resume", force = false)
    }

    fun onTicketWindowFocusChanged(activity: Activity, hasFocus: Boolean) {
        val session = sessions[activity] ?: return
        if (session.restored) return
        if (hasFocus) {
            Logx.d("[ticket] focus gained activity=${activity.javaClass.name}")
            cancelDelayedReaderDisable(session)
            if (!session.originalEnabled) return
            val now = SystemClock.uptimeMillis()
            if (now - session.lastReaderEnableAtMs < REAPPLY_MIN_INTERVAL_MS) return
            ensureProtection(activity, session, reason = "focus_gained", force = false)
        } else {
            Logx.d("[ticket] focus lost activity=${activity.javaClass.name}")
            // If focus is lost, don't immediately drop protection; rely on delayed pause disable
            // and/or onStop restore.
        }
    }

    fun onHostDisableReaderModeRequested(
        activity: Activity,
        stackTrace: String?,
        requestedBy: String = "host_disableReaderMode",
        origin: String = "host_disable"
    ) {
        val session = sessions[activity] ?: return
        if (session.restored) return
        session.lastDisableRequestedAtMs = SystemClock.uptimeMillis()
        session.lastDisableRequestedBy = requestedBy
        session.lastDisableRequestedStack = stackTrace
        Logx.i(
            "[ticket] disableReaderMode requested (will delay) " +
                "requestedBy=$requestedBy activity=${activity.javaClass.name} requestedAt=${session.lastDisableRequestedAtMs}"
        )
        if (stackTrace != null && debugStackTraceEnabled) {
            Logx.d("[ticket] disableReaderMode host stack\n$stackTrace")
        }
        // Delay actual disable to survive short-lived pauses.
        scheduleDelayedReaderDisable(activity, session, origin = origin)
    }

    fun onTicketResume(activity: Activity) {
        Logx.i("[ticket] onResume activity=${activity.javaClass.name}")
        cancelPendingEnterToasts()
        val session = sessions[activity] ?: Session(
            originalEnabled = NfcToggler.isEnabled(activity)
        ).also { sessions[activity] = it }

        Logx.d("[ticket] session originalEnabled=${session.originalEnabled} disabledByModule=${session.disabledByModule} readerModeRequested=${session.readerModeRequested} readerModeActiveAssumed=${session.readerModeActiveAssumed} restored=${session.restored}")

        // If NFC was originally OFF, keep it OFF and do nothing.
        if (!session.originalEnabled) {
            if (!session.keepOffToastShown) {
                session.keepOffToastShown = true
                toastOnce(activity, "AutoNFC: NFC already OFF (keeping OFF)")
            }
            return
        }

        cancelDelayedReaderDisable(session)
        // Intentionally do not call ensureProtection here.
        // Keep onPostResume as the primary trigger to avoid duplicate enable attempts.
    }

    fun onHostEnableReaderModeCalled(activity: Activity) {
        // Host may enable ReaderMode inside TicketActivity.onResume BEFORE our after-hook runs.
        // Record it to keep our state aligned with system logs.
        val session = sessions[activity] ?: Session(
            originalEnabled = NfcToggler.isEnabled(activity)
        ).also { sessions[activity] = it }

        session.readerModeRequested = true
        session.readerModeActiveAssumed = true
        session.lastReaderEnableAtMs = SystemClock.uptimeMillis()
    }

    fun onTicketStop(activity: Activity) {
        Logx.i("[ticket] onStop activity=${activity.javaClass.name}")
        cancelPendingEnterToasts()
        val session = sessions[activity] ?: return
        if (session.restored) return
        session.restored = true

        cancelDelayedReaderDisable(session)

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

            if (session.readerModeActiveAssumed) {
                if (!session.isToggling) {
                    session.isToggling = true
                    session.lastDisableRequestedAtMs = SystemClock.uptimeMillis()
                    session.lastDisableRequestedBy = "module_onStop"
                    session.lastDisableRequestedStack = null
                    val r = withBypassDisableReaderModeHook { ReaderModeController.disable(activity) }
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

    private fun ensureProtection(activity: Activity, session: Session, reason: String, force: Boolean) {
        if (session.restored || !isActivityUsable(activity) || sessions[activity] !== session) {
            session.isToggling = false
            return
        }

        // If we're already protected and this isn't a forced re-apply, keep quiet.
        // NOTE: don't early-return on readerModeActiveAssumed here, because ROOT strategy may still need to toggle NFC off
        // even if host/framework already enabled ReaderMode.
        if (!force && (session.disabledByModule || session.isToggling)) return

        // If already in toggling state, don't stack.
        if (session.isToggling) return
        session.isToggling = true

        requestModuleStrategy(activity) { strategy ->
            if (session.restored || !isActivityUsable(activity) || sessions[activity] !== session) {
                session.isToggling = false
                return@requestModuleStrategy
            }

            when (strategy) {
                NfcProtectionStrategy.ROOT -> {
                    session.readerModeRequested = false
                    // If we already successfully disabled NFC via root, don't re-toggle.
                    if (session.disabledByModule) {
                        session.isToggling = false
                        return@requestModuleStrategy
                    }
                    requestModuleToggle(activity, enabled = false) { ok, err ->
                        if (session.restored || !isActivityUsable(activity) || sessions[activity] !== session) {
                            session.isToggling = false
                            return@requestModuleToggle
                        }
                        if (ok) {
                            session.isToggling = false
                            session.disabledByModule = true
                            session.enterToastShown = true
                            toastEnterStatus(activity, "AutoNFC: NFC OFF (Root)")
                        } else {
                            Logx.w("[ticket] root toggle failed, fallback to ReaderMode err=$err")
                            enableReaderModeFallback(activity, session, err)
                        }
                    }
                }

                NfcProtectionStrategy.READER_MODE -> {
                    session.readerModeRequested = true

                    // If ReaderMode was enabled by the host/framework before we got a chance to enable it ourselves,
                    // ensure the user still gets an entry toast once per session (only for READER_MODE strategy).
                    if (!session.enterToastShown && session.readerModeActiveAssumed && !session.disabledByModule) {
                        session.enterToastShown = true
                        toastEnterStatus(activity, "AutoNFC: ReaderMode active")
                    }

                    // Idempotence/throttle: avoid hammering enableReaderMode on focus churn or duplicate triggers.
                    val now = SystemClock.uptimeMillis()
                    if (!force && session.readerModeActiveAssumed && now - session.lastReaderEnableAtMs < REAPPLY_MIN_INTERVAL_MS) {
                        session.isToggling = false
                        return@requestModuleStrategy
                    }
                    enableReaderModeFallback(activity, session, reason)
                }
            }
        }
    }

    private fun enableReaderModeFallback(activity: Activity, session: Session, reason: String?) {
        if (session.restored || !isActivityUsable(activity) || sessions[activity] !== session) {
            session.isToggling = false
            return
        }
        // Mark intent BEFORE the binder call so state matches system logs.
        session.readerModeRequested = true
        val r = ReaderModeController.enable(activity)
        mainHandler.post {
            session.isToggling = false
            if (r.ok) {
                session.readerModeActiveAssumed = true
                session.lastReaderEnableAtMs = SystemClock.uptimeMillis()
                session.enterToastShown = true
                toastEnterStatus(activity, "AutoNFC: ReaderMode enabled")
            } else {
                val msg = r.error ?: "Failed to enable NFC ReaderMode"
                toastOnce(activity, "AutoNFC: ReaderMode enable failed: $msg")
                Logx.w("[ticket] reader mode failed reason=$reason err=$msg")
            }
        }
    }

    private fun scheduleDelayedReaderDisable(activity: Activity, session: Session, origin: String) {
        cancelDelayedReaderDisable(session)
        Logx.i(
            "[ticket] scheduled delayed reader disable origin=$origin delayMs=$PAUSE_DISABLE_DELAY_MS " +
                "requestedBy=${session.lastDisableRequestedBy}"
        )
        val runnable = Runnable {
            // Only disable if still the same active session and not restored.
            if (session.restored || !isActivityUsable(activity) || sessions[activity] !== session) return@Runnable
            val now = SystemClock.uptimeMillis()
            val delta = if (session.lastDisableRequestedAtMs > 0) now - session.lastDisableRequestedAtMs else -1
            Logx.i(
                "[ticket] executing delayed reader disable origin=$origin " +
                    "requestedBy=${session.lastDisableRequestedBy} deltaMs=$delta"
            )
            session.lastDisableExecutedAtMs = SystemClock.uptimeMillis()
            session.lastDisableExecutedBy = "module_delayed_disable(origin=$origin)"
            val r = withBypassDisableReaderModeHook { ReaderModeController.disable(activity) }
            if (r.ok) {
                session.readerModeActiveAssumed = false
                Logx.i("[ticket] reader disabled (delayed) origin=$origin")
            } else {
                Logx.w("[ticket] reader delayed disable failed origin=$origin err=${r.error}")
            }
        }
        session.pendingDisable = runnable
        mainHandler.postDelayed(runnable, PAUSE_DISABLE_DELAY_MS)
    }

    private fun cancelDelayedReaderDisable(session: Session) {
        val r = session.pendingDisable ?: return
        mainHandler.removeCallbacks(r)
        session.pendingDisable = null
        Logx.d("[ticket] cancelled delayed reader disable")
    }

    private fun requestModuleToggle(activity: Activity, enabled: Boolean, callback: (ok: Boolean, err: String?) -> Unit) {
        val intent = Intent(ModuleRootProtocol.ACTION_TOGGLE_NFC).apply {
            setClassName(BuildConfig.APPLICATION_ID, ModuleRootProtocol.RECEIVER_CLASS)
            putExtra(ModuleRootProtocol.EXTRA_ENABLED, enabled)
            putExtra(ModuleRootProtocol.EXTRA_REQUEST_ID, "-")
        }

        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeout = Runnable {
            if (called.compareAndSet(false, true)) {
                callback(false, "timeout")
            }
        }
        mainHandler.postDelayed(timeout, IPC_TOGGLE_TIMEOUT_MS)

        runCatching {
            activity.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        mainHandler.removeCallbacks(timeout)
                        if (!called.compareAndSet(false, true)) return
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
            mainHandler.removeCallbacks(timeout)
            if (!called.compareAndSet(false, true)) return@onFailure
            callback(false, it.message ?: it.javaClass.name)
        }
    }

    private fun requestModuleStrategy(activity: Activity, callback: (NfcProtectionStrategy) -> Unit) {
        // Default: ReaderMode is safest on non-root devices
        val defaultStrategy = NfcProtectionStrategy.READER_MODE
        val intent = Intent(ModuleStatusProtocol.ACTION_GET_SETTINGS).apply {
            setClassName(BuildConfig.APPLICATION_ID, ModuleStatusProtocol.RECEIVER_CLASS)
        }

        val called = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeout = Runnable {
            if (called.compareAndSet(false, true)) {
                callback(defaultStrategy)
            }
        }
        mainHandler.postDelayed(timeout, IPC_STRATEGY_TIMEOUT_MS)

        runCatching {
            activity.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        mainHandler.removeCallbacks(timeout)
                        if (!called.compareAndSet(false, true)) return
                        val data = resultData
                        val payload = data?.removePrefix(ModuleStatusProtocol.RESULT_SETTINGS_PREFIX)
                        val settings = parseSettingsPayload(payload)

                        debugLogEnabled = settings.debugLog
                        debugStackTraceEnabled = settings.debugStack || settings.debugLog
                        Logx.setDebugEnabled(settings.debugLog)

                        val parsed = NfcProtectionStrategy.fromWireValue(settings.strategyWire)
                        callback(parsed ?: defaultStrategy)
                    }
                },
                mainHandler,
                0,
                null,
                null
            )
        }.onFailure {
            mainHandler.removeCallbacks(timeout)
            if (!called.compareAndSet(false, true)) return@onFailure
            callback(defaultStrategy)
        }
    }

    private data class SettingsPayload(
        val strategyWire: String?,
        val debugLog: Boolean,
        val debugStack: Boolean
    )

    private fun parseSettingsPayload(payload: String?): SettingsPayload {
        if (payload.isNullOrBlank()) return SettingsPayload(strategyWire = null, debugLog = false, debugStack = false)

        // Backward compatible:
        // - old format: "reader_mode" / "root"
        // - new format: "strategy=reader_mode;debug_stack=1"
        if (!payload.contains('=')) return SettingsPayload(strategyWire = payload, debugLog = false, debugStack = false)

        val pairs = payload.split(';')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0 || idx == part.lastIndex) return@mapNotNull null
                part.substring(0, idx).trim() to part.substring(idx + 1).trim()
            }
            .toMap()

        val strategy = pairs["strategy"]
        val debugLog = pairs[ModuleStatusProtocol.KEY_DEBUG_LOG]?.let { it == "1" || it.equals("true", true) } ?: false
        val debugStack = pairs[ModuleStatusProtocol.KEY_DEBUG_STACK]?.let { it == "1" || it.equals("true", true) } ?: false
        return SettingsPayload(strategyWire = strategy, debugLog = debugLog, debugStack = debugStack)
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

    private fun isActivityUsable(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            !activity.isDestroyed
        } else {
            true
        }
    }

}

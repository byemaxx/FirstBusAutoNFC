package com.firstbus.auotnfc.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.firstbus.auotnfc.BuildConfig
import java.util.WeakHashMap
import java.util.UUID

internal object TicketNfcController {

    private data class Session(
        val originalEnabled: Boolean,
        var disabledByModule: Boolean = false,
        var restored: Boolean = false,
        var isToggling: Boolean = false,
        var failureDialogShown: Boolean = false,
        var keepOffToastShown: Boolean = false
    )

    private val sessions = WeakHashMap<Activity, Session>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onTicketResume(activity: Activity) {
        Logx.i("[ticket] onResume activity=${activity.javaClass.name}")
        val session = sessions[activity] ?: Session(
            originalEnabled = NfcToggler.isEnabled(activity)
        ).also { sessions[activity] = it }

        Logx.d("[ticket] session originalEnabled=${session.originalEnabled} disabledByModule=${session.disabledByModule} restored=${session.restored}")

        // If NFC was originally OFF, keep it OFF and do nothing.
        if (!session.originalEnabled) {
            if (!session.keepOffToastShown) {
                session.keepOffToastShown = true
                toastOnMain(activity, "NFC is already OFF. Keeping it OFF.")
            }
            return
        }

        // If we've already disabled it for this Activity session, don't spam.
        if (session.disabledByModule || session.isToggling) return
        session.isToggling = true

        Thread {
            val reqId = UUID.randomUUID().toString()
            Logx.i("[ipc] request disable req=$reqId")

            val intent = Intent(ModuleRootProtocol.ACTION_TOGGLE_NFC).apply {
                setClassName(BuildConfig.APPLICATION_ID, ModuleRootProtocol.RECEIVER_CLASS)
                putExtra(ModuleRootProtocol.EXTRA_ENABLED, false)
                putExtra(ModuleRootProtocol.EXTRA_REQUEST_ID, reqId)
            }

            activity.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: Intent) {
                        val code = resultCode
                        val data = resultData
                        Logx.i("[ipc] reply disable req=$reqId code=$code data=$data")
                        mainHandler.post {
                            session.isToggling = false
                            if (code == 0 && data == ModuleRootProtocol.RESULT_OK) {
                                session.disabledByModule = true
                                toastOnMain(activity, "NFC turned OFF automatically")
                            } else {
                                val reason = data?.removePrefix(ModuleRootProtocol.RESULT_ERR_PREFIX)
                                showFailureDialogOnce(
                                    activity = activity,
                                    session = session,
                                    message = reason?.ifBlank { null }
                                        ?: "Failed to turn OFF NFC automatically."
                                )
                            }
                        }
                    }
                },
                mainHandler,
                0,
                null,
                null
            )
        }.start()
    }

    fun onTicketStop(activity: Activity) {
        Logx.i("[ticket] onStop/onPause activity=${activity.javaClass.name}")
        val session = sessions[activity] ?: return
        if (session.restored) return
        session.restored = true

        // Only restore if it was originally ON and we actually changed it.
        if (session.originalEnabled && session.disabledByModule) {
            if (!session.isToggling) {
                session.isToggling = true
                Thread {
                    val reqId = UUID.randomUUID().toString()
                    Logx.i("[ipc] request enable req=$reqId")

                    val intent = Intent(ModuleRootProtocol.ACTION_TOGGLE_NFC).apply {
                        setClassName(BuildConfig.APPLICATION_ID, ModuleRootProtocol.RECEIVER_CLASS)
                        putExtra(ModuleRootProtocol.EXTRA_ENABLED, true)
                        putExtra(ModuleRootProtocol.EXTRA_REQUEST_ID, reqId)
                    }

                    activity.sendOrderedBroadcast(
                        intent,
                        null,
                        object : BroadcastReceiver() {
                            override fun onReceive(context: android.content.Context, intent: Intent) {
                                val code = resultCode
                                val data = resultData
                                Logx.i("[ipc] reply enable req=$reqId code=$code data=$data")
                                mainHandler.post {
                                    session.isToggling = false
                                    if (code == 0 && data == ModuleRootProtocol.RESULT_OK) {
                                        toastOnMain(activity, "NFC restored to ON")
                                    } else {
                                        val reason = data?.removePrefix(ModuleRootProtocol.RESULT_ERR_PREFIX)
                                        toastOnMain(activity, reason?.ifBlank { null } ?: "Failed to restore NFC to ON")
                                    }
                                }
                            }
                        },
                        mainHandler,
                        0,
                        null,
                        null
                    )
                }.start()
            }
        }

        sessions.remove(activity)
    }

    private fun toastOnMain(activity: Activity, text: String) {
        mainHandler.post {
            runCatching {
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFailureDialogOnce(activity: Activity, session: Session, message: String) {
        if (session.failureDialogShown) return
        session.failureDialogShown = true

        mainHandler.post {
            if (activity.isFinishing) return@post
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) return@post

            runCatching {
                AlertDialog.Builder(activity)
                    .setTitle("Auto NFC Toggle Failed")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }.onFailure { Logx.e("[ui] failed to show dialog", it) }
        }
    }
}

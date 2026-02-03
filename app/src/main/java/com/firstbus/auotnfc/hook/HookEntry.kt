package com.firstbus.auotnfc.hook

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        debugLog {
            tag = "FirstBusAutoNFC"
        }
    }

    override fun onHook() = encase {
        loadApp(name = "com.firstgroup.first.bus") {
            Logx.i("[hook] loaded app=com.firstgroup.first.bus")

            // Fallback: TicketActivity might NOT override some lifecycle methods (e.g. onPostResume/onWindowFocusChanged).
            // Hook Activity-level callbacks and filter at runtime.
            "android.app.Activity".toClass().resolve().apply {
                firstMethod {
                    name = "onPostResume"
                    emptyParameters()
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching {
                            val activity = instance<Activity>()
                            if (TicketNfcController.isTicketActivity(activity)) {
                                TicketNfcController.onTicketPostResume(activity)
                            }
                        }.onFailure { Logx.e("[hook] Activity.onPostResume handler error", it) }
                    }
                }

                firstMethod {
                    name = "onWindowFocusChanged"
                    parameters(Boolean::class)
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching {
                            val activity = instance<Activity>()
                            if (!TicketNfcController.isTicketActivity(activity)) return@runCatching
                            val hasFocus = args.getOrNull(0) as? Boolean ?: return@runCatching
                            TicketNfcController.onTicketWindowFocusChanged(activity, hasFocus)
                        }.onFailure { Logx.e("[hook] Activity.onWindowFocusChanged handler error", it) }
                    }
                }
            }

            // Fallback: force our desired ReaderMode flags/extras whenever host enables ReaderMode
            // for the ticket Activity. This protects against multiple enableReaderMode calls or
            // changes in the host code path.
            "android.nfc.NfcAdapter".toClass().resolve().apply {
                firstMethod {
                    name = "enableReaderMode"
                    parameters(Activity::class, NfcAdapter.ReaderCallback::class, Int::class, Bundle::class)
                    returnType = Void.TYPE
                }.hook {
                    before {
                        runCatching {
                            val activity = args.getOrNull(0) as? Activity ?: return@runCatching
                            if (!TicketNfcController.isTicketActivity(activity)) return@runCatching

                            // Host may enable ReaderMode before our TicketActivity.onResume after-hook runs.
                            // Record it to keep controller state aligned with system logs.
                            TicketNfcController.onHostEnableReaderModeCalled(activity)

                            val (flags, extras) = ReaderModeController.desiredConfig()
                            args[2] = flags
                            args[3] = extras
                        }.onFailure { Logx.e("[hook] enableReaderMode override error", it) }
                    }
                }

                // Host calls disableReaderMode in TicketActivity.onPause. That can drop protection on short focus loss.
                // We intercept it for the ticket activity and delay-disable instead (cancelled on resume/focus gain).
                firstMethod {
                    name = "disableReaderMode"
                    parameters(Activity::class)
                    returnType = Void.TYPE
                }.hook {
                    replaceUnit {
                        val activity = args().first().cast<Activity>()
                        if (activity != null && TicketNfcController.isTicketActivity(activity)) {
                            // Allow module-initiated disables (onStop / delayed runnable) to call original.
                            if (TicketNfcController.shouldBypassDisableReaderModeHook()) {
                                callOriginal()
                                return@replaceUnit
                            }

                            val stack = if (TicketNfcController.isDebugStackTraceEnabled()) {
                                Log.getStackTraceString(Throwable("disableReaderMode stack"))
                            } else {
                                null
                            }
                            if (stack != null) Logx.d("[hook] disableReaderMode requested\n$stack")
                            TicketNfcController.onHostDisableReaderModeRequested(
                                activity = activity,
                                stackTrace = stack,
                                requestedBy = "host_disableReaderMode",
                                origin = "host_disable"
                            )
                            return@replaceUnit
                        }
                        callOriginal()
                    }
                }
            }

            // Critical: Android framework disables ReaderMode on pause via NfcActivityManager.
            // This can happen BEFORE app code calls NfcAdapter.disableReaderMode, so our previous
            // hook could be "too late". Intercept here to make delay-disable actually effective.
            runCatching {
                val mgr = "android.nfc.NfcActivityManager".toClass().resolve()
                Logx.i("[hook] trying to hook android.nfc.NfcActivityManager")
                mgr.apply {
                    val onActivityPaused1 = optional(silent = true).firstMethodOrNull {
                        name = "onActivityPaused"
                        parameters(Activity::class)
                        returnType = Void.TYPE
                    }
                    if (onActivityPaused1 == null) {
                        Logx.w("[hook] NfcActivityManager.onActivityPaused(Activity) not found")
                    } else {
                        Logx.i("[hook] hooked NfcActivityManager.onActivityPaused(Activity)")
                        onActivityPaused1.hook {
                            replaceUnit {
                                val activity = args.getOrNull(0) as? Activity
                                if (activity == null || !TicketNfcController.isTicketActivity(activity)) {
                                    callOriginal()
                                    return@replaceUnit
                                }
                                Logx.d("[hook] intercept onActivityPaused(activity) activity=${activity.javaClass.name}")
                                val stack = if (TicketNfcController.isDebugStackTraceEnabled()) {
                                    Log.getStackTraceString(Throwable("onActivityPaused stack"))
                                } else {
                                    null
                                }
                                TicketNfcController.onHostDisableReaderModeRequested(
                                    activity = activity,
                                    stackTrace = stack,
                                    requestedBy = "framework_onActivityPaused",
                                    origin = "framework_pause"
                                )
                                // Do NOT call original: on this ROM we can't hook setReaderMode(..), so onActivityPaused
                                // is the earliest reliable interception point to prevent immediate ReaderMode shutdown.
                            }
                        }
                    }
                }
            }.onFailure {
                Logx.w("[hook] NfcActivityManager hook unavailable: ${it.javaClass.simpleName}: ${it.message}")
            }

            "com.firstgroup.main.tabs.mtickets.ticket.mvp.TicketActivity".toClass().resolve().apply {
                firstMethod {
                    name = "onResume"
                    emptyParameters()
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching { TicketNfcController.onTicketResume(instance<Activity>()) }
                            .onFailure { Logx.e("[hook] onResume handler error", it) }
                    }
                }

                // Optional: some versions may not override these methods.
                optional(silent = true).firstMethodOrNull {
                    name = "onPostResume"
                    emptyParameters()
                    returnType = Void.TYPE
                }?.hook {
                    after {
                        runCatching { TicketNfcController.onTicketPostResume(instance<Activity>()) }
                            .onFailure { Logx.e("[hook] onPostResume handler error", it) }
                    }
                }

                optional(silent = true).firstMethodOrNull {
                    name = "onWindowFocusChanged"
                    parameters(Boolean::class)
                    returnType = Void.TYPE
                }?.hook {
                    after {
                        runCatching {
                            val hasFocus = args.getOrNull(0) as? Boolean ?: return@runCatching
                            TicketNfcController.onTicketWindowFocusChanged(instance<Activity>(), hasFocus)
                        }.onFailure { Logx.e("[hook] onWindowFocusChanged handler error", it) }
                    }
                }

                firstMethod {
                    name = "onPause"
                    emptyParameters()
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching { TicketNfcController.onTicketPause(instance<Activity>()) }
                            .onFailure { Logx.e("[hook] onPause handler error", it) }
                    }
                }
                firstMethod {
                    name = "onStop"
                    emptyParameters()
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching { TicketNfcController.onTicketStop(instance<Activity>()) }
                            .onFailure { Logx.e("[hook] onStop handler error", it) }
                    }
                }
            }
        }
    }
}
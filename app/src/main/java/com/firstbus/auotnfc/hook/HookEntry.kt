package com.firstbus.auotnfc.hook

import android.app.Activity
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
                firstMethod {
                    name = "onPause"
                    emptyParameters()
                    returnType = Void.TYPE
                }.hook {
                    after {
                        runCatching { TicketNfcController.onTicketStop(instance<Activity>()) }
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
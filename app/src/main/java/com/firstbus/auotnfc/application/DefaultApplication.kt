package com.firstbus.auotnfc.application

import androidx.appcompat.app.AppCompatDelegate
import com.firstbus.auotnfc.hook.Logx
import com.firstbus.auotnfc.hook.ModuleSettingsStore
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class DefaultApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        /**
         * 跟随系统夜间模式
         * Follow system night mode
         */
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Apply persisted debug switch (default: OFF)
        runCatching {
            Logx.setDebugEnabled(ModuleSettingsStore.getDebugLog(this))
        }
    }
}
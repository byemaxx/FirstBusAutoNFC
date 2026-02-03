package com.firstbus.auotnfc.hook

import android.content.Context

internal object ModuleSettingsStore {

    fun getStrategy(context: Context): NfcProtectionStrategy {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        val v = prefs.getString(ModuleStatusProtocol.KEY_STRATEGY, null)
        return NfcProtectionStrategy.fromWireValue(v) ?: NfcProtectionStrategy.READER_MODE
    }

    fun setStrategy(context: Context, strategy: NfcProtectionStrategy) {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ModuleStatusProtocol.KEY_STRATEGY, strategy.wireValue).apply()
    }

    fun setLastRootOk(context: Context, ok: Boolean) {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ModuleStatusProtocol.KEY_LAST_ROOT_OK, ok).apply()
    }

    fun getLastRootOk(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(ModuleStatusProtocol.KEY_LAST_ROOT_OK)) {
            prefs.getBoolean(ModuleStatusProtocol.KEY_LAST_ROOT_OK, false)
        } else {
            null
        }
    }

    fun setDebugStackTrace(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ModuleStatusProtocol.KEY_DEBUG_STACK, enabled).apply()
    }

    fun getDebugStackTrace(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ModuleStatusProtocol.KEY_DEBUG_STACK, false)
    }

    fun setDebugLog(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ModuleStatusProtocol.KEY_DEBUG_LOG, enabled).apply()
    }

    fun getDebugLog(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ModuleStatusProtocol.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ModuleStatusProtocol.KEY_DEBUG_LOG, false)
    }
}

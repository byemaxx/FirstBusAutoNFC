package com.firstbus.auotnfc.hook

import android.util.Log

internal object Logx {

    private const val TAG = "FirstBusAutoNFC"

    @Volatile
    private var debugEnabled: Boolean = false

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    fun d(msg: String) {
        if (!debugEnabled) return
        Log.d(TAG, msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun e(msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
    }
}

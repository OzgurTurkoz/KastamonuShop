package com.kastamonushop.app

import android.app.Application
import android.util.Log

class KastamonuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("kastamonushop_ffi")
        } catch (t: Throwable) {
            Log.e("KastamonuShop", "Failed to load libkastamonushop_ffi.so", t)
            startupError = t.stackTraceToString()
        }
    }

    companion object {
        @Volatile
        var startupError: String? = null
    }
}

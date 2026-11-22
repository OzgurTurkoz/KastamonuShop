package com.kastamonushop.app

import android.app.Application

class KastamonuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("kastamonushop_ffi")
    }
}

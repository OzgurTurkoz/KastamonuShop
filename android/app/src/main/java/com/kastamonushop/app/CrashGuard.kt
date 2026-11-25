package com.kastamonushop.app

import android.content.Context
import android.util.Log
import java.io.File

internal object CrashGuard {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine(throwable.stackTraceToString())
                }
                File(appCtx.filesDir, FILE_NAME).writeText(report.take(12000))
                Log.e("StonebridgeCrash", report)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        return runCatching {
            val f = File(context.filesDir, FILE_NAME)
            if (f.exists()) f.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    fun clearLastCrash(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}

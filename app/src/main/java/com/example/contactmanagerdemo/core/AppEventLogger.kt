package com.example.contactmanagerdemo.core

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppEventLogger {

    private const val LOG_DIR = "ContactManagerLogs"
    private const val TAG_APP = "APP"

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var sessionFileName: String = "session-unknown.txt"

    private val lock = Any()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        sessionFileName = "session-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val logPath = runCatching { resolveLogFile(context).absolutePath }.getOrDefault("unavailable")
        info(TAG_APP, "Logger initialized, file=$logPath")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            error("CRASH", "Uncaught exception in ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun info(event: String, message: String) {
        write("INFO", event, message, null)
    }

    fun warn(event: String, message: String) {
        write("WARN", event, message, null)
    }

    fun error(event: String, message: String, throwable: Throwable? = null) {
        write("ERROR", event, message, throwable)
    }

    private fun write(level: String, event: String, message: String, throwable: Throwable?) {
        val context = appContext ?: return
        synchronized(lock) {
            runCatching {
                val logFile = resolveLogFile(context)
                val line = buildString {
                    append(timestamp())
                    append(" [")
                    append(level)
                    append("] [")
                    append(event)
                    append("] ")
                    append(message)
                    append('\n')
                    if (throwable != null) {
                        append(throwable.javaClass.name)
                        append(": ")
                        append(throwable.message.orEmpty())
                        append('\n')
                        throwable.stackTrace.take(12).forEach { ste ->
                            append("  at ")
                            append(ste.toString())
                            append('\n')
                        }
                    }
                }
                logFile.appendText(line)
            }
        }
    }

    private fun resolveLogFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val dir = File(baseDir, LOG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, sessionFileName)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}

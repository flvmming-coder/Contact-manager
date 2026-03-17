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
    private const val REQUESTED_LOG_ROOT = "Android/data/com.contact.manager"
    private const val PREFS_NAME = "contact_manager_dev_settings"
    private const val KEY_LOGS_ENABLED = "logs_enabled"

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var sessionFileName: String = "session-unknown.txt"
    @Volatile
    private var logsEnabled: Boolean = true
    @Volatile
    private var resolvedLogDirectory: String = "unavailable"

    private val lock = Any()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        logsEnabled = readLoggingEnabled(context.applicationContext)
        sessionFileName = "session-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        resolvedLogDirectory = runCatching { resolveLogDirectory(context.applicationContext).absolutePath }.getOrDefault("unavailable")
        info(TAG_APP, "Logger initialized, enabled=$logsEnabled, dir=$resolvedLogDirectory")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            error("CRASH", "Uncaught exception in ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun isLoggingEnabled(): Boolean = logsEnabled

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        val ctx = appContext ?: context.applicationContext
        synchronized(lock) {
            if (!enabled && logsEnabled) {
                write("INFO", "LOGGER", "Logs disabled by user", null, force = true)
            }
            logsEnabled = enabled
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOGS_ENABLED, enabled)
                .apply()
            if (enabled) {
                write("INFO", "LOGGER", "Logs enabled by user", null, force = true)
            }
        }
    }

    fun getCurrentLogDirectory(context: Context): String {
        val ctx = appContext ?: context.applicationContext
        if (resolvedLogDirectory == "unavailable") {
            resolvedLogDirectory = runCatching { resolveLogDirectory(ctx).absolutePath }.getOrDefault("unavailable")
        }
        return resolvedLogDirectory
    }

    fun listLogFiles(context: Context): List<File> {
        val ctx = appContext ?: context.applicationContext
        val dir = runCatching { resolveLogDirectory(ctx) }.getOrNull() ?: return emptyList()
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
        return files.sortedByDescending { it.lastModified() }
    }

    fun readLogFile(context: Context, fileName: String, maxChars: Int = 60_000): String? {
        val file = listLogFiles(context).firstOrNull { it.name == fileName } ?: return null
        return runCatching {
            val text = file.readText()
            if (text.length > maxChars) {
                text.takeLast(maxChars)
            } else {
                text
            }
        }.getOrNull()
    }

    fun clearAllLogs(context: Context) {
        val files = listLogFiles(context)
        files.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun info(event: String, message: String) {
        write("INFO", event, message, null, force = false)
    }

    fun warn(event: String, message: String) {
        write("WARN", event, message, null, force = false)
    }

    fun error(event: String, message: String, throwable: Throwable? = null) {
        write("ERROR", event, message, throwable, force = false)
    }

    private fun write(level: String, event: String, message: String, throwable: Throwable?, force: Boolean) {
        val context = appContext ?: return
        if (!force && !logsEnabled) return
        synchronized(lock) {
            runCatching {
                val logFile = resolveLogFile(context)
                resolvedLogDirectory = logFile.parentFile?.absolutePath ?: resolvedLogDirectory
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

    private fun readLoggingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGS_ENABLED, true)
    }

    private fun resolveLogFile(context: Context): File {
        val dir = resolveLogDirectory(context)
        return File(dir, sessionFileName)
    }

    private fun resolveLogDirectory(context: Context): File {
        runCatching {
            val externalRoot = Environment.getExternalStorageDirectory()
            val requestedRoot = File(externalRoot, REQUESTED_LOG_ROOT)
            if ((requestedRoot.exists() || requestedRoot.mkdirs()) && requestedRoot.canWrite()) {
                val requestedDir = File(requestedRoot, LOG_DIR)
                if (requestedDir.exists() || requestedDir.mkdirs()) {
                    return requestedDir
                }
            }
        }

        val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val fallbackDir = File(fallbackBase, LOG_DIR)
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}

package com.example.contactmanagerdemo.core

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppEventLogger {

    data class PendingCrashReport(
        val file: File,
        val sessionStartedAtMs: Long,
        val crashedAtMs: Long,
        val deviceName: String,
    )

    private const val LOG_DIR = "ContactManagerLogs"
    private const val LOG_FILE_PREFIX = "rolling-"
    private const val LOG_FILE_SUFFIX = ".txt"
    private const val TAG_APP = "APP"
    private const val REQUESTED_LOG_ROOT = "Android/data/com.contact.manager"
    private const val PREFS_NAME = "contact_manager_dev_settings"
    private const val KEY_LOGS_ENABLED = "logs_enabled"
    private const val KEY_SESSION_START_MS = "log_session_start_ms"
    private const val KEY_PENDING_CRASH_FILE = "pending_crash_file"
    private const val KEY_PENDING_CRASH_START = "pending_crash_start"
    private const val KEY_PENDING_CRASH_END = "pending_crash_end"
    private const val KEY_PENDING_CRASH_DEVICE = "pending_crash_device"
    private const val LOG_WINDOW_MS = 3L * 24L * 60L * 60L * 1000L

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var activeLogFileName: String = "${LOG_FILE_PREFIX}0$LOG_FILE_SUFFIX"
    @Volatile
    private var sessionStartedAtMs: Long = 0L
    @Volatile
    private var logsEnabled: Boolean = true
    @Volatile
    private var resolvedLogDirectory: String = "unavailable"

    private val lock = Any()

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx

        logsEnabled = readLoggingEnabled(ctx)
        sessionStartedAtMs = System.currentTimeMillis()
        activeLogFileName = logFileNameForWindow(sessionStartedAtMs)
        resolvedLogDirectory = runCatching { resolveLogDirectory(ctx).absolutePath }.getOrDefault("unavailable")
        purgeObsoleteLogs(ctx, sessionStartedAtMs)

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SESSION_START_MS, sessionStartedAtMs)
            .apply()

        info(
            TAG_APP,
            "Logger initialized; sessionStart=$sessionStartedAtMs; file=$activeLogFileName; dir=$resolvedLogDirectory; sdk=${Build.VERSION.SDK_INT}; device=${deviceName()}",
        )

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashAt = System.currentTimeMillis()
            error("CRASH", "Uncaught exception in thread=${thread.name}", throwable)
            markPendingCrash(ctx, crashAt)
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

    fun markSessionClosed(totalContacts: Int, importedContacts: Int) {
        info(
            TAG_APP,
            "Session closed; contactsTotal=$totalContacts; contactsImported=$importedContacts; sessionDurationMs=${System.currentTimeMillis() - sessionStartedAtMs}",
        )
    }

    fun consumePendingCrashReport(context: Context): PendingCrashReport? {
        val ctx = appContext ?: context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fileName = prefs.getString(KEY_PENDING_CRASH_FILE, null).orEmpty()
        val startedAt = prefs.getLong(KEY_PENDING_CRASH_START, 0L)
        val crashedAt = prefs.getLong(KEY_PENDING_CRASH_END, 0L)
        val device = prefs.getString(KEY_PENDING_CRASH_DEVICE, deviceName()).orEmpty()
        if (fileName.isBlank() || startedAt <= 0L || crashedAt <= 0L) return null

        prefs.edit()
            .remove(KEY_PENDING_CRASH_FILE)
            .remove(KEY_PENDING_CRASH_START)
            .remove(KEY_PENDING_CRASH_END)
            .remove(KEY_PENDING_CRASH_DEVICE)
            .apply()

        val file = File(resolveLogDirectory(ctx), fileName)
        if (!file.exists()) return null
        return PendingCrashReport(
            file = file,
            sessionStartedAtMs = startedAt,
            crashedAtMs = crashedAt,
            deviceName = device,
        )
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
        purgeObsoleteLogs(ctx, System.currentTimeMillis())
        val dir = runCatching { resolveLogDirectory(ctx) }.getOrNull() ?: return emptyList()
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
        return files.sortedByDescending { it.lastModified() }
    }

    fun readLogFile(context: Context, fileName: String, maxChars: Int = 60_000): String? {
        val file = listLogFiles(context).firstOrNull { it.name == fileName } ?: return null
        return runCatching {
            val text = file.readText()
            if (text.length > maxChars) text.takeLast(maxChars) else text
        }.getOrNull()
    }

    fun clearAllLogs(context: Context) {
        listLogFiles(context).forEach { file -> runCatching { file.delete() } }
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

    private fun markPendingCrash(context: Context, crashAtMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_CRASH_FILE, activeLogFileName)
            .putLong(KEY_PENDING_CRASH_START, sessionStartedAtMs)
            .putLong(KEY_PENDING_CRASH_END, crashAtMs)
            .putString(KEY_PENDING_CRASH_DEVICE, deviceName())
            .apply()
    }

    private fun write(level: String, event: String, message: String, throwable: Throwable?, force: Boolean) {
        val context = appContext ?: return
        if (!force && !logsEnabled) return
        synchronized(lock) {
            runCatching {
                val now = System.currentTimeMillis()
                purgeObsoleteLogs(context, now)
                val logFile = resolveLogFile(context, now)
                resolvedLogDirectory = logFile.parentFile?.absolutePath ?: resolvedLogDirectory
                activeLogFileName = logFile.name

                val sanitized = sanitizeMessage(message)
                val line = buildString {
                    append(timestamp(now))
                    append(" [")
                    append(level)
                    append("] [")
                    append(event)
                    append("] ")
                    append(sanitized)
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

    private fun sanitizeMessage(raw: String): String {
        var value = raw
        value = value.replace(Regex("\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b"), "<email>")
        value = value.replace(Regex("\\+?\\d[\\d\\s()\\-]{5,}\\d"), "<number>")
        return value
    }

    private fun readLoggingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGS_ENABLED, true)
    }

    private fun purgeObsoleteLogs(context: Context, nowMs: Long) {
        val dir = runCatching { resolveLogDirectory(context) }.getOrNull() ?: return
        val minWindowStartToKeep = windowStart(nowMs) - LOG_WINDOW_MS
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .forEach { file ->
                val window = extractWindowStart(file.name)
                val shouldDelete = if (window != null) {
                    window < minWindowStartToKeep
                } else {
                    nowMs - file.lastModified() > LOG_WINDOW_MS * 2
                }
                if (shouldDelete) {
                    runCatching { file.delete() }
                }
            }
    }

    private fun resolveLogFile(context: Context, nowMs: Long): File {
        val dir = resolveLogDirectory(context)
        return File(dir, logFileNameForWindow(nowMs))
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

    private fun windowStart(nowMs: Long): Long = nowMs - (nowMs % LOG_WINDOW_MS)

    private fun logFileNameForWindow(nowMs: Long): String {
        return "$LOG_FILE_PREFIX${windowStart(nowMs)}$LOG_FILE_SUFFIX"
    }

    private fun extractWindowStart(fileName: String): Long? {
        if (!fileName.startsWith(LOG_FILE_PREFIX) || !fileName.endsWith(LOG_FILE_SUFFIX)) return null
        val raw = fileName.removePrefix(LOG_FILE_PREFIX).removeSuffix(LOG_FILE_SUFFIX)
        return raw.toLongOrNull()
    }

    private fun deviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    private fun timestamp(nowMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))
    }
}

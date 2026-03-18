package com.example.contactmanagerdemo.core

import android.content.Context

object DevSecurityManager {

    fun isRestartBypassAuthorized(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_RESTART_BYPASS_AUTHORIZED, false)
    }

    fun setRestartBypassAuthorized(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESTART_BYPASS_AUTHORIZED, enabled).apply()
    }

    fun isBypassCodeControlsVisible(context: Context): Boolean {
        val shared = prefs(context)
        return shared.getBoolean(KEY_RESTART_BYPASS_CONTROLS_VISIBLE, false) &&
            !shared.getBoolean(KEY_RESTART_BYPASS_LOCKED, false)
    }

    fun markAppUpdated(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RESTART_BYPASS_CONTROLS_VISIBLE, true)
            .putBoolean(KEY_RESTART_BYPASS_LOCKED, false)
            .putInt(KEY_FULL_RESET_STREAK, 0)
            .putBoolean(KEY_LAST_ACTION_FULL_RESET, false)
            .apply()
    }

    fun registerFullResetAttempt(context: Context) {
        val shared = prefs(context)
        val wasPreviousReset = shared.getBoolean(KEY_LAST_ACTION_FULL_RESET, false)
        val streak = if (wasPreviousReset) shared.getInt(KEY_FULL_RESET_STREAK, 0) + 1 else 1
        val editor = shared.edit()
            .putBoolean(KEY_RESTART_BYPASS_AUTHORIZED, false)
            .putBoolean(KEY_LAST_ACTION_FULL_RESET, true)

        if (streak >= REQUIRED_FULL_RESETS_FOR_UNLOCK) {
            editor
                .putInt(KEY_FULL_RESET_STREAK, 0)
                .putBoolean(KEY_RESTART_BYPASS_CONTROLS_VISIBLE, true)
                .putBoolean(KEY_RESTART_BYPASS_LOCKED, false)
                .putBoolean(KEY_LAST_ACTION_FULL_RESET, false)
                .apply()
        } else {
            editor
                .putInt(KEY_FULL_RESET_STREAK, streak)
                .apply()
        }
    }

    fun handleInvalidCodeAttempt(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RESTART_BYPASS_AUTHORIZED, false)
            .putBoolean(KEY_RESTART_BYPASS_CONTROLS_VISIBLE, false)
            .putBoolean(KEY_RESTART_BYPASS_LOCKED, true)
            .putInt(KEY_FULL_RESET_STREAK, 0)
            .putBoolean(KEY_LAST_ACTION_FULL_RESET, false)
            .apply()
    }

    fun handleValidCodeEntered(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RESTART_BYPASS_AUTHORIZED, true)
            .putBoolean(KEY_RESTART_BYPASS_CONTROLS_VISIBLE, true)
            .putBoolean(KEY_RESTART_BYPASS_LOCKED, false)
            .putInt(KEY_FULL_RESET_STREAK, 0)
            .putBoolean(KEY_LAST_ACTION_FULL_RESET, false)
            .apply()
    }

    fun resetSessionFlags(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RESTART_BYPASS_AUTHORIZED, false)
            .putBoolean(KEY_SERVICE_GROUP_VISIBLE, false)
            .putBoolean(KEY_LAST_ACTION_FULL_RESET, false)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "contact_manager_dev_settings"
    private const val KEY_SERVICE_GROUP_VISIBLE = "service_group_visible"
    private const val KEY_RESTART_BYPASS_AUTHORIZED = "restart_bypass_authorized"
    private const val KEY_RESTART_BYPASS_LOCKED = "restart_bypass_locked"
    private const val KEY_RESTART_BYPASS_CONTROLS_VISIBLE = "restart_bypass_controls_visible"
    private const val KEY_FULL_RESET_STREAK = "full_reset_streak"
    private const val KEY_LAST_ACTION_FULL_RESET = "last_action_full_reset"
    private const val REQUIRED_FULL_RESETS_FOR_UNLOCK = 2
}

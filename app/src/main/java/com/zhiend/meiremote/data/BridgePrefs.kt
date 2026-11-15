package com.zhiend.meiremote.data

import android.content.Context

/** Persisted connection + appearance settings for the Meibridge client. */
class BridgePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("mei_bridge", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, "192.168.1.25") ?: ""
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 18444)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var defaultCategory: String
        get() = prefs.getString(KEY_CATEGORY, "Personal") ?: "Personal"
        set(value) = prefs.edit().putString(KEY_CATEGORY, value.trim()).apply()

    /** "system" | "light" | "dark" */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Auto-flush the offline outbox whenever the desktop is reachable. */
    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    var lastPingOk: Boolean
        get() = prefs.getBoolean(KEY_LAST_PING, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_PING, value).apply()

    var lastPingAt: Long
        get() = prefs.getLong(KEY_LAST_PING_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PING_AT, value).apply()

    fun isConfigured(): Boolean = host.isNotBlank() && port in 1..65535 && token.isNotBlank()

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_CATEGORY = "default_category"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_BUBBLE = "bubble_enabled"
        private const val KEY_LAST_PING = "last_ping_ok"
        private const val KEY_LAST_PING_AT = "last_ping_at"
    }
}

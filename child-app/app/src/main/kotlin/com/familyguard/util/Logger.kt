package com.familyguard.util

import android.content.Context
import android.util.Log
import java.util.*

object Logger {
    private const val KEY = "log_cache"
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("logger", Context.MODE_PRIVATE)
    }

    fun d(tag: String, msg: String) = log("debug", tag, msg)
    fun i(tag: String, msg: String, extra: Map<String, Any>? = null) = log("info", tag, msg, extra)
    fun w(tag: String, msg: String) = log("warn", tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        log("error", tag, "$msg | ${tr?.message}", mapOf("stack" to Log.getStackTraceString(tr)))
        Log.e(tag, msg, tr)
    }

    private fun log(level: String, tag: String, msg: String, extra: Map<String, Any>? = null) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val entry = mapOf(
            "level" to level, "tag" to tag,
            "message" to (if (extra != null) "$msg | $extra" else msg),
            "timestamp" to fmt.format(Date())
        )
        val json = org.json.JSONObject(entry).toString()
        val p = prefs ?: return
        val list = (p.getString(KEY, "") ?: "").split("\n").toMutableList()
        list.add(json)
        if (list.size > 500) list.removeAt(0)
        p.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    fun getCachedLogs(): List<String> {
        val raw = prefs?.getString(KEY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun clearCache() {
        prefs?.edit()?.remove(KEY)?.apply()
    }
}

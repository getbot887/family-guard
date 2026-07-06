package com.familyguard.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RuleStorage(context: Context) {

    companion object { var appContext: Context? = null; init { appContext = null } }

    private val prefs = context.getSharedPreferences("family_guard", Context.MODE_PRIVATE)

    init { appContext = context }

    fun getRules(): List<BlockRule> = parseRules(prefs.getString("rules", "[]") ?: "[]")

    fun saveRules(rules: List<BlockRule>) {
        prefs.edit().putString("rules", JSONArray().apply {
            rules.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("is_active", r.isActive)
                    put("blocked_apps", JSONArray(r.blockedApps))
                    put("schedules", JSONArray().apply {
                        r.schedules.forEach { s ->
                            put(JSONObject().apply {
                                put("days_of_week", JSONArray(s.daysOfWeek))
                                put("start_time", s.startTime); put("end_time", s.endTime)
                            })
                        }
                    })
                })
            }
        }.toString()).apply()
    }

    fun shouldSync(): Boolean {
        val interval = getSyncInterval() * 1000L
        return System.currentTimeMillis() - prefs.getLong("last_sync", 0) > interval
    }
    fun markSynced() { prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply() }
    fun saveSyncInterval(seconds: Int) { prefs.edit().putInt("sync_interval_seconds", seconds).apply() }
    fun getSyncInterval(): Int = prefs.getInt("sync_interval_seconds", 120)

    fun saveDeviceToken(t: String) { prefs.edit().putString("device_token", t).apply() }
    fun getDeviceToken() = prefs.getString("device_token", "") ?: ""
    fun saveDeviceId(id: String) { prefs.edit().putString("device_id", id).apply() }
    fun getDeviceId() = prefs.getString("device_id", "") ?: ""

    fun saveBlockEvent(pkg: String, name: String) {
        val arr = JSONArray(prefs.getString("events", "[]") ?: "[]")
        arr.put(JSONObject().apply {
            put("package_name", pkg); put("app_name", name)
            put("blocked_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        })
        if (arr.length() > 500) {
            val new = JSONArray(); for (i in arr.length()-500 until arr.length()) new.put(arr.get(i))
            prefs.edit().putString("events", new.toString()).apply()
        } else {
            prefs.edit().putString("events", arr.toString()).apply()
        }
    }

    fun getUnreportedEvents(): List<JSONObject> {
        val arr = JSONArray(prefs.getString("events", "[]") ?: "[]")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun clearEvents() { prefs.edit().remove("events").apply() }

    private fun parseRules(json: String): List<BlockRule> = try {
        JSONArray(json).let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                val apps = mutableListOf<String>()
                r.optJSONArray("blocked_apps")?.let { for (j in 0 until it.length()) apps.add(it.getString(j)) }
                val scheds = mutableListOf<TimeSlot>()
                r.optJSONArray("schedules")?.let { schedArr ->
                    for (j in 0 until schedArr.length()) {
                        val s = schedArr.getJSONObject(j)
                        val days = mutableListOf<Int>()
                        s.optJSONArray("days_of_week")?.let { daysArr ->
                            for (k in 0 until daysArr.length()) days.add(daysArr.getInt(k))
                        }
                        scheds.add(TimeSlot(days, s.getString("start_time"), s.getString("end_time")))
                    }
                }
                BlockRule(r.getInt("id"), r.getString("name"), r.getBoolean("is_active"), apps, scheds)
            }
        }
    } catch (_: Exception) { emptyList() }
}

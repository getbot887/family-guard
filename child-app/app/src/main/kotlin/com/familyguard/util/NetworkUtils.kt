package com.familyguard.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkUtils {
    private const val API_PATH = "/api/v1"
    private const val PREFS_NAME = "family_guard"
    private const val KEY_BASE_URL = "server_base_url"
    private const val DEFAULT_DOMAIN = "http://192.168.2.169:8080"

    var baseUrl: String = DEFAULT_DOMAIN
        private set
    val fullBaseUrl: String get() = "${baseUrl.trimEnd('/')}$API_PATH"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json".toMediaTypeOrNull()

    fun loadSavedUrl(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        baseUrl = (prefs.getString(KEY_BASE_URL, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN).trimEnd('/')
    }

    fun updateBaseUrl(context: android.content.Context, domain: String) {
        baseUrl = domain.trimEnd('/')
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, baseUrl).apply()
    }

    suspend fun registerDevice(deviceId: String, deviceName: String, pairingCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId); put("device_name", deviceName); put("pairing_code", pairingCode)
            }
            val url = "$fullBaseUrl/child/register"
            android.util.Log.d("NetUtils", "POST $url body=$body")
            val resp = client.newCall(Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_MEDIA, body.toString()))
                .build()).execute()
            val bodyStr = resp.body?.string() ?: ""
            android.util.Log.d("NetUtils", "HTTP ${resp.code} body=$bodyStr")
            if (resp.isSuccessful) {
                try {
                    JSONObject(bodyStr).getJSONObject("data").getString("token")
                } catch (e: Exception) {
                    android.util.Log.e("NetUtils", "JSON解析失败: ${e.message}")
                    null
                }
            } else {
                android.util.Log.w("NetUtils", "请求失败: HTTP ${resp.code} $bodyStr")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("NetUtils", "网络异常: ${e.message}", e)
            null
        }
    }

    suspend fun fetchConfig(token: String): List<BlockRule>? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder()
                .url("$fullBaseUrl/child/config")
                .header("X-Device-Token", token)
                .build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val data = JSONObject(resp.body!!.string()).getJSONObject("data")
            val rulesArr = data.getJSONArray("rules")
            val rules = mutableListOf<BlockRule>()
            for (i in 0 until rulesArr.length()) {
                val r = rulesArr.getJSONObject(i)
                val apps = mutableListOf<String>()
                val appsArr = r.optJSONArray("apps")
                appsArr?.let { for (j in 0 until it.length()) apps.add(it.getString(j)) }
                val scheds = mutableListOf<TimeSlot>()
                val schedArr = r.optJSONArray("schedules")
                schedArr?.let { for (j in 0 until it.length()) {
                    val s = it.getJSONObject(j)
                    val days = mutableListOf<Int>()
                    s.optJSONArray("days_of_week")?.let { for (k in 0 until it.length()) days.add(it.getInt(k)) }
                    scheds.add(TimeSlot(days, s.getString("start_time"), s.getString("end_time")))
                }}
                rules.add(BlockRule(r.getInt("id"), r.getString("name"), true, apps, scheds))
            }
            rules
        } catch (_: Exception) { null }
    }

    suspend fun reportEvents(token: String, events: List<JSONObject>) = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder()
                .url("$fullBaseUrl/child/events")
                .post(RequestBody.create(JSON_MEDIA,
                    JSONObject().apply { put("events", JSONArray(events)) }.toString()))
                .header("X-Device-Token", token)
                .build()).execute()
        } catch (_: Exception) {}
    }

    suspend fun sendHeartbeat(token: String) = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder()
                .url("$fullBaseUrl/child/heartbeat")
                .post(RequestBody.create(JSON_MEDIA, "{}"))
                .header("X-Device-Token", token)
                .build()).execute()
        } catch (_: Exception) {}
    }

    // ===== 日志上传 =====

    fun saveToken(context: android.content.Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putString("device_token", token).apply()
    }

    fun getToken(context: android.content.Context): String {
        return context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString("device_token", "") ?: ""
    }

    suspend fun uploadLogs(token: String, logs: List<String>) = withContext(Dispatchers.IO) {
        if (logs.isEmpty()) return@withContext
        try {
            val jsonLogs = JSONArray()
            logs.forEach { logLine ->
                try { jsonLogs.put(JSONObject(logLine)) } catch (_: Exception) {}
            }
            if (jsonLogs.length() == 0) return@withContext
            client.newCall(Request.Builder()
                .url("$fullBaseUrl/child/logs")
                .post(RequestBody.create(JSON_MEDIA,
                    JSONObject().apply {
                        put("source", "child-app")
                        put("logs", jsonLogs)
                    }.toString()))
                .header("X-Device-Token", token)
                .build()).execute()
        } catch (_: Exception) {}
    }
}

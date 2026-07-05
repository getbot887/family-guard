package com.familyguard.collector

import android.content.Context
import com.familyguard.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppCollector {
    fun getInstalledApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0).map {
            it.activityInfo.packageName to (it.activityInfo.loadLabel(pm).toString())
        }.distinctBy { it.first }
    }

    suspend fun uploadApps(context: Context) {
        val token = NetworkUtils.getToken(context)
        if (token.isEmpty()) return
        val apps = getInstalledApps(context)
        if (apps.isEmpty()) return
        withContext(Dispatchers.IO) {
            NetworkUtils.uploadApps(token, apps)
        }
    }

    fun reportChange(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("family_guard", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("needs_full_sync", true).apply()
    }
}

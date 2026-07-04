package com.familyguard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.familyguard.util.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class SyncService : Service() {
    companion object { private const val CHANNEL_ID = "sync_channel"; private const val NOTIFY_ID = 1001 }
    private val handler = CoroutineExceptionHandler { _, e ->
        Log.e("SyncService", "协程异常，30秒后恢复", e)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
    private lateinit var storage: RuleStorage

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        NetworkUtils.loadSavedUrl(this)
        storage = RuleStorage(this)
        createChannel()
        startForeground(NOTIFY_ID, android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FamilyGuard").setContentText("规则同步中")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build())
        Log.d("SyncService", "已启动")

        scope.launch {
            while (isActive) {
                try {
                    syncRules()
                    reportEvents()
                } catch (e: Exception) {
                    Log.e("SyncService", "同步循环异常", e)
                }
                delay(15 * 60 * 1000L)
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private suspend fun syncRules() {
        if (!storage.shouldSync()) return
        try {
            val rules = NetworkUtils.fetchConfig(storage.getDeviceToken())
            if (rules != null) { storage.saveRules(rules); Log.d("SyncService", "规则同步成功") }
        } catch (e: Exception) { Log.e("SyncService", "同步失败", e) }
    }

    private suspend fun reportEvents() {
        val events = storage.getUnreportedEvents()
        if (events.isEmpty()) return
        try {
            NetworkUtils.reportEvents(storage.getDeviceToken(), events)
            Log.d("SyncService", "上报${events.size}条事件")
        } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "规则同步", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }
}

package com.familyguard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.familyguard.collector.AppCollector
import com.familyguard.util.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class SyncService : Service() {
    companion object {
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFY_ID = 1001
        private const val LOG_UPLOAD_INTERVAL = 5 * 1000L // 5秒
        private var syncInterval = 2 * 60 * 1000L // 默认2分钟，可从服务器配置覆盖
    }
    private val handler = CoroutineExceptionHandler { _, e ->
        Log.e("SyncService", "协程异常，30秒后恢复", e)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
    private lateinit var storage: RuleStorage

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        NetworkUtils.loadSavedUrl(this)
        Logger.init(this)
        storage = RuleStorage(this)
        createChannel()
        startForeground(NOTIFY_ID, android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FamilyGuard").setContentText("规则同步中")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build())
        Log.d("SyncService", "已启动")

        // 定时同步规则（立即执行一次，之后按服务器配置的间隔）
        scope.launch {
            while (isActive) {
                try {
                    syncRules()
                    reportEvents()
                    sendHeartbeat()
                } catch (e: Exception) {
                    Log.e("SyncService", "规则同步异常", e)
                }
                delay(syncInterval)
            }
        }

        // 定时上传日志（立即执行一次，之后每5秒）
        scope.launch {
            while (isActive) {
                try {
                    uploadLogs()
                } catch (e: Exception) {
                    Log.e("SyncService", "日志上传异常", e)
                }
                delay(LOG_UPLOAD_INTERVAL)
            }
        }

        // 定时上报已安装App列表（立即执行一次，之后每30分钟）
        scope.launch {
            while (isActive) {
                try {
                    reportApps()
                } catch (e: Exception) {
                    Log.e("SyncService", "App上报异常", e)
                }
                delay(30 * 60 * 1000L)
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private suspend fun syncRules() {
        if (!storage.shouldSync()) return
        try {
            val config = NetworkUtils.fetchConfig(storage.getDeviceToken())
            if (config != null) {
                storage.saveRules(config.rules)
                if (config.logLevel.isNotEmpty()) {
                    Logger.setLevel(config.logLevel)
                    Logger.i("SyncService", "日志等级已更新: ${config.logLevel}")
                }
                Logger.i("SyncService", "规则同步成功")
            }
        } catch (e: Exception) { Logger.e("SyncService", "规则同步失败", e) }
    }

    private suspend fun reportEvents() {
        val events = storage.getUnreportedEvents()
        if (events.isEmpty()) return
        try {
            NetworkUtils.reportEvents(storage.getDeviceToken(), events)
            storage.clearEvents()
            Logger.i("SyncService", "上报${events.size}条事件")
        } catch (e: Exception) { Logger.e("SyncService", "事件上报失败", e) }
    }

    private suspend fun uploadLogs() {
        val token = NetworkUtils.getToken(this)
        if (token.isEmpty()) return
        val logs = Logger.getCachedLogs()
        if (logs.isEmpty()) return
        try {
            NetworkUtils.uploadLogs(token, logs)
            Logger.clearCache()
        } catch (e: Exception) {
            Log.e("SyncService", "日志上传失败", e)
        }
    }

    private suspend fun sendHeartbeat() {
        val token = NetworkUtils.getToken(this)
        if (token.isEmpty()) return
        try {
            NetworkUtils.sendHeartbeat(token)
        } catch (_: Exception) {}
    }

    private suspend fun reportApps() {
        try {
            AppCollector.uploadApps(this)
            Logger.i("SyncService", "已上报App列表")
        } catch (e: Exception) {
            Logger.e("SyncService", "App上报失败", e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "规则同步", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }
}

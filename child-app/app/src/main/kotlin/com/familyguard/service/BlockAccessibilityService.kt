package com.familyguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.familyguard.BlockActivity
import com.familyguard.util.RuleMatcher
import com.familyguard.util.RuleStorage
import com.familyguard.util.Logger
import com.familyguard.util.NetworkUtils

class BlockAccessibilityService : AccessibilityService() {

    companion object { private const val TAG = "BlockService"; var instance: BlockAccessibilityService? = null; var isRunning = false }

    private var lastPkg: String? = null
    private var lastCheck = 0L
    private lateinit var matcher: RuleMatcher
    private lateinit var storage: RuleStorage

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this; isRunning = true
        NetworkUtils.loadSavedUrl(this)
        storage = RuleStorage(this); matcher = RuleMatcher(storage)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        Log.d(TAG, "无障碍服务已连接")
        startService(Intent(this, SyncService::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg == "com.familyguard" || pkg == lastPkg) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 500) return; lastCheck = now
        lastPkg = pkg

        if (matcher.shouldBlock(pkg)) {
            Log.i(TAG, "拦截: $pkg")
            Logger.i(TAG, "拦截应用", mapOf("pkg" to pkg))

            val intent = Intent(this, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("pkg", pkg)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; isRunning = false }
}

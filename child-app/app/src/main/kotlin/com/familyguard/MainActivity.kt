package com.familyguard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.familyguard.receiver.DeviceAdmin
import com.familyguard.service.SyncService
import com.familyguard.util.Logger
import com.familyguard.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var step1Status: TextView
    private lateinit var step2Status: TextView
    private lateinit var step3Status: TextView
    private lateinit var btnGrantNotify: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnOpenAdmin: Button
    private lateinit var editDomain: EditText
    private lateinit var tvStatus: TextView
    private lateinit var editCode: EditText
    private lateinit var btnBind: Button
    private lateinit var tvBindStatus: TextView
    private lateinit var panelStatus: LinearLayout
    private lateinit var tvConnStatus: TextView
    private lateinit var tvConfigTime: TextView
    private lateinit var tvLogLevel: TextView
    private lateinit var tvTodayBlocked: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NetworkUtils.loadSavedUrl(this)
        Logger.init(this)

        step1Status = findViewById(R.id.step1_status)
        btnGrantNotify = findViewById(R.id.btn_grant_notify)
        step2Status = findViewById(R.id.step2_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        step3Status = findViewById(R.id.step3_status)
        btnOpenAdmin = findViewById(R.id.btn_open_admin)
        editDomain = findViewById(R.id.editDomain)
        val btnSave = findViewById<Button>(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
        editCode = findViewById(R.id.editCode)
        btnBind = findViewById(R.id.btnBind)
        tvBindStatus = findViewById(R.id.tvBindStatus)
        panelStatus = findViewById(R.id.panelStatus)
        tvConnStatus = findViewById(R.id.tvConnStatus)
        tvConfigTime = findViewById(R.id.tvConfigTime)
        tvLogLevel = findViewById(R.id.tvLogLevel)
        tvTodayBlocked = findViewById(R.id.tvTodayBlocked)

        editDomain.setText(NetworkUtils.baseUrl)

        refreshStatus()
        refreshDeviceStatus()

        // 如果已有token（之前绑定过），显示状态面板
        if (NetworkUtils.getToken(this).isNotEmpty()) {
            panelStatus.visibility = android.view.View.VISIBLE
        }

        btnGrantNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOpenAdmin.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }

        btnSave.setOnClickListener {
            val domain = editDomain.text.toString().trim()
            if (domain.isEmpty() || !domain.startsWith("http")) {
                Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            NetworkUtils.updateBaseUrl(this, domain)
            tvStatus.text = "已保存: ${NetworkUtils.baseUrl}"
            tvStatus.visibility = android.view.View.VISIBLE
        }

        btnTest.setOnClickListener {
            Logger.i("TestConn", "开始测试连接: ${NetworkUtils.baseUrl}")
            tvStatus.text = "正在测试连接..."
            tvStatus.visibility = android.view.View.VISIBLE
            btnTest.isEnabled = false
            btnTest.text = "测试中..."

            Thread {
                try {
                    val url = "${NetworkUtils.baseUrl}/health"
                    val request = okhttp3.Request.Builder().url(url).get().build()
                    val response = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build().newCall(request).execute()
                    runOnUiThread {
                        tvStatus.text = if (response.isSuccessful) "连接成功" else "连接失败: HTTP ${response.code}"
                        tvStatus.setTextColor(if (response.isSuccessful) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
                        if (response.isSuccessful) refreshDeviceStatus()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "连接失败: ${e.message}"
                        tvStatus.setTextColor(0xFFF44336.toInt())
                    }
                } finally {
                    runOnUiThread { btnTest.isEnabled = true; btnTest.text = "测试连接" }
                }
            }.start()
        }

        btnBind.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "请输入6位配对码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnBind.isEnabled = false
            btnBind.text = "绑定中..."
            tvBindStatus.visibility = android.view.View.VISIBLE
            tvBindStatus.text = "正在绑定..."

            val deviceId = "android_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            CoroutineScope(Dispatchers.IO).launch {
                Logger.i("Bind", "开始绑定，配对码: $code")
                val token = NetworkUtils.registerDevice(deviceId, deviceName, code)
                runOnUiThread {
                    if (token != null) {
                        NetworkUtils.saveToken(this@MainActivity, token)
                        Logger.i("Bind", "绑定成功，deviceId=$deviceId")
                        tvBindStatus.text = "绑定成功！"
                        tvBindStatus.setTextColor(0xFF4CAF50.toInt())
                        Toast.makeText(this@MainActivity, "设备已绑定", Toast.LENGTH_SHORT).show()
                        startService(Intent(this@MainActivity, SyncService::class.java))
                        // 显示状态面板
                        panelStatus.visibility = android.view.View.VISIBLE
                        refreshDeviceStatus()
                    } else {
                        Logger.w("Bind", "绑定失败，配对码: $code")
                        tvBindStatus.text = "绑定失败，请检查配对码"
                        tvBindStatus.setTextColor(0xFFF44336.toInt())
                    }
                    btnBind.isEnabled = true
                    btnBind.text = "绑定设备"
                }
            }
        }

        // 状态面板按钮
        findViewById<Button>(R.id.btnForceSync)?.setOnClickListener {
            Toast.makeText(this, "已触发同步", Toast.LENGTH_SHORT).show()
            startService(Intent(this, SyncService::class.java))
        }
        findViewById<Button>(R.id.btnForceUpload)?.setOnClickListener {
            Toast.makeText(this, "已触发日志上传", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                val token = NetworkUtils.getToken(this@MainActivity)
                if (token.isNotEmpty()) {
                    val logs = com.familyguard.util.Logger.getCachedLogs()
                    if (logs.isNotEmpty()) {
                        NetworkUtils.uploadLogs(token, logs)
                        com.familyguard.util.Logger.clearCache()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btnRefreshStatus)?.setOnClickListener {
            refreshDeviceStatus()
            Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDeviceStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) refreshStatus()
    }

    private fun refreshStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                step1Status.text = "✓ 已授权"; step1Status.setTextColor(0xFF4CAF50.toInt()); btnGrantNotify.isEnabled = false
            } else {
                step1Status.text = "✗ 未授权"; step1Status.setTextColor(0xFFF44336.toInt()); btnGrantNotify.isEnabled = true
            }
        } else {
            step1Status.text = "✓ Android 13 以下无需此权限"; step1Status.setTextColor(0xFF4CAF50.toInt()); btnGrantNotify.isEnabled = false
        }

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
        if (enabled) {
            step2Status.text = "✓ 已开启"; step2Status.setTextColor(0xFF4CAF50.toInt()); btnOpenAccessibility.text = "重新检查"
        } else {
            step2Status.text = "✗ 未开启"; step2Status.setTextColor(0xFFF44336.toInt()); btnOpenAccessibility.text = "去设置中开启"
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdmin::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            step3Status.text = "✓ 已激活"; step3Status.setTextColor(0xFF4CAF50.toInt()); btnOpenAdmin.text = "重新检查"
        } else {
            step3Status.text = "✗ 未激活"; step3Status.setTextColor(0xFFF44336.toInt()); btnOpenAdmin.text = "去设置中激活"
        }
    }

    private fun refreshDeviceStatus() {
        val prefs = getSharedPreferences("family_guard", Context.MODE_PRIVATE)

        // 后端连接状态（最近一次心跳时间）
        val lastHeartbeat = prefs.getLong("last_heartbeat_ok", 0)
        val now = System.currentTimeMillis()
        tvConnStatus.text = if (now - lastHeartbeat < 5 * 60 * 1000L)
            "🟢 后端连接: 已连接" else if (lastHeartbeat > 0)
            "🟡 后端连接: ${(now - lastHeartbeat) / 1000 / 60}分钟前" else "🔴 后端连接: 未连接"

        // 配置更新时间
        val lastSync = prefs.getLong("last_sync", 0)
        tvConfigTime.text = if (lastSync > 0) {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            "📋 配置更新: ${fmt.format(Date(lastSync))}"
        } else "📋 配置更新: 尚未同步"

        // 日志等级
        tvLogLevel.text = "🔧 日志等级: ${prefs.getString("log_level", "debug") ?: "debug"}"

        // 今日拦截次数
        val eventsJson = prefs.getString("events", "[]") ?: "[]"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val count = try {
            val arr = org.json.JSONArray(eventsJson)
            (0 until arr.length()).count { i ->
                val t = arr.getJSONObject(i).optString("blocked_at", "")
                t.startsWith(today)
            }
        } catch (_: Exception) { 0 }
        tvTodayBlocked.text = "📊 今日拦截: $count 次"
    }

    companion object {
        private lateinit var btnTest: Button
    }
}

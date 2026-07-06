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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Setup wizard views
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

    // Dashboard views
    private lateinit var dashConn: TextView
    private lateinit var dashLogLevel: TextView
    private lateinit var dashConfig: TextView
    private lateinit var dashBlocked: TextView
    private lateinit var tvHomeNotify: TextView
    private lateinit var tvHomeAccess: TextView
    private lateinit var tvHomeAdmin: TextView
    private lateinit var btnDashSync: Button
    private lateinit var btnDashUpload: Button
    private lateinit var btnDashSettings: Button
    private lateinit var dashboard: LinearLayout
    private lateinit var setupWizard: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NetworkUtils.loadSavedUrl(this)
        Logger.init(this)

        // 首次启动自动保存默认服务器地址
        val prefs = getSharedPreferences("family_guard", Context.MODE_PRIVATE)
        if (!prefs.contains("server_base_url")) {
            NetworkUtils.updateBaseUrl(this, NetworkUtils.baseUrl)
        }

        // Setup wizard views
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
        setupWizard = findViewById(R.id.setupWizard)

        // Dashboard views
        dashboard = findViewById(R.id.dashboard)
        dashConn = findViewById(R.id.tvDashConn)
        dashLogLevel = findViewById(R.id.tvDashLogLevel)
        dashConfig = findViewById(R.id.tvDashConfig)
        dashBlocked = findViewById(R.id.tvDashBlocked)
        tvHomeNotify = findViewById(R.id.tvHomeNotify)
        tvHomeAccess = findViewById(R.id.tvHomeAccess)
        tvHomeAdmin = findViewById(R.id.tvHomeAdmin)
        btnDashSync = findViewById(R.id.btnDashSync)
        btnDashUpload = findViewById(R.id.btnDashUpload)
        btnDashSettings = findViewById(R.id.btnDashSettings)
        val layoutLoading = findViewById<LinearLayout>(R.id.layoutLoading)
        val tvLoading = findViewById<TextView>(R.id.tvLoadingStatus)

        editDomain.setText(NetworkUtils.baseUrl)

        // 启动时先显示加载界面，检测设备是否已绑定
        layoutLoading.visibility = android.view.View.VISIBLE
        setupWizard.visibility = android.view.View.GONE
        dashboard.visibility = android.view.View.GONE

        autoRegister(layoutLoading, tvLoading)

        // Setup wizard button listeners
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
            if (domain.isEmpty()) {
                NetworkUtils.updateBaseUrl(this, NetworkUtils.baseUrl)
                editDomain.setText(NetworkUtils.baseUrl)
            } else if (!domain.startsWith("http")) {
                Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else {
                NetworkUtils.updateBaseUrl(this, domain)
            }
            tvStatus.text = "已保存: ${NetworkUtils.baseUrl}"
            tvStatus.visibility = android.view.View.VISIBLE
        }

        btnTest.setOnClickListener {
            val inputDomain = editDomain.text.toString().trim()
            val testUrl = if (inputDomain.isNotEmpty() && inputDomain.startsWith("http")) {
                NetworkUtils.updateBaseUrl(this, inputDomain)
                inputDomain
            } else if (inputDomain.isEmpty()) {
                NetworkUtils.updateBaseUrl(this, NetworkUtils.baseUrl)
                editDomain.setText(NetworkUtils.baseUrl)
                NetworkUtils.baseUrl
            } else {
                Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Logger.i("TestConn", "开始测试连接: $testUrl")
            tvStatus.text = "正在测试连接..."
            tvStatus.visibility = android.view.View.VISIBLE
            btnTest.isEnabled = false
            btnTest.text = "测试中..."

            Thread {
                try {
                    val url = "$testUrl/health"
                    val request = okhttp3.Request.Builder().url(url).get().build()
                    val response = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build().newCall(request).execute()
                    runOnUiThread {
                        tvStatus.text = if (response.isSuccessful) "连接成功" else "连接失败: HTTP ${response.code}"
                        tvStatus.setTextColor(if (response.isSuccessful) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
                        if (response.isSuccessful) refreshStatus()
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
                        Logger.i("Bind", "绑定成功")
                        Toast.makeText(this@MainActivity, "绑定成功", Toast.LENGTH_SHORT).show()
                        switchToDashboard()
                        startService(Intent(this@MainActivity, SyncService::class.java))
                    } else {
                        Logger.w("Bind", "绑定失败")
                        tvBindStatus.text = "绑定失败，请检查配对码"
                        tvBindStatus.setTextColor(0xFFF44336.toInt())
                    }
                    btnBind.isEnabled = true
                    btnBind.text = "绑定设备"
                }
            }
        }

        // Dashboard button listeners
        btnDashSync.setOnClickListener {
            Toast.makeText(this, "正在同步...", Toast.LENGTH_SHORT).show()
            startService(Intent(this, SyncService::class.java))
        }
        btnDashUpload.setOnClickListener {
            Toast.makeText(this, "正在上传日志...", Toast.LENGTH_SHORT).show()
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
        btnDashSettings.setOnClickListener {
            // 切换到设置向导重新配置
            dashboard.visibility = android.view.View.GONE
            setupWizard.visibility = android.view.View.VISIBLE
            refreshStatus()
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDashboard()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) refreshStatus()
    }

    private fun switchToDashboard() {
        findViewById<LinearLayout>(R.id.layoutLoading).visibility = android.view.View.GONE
        setupWizard.visibility = android.view.View.GONE
        dashboard.visibility = android.view.View.VISIBLE
        refreshDashboard()
    }

    private fun switchToSetupWizard() {
        findViewById<LinearLayout>(R.id.layoutLoading).visibility = android.view.View.GONE
        setupWizard.visibility = android.view.View.VISIBLE
        dashboard.visibility = android.view.View.GONE
        refreshStatus()
    }

    private fun autoRegister(layoutLoading: LinearLayout, tvLoading: TextView) {
        val deviceId = "android_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
        Logger.i("AutoReg", "检测设备 deviceId=$deviceId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 尝试用空配对码注册（后端如果已绑定会直接返回Token）
                tvLoading.post { tvLoading.text = "正在连接服务器..." }
                val newToken = NetworkUtils.registerDevice(deviceId, "", "")
                if (newToken != null) {
                    NetworkUtils.saveToken(this@MainActivity, newToken)
                    Logger.i("AutoReg", "设备已绑定，自动登录成功")
                    runOnUiThread {
                        switchToDashboard()
                        startService(Intent(this@MainActivity, SyncService::class.java))
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Logger.e("AutoReg", "自动注册异常", e)
            }
            // 未绑定或网络错误 → 显示设置向导
            tvLoading.post { tvLoading.text = "首次使用，请完成设置" }
            // 延迟一下让用户看到提示
            kotlinx.coroutines.delay(800)
            runOnUiThread { switchToSetupWizard() }
        }
    }

    private fun refreshStatus() {
        val isNotifyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        step1Status.text = if (isNotifyGranted) "✓" else "✗"
        step1Status.setTextColor(if (isNotifyGranted) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        btnGrantNotify.visibility = if (isNotifyGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) android.view.View.GONE else android.view.View.VISIBLE

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessEnabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
        step2Status.text = if (accessEnabled) "✓" else "✗"
        step2Status.setTextColor(if (accessEnabled) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdmin::class.java)
        val adminActive = dpm.isAdminActive(adminComponent)
        step3Status.text = if (adminActive) "✓" else "✗"
        step3Status.setTextColor(if (adminActive) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }

    private fun refreshDashboard() {
        val prefs = getSharedPreferences("family_guard", Context.MODE_PRIVATE)

        val lastH = prefs.getLong("last_heartbeat_ok", 0)
        val now = System.currentTimeMillis()
        dashConn.text = if (now - lastH < 5 * 60 * 1000L) "已连接" else "未连接"
        dashConn.setTextColor(if (now - lastH < 5 * 60 * 1000L) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())

        val lastSync = prefs.getLong("last_sync", 0)
        dashConfig.text = if (lastSync > 0) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync)) else "尚未同步"

        dashLogLevel.text = prefs.getString("log_level", "debug") ?: "debug"

        val eventsJson = prefs.getString("events", "[]") ?: "[]"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val count = try {
            val arr = org.json.JSONArray(eventsJson)
            (0 until arr.length()).count { idx -> arr.getJSONObject(idx).optString("blocked_at", "").startsWith(today) }
        } catch (_: Exception) { 0 }
        dashBlocked.text = "$count"

        // 权限状态（仪表盘）
        tvHomeNotify.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) "已关闭" else "已开启"
        tvHomeNotify.setTextColor(if (tvHomeNotify.text == "已开启") 0xFF22C55E.toInt() else 0xFFEF4444.toInt())

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessOk = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
        tvHomeAccess.text = if (accessOk) "已开启" else "已关闭"
        tvHomeAccess.setTextColor(if (accessOk) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminOk = dpm.isAdminActive(ComponentName(this, DeviceAdmin::class.java))
        tvHomeAdmin.text = if (adminOk) "已激活" else "未激活"
        tvHomeAdmin.setTextColor(if (adminOk) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())
    }

    companion object {
        private lateinit var btnTest: Button
    }
}

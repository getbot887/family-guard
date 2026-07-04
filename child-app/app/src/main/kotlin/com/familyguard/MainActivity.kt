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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.familyguard.receiver.DeviceAdmin
import com.familyguard.util.Logger
import com.familyguard.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        editDomain.setText(NetworkUtils.baseUrl)

        refreshStatus()

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
            tvStatus.text = "正在测试连接..."
            tvStatus.visibility = android.view.View.VISIBLE
            btnTest.isEnabled = false
            btnTest.text = "测试中..."

            Thread {
                try {
                    val url = "${NetworkUtils.fullBaseUrl}/health"
                    val request = okhttp3.Request.Builder().url(url).get().build()
                    val response = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build().newCall(request).execute()
                    runOnUiThread {
                        tvStatus.text = if (response.isSuccessful) "连接成功" else "连接失败: HTTP ${response.code}"
                        tvStatus.setTextColor(if (response.isSuccessful) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
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
                val token = NetworkUtils.registerDevice(deviceId, deviceName, code)
                runOnUiThread {
                    if (token != null) {
                        NetworkUtils.saveToken(this@MainActivity, token)
                        tvBindStatus.text = "绑定成功！"
                        tvBindStatus.setTextColor(0xFF4CAF50.toInt())
                        Toast.makeText(this@MainActivity, "设备已绑定", Toast.LENGTH_SHORT).show()
                    } else {
                        tvBindStatus.text = "绑定失败，请检查配对码"
                        tvBindStatus.setTextColor(0xFFF44336.toInt())
                    }
                    btnBind.isEnabled = true
                    btnBind.text = "绑定设备"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) refreshStatus()
    }

    private fun refreshStatus() {
        // Step 1: 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                step1Status.text = "✓ 已授权"
                step1Status.setTextColor(0xFF4CAF50.toInt())
                btnGrantNotify.isEnabled = false
            } else {
                step1Status.text = "✗ 未授权"
                step1Status.setTextColor(0xFFF44336.toInt())
                btnGrantNotify.isEnabled = true
            }
        } else {
            step1Status.text = "✓ Android 13 以下无需此权限"
            step1Status.setTextColor(0xFF4CAF50.toInt())
            btnGrantNotify.isEnabled = false
        }

        // Step 2: 无障碍服务
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
        if (enabled) {
            step2Status.text = "✓ 已开启"
            step2Status.setTextColor(0xFF4CAF50.toInt())
            btnOpenAccessibility.text = "重新检查"
        } else {
            step2Status.text = "✗ 未开启"
            step2Status.setTextColor(0xFFF44336.toInt())
            btnOpenAccessibility.text = "去设置中开启"
        }

        // Step 3: 设备管理员
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdmin::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            step3Status.text = "✓ 已激活"
            step3Status.setTextColor(0xFF4CAF50.toInt())
            btnOpenAdmin.text = "重新检查"
        } else {
            step3Status.text = "✗ 未激活"
            step3Status.setTextColor(0xFFF44336.toInt())
            btnOpenAdmin.text = "去设置中激活"
        }
    }

    companion object {
        private lateinit var btnTest: Button
    }
}

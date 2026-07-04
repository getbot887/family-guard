package com.familyguard

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.util.NetworkUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NetworkUtils.loadSavedUrl(this)

        val editDomain = findViewById<EditText>(R.id.editDomain)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        editDomain.setText(NetworkUtils.baseUrl)

        btnSave.setOnClickListener {
            val domain = editDomain.text.toString().trim()
            if (domain.isEmpty() || !domain.startsWith("http")) {
                Toast.makeText(this, "请输入有效的 URL（以 http/https 开头）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            NetworkUtils.updateBaseUrl(this, domain)
            tvStatus.text = "已保存: ${NetworkUtils.baseUrl}"
            tvStatus.setTextColor(Color.DKGRAY)
            tvStatus.visibility = View.VISIBLE
            Toast.makeText(this, "服务器地址已更新", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            tvStatus.text = "正在测试连接..."
            tvStatus.setTextColor(Color.DKGRAY)
            tvStatus.visibility = View.VISIBLE
            btnTest.isEnabled = false
            btnTest.text = "测试中..."

            Thread {
                try {
                    val url = "${NetworkUtils.fullBaseUrl}/health"
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful && body.contains("ok")) {
                        runOnUiThread {
                            tvStatus.text = "连接成功: ${response.code}"
                            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                        }
                    } else {
                        runOnUiThread {
                            tvStatus.text = "连接失败: HTTP ${response.code}"
                            tvStatus.setTextColor(Color.RED)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    runOnUiThread {
                        tvStatus.text = "连接失败: 请求超时"
                        tvStatus.setTextColor(Color.RED)
                    }
                } catch (e: java.net.UnknownHostException) {
                    runOnUiThread {
                        tvStatus.text = "连接失败: 无法解析主机名"
                        tvStatus.setTextColor(Color.RED)
                    }
                } catch (e: java.net.ConnectException) {
                    runOnUiThread {
                        tvStatus.text = "连接失败: 无法连接到服务器"
                        tvStatus.setTextColor(Color.RED)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "连接失败: ${e.message}"
                        tvStatus.setTextColor(Color.RED)
                    }
                } finally {
                    runOnUiThread {
                        btnTest.isEnabled = true
                        btnTest.text = "测试连接"
                    }
                }
            }.start()
        }
    }
}

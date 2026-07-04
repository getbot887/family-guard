package com.familyguard

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.util.NetworkUtils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NetworkUtils.loadSavedUrl(this)

        val editDomain = findViewById<EditText>(R.id.editDomain)
        val btnSave = findViewById<Button>(R.id.btnSave)
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
            tvStatus.visibility = View.VISIBLE
            Toast.makeText(this, "服务器地址已更新", Toast.LENGTH_SHORT).show()
        }
    }
}

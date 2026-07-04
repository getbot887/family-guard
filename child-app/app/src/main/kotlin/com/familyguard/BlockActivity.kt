package com.familyguard

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(android.R.layout.simple_list_item_2)
        val pkg = intent.getStringExtra("pkg") ?: "未知"
        val name = getAppName(pkg)
        val tv1 = findViewById<android.R.id.text1>(android.R.id.text1)
        val tv2 = findViewById<android.R.id.text2>(android.R.id.text2)
        tv1?.text = "应用已限制"
        tv2?.text = "「$name」当前处于限制使用时段"
    }

    private fun getAppName(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish(); return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

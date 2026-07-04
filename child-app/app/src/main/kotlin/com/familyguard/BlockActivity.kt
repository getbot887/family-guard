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

        val pkg = intent.getStringExtra("pkg") ?: "未知"
        val name = getAppName(pkg)
        val textView = android.widget.TextView(this).apply {
            textSize = 20f
            setPadding(48, 48, 48, 48)
            text = "应用已限制\n「$name」当前处于限制使用时段"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(textView)
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

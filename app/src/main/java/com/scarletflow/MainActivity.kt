package com.scarletflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.scarletflow.databinding.ActivityMainBinding
import com.scarletflow.service.AutoReplyService
import com.scarletflow.service.FloatingButtonService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        loadSettings()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupViews() {
        // 回复内容输入框
        binding.etReplyContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveReplyContent(s?.toString() ?: "")
            }
        })

        // 间隔时间输入
        binding.etInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val seconds = s?.toString()?.toLongOrNull() ?: 30L
                val interval = (if (seconds < 1) 1 else seconds) * 1000L
                saveInterval(interval)
            }
        })

        // 开启无障碍服务按钮
        binding.btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 启动/停止按钮
        binding.btnToggle.setOnClickListener {
            toggleAutoReply()
        }

        // 打开小红书按钮
        binding.btnOpenXhs.setOnClickListener {
            openXiaohongshu()
        }

        // 悬浮窗按钮
        binding.btnFloating.setOnClickListener {
            toggleFloatingButton()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(AutoReplyService.PREFS_NAME, Context.MODE_PRIVATE)

        // 加载回复内容
        val content = prefs.getString(AutoReplyService.KEY_REPLY_CONTENT, "") ?: ""
        binding.etReplyContent.setText(content)

        // 加载间隔时间（毫秒转秒）
        val intervalMs = prefs.getLong(AutoReplyService.KEY_INTERVAL, 30000L)
        val intervalSec = intervalMs / 1000
        binding.etInterval.setText(intervalSec.toString())
    }

    private fun saveReplyContent(content: String) {
        getSharedPreferences(AutoReplyService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AutoReplyService.KEY_REPLY_CONTENT, content)
            .apply()
    }

    private fun saveInterval(interval: Long) {
        getSharedPreferences(AutoReplyService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(AutoReplyService.KEY_INTERVAL, interval)
            .apply()
    }

    private fun updateServiceStatus() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val service = AutoReplyService.instance
        val isRunning = service?.isAutoReplyRunning() == true

        // 更新无障碍服务状态
        if (isAccessibilityEnabled) {
            binding.tvAccessibilityStatus.text = "无障碍服务: 已开启"
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.green))
            binding.btnOpenAccessibility.text = "重新设置"
        } else {
            binding.tvAccessibilityStatus.text = "无障碍服务: 未开启"
            binding.tvAccessibilityStatus.setTextColor(getColor(R.color.red))
            binding.btnOpenAccessibility.text = "开启无障碍服务"
        }

        // 更新自动回复状态
        binding.btnToggle.isEnabled = isAccessibilityEnabled
        if (isRunning) {
            binding.btnToggle.text = "停止自动回复"
            binding.tvStatus.text = "状态: 运行中"
            binding.tvStatus.setTextColor(getColor(R.color.green))
        } else {
            binding.btnToggle.text = "开始自动回复"
            binding.tvStatus.text = "状态: 已停止"
            binding.tvStatus.setTextColor(getColor(R.color.gray))
        }

        // 更新悬浮窗按钮状态
        val isFloatingRunning = FloatingButtonService.isRunning()
        binding.btnFloating.text = if (isFloatingRunning) "关闭悬浮按钮" else "显示悬浮按钮"
    }

    private fun toggleAutoReply() {
        val service = AutoReplyService.instance
        if (service == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        val content = binding.etReplyContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入回复内容", Toast.LENGTH_SHORT).show()
            return
        }

        if (service.isAutoReplyRunning()) {
            service.stopAutoReply()
            Toast.makeText(this, "自动回复已停止", Toast.LENGTH_SHORT).show()
        } else {
            service.startAutoReply()
            Toast.makeText(this, "自动回复已启动", Toast.LENGTH_SHORT).show()
        }

        updateServiceStatus()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 最可靠的检测：服务实例是否存在
        if (AutoReplyService.instance != null) {
            return true
        }

        // 备用检测：检查系统设置
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains("com.scarletflow")
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到并开启 ScarletFlow 服务", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openXiaohongshu() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.xingin.xhs")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "未安装小红书", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开小红书", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFloatingButton() {
        if (FloatingButtonService.isRunning()) {
            // 关闭悬浮窗
            stopService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "悬浮按钮已关闭", Toast.LENGTH_SHORT).show()
        } else {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return
            }

            // 启动悬浮窗服务
            val intent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "悬浮按钮已显示", Toast.LENGTH_SHORT).show()
        }
        updateServiceStatus()
    }
}

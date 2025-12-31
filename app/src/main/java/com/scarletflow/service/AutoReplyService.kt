package com.scarletflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.scarletflow.MainActivity
import com.scarletflow.R
import kotlin.random.Random

class AutoReplyService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoReplyService"
        private const val CHANNEL_ID = "scarletflow_channel"
        private const val NOTIFICATION_ID = 1001

        const val PREFS_NAME = "scarletflow_prefs"
        const val KEY_REPLY_CONTENT = "reply_content"
        const val KEY_INTERVAL = "interval"
        const val KEY_ENABLED = "enabled"
        const val KEY_HUMAN_MODE = "human_mode"

        const val ACTION_START = "com.scarletflow.ACTION_START"
        const val ACTION_STOP = "com.scarletflow.ACTION_STOP"

        var instance: AutoReplyService? = null
            private set
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var replyContents = listOf<String>() // 支持多条内容
    private var intervalMs = 30000L // 默认30秒
    private var humanMode = true // 拟人模式
    private var sendCount = 0

    /**
     * 随机获取一条回复内容
     */
    private fun getRandomReplyContent(): String {
        if (replyContents.isEmpty()) return ""
        if (replyContents.size == 1) return replyContents[0]
        return replyContents[Random.nextInt(replyContents.size)]
    }

    private val sendTask = object : Runnable {
        override fun run() {
            Log.d(TAG, "sendTask.run() called, isRunning=$isRunning")
            if (isRunning) {
                sendCount++
                Log.d(TAG, "Executing send #$sendCount")

                try {
                    performAutoReply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performAutoReply", e)
                }

                // 无论成功失败，继续调度下一次（使用随机间隔）
                val randomInterval = getRandomizedInterval()
                Log.d(TAG, "Scheduling next send #${sendCount+1} in ${randomInterval}ms")

                // 确保 handler 还有效
                if (isRunning && ::handler.isInitialized) {
                    handler.postDelayed(this, randomInterval)
                    Log.d(TAG, "postDelayed succeeded")
                } else {
                    Log.w(TAG, "Handler not available or stopped")
                }
            } else {
                Log.d(TAG, "isRunning is false, stopping")
            }
        }
    }

    /**
     * 生成随机化的间隔时间，模拟人类行为
     * - 基础间隔 ± 30% 随机波动
     * - 偶尔会有更长的"思考"时间（10%概率额外+50%时间）
     * - 最小间隔保证1秒
     */
    private fun getRandomizedInterval(): Long {
        // 基础波动：±30%
        val minFactor = 0.7
        val maxFactor = 1.3
        val baseFactor = minFactor + Random.nextDouble() * (maxFactor - minFactor)

        var randomInterval = (intervalMs * baseFactor).toLong()

        // 10% 概率额外增加"思考时间"，模拟人偶尔分心
        if (Random.nextInt(100) < 10) {
            randomInterval += (intervalMs * 0.5 * Random.nextDouble()).toLong()
        }

        // 最小间隔1秒，防止过快
        return maxOf(randomInterval, 1000L)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 创建专用的后台线程
        handlerThread = HandlerThread("AutoReplyThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        createNotificationChannel()
        loadSettings()
        Log.d(TAG, "Service created, HandlerThread started")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里监听小红书的界面变化
        event?.let {
            if (it.packageName == "com.xingin.xhs") {
                Log.d(TAG, "XHS event: ${it.eventType}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAutoReply()
        // 停止后台线程
        if (::handlerThread.isInitialized) {
            handlerThread.quitSafely()
        }
        Log.d(TAG, "Service destroyed")
    }

    fun startAutoReply() {
        loadSettings()
        if (replyContents.isEmpty()) {
            Log.w(TAG, "Reply contents is empty")
            mainHandler.post {
                Toast.makeText(this, "请先设置回复内容", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 重置计数器
        sendCount = 0
        isRunning = true

        // 确保 handler 已初始化
        if (!::handler.isInitialized) {
            Log.e(TAG, "Handler not initialized!")
            return
        }

        // 开始任务
        handler.post(sendTask)
        updateNotification("自动回复运行中 (${replyContents.size}条)")
        Log.d(TAG, "Auto reply started, interval: ${intervalMs}ms, contents: ${replyContents.size} items")

        mainHandler.post {
            val msg = if (replyContents.size > 1)
                "自动回复已启动 (${replyContents.size}条随机)"
            else
                "自动回复已启动"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAutoReply() {
        isRunning = false
        handler.removeCallbacks(sendTask)
        updateNotification("自动回复已停止")
        Log.d(TAG, "Auto reply stopped")
    }

    fun isAutoReplyRunning(): Boolean = isRunning

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawContent = prefs.getString(KEY_REPLY_CONTENT, "") ?: ""

        // 按行分割，过滤空行
        replyContents = rawContent
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        intervalMs = prefs.getLong(KEY_INTERVAL, 30000L)
        humanMode = prefs.getBoolean(KEY_HUMAN_MODE, true)
        Log.d(TAG, "Loaded ${replyContents.size} reply contents, humanMode=$humanMode")
    }

    /**
     * 根据内容长度计算拟人输入时间
     * 模拟人类打字：每个字 150-300ms
     */
    private fun getHumanTypingDelay(content: String): Long {
        if (!humanMode) return 500L // 非拟人模式固定500ms

        val charCount = content.length
        // 每个字 150-300ms，加上随机波动
        val baseDelayPerChar = 150 + Random.nextInt(150) // 150-300ms
        val typingTime = charCount * baseDelayPerChar

        // 加上思考时间 200-500ms
        val thinkingTime = 200 + Random.nextInt(300)

        return (typingTime + thinkingTime).toLong()
    }

    private fun performAutoReply() {
        Log.d(TAG, "Performing auto reply...")

        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "Root node is null")
            return
        }

        // 随机选择一条内容
        val contentToSend = getRandomReplyContent()
        if (contentToSend.isEmpty()) {
            Log.w(TAG, "No content to send")
            return
        }
        Log.d(TAG, "Selected content: $contentToSend")

        try {
            // 尝试找到输入框并发送消息
            if (findAndClickInputBox(rootNode)) {
                // 计算拟人延迟：根据内容长度模拟打字时间
                val typingDelay = getHumanTypingDelay(contentToSend)
                Log.d(TAG, "Human typing delay: ${typingDelay}ms for ${contentToSend.length} chars")

                mainHandler.postDelayed({
                    try {
                        inputText(contentToSend)
                        // 输入完成后稍等一下再点发送，模拟检查内容
                        val sendDelay = 300L + Random.nextInt(400) // 300-700ms
                        mainHandler.postDelayed({
                            try {
                                clickSendButton()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in clickSendButton", e)
                            }
                        }, sendDelay)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in inputText", e)
                    }
                }, typingDelay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing auto reply", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (e: Exception) {
                // ignore recycle error
            }
        }
    }

    private fun findAndClickInputBox(rootNode: AccessibilityNodeInfo): Boolean {
        // 尝试多种方式找到输入框

        // 方式1: 通过文本查找 "说点什么..." 或类似提示
        val inputHints = listOf("说点什么", "发送弹幕", "说说你的想法", "发条弹幕")
        for (hint in inputHints) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(hint)
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isClickable || node.isEnabled) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked input box with hint: $hint")
                        nodes.forEach { it.recycle() }
                        return true
                    }
                    // 如果节点本身不可点击，尝试点击父节点
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked parent of input box")
                            nodes.forEach { it.recycle() }
                            return true
                        }
                        parent = parent.parent
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // 方式2: 通过类名查找 EditText
        val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
        if (editTexts.isNotEmpty()) {
            val editText = editTexts.first()
            editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked EditText")
            editTexts.forEach { it.recycle() }
            return true
        }

        Log.w(TAG, "Could not find input box")
        return false
    }

    private fun inputText(text: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 找到当前焦点的输入框
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "Text input: $text")
                focusedNode.recycle()
            } else {
                // 备用方案：找到所有EditText并设置文本
                val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
                for (editText in editTexts) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    Log.d(TAG, "Text input via EditText: $text")
                }
                editTexts.forEach { it.recycle() }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun clickSendButton() {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 尝试找到发送按钮
            val sendTexts = listOf("发送", "发布")
            for (text in sendTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked send button: $text")
                        nodes.forEach { it.recycle() }
                        rootNode.recycle()
                        return
                    }
                    // 尝试点击父节点
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked parent of send button")
                            nodes.forEach { it.recycle() }
                            rootNode.recycle()
                            return
                        }
                        parent = parent.parent
                    }
                }
                nodes.forEach { it.recycle() }
            }

            // 备用方案：通过 viewId 查找
            val sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.xingin.xhs:id/send_btn")
            if (sendButtons.isNotEmpty()) {
                sendButtons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked send button by viewId")
                sendButtons.forEach { it.recycle() }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun findNodesByClassName(
        rootNode: AccessibilityNodeInfo,
        className: String
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesByClassNameRecursive(rootNode, className, result)
        return result
    }

    private fun findNodesByClassNameRecursive(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassNameRecursive(child, className, result)
            child.recycle()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScarletFlow 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "小红书直播自动回复服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("服务已启动，等待开始自动回复")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScarletFlow")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

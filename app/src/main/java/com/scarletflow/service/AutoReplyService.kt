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

    // 打错字概率 (5% = 人类正常打字错误率)
    private val typoRate = 0.05

    /**
     * 拼音相似字映射表
     * 模拟人类在拼音输入法上常见的打错情况
     */
    private val pinyinSimilarChars = mapOf(
        // 声母相近 (z/c/s, zh/ch/sh, n/l, f/h, r/l)
        '在' to listOf('才', '再', '载'),
        '是' to listOf('时', '事', '市', '式', '十'),
        '不' to listOf('步', '部', '布', '补'),
        '了' to listOf('乐', '勒', '累'),
        '我' to listOf('握', '窝', '卧'),
        '你' to listOf('泥', '逆', '腻', '拟'),
        '的' to listOf('地', '得', '底', '弟'),
        '他' to listOf('她', '它', '塔', '踏'),
        '好' to listOf('号', '浩', '耗', '毫'),
        '这' to listOf('着', '者', '浙', '遮'),
        '有' to listOf('又', '友', '右', '优', '由'),
        '大' to listOf('达', '打', '答'),
        '人' to listOf('仁', '忍', '认', '任'),
        '来' to listOf('赖', '莱', '睐', '籁'),
        '吗' to listOf('妈', '马', '码', '嘛', '骂'),
        '什' to listOf('深', '神', '身', '审'),
        '么' to listOf('没', '们', '闷', '蒙'),
        '哈' to listOf('哈', '蛤', '铪'),
        '啊' to listOf('阿', '呵', '吖'),
        '哦' to listOf('噢', '喔', '欧'),
        '嗯' to listOf('恩', '嗯', '摁'),
        '呢' to listOf('那', '呐', '纳'),
        '吧' to listOf('把', '爸', '八', '巴'),
        '呀' to listOf('雅', '鸦', '压', '押'),
        '嘿' to listOf('黑', '嗨', '咳'),
        '喜' to listOf('洗', '西', '习', '细', '系'),
        '欢' to listOf('换', '环', '还', '幻', '唤'),
        '爱' to listOf('唉', '矮', '艾', '碍'),
        '想' to listOf('响', '向', '像', '象', '项'),
        '看' to listOf('砍', '侃', '刊', '坎'),
        '说' to listOf('所', '锁', '索', '缩'),
        '听' to listOf('停', '廷', '挺', '艇'),
        '真' to listOf('珍', '针', '侦', '斟', '震'),
        '太' to listOf('台', '态', '泰', '汰'),
        '棒' to listOf('帮', '邦', '绑', '傍', '榜'),
        '厉' to listOf('力', '历', '立', '丽', '利'),
        '害' to listOf('还', '海', '孩', '咳'),
        '哇' to listOf('挖', '娃', '瓦', '袜'),
        '牛' to listOf('扭', '纽', '钮', '妞'),
        '赞' to listOf('暂', '攒', '咱', '簪'),
        '支' to listOf('只', '知', '之', '直', '值', '枝'),
        '持' to listOf('迟', '池', '驰', '尺', '齿'),
        '加' to listOf('家', '夹', '假', '价', '嘉'),
        '油' to listOf('有', '又', '游', '由', '优', '犹'),
        '冲' to listOf('充', '虫', '宠', '崇'),
        '鸭' to listOf('呀', '压', '押', '雅'),
        '起' to listOf('气', '七', '期', '其', '奇', '启'),
        '开' to listOf('凯', '慨', '楷', '揩'),
        '心' to listOf('新', '欣', '辛', '信', '芯', '薪'),
        '快' to listOf('块', '筷', '侩', '脍'),
        '乐' to listOf('了', '勒', '雷', '累'),
        '可' to listOf('克', '刻', '客', '渴', '科'),
        '以' to listOf('已', '亿', '易', '艺', '译'),
        '很' to listOf('恨', '狠', '痕'),
        '非' to listOf('飞', '肥', '妃', '菲', '费'),
        '常' to listOf('场', '长', '唱', '畅', '尝'),
        '感' to listOf('干', '赶', '敢', '甘', '杆'),
        '谢' to listOf('写', '泻', '卸', '械', '解'),
        '帅' to listOf('衰', '率', '甩', '摔'),
        '美' to listOf('每', '妹', '梅', '眉', '没'),
        '漂' to listOf('飘', '票', '瓢', '嫖'),
        '亮' to listOf('量', '凉', '两', '辆', '良'),
        '酷' to listOf('哭', '苦', '库', '裤', '酷'),
        '帮' to listOf('棒', '邦', '绑', '傍', '榜'),
        '多' to listOf('朵', '夺', '躲', '堕'),
        '少' to listOf('烧', '稍', '捎', '梢'),
        '点' to listOf('电', '店', '典', '颠', '掂'),
        '一' to listOf('衣', '医', '依', '已', '以'),
        '二' to listOf('尔', '耳', '儿', '饵'),
        '三' to listOf('山', '散', '伞', '叁'),
        '四' to listOf('死', '丝', '思', '斯', '私'),
        '五' to listOf('午', '武', '舞', '务', '物'),
        '六' to listOf('流', '留', '刘', '柳', '陆'),
        '七' to listOf('起', '期', '其', '奇', '棋'),
        '八' to listOf('吧', '把', '爸', '巴', '拔'),
        '九' to listOf('就', '久', '酒', '旧', '救'),
        '十' to listOf('时', '是', '事', '市', '石')
    )

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
                    // 注意：下一次调度在 performAutoReply 内部完成（发送成功后）
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performAutoReply", e)
                    // 出错时也要调度下一次，否则会停止
                    scheduleNextSend()
                }
            } else {
                Log.d(TAG, "isRunning is false, stopping")
            }
        }
    }

    /**
     * 调度下一次发送
     * 在当前消息发送完成后调用
     */
    private fun scheduleNextSend() {
        if (!isRunning || !::handler.isInitialized) {
            Log.w(TAG, "Cannot schedule next send: isRunning=$isRunning")
            return
        }

        val randomInterval = getRandomizedInterval()
        Log.d(TAG, "Scheduling next send #${sendCount + 1} in ${randomInterval}ms (after send complete)")
        handler.postDelayed(sendTask, randomInterval)
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
            scheduleNextSend() // 也要调度下一次
            return
        }

        // 随机选择一条内容
        val contentToSend = getRandomReplyContent()
        if (contentToSend.isEmpty()) {
            Log.w(TAG, "No content to send")
            scheduleNextSend() // 也要调度下一次
            return
        }
        Log.d(TAG, "Selected content: $contentToSend")

        try {
            // 尝试找到输入框并发送消息
            if (findAndClickInputBox(rootNode)) {
                Log.d(TAG, "Starting char-by-char input for ${contentToSend.length} chars")

                // 点击输入框后，短暂延迟再开始逐字输入
                val focusDelay = 200L + Random.nextInt(200) // 200-400ms 等待输入框聚焦
                mainHandler.postDelayed({
                    try {
                        // 使用逐字输入，完成后再点击发送
                        inputText(contentToSend) {
                            // 输入完成后的回调，稍等一下再点发送（模拟检查内容）
                            val sendDelay = 300L + Random.nextInt(400) // 300-700ms
                            mainHandler.postDelayed({
                                try {
                                    clickSendButton()
                                    Log.d(TAG, "Send complete, scheduling next...")
                                    // 发送完成后，调度下一次（间隔从这里开始算）
                                    scheduleNextSend()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in clickSendButton", e)
                                    scheduleNextSend() // 出错也要继续
                                }
                            }, sendDelay)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in inputText", e)
                        scheduleNextSend() // 出错也要继续
                    }
                }, focusDelay)
            } else {
                // 找不到输入框，也要调度下一次
                Log.w(TAG, "Input box not found, scheduling next send anyway")
                scheduleNextSend()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing auto reply", e)
            scheduleNextSend() // 出错也要继续
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

    /**
     * 原始的一次性输入方法（保留作为备用）
     */
    private fun inputTextDirect(text: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "Text input direct: $text")
                focusedNode.recycle()
            } else {
                val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
                for (editText in editTexts) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
                editTexts.forEach { it.recycle() }
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 获取拼音相似的错误字符
     * 如果字符在映射表中，随机返回一个相似字；否则返回原字符
     */
    private fun getTypoChar(char: Char): Char {
        val similarChars = pinyinSimilarChars[char]
        return if (similarChars != null && similarChars.isNotEmpty()) {
            similarChars[Random.nextInt(similarChars.size)]
        } else {
            char
        }
    }

    /**
     * 逐字输入文本（带打错字和修正效果）
     * @param text 要输入的完整文本
     * @param onComplete 输入完成后的回调
     */
    private fun inputTextCharByChar(text: String, onComplete: () -> Unit) {
        if (!humanMode) {
            // 非拟人模式，直接一次性输入
            inputTextDirect(text)
            onComplete()
            return
        }

        val chars = text.toCharArray()
        var currentIndex = 0
        var currentText = StringBuilder()
        var pendingTypoFix = false  // 是否有待修正的错字
        var correctChar: Char = ' ' // 正确的字符

        fun scheduleNextChar() {
            if (pendingTypoFix) {
                // 需要修正错字：先删除错字，再输入正确的
                val deleteDelay = 100L + Random.nextInt(150) // 100-250ms 发现错误
                mainHandler.postDelayed({
                    // 删除最后一个字符（模拟退格）
                    currentText.deleteCharAt(currentText.length - 1)
                    setTextToInputField(currentText.toString())
                    Log.d(TAG, "Deleted typo, current: $currentText")

                    // 再输入正确的字符
                    val retypeDelay = 80L + Random.nextInt(120) // 80-200ms 重新输入
                    mainHandler.postDelayed({
                        currentText.append(correctChar)
                        setTextToInputField(currentText.toString())
                        Log.d(TAG, "Fixed typo, current: $currentText")
                        pendingTypoFix = false

                        // 继续下一个字符
                        val nextDelay = getCharInputDelay()
                        mainHandler.postDelayed({ scheduleNextChar() }, nextDelay)
                    }, retypeDelay)
                }, deleteDelay)
                return
            }

            if (currentIndex >= chars.size) {
                // 输入完成
                Log.d(TAG, "Character-by-character input completed: $currentText")
                onComplete()
                return
            }

            val char = chars[currentIndex]
            currentIndex++

            // 判断是否打错字 (typoRate 概率)
            val shouldTypo = humanMode && Random.nextDouble() < typoRate && pinyinSimilarChars.containsKey(char)

            if (shouldTypo) {
                // 打错字
                val typoChar = getTypoChar(char)
                currentText.append(typoChar)
                setTextToInputField(currentText.toString())
                Log.d(TAG, "Typed wrong: $typoChar (should be $char)")

                // 标记需要修正
                pendingTypoFix = true
                correctChar = char

                // 等待一会儿后修正（模拟发现错误的时间）
                val noticeDelay = 200L + Random.nextInt(400) // 200-600ms 发现错误
                mainHandler.postDelayed({ scheduleNextChar() }, noticeDelay)
            } else {
                // 正常输入
                currentText.append(char)
                setTextToInputField(currentText.toString())
                Log.d(TAG, "Typed: $char, current: $currentText")

                // 计算下一个字符的输入延迟
                val delay = getCharInputDelay()
                mainHandler.postDelayed({ scheduleNextChar() }, delay)
            }
        }

        // 开始逐字输入
        scheduleNextChar()
    }

    /**
     * 获取单个字符的输入延迟（模拟人类打字速度的变化）
     */
    private fun getCharInputDelay(): Long {
        // 基础打字速度：80-200ms 每字符
        val baseDelay = 80L + Random.nextInt(120)

        // 5% 概率有小停顿（思考下一个字）
        val thinkingPause = if (Random.nextInt(100) < 5) {
            200L + Random.nextInt(300) // 200-500ms 思考
        } else {
            0L
        }

        return baseDelay + thinkingPause
    }

    /**
     * 将文本设置到输入框
     */
    private fun setTextToInputField(text: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusedNode.recycle()
            } else {
                val editTexts = findNodesByClassName(rootNode, "android.widget.EditText")
                if (editTexts.isNotEmpty()) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    editTexts.first().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
                editTexts.forEach { it.recycle() }
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 主要的文本输入方法（现在使用逐字输入）
     */
    private fun inputText(text: String, onComplete: () -> Unit = {}) {
        inputTextCharByChar(text, onComplete)
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

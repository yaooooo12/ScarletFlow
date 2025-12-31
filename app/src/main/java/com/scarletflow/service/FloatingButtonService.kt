package com.scarletflow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.scarletflow.MainActivity
import com.scarletflow.R

class FloatingButtonService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_channel"
        private const val NOTIFICATION_ID = 1002

        var instance: FloatingButtonService? = null
            private set

        fun isRunning() = instance != null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var btnToggle: View
    private lateinit var tvStatus: TextView
    private lateinit var btnClose: ImageView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupFloatingView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        btnToggle = floatingView.findViewById(R.id.btnFloatingToggle)
        tvStatus = floatingView.findViewById(R.id.tvFloatingStatus)
        btnClose = floatingView.findViewById(R.id.btnFloatingClose)

        updateStatus()

        // 点击切换自动回复
        btnToggle.setOnClickListener {
            toggleAutoReply()
        }

        // 关闭悬浮窗
        btnClose.setOnClickListener {
            stopSelf()
        }

        // 拖动悬浮窗
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    private fun toggleAutoReply() {
        val service = AutoReplyService.instance
        if (service == null) {
            tvStatus.text = "请先开启无障碍"
            return
        }

        if (service.isAutoReplyRunning()) {
            service.stopAutoReply()
        } else {
            service.startAutoReply()
        }
        updateStatus()
    }

    fun updateStatus() {
        if (!::tvStatus.isInitialized) return

        val service = AutoReplyService.instance
        val isRunning = service?.isAutoReplyRunning() == true

        if (isRunning) {
            tvStatus.text = "运行中"
            tvStatus.setTextColor(getColor(R.color.green))
            btnToggle.setBackgroundResource(R.drawable.floating_btn_running)
        } else {
            tvStatus.text = "已停止"
            tvStatus.setTextColor(getColor(R.color.gray))
            btnToggle.setBackgroundResource(R.drawable.floating_btn_stopped)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScarletFlow 悬浮窗")
            .setContentText("悬浮控制按钮运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

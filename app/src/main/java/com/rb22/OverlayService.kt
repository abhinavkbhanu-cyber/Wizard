package com.rb22
import android.app.*
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var edgeView: View? = null
    private var tabView: TextView? = null
    private var panelView: View? = null
    private var downX = 0f
    private var startTime = 0L

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("rb22", "RB22 Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val notification = Notification.Builder(this, "rb22")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22")
                .setContentText("Right-edge gaming overlay active")
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(22, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(22, notification)
        }
        showEdge()
    }

    private fun lp(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width, height,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        flags, PixelFormat.TRANSLUCENT
    )

    private fun showEdge() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow RB22 display over other apps first.", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        edgeView = View(this).apply {
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; startTime = System.currentTimeMillis(); true }
                    MotionEvent.ACTION_UP -> {
                        if (downX - e.rawX > 80 && System.currentTimeMillis() - startTime < 1000) showTab()
                        true
                    }
                    else -> true
                }
            }
        }
        val p = lp(dp(18), WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        p.gravity = Gravity.RIGHT
        wm.addView(edgeView, p)
    }

    private fun showTab() {
        edgeView?.let { runCatching { wm.removeView(it) } }
        val tab = TextView(this).apply {
            text = "RB22"; textSize = 14f; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(35,45,70))
            setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { showPanel() }
        }
        tabView = tab
        val p = lp(dp(70), dp(48), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        wm.addView(tab, p)
    }

    private fun showPanel() {
        tabView?.let { runCatching { wm.removeView(it) } }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14)); setBackgroundColor(Color.rgb(18,27,45))
            addView(text("🤖 RB22  ● ACTIVE", 17))
            addView(text("⚡ EXTREME FPS", 15))
            addView(text("FPS   --    PING --", 14))
            addView(text("RAM   --    🌡️ --", 14))
            setOnClickListener { hidePanel() }
        }
        panelView = box
        val p = lp(dp(230), dp(180), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        wm.addView(box, p)
    }

    private fun hidePanel() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        showEdge()
    }
    private fun text(s: String, size: Int) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(Color.WHITE); setPadding(0, dp(3), 0, dp(3))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        listOf(panelView, tabView, edgeView).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

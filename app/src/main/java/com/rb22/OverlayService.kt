package com.rb22

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
    private var tabView: View? = null
    private var panelView: View? = null
    private var downX=0f
    private var downY=0f
    private var panelX=0
    private var panelY=0
    private var startTime=0L

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel=NotificationChannel("rb22","RB22 Overlay",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val n=Notification.Builder(this,"rb22").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22 Performance Overlay").setContentText("Premium gaming overlay active").build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(22,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(22,n)
        }
        showEdge()
    }

    private fun lp(w:Int,h:Int,flags:Int)=WindowManager.LayoutParams(w,h,
        if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        flags,PixelFormat.TRANSLUCENT)

    private fun glass()=GradientDrawable().apply {
        setColor(Color.argb(178,8,13,28)); cornerRadius=dp(22).toFloat()
        setStroke(dp(1),Color.argb(115,120,210,255))
    }

    private fun pill(active:Boolean)=GradientDrawable().apply {
        setColor(if(active) Color.argb(205,20,95,100) else Color.argb(145,30,39,62))
        cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.argb(80,150,220,255))
    }

    private fun showEdge() {
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow RB22 display over other apps first.",Toast.LENGTH_LONG).show();stopSelf();return}
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        edgeView=View(this).apply {
            setBackgroundColor(Color.argb(18,110,230,255))
            setOnTouchListener { _,e ->
                when(e.action){
                    MotionEvent.ACTION_DOWN->{downX=e.rawX;startTime=System.currentTimeMillis();true}
                    MotionEvent.ACTION_UP->{if(downX-e.rawX>dp(70)&&System.currentTimeMillis()-startTime<1000)showTab();true}
                    else->true
                }
            }
        }
        val p=lp(dp(16),WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        p.gravity=Gravity.RIGHT;wm.addView(edgeView,p)
    }

    private fun showTab(){
        edgeView?.let{runCatching{wm.removeView(it)}}
        tabView=TextView(this).apply{
            text="RB22  ›";textSize=13f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            background=glass();setPadding(dp(10),0,dp(8),0);setOnClickListener{showPanel()}
        }
        val p=lp(dp(76),dp(52),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;wm.addView(tabView,p)
    }

    private fun showPanel(){
        tabView?.let{runCatching{wm.removeView(it)}}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=glass()}
        val header=LinearLayout(this).apply{
            gravity=Gravity.CENTER_VERTICAL
            addView(text("RB22",19,true))
            addView(text("  •  PERFORMANCE HUD",11,false).apply{setTextColor(Color.rgb(155,205,230));layoutParams=LinearLayout.LayoutParams(0,dp(30),1f)})
            addView(text("×",22,true).apply{setOnClickListener{hidePanel()}})
        }
        box.addView(header)
        box.addView(text("●  GAME SESSION  •  ACTIVE",11,true).apply{setTextColor(Color.rgb(115,235,205));setPadding(0,dp(7),0,dp(5))})
        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;addView(stat("FPS","--"));addView(stat("PING","--"));addView(stat("RAM","--"));addView(stat("TEMP","--"))}
        box.addView(stats)
        box.addView(text("BOOST PROFILE",10,true).apply{setTextColor(Color.rgb(155,175,215));setPadding(0,dp(10),0,dp(5))})
        val boosts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        boosts.addView(action("BASIC",false){notifyBoost("Basic")});boosts.addView(action("ADVANCED",true){notifyBoost("Advanced")});boosts.addView(action("TURBO",true){notifyBoost("Turbo")})
        box.addView(boosts)
        box.addView(text("Drag panel to move  •  × to close",10,false).apply{setTextColor(Color.rgb(145,155,185));gravity=Gravity.CENTER;setPadding(0,dp(9),0,0)})
        box.setOnTouchListener{_,e->
            when(e.action){
                MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;panelX=currentX();panelY=currentY();true}
                MotionEvent.ACTION_MOVE->{val p=panelView?.layoutParams as? WindowManager.LayoutParams;if(p!=null){p.x=panelX+(e.rawX-downX).toInt();p.y=panelY+(e.rawY-downY).toInt();runCatching{wm.updateViewLayout(panelView,p)}};true}
                else->true
            }
        }
        panelView=box
        val p=lp(dp(330),dp(250),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        p.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;p.x=dp(10);wm.addView(box,p)
    }

    private fun currentX()=(panelView?.layoutParams as? WindowManager.LayoutParams)?.x ?: dp(10)
    private fun currentY()=(panelView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0

    private fun stat(label:String,value:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=pill(false);setPadding(dp(5),dp(5),dp(5),dp(5))
        addView(text(value,16,true).apply{gravity=Gravity.CENTER});addView(text(label,9,true).apply{gravity=Gravity.CENTER;setTextColor(Color.rgb(155,175,210))})
        layoutParams=LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(2),0,dp(2),0)}
    }

    private fun action(label:String,active:Boolean,onClick:()->Unit)=TextView(this).apply{
        text=label;textSize=9.5f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=pill(active);setPadding(dp(4),0,dp(4),0)
        setOnClickListener{onClick()};layoutParams=LinearLayout.LayoutParams(0,dp(40),1f).apply{setMargins(dp(2),0,dp(2),0)}
    }

    private fun notifyBoost(name:String){
        sendBroadcast(Intent("com.rb22.BOOST_PROFILE").setPackage(packageName).putExtra("profile",name))
        Toast.makeText(this,"$name Boost applied",Toast.LENGTH_SHORT).show()
    }

    private fun hidePanel(){panelView?.let{runCatching{wm.removeView(it)}};panelView=null;showEdge()}
    private fun text(s:String,size:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(Color.WHITE);if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){listOf(panelView,tabView,edgeView).forEach{v->v?.let{runCatching{wm.removeView(it)}}};super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
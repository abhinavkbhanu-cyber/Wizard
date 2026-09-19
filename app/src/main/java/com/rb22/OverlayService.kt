package com.rb22

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

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
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var statViews = mutableMapOf<String, TextView>()
    private var fpsMonitor: ScreenFpsMonitor? = null
    private var projectionResultCode: Int? = null
    private var projectionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        startTime = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel=NotificationChannel("rb22","RB22 Overlay",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val n=Notification.Builder(this,"rb22").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22 Performance Overlay").setContentText("Premium gaming HUD active").build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(22,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(22,n)
        }
        showEdge()
    }

    private fun lp(w:Int,h:Int,flags:Int)=WindowManager.LayoutParams(w,h,
        if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        flags,PixelFormat.TRANSLUCENT)

    private fun glass()=GradientDrawable().apply {
        setColor(Color.argb(235,7,12,27)); cornerRadius=dp(22).toFloat()
        setStroke(dp(1),Color.argb(190,105,225,255))
    }

    private fun pill(active:Boolean)=GradientDrawable().apply {
        setColor(if(active) Color.argb(225,17,91,99) else Color.argb(220,27,36,58))
        cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.argb(145,130,220,255))
    }

    private fun showEdge() {
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow RB22 display over other apps first.",Toast.LENGTH_LONG).show();stopSelf();return}
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        edgeView=View(this).apply {
            setBackgroundColor(Color.argb(30,90,230,255))
            setOnTouchListener { _,e ->
                when(e.action){
                    MotionEvent.ACTION_DOWN->{downX=e.rawX;startTime=System.currentTimeMillis();true}
                    MotionEvent.ACTION_UP->{if(downX-e.rawX>dp(70)&&System.currentTimeMillis()-startTime<1000)showTab();true}
                    else->true
                }
            }
        }
        val p=lp(dp(18),WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        p.gravity=Gravity.RIGHT;wm.addView(edgeView,p)
    }

    private fun showTab(){
        edgeView?.let{runCatching{wm.removeView(it)}}
        tabView=TextView(this).apply{
            text="RB22  ›";textSize=13f;setTextColor(Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER
            background=glass();setPadding(dp(10),0,dp(8),0);setOnClickListener{showPanel()}
        }
        val p=lp(dp(82),dp(54),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;wm.addView(tabView,p)
    }

    private fun showPanel(){
        tabView?.let{runCatching{wm.removeView(it)}}
        statViews.clear()
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(14),dp(15),dp(13));background=glass()}
        val header=LinearLayout(this).apply{
            gravity=Gravity.CENTER_VERTICAL
            addView(text("RB22",20,true))
            addView(text("  •  PERFORMANCE HUD",11,true).apply{setTextColor(Color.rgb(135,235,220));layoutParams=LinearLayout.LayoutParams(0,dp(30),1f)})
            addView(text("×",23,true).apply{setOnClickListener{hidePanel()}})
        }
        box.addView(header)
        box.addView(text("●  GAME SESSION  •  ACTIVE",11,true).apply{setTextColor(Color.rgb(100,255,205));setPadding(0,dp(6),0,dp(6))})

        val stats=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val row1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        row1.addView(stat("RAM","--","ram"));row1.addView(stat("TEMP","--","temp"));row1.addView(stat("BATTERY","--","battery"))
        val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        row2.addView(stat("NET","--","net"));row2.addView(stat("SESSION","--","session"));row2.addView(stat("FPS","--","fps"))
        stats.addView(row1);stats.addView(row2);box.addView(stats)

        box.addView(text("QUICK CONTROL",10,true).apply{setTextColor(Color.rgb(175,200,235));setPadding(0,dp(10),0,dp(5))})
        val boosts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        boosts.addView(action("BASIC",false){notifyBoost("Basic")})
        boosts.addView(action("ADVANCED",true){notifyBoost("Advanced")})
        boosts.addView(action("TURBO",true){notifyBoost("Turbo")})
        box.addView(boosts)

        val tools=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        tools.addView(action("SMART",false){notifyBoost("Smart")})
        tools.addView(action("FOCUS",false){notifyBoost("Focus")})
        box.addView(tools)

        box.addView(text("LIVE MONITOR  •  FPS requires game/system support",9,false).apply{
            setTextColor(Color.rgb(195,205,225));gravity=Gravity.CENTER;setPadding(0,dp(8),0,0)
        })
        box.setOnTouchListener{_,e->
            when(e.action){
                MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;panelX=currentX();panelY=currentY();true}
                MotionEvent.ACTION_MOVE->{
                    val p=panelView?.layoutParams as? WindowManager.LayoutParams
                    if(p!=null){
                        // Panel uses RIGHT gravity, so positive x moves it LEFT.
                        // Invert the horizontal finger delta so swipe-left moves left.
                        val dx=(e.rawX-downX).toInt()
                        val dy=(e.rawY-downY).toInt()
                        val maxX=resources.displayMetrics.widthPixels-dp(24)
                        val maxY=resources.displayMetrics.heightPixels-dp(24)
                        p.x=(panelX-dx).coerceIn(-dp(8),maxX)
                        p.y=(panelY+dy).coerceIn(-dp(8),maxY)
                        runCatching{wm.updateViewLayout(panelView,p)}
                    }
                    true
                }
                else->true
            }
        }
        panelView=box
        val p=lp(dp(348),dp(315),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        p.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;p.x=dp(10);wm.addView(box,p)
        startStats()
        startFpsMonitor()
    }

    private fun startFpsMonitor(){
        val code=projectionResultCode ?: return
        val data=projectionData ?: return
        fpsMonitor?.stop()
        fpsMonitor=ScreenFpsMonitor(this){fps-> handler.post{if(panelView!=null)statViews["fps"]?.text=fps.toString()} }
        fpsMonitor?.start(code,data)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent!=null){
            val code=intent.getIntExtra(ScreenFpsMonitor.EXTRA_RESULT_CODE,-1)
            if(code>0)projectionResultCode=code
            val data=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(ScreenFpsMonitor.EXTRA_RESULT_DATA,Intent::class.java) else intent.getParcelableExtra<Intent>(ScreenFpsMonitor.EXTRA_RESULT_DATA)
            if(data!=null)projectionData=data
        }
        return START_STICKY
    }

    private fun startStats(){
        handler.removeCallbacksAndMessages("stats")
        val tick=object:Runnable{
            override fun run(){
                if(panelView==null)return
                updateSystemStats()
                handler.postDelayed(this,1000)
            }
        }
        handler.post(tick)
        io.execute {
            while(panelView!=null){
                val ms=runCatching{
                    Socket().use{ s->
                        val t=System.nanoTime()
                        s.connect(InetSocketAddress("1.1.1.1",443),900)
                        (System.nanoTime()-t)/1_000_000
                    }
                }.getOrNull()
                handler.post { if(panelView!=null) statViews["net"]?.text = ms?.let{it.toString()+" ms"} ?: "--" }
                Thread.sleep(2500)
            }
        }
    }

    private fun updateSystemStats(){
        val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info=ActivityManager.MemoryInfo();am.getMemoryInfo(info)
        val used=info.totalMem-info.availMem
        val ram=if(info.totalMem>0) used*100/info.totalMem else 0
        statViews["ram"]?.text=ram.toString()+"%"
        val battery=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
        if(level>=0)statViews["battery"]?.text=(level*100/scale).toString()+"%"
        val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)?:-1
        statViews["temp"]?.text=if(temp>=0)(temp/10.0).toString()+"°C" else "--"
        val seconds=((System.currentTimeMillis()-startTime)/1000).toInt()
        statViews["session"]?.text=String.format("%02d:%02d",seconds/60,seconds%60)
        statViews["fps"]?.text="--"
    }

    private fun currentX()=(panelView?.layoutParams as? WindowManager.LayoutParams)?.x ?: dp(10)
    private fun currentY()=(panelView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0

    private fun stat(label:String,value:String,key:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=pill(false);setPadding(dp(4),dp(6),dp(4),dp(6))
        addView(text(value,15,true).apply{gravity=Gravity.CENTER;statViews[key]=this})
        addView(text(label,9,true).apply{gravity=Gravity.CENTER;setTextColor(Color.rgb(190,205,235))})
        layoutParams=LinearLayout.LayoutParams(0,dp(55),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))}
    }

    private fun action(label:String,active:Boolean,onClick:()->Unit)=TextView(this).apply{
        text=label;textSize=9.5f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD;background=pill(active);setPadding(dp(4),0,dp(4),0)
        setOnClickListener{onClick()};layoutParams=LinearLayout.LayoutParams(0,dp(39),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))}
    }

    private fun notifyBoost(name:String){
        sendBroadcast(Intent("com.rb22.BOOST_PROFILE").setPackage(packageName).putExtra("profile",name))
        Toast.makeText(this,"$name mode selected",Toast.LENGTH_SHORT).show()
    }

    private fun hidePanel(){handler.removeCallbacksAndMessages("stats");panelView?.let{runCatching{wm.removeView(it)}};panelView=null;showEdge()}
    private fun text(s:String,size:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(Color.WHITE);if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);io.shutdownNow();listOf(panelView,tabView,edgeView).forEach{v->v?.let{runCatching{wm.removeView(it)}}};super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
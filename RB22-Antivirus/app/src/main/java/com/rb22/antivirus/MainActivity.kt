package com.rb22.antivirus

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

private data class ScanItem(val appName:String,val packageName:String,val sha256:String?,val error:String?=null,val knownThreat:Boolean=false)
private data class ScanReport(val checked:Int,val unreadable:Int,val matches:List<ScanItem>,val items:List<ScanItem>)

private object LocalThreatDatabase {
    fun load(activity:Activity):Set<String> = runCatching {
        val json=org.json.JSONObject(activity.assets.open("threat-indicators.json").bufferedReader().use{it.readText()})
        require(json.optInt("schemaVersion")==1)
        val a=json.getJSONArray("indicators")
        buildSet { for(i in 0 until a.length()){val h=a.optString(i).trim().lowercase();if(h.matches(Regex("[a-f0-9]{64}")))add(h)} }
    }.getOrDefault(emptySet())
}
private class ApkScanner(private val activity:Activity) {
    fun scan():ScanReport {
        val db=LocalThreatDatabase.load(activity); val pm=activity.packageManager
        val items=pm.getInstalledApplications(0).map { app ->
            val name=runCatching{pm.getApplicationLabel(app).toString()}.getOrDefault(app.packageName)
            val result=runCatching { hash(File(app.sourceDir ?: error("APK path unavailable"))) }
            val h=result.getOrNull()
            ScanItem(name,app.packageName,h,result.exceptionOrNull()?.message,h!=null&&db.contains(h))
        }.sortedBy{it.appName.lowercase()}
        return ScanReport(items.count{it.sha256!=null},items.count{it.sha256==null},items.filter{it.knownThreat},items)
    }
    private fun hash(file:File):String {
        require(file.isFile&&file.canRead()){"APK could not be read"}
        val md=MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}}
        return md.digest().joinToString(""){"%02x".format(it)}
    }
}

class MainActivity:Activity() {
    private val executor=Executors.newSingleThreadExecutor()
    private lateinit var scanButton:Button
    private lateinit var shieldButton:Button
    private lateinit var shieldStatus:TextView
    private lateinit var statusText:TextView
    private lateinit var resultText:TextView

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(8,10,16);window.navigationBarColor=Color.rgb(8,10,16)
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,32,24,24);setBackgroundColor(Color.rgb(8,10,16))}
        content.addView(label("RB22",15f,Color.rgb(255,75,75),true))
        content.addView(label("ANTIVIRUS",28f,Color.WHITE,true))
        content.addView(label("Android security • Alpha 0.2.0",14f,Color.LTGRAY))
        content.addView(space(22))
        content.addView(label("RB22 SHIELD",21f,Color.WHITE,true))
        shieldStatus=label("Ad and domain filtering is OFF.",14f,Color.LTGRAY);content.addView(shieldStatus)
        content.addView(label("DNS-level filtering with a starter local blocklist. Android will ask permission to create a local VPN connection.",13f,Color.rgb(180,190,205)))
        shieldButton=Button(this).apply{text="ENABLE AD & THREAT BLOCKING";isAllCaps=false;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(180,35,48))}
        content.addView(shieldButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(space(24))
        statusText=label("APK scanner: manual scan only.",14f,Color.rgb(220,225,235));content.addView(statusText)
        scanButton=Button(this).apply{text="SCAN INSTALLED APPS";isAllCaps=false;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(180,35,48))}
        content.addView(scanButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(space(18));content.addView(label("Scan results",19f,Color.WHITE,true))
        resultText=label("No scan has been run yet. The scanner compares installed APK SHA-256 fingerprints with the bundled local indicator list.",14f,Color.rgb(205,210,220))
        content.addView(resultText);setContentView(ScrollView(this).apply{addView(content)})
        scanButton.setOnClickListener{startScan()};shieldButton.setOnClickListener{toggleShield()};refreshShield()
    }
    override fun onResume(){super.onResume();if(::shieldStatus.isInitialized)refreshShield()}
    private fun toggleShield(){
        val prefs=getSharedPreferences("rb22_shield",MODE_PRIVATE)
        if(prefs.getBoolean("enabled",false)){
            startService(Intent(this,Rb22ShieldVpnService::class.java).setAction(Rb22ShieldVpnService.STOP))
            prefs.edit().putBoolean("enabled",false).apply();refreshShield();return
        }
        val prepare=VpnService.prepare(this)
        if(prepare!=null){@Suppress("DEPRECATION") startActivityForResult(prepare,2202)}else startShield()
    }
    @Deprecated("VPN permission callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==2202&&resultCode==RESULT_OK)startShield()}
    private fun startShield(){
        val i=Intent(this,Rb22ShieldVpnService::class.java).setAction(Rb22ShieldVpnService.START)
        if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i)
        shieldStatus.text="Starting RB22 Shield…";shieldButton.text="STOP RB22 SHIELD"
    }
    private fun refreshShield(){
        val enabled=getSharedPreferences("rb22_shield",MODE_PRIVATE).getBoolean("enabled",false)
        shieldStatus.text=if(enabled)"RB22 Shield is active. DNS filtering is enabled." else "Ad and domain filtering is OFF."
        shieldButton.text=if(enabled)"STOP RB22 SHIELD" else "ENABLE AD & THREAT BLOCKING"
    }
    private fun startScan(){
        scanButton.isEnabled=false;statusText.text="Scanning installed applications…";resultText.text="Calculating APK fingerprints."
        executor.execute{
            val outcome=runCatching{ApkScanner(this).scan()}
            runOnUiThread{
                scanButton.isEnabled=true;val r=outcome.getOrNull()
                if(r==null){statusText.text="Scan could not complete.";resultText.text=outcome.exceptionOrNull()?.message?:"Unknown scan error";return@runOnUiThread}
                statusText.text="Scan complete • ${r.checked} APKs fingerprinted"
                resultText.text=buildString{
                    append("Known signature matches: ${r.matches.size}\nUnreadable APKs: ${r.unreadable}\n\n")
                    if(r.matches.isEmpty())append("No matches in the bundled signature list. This is not proof the device is malware-free; the initial database is empty.\n")
                    else r.matches.forEach{append("MATCH: ${it.appName} (${it.packageName})\nSHA-256: ${it.sha256}\n")}
                    append("\nInstalled apps\n");r.items.forEach{append("• ${it.appName}: ");append(if(it.sha256==null)"Not scanned" else if(it.knownThreat)"Signature match" else "No local match");append("\n")}
                }
            }
        }
    }
    private fun label(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,4,0,4)}
    private fun space(h:Int)=TextView(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
    override fun onDestroy(){executor.shutdownNow();super.onDestroy()}
}

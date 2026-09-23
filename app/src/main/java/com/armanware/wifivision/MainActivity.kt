package com.armanware.wifivision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView; private lateinit var overlay: WifiOverlayView
    private lateinit var networkText: TextView; private lateinit var modeText: TextView
    private lateinit var locatePanel: View; private lateinit var locateStatus: TextView; private lateinit var directionArrow: TextView; private lateinit var locateTitle: TextView
    private lateinit var apPanel: View; private lateinit var apListContainer: LinearLayout; private lateinit var scanStatus: TextView
    private lateinit var wifiManager: WifiManager
    private val handler=Handler(Looper.getMainLooper())
    private var surveying=false; private var locating=false; private var samples=0; private var bestRssi=-100; private var previousRssi=-100
    private var targetBssid:String?=null; private var targetSsid:String?=null
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){startIfReady()}
    private val poller=object:Runnable{override fun run(){updateWifi(); if(apPanel.visibility==View.VISIBLE)refreshScanResults(false); handler.postDelayed(this,1200)}}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_main)
        previewView=findViewById(R.id.previewView);overlay=findViewById(R.id.wifiOverlay);networkText=findViewById(R.id.networkText);modeText=findViewById(R.id.modeText)
        locatePanel=findViewById(R.id.locatePanel);locateStatus=findViewById(R.id.locateStatus);directionArrow=findViewById(R.id.directionArrow);locateTitle=findViewById(R.id.locateTitle)
        apPanel=findViewById(R.id.apPanel);apListContainer=findViewById(R.id.apListContainer);scanStatus=findViewById(R.id.scanStatus);wifiManager=applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        findViewById<Button>(R.id.cameraButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.AR;modeText.text="AR CAMERA  •  LIVE SIGNAL FIELD"}
        findViewById<Button>(R.id.heatmapButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.HEATMAP;modeText.text="HEATMAP  •  LIVE RSSI MAP"}
        findViewById<Button>(R.id.propagationButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.PROPAGATION;modeText.text="RF VIEW  •  ESTIMATED PROPAGATION"}
        findViewById<Button>(R.id.surveyButton).setOnClickListener{surveying=!surveying;if(!surveying)samples=0;modeText.text=if(surveying)"● SURVEY RECORDING  •  WALK SLOWLY" else "SURVEY PAUSED"}
        findViewById<Button>(R.id.locateButton).setOnClickListener{targetBssid=null;targetSsid=null;startLocator("Connected Wi-Fi")}
        findViewById<Button>(R.id.closeLocateButton).setOnClickListener{locating=false;locatePanel.visibility=View.GONE}
        findViewById<Button>(R.id.apListButton).setOnClickListener{apPanel.visibility=View.VISIBLE;requestScan()}
        findViewById<Button>(R.id.scanButton).setOnClickListener{requestScan()};findViewById<Button>(R.id.closeApButton).setOnClickListener{apPanel.visibility=View.GONE}
        requestNeededPermissions()
    }
    private fun showCamera(){apPanel.visibility=View.GONE}
    private fun requestNeededPermissions(){val p=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION);if(android.os.Build.VERSION.SDK_INT>=33)p+=Manifest.permission.NEARBY_WIFI_DEVICES;permissionLauncher.launch(p.toTypedArray())}
    private fun startIfReady(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();handler.removeCallbacks(poller);handler.post(poller)}
    private fun startCamera(){val future=ProcessCameraProvider.getInstance(this);future.addListener({val provider=future.get();val preview=Preview.Builder().build().also{it.setSurfaceProvider(previewView.surfaceProvider)};provider.unbindAll();provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview)},ContextCompat.getMainExecutor(this))}

    @Suppress("DEPRECATION") private fun requestScan(){try{scanStatus.text=if(wifiManager.startScan())"Scanning nearby APs…" else "Android scan throttled — showing latest cached scan";handler.postDelayed({refreshScanResults(true)},1400)}catch(_:SecurityException){scanStatus.text="Location / Nearby Wi-Fi permission required"}}
    @Suppress("DEPRECATION") private fun refreshScanResults(force:Boolean){try{val results=wifiManager.scanResults.sortedByDescending{it.level}.distinctBy{it.BSSID};if(force||apListContainer.childCount==0)renderAps(results);if(targetBssid!=null&&locating){results.firstOrNull{it.BSSID.equals(targetBssid,true)}?.let{updateLocator(it.level)}}}catch(_:SecurityException){scanStatus.text="Cannot read scan results"}}
    private fun renderAps(results:List<ScanResult>){apListContainer.removeAllViews();scanStatus.text="${results.size} radios found • tap one to LOCATE";results.take(40).forEach{ap->
        val ssid=if(android.os.Build.VERSION.SDK_INT>=33)ap.wifiSsid?.toString()?.trim('"')?.ifBlank{"Hidden network"}?:"Hidden network" else @Suppress("DEPRECATION") (ap.SSID.ifBlank{"Hidden network"})
        val band=band(ap.frequency);val ch=channel(ap.frequency);val b=Button(this).apply{isAllCaps=false;textAlignment=View.TEXT_ALIGNMENT_TEXT_START;text="$ssid\n${ap.level} dBm   •   $band   CH $ch\n${ap.BSSID}";setTextColor(Color.WHITE);textSize=14f;setBackgroundColor(Color.rgb(16,38,58));setPadding(22,12,12,12);minHeight=105
            setOnClickListener{targetBssid=ap.BSSID;targetSsid=ssid;apPanel.visibility=View.GONE;startLocator("$ssid • ${ap.BSSID}");updateLocator(ap.level)}}
        val lp=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,10)};apListContainer.addView(b,lp)
    }}
    private fun band(f:Int)=when{f>=5925->"6 GHz";f>=4900->"5 GHz";f>0->"2.4 GHz";else->"?"}
    private fun channel(f:Int)=when{f==2484->14;f in 2412..2472->(f-2407)/5;f in 5000..5895->(f-5000)/5;f>=5955->(f-5950)/5;else->0}
    private fun startLocator(name:String){locating=true;bestRssi=-100;previousRssi=-100;locateTitle.text="RF LOCATOR • $name";locatePanel.visibility=View.VISIBLE;modeText.text="LOCATE  •  RSSI DIRECTION ESTIMATE";locateStatus.text="Move several feet and rotate slowly\nLearning signal trend…"}

    @Suppress("DEPRECATION") private fun updateWifi(){try{val info=wifiManager.connectionInfo;val rssi=info.rssi;if(surveying)samples++;if(targetBssid==null){overlay.setRssi(rssi,surveying);if(locating)updateLocator(rssi)};val ssid=info.ssid?.removePrefix("\"")?.removeSuffix("\"")?:"Unknown";val f=info.frequency;networkText.text="$ssid   $rssi dBm   •   ${band(f)} CH ${channel(f)}"+(if(surveying)"   •   $samples samples" else "")} }catch(_:SecurityException){networkText.text="Wi-Fi permission required"}}
    private fun updateLocator(rssi:Int){if(rssi>bestRssi)bestRssi=rssi;if(previousRssi<=-100){previousRssi=rssi;locateStatus.text="$rssi dBm • baseline captured\nMove several feet…";return};val delta=rssi-previousRssi;val confidence=((bestRssi+100)*100/70).coerceIn(5,99);overlay.setRssi(rssi,false)
        when{delta>=2->{directionArrow.text="↑";directionArrow.setTextColor(0xFF55FF88.toInt());locateStatus.text="$rssi dBm • GETTING STRONGER ↑\nKeep moving this way\nConfidence $confidence%"};delta<=-2->{directionArrow.text="↶";directionArrow.setTextColor(0xFFFFB23E.toInt());locateStatus.text="$rssi dBm • GETTING WEAKER ↓\nTurn / try another direction\nBest $bestRssi dBm"};else->{directionArrow.text="↗";directionArrow.setTextColor(0xFF35AFFF.toInt());locateStatus.text="$rssi dBm • sampling…\nMove farther and rotate slowly\nBest $bestRssi dBm"}};previousRssi=rssi}
    override fun onDestroy(){handler.removeCallbacks(poller);super.onDestroy()}
}

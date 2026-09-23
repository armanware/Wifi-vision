package com.armanware.wifivision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: WifiOverlayView
    private lateinit var networkText: TextView
    private lateinit var modeText: TextView
    private lateinit var locatePanel: View
    private lateinit var locateStatus: TextView
    private lateinit var directionArrow: TextView
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var surveying = false
    private var locating = false
    private var samples = 0
    private var bestRssi = -100
    private var previousRssi = -100
    private var trendScore = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startIfReady() }
    private val poller = object : Runnable { override fun run() { updateWifi(); handler.postDelayed(this, 650) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        previewView=findViewById(R.id.previewView); overlay=findViewById(R.id.wifiOverlay); networkText=findViewById(R.id.networkText); modeText=findViewById(R.id.modeText)
        locatePanel=findViewById(R.id.locatePanel); locateStatus=findViewById(R.id.locateStatus); directionArrow=findViewById(R.id.directionArrow)
        wifiManager=applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        findViewById<Button>(R.id.cameraButton).setOnClickListener { overlay.mode=WifiOverlayView.Mode.AR; modeText.text="AR CAMERA  •  LIVE SIGNAL FIELD" }
        findViewById<Button>(R.id.heatmapButton).setOnClickListener { overlay.mode=WifiOverlayView.Mode.HEATMAP; modeText.text="HEATMAP  •  LIVE RSSI MAP" }
        findViewById<Button>(R.id.propagationButton).setOnClickListener { overlay.mode=WifiOverlayView.Mode.PROPAGATION; modeText.text="RF VIEW  •  ESTIMATED PROPAGATION" }
        findViewById<Button>(R.id.surveyButton).setOnClickListener { surveying=!surveying; if(!surveying) samples=0; modeText.text=if(surveying) "● SURVEY RECORDING  •  WALK SLOWLY" else "SURVEY PAUSED" }
        findViewById<Button>(R.id.locateButton).setOnClickListener { locating=true; bestRssi=-100; trendScore=0; locatePanel.visibility=View.VISIBLE; modeText.text="LOCATE  •  RSSI DIRECTION ESTIMATE" }
        findViewById<Button>(R.id.closeLocateButton).setOnClickListener { locating=false; locatePanel.visibility=View.GONE; modeText.text="AR CAMERA  •  LIVE SIGNAL FIELD" }
        requestNeededPermissions()
    }

    private fun requestNeededPermissions(){ val p=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION); if(android.os.Build.VERSION.SDK_INT>=33)p+=Manifest.permission.NEARBY_WIFI_DEVICES; permissionLauncher.launch(p.toTypedArray()) }
    private fun startIfReady(){ if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera(); handler.removeCallbacks(poller); handler.post(poller) }
    private fun startCamera(){ val future=ProcessCameraProvider.getInstance(this); future.addListener({ val provider=future.get(); val preview=Preview.Builder().build().also{it.setSurfaceProvider(previewView.surfaceProvider)}; provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview)},ContextCompat.getMainExecutor(this)) }

    @Suppress("DEPRECATION")
    private fun updateWifi(){
        try{
            val info=wifiManager.connectionInfo; val rssi=info.rssi
            if(surveying)samples++; overlay.setRssi(rssi,surveying)
            val ssid=info.ssid?.removePrefix("\"")?.removeSuffix("\"")?:"Unknown"; val f=info.frequency
            val band=when{f>=5925->"6 GHz";f>=4900->"5 GHz";f>0->"2.4 GHz";else->"?"}; val ch=when{f in 2412..2484->(f-2407)/5;f in 5000..5895->(f-5000)/5;f>=5955->(f-5950)/5;else->0}
            networkText.text="$ssid   $rssi dBm   •   $band CH $ch" + if(surveying) "   •   $samples samples" else ""
            if(locating) updateLocator(rssi)
            previousRssi=rssi
        }catch(_:SecurityException){networkText.text="Wi-Fi permission required"}
    }

    private fun updateLocator(rssi:Int){
        if(rssi>bestRssi)bestRssi=rssi
        val delta=rssi-previousRssi
        trendScore=(trendScore+delta).coerceIn(-12,12)
        val confidence=((bestRssi+100)*100/70).coerceIn(5,99)
        when { delta>=2 -> { directionArrow.text="↑"; directionArrow.setTextColor(0xFF55FF88.toInt()); locateStatus.text="$rssi dBm  •  GETTING STRONGER ↑\nKeep moving this way\nConfidence $confidence%" }
            delta<=-2 -> { directionArrow.text="↶"; directionArrow.setTextColor(0xFFFFB23E.toInt()); locateStatus.text="$rssi dBm  •  GETTING WEAKER ↓\nTurn around / try another direction\nBest $bestRssi dBm" }
            else -> { directionArrow.text="↗"; directionArrow.setTextColor(0xFF35AFFF.toInt()); locateStatus.text="$rssi dBm  •  sampling…\nMove several feet and rotate slowly\nBest $bestRssi dBm" }
        }
    }
    override fun onDestroy(){handler.removeCallbacks(poller);super.onDestroy()}
}

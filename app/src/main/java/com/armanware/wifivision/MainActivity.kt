package com.armanware.wifivision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var surveying = false
    private var samples = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startIfReady() }
    private val poller = object : Runnable {
        override fun run() { updateWifi(); handler.postDelayed(this, 650) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.wifiOverlay)
        networkText = findViewById(R.id.networkText)
        modeText = findViewById(R.id.modeText)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        findViewById<Button>(R.id.cameraButton).setOnClickListener { overlay.mode = WifiOverlayView.Mode.AR; modeText.text = "AR CAMERA • LIVE SIGNAL FIELD" }
        findViewById<Button>(R.id.heatmapButton).setOnClickListener { overlay.mode = WifiOverlayView.Mode.HEATMAP; modeText.text = "HEATMAP • MOVE PHONE AROUND ROOM" }
        findViewById<Button>(R.id.propagationButton).setOnClickListener { overlay.mode = WifiOverlayView.Mode.PROPAGATION; modeText.text = "PROPAGATION • ESTIMATED RF FIELD" }
        findViewById<Button>(R.id.surveyButton).setOnClickListener {
            surveying = !surveying
            if (!surveying) samples = 0
            modeText.text = if (surveying) "● SURVEY RECORDING • walk slowly" else "SURVEY PAUSED"
        }
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val p = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        permissionLauncher.launch(p.toTypedArray())
    }

    private fun startIfReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        handler.removeCallbacks(poller); handler.post(poller)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get(); val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("DEPRECATION")
    private fun updateWifi() {
        try {
            val info = wifiManager.connectionInfo; val rssi = info.rssi
            if (surveying) samples++
            overlay.setRssi(rssi, surveying)
            val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "Unknown"
            val f = info.frequency
            val band = when { f >= 5925 -> "6 GHz"; f >= 4900 -> "5 GHz"; f > 0 -> "2.4 GHz"; else -> "?" }
            val ch = when { f in 2412..2484 -> (f - 2407) / 5; f in 5000..5895 -> (f - 5000) / 5; f >= 5955 -> (f - 5950) / 5; else -> 0 }
            networkText.text = "$ssid   $rssi dBm\n$band • CH $ch • ${f} MHz\nBSSID ${info.bssid ?: "unavailable"}" + if (surveying) "\nSURVEY SAMPLES: $samples" else ""
        } catch (_: SecurityException) { networkText.text = "Wi-Fi permission required" }
    }

    override fun onDestroy() { handler.removeCallbacks(poller); super.onDestroy() }
}

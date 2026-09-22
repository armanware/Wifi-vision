package com.armanware.wifivision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startIfReady() }

    private val poller = object : Runnable {
        override fun run() {
            updateWifi()
            handler.postDelayed(this, 750)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.wifiOverlay)
        networkText = findViewById(R.id.networkText)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startIfReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("DEPRECATION")
    private fun updateWifi() {
        try {
            val info = wifiManager.connectionInfo
            val rssi = info.rssi
            overlay.setRssi(rssi)
            val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "Unknown"
            val freq = info.frequency
            val band = when { freq >= 5925 -> "6 GHz"; freq >= 4900 -> "5 GHz"; freq > 0 -> "2.4 GHz"; else -> "?" }
            networkText.text = "$ssid  •  $band  •  ${freq} MHz\nBSSID: ${info.bssid ?: "unavailable"}"
        } catch (e: SecurityException) {
            networkText.text = "Wi-Fi permission required"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }
}

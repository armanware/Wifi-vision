package com.armanware.wifivision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.*
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

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var previewView:PreviewView; private lateinit var overlay:WifiOverlayView; private lateinit var networkText:TextView; private lateinit var modeText:TextView
    private lateinit var locatePanel:View; private lateinit var locateStatus:TextView; private lateinit var directionArrow:TextView; private lateinit var locateTitle:TextView
    private lateinit var apPanel:View; private lateinit var apListContainer:LinearLayout; private lateinit var scanStatus:TextView; private lateinit var wifiManager:WifiManager
    private lateinit var sensorManager:SensorManager; private var stepSensor:Sensor?=null; private var rotationSensor:Sensor?=null
    private var stepBase=-1; private var stepCount=0; private var azimuth=0f
    private val handler=Handler(Looper.getMainLooper()); private var surveying=false; private var locating=false; private var samples=0; private var bestRssi=-100; private var previousRssi=-100
    private var targetBssid:String?=null; private var targetSsid:String?=null; private var targetRssi:Int?=null
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){startIfReady()}
    private val poller=object:Runnable{override fun run(){updateWifi();if(targetBssid!=null&&(surveying||locating))refreshScanResults(false);if(apPanel.visibility==View.VISIBLE)refreshScanResults(false);handler.postDelayed(this,1200)}}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_main)
        previewView=findViewById(R.id.previewView);overlay=findViewById(R.id.wifiOverlay);networkText=findViewById(R.id.networkText);modeText=findViewById(R.id.modeText);locatePanel=findViewById(R.id.locatePanel);locateStatus=findViewById(R.id.locateStatus);directionArrow=findViewById(R.id.directionArrow);locateTitle=findViewById(R.id.locateTitle);apPanel=findViewById(R.id.apPanel);apListContainer=findViewById(R.id.apListContainer);scanStatus=findViewById(R.id.scanStatus)
        wifiManager=applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager;sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager;stepSensor=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);rotationSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        findViewById<Button>(R.id.cameraButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.AR;modeText.text="AR CAMERA  •  LIVE SIGNAL FIELD"}
        findViewById<Button>(R.id.heatmapButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.HEATMAP;modeText.text="HEATMAP  •  LIVE RSSI MAP"}
        findViewById<Button>(R.id.propagationButton).setOnClickListener{showCamera();overlay.mode=WifiOverlayView.Mode.PROPAGATION;modeText.text="RF MAP  •  ${overlay.surveyCount()} MEASURED SAMPLES"}
        findViewById<Button>(R.id.surveyButton).setOnClickListener{surveying=!surveying;if(surveying){overlay.clearSurvey();samples=0;stepBase=-1;modeText.text="● SURVEY RECORDING • WALK THROUGH THE AREA"}else modeText.text="SURVEY SAVED • OPEN RF VIEW (${overlay.surveyCount()} samples)"}
        findViewById<Button>(R.id.locateButton).setOnClickListener{targetBssid=null;targetSsid=null;startLocator("Connected Wi-Fi")};findViewById<Button>(R.id.closeLocateButton).setOnClickListener{locating=false;locatePanel.visibility=View.GONE}
        findViewById<Button>(R.id.apListButton).setOnClickListener{apPanel.visibility=View.VISIBLE;requestScan()};findViewById<Button>(R.id.scanButton).setOnClickListener{requestScan()};findViewById<Button>(R.id.closeApButton).setOnClickListener{apPanel.visibility=View.GONE};requestNeededPermissions()
    }
    private fun showCamera(){apPanel.visibility=View.GONE}
    private fun requestNeededPermissions(){val p=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACTIVITY_RECOGNITION);if(android.os.Build.VERSION.SDK_INT>=33)p+=Manifest.permission.NEARBY_WIFI_DEVICES;permissionLauncher.launch(p.toTypedArray())}
    private fun startIfReady(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();handler.removeCallbacks(poller);handler.post(poller)}
    private fun startCamera(){val future=ProcessCameraProvider.getInstance(this);future.addListener({val provider=future.get();val preview=Preview.Builder().build().also{it.setSurfaceProvider(previewView.surfaceProvider)};provider.unbindAll();provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview)},ContextCompat.getMainExecutor(this))}
    @Suppress("DEPRECATION") private fun requestScan(){try{scanStatus.text=if(wifiManager.startScan())"Scanning nearby APs…" else "Android scan throttled — latest scan";handler.postDelayed({refreshScanResults(true)},1400)}catch(_:SecurityException){scanStatus.text="Wi-Fi permission required"}}
    @Suppress("DEPRECATION") private fun refreshScanResults(force:Boolean){try{val results=wifiManager.scanResults.sortedByDescending{it.level}.distinctBy{it.BSSID};if(force||apListContainer.childCount==0)renderAps(results);targetBssid?.let{b->results.firstOrNull{it.BSSID.equals(b,true)}?.let{ap->targetRssi=ap.level;if(locating)updateLocator(ap.level)}}}catch(_:SecurityException){}}
    private fun renderAps(results:List<ScanResult>){apListContainer.removeAllViews();scanStatus.text="${results.size} radios found • tap one to select";results.take(40).forEach{ap->val ssid=if(android.os.Build.VERSION.SDK_INT>=33)ap.wifiSsid?.toString()?.trim('"')?.ifBlank{"Hidden network"}?:"Hidden network" else @Suppress("DEPRECATION") ap.SSID.ifBlank{"Hidden network"};val b=Button(this).apply{isAllCaps=false;textAlignment=View.TEXT_ALIGNMENT_TEXT_START;text="$ssid\n${ap.level} dBm • ${band(ap.frequency)} CH ${channel(ap.frequency)}\n${ap.BSSID}";setTextColor(Color.WHITE);textSize=14f;setBackgroundColor(Color.rgb(16,38,58));setPadding(22,12,12,12);minHeight=105;setOnClickListener{targetBssid=ap.BSSID;targetSsid=ssid;targetRssi=ap.level;apPanel.visibility=View.GONE;startLocator("$ssid • ${ap.BSSID}");updateLocator(ap.level)}};apListContainer.addView(b,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,10)})}}
    private fun band(f:Int)=when{f>=5925->"6 GHz";f>=4900->"5 GHz";f>0->"2.4 GHz";else->"?"};private fun channel(f:Int)=when{f==2484->14;f in 2412..2472->(f-2407)/5;f in 5000..5895->(f-5000)/5;f>=5955->(f-5950)/5;else->0}
    private fun startLocator(name:String){locating=true;bestRssi=-100;previousRssi=-100;locateTitle.text="RF LOCATOR • $name";locatePanel.visibility=View.VISIBLE;modeText.text="LOCATE • RSSI DIRECTION ESTIMATE";locateStatus.text="Move several feet and rotate slowly\nLearning signal trend…"}
    @Suppress("DEPRECATION") private fun updateWifi(){try{val info=wifiManager.connectionInfo;val connectedRssi=info.rssi;val activeRssi=targetRssi?:connectedRssi;overlay.setRssi(activeRssi,surveying);if(surveying){samples++;overlay.addSurveySample(activeRssi,stepCount,azimuth)};if(targetBssid==null&&locating)updateLocator(connectedRssi);val ssid=targetSsid?:info.ssid?.removePrefix("\"")?.removeSuffix("\"")?:"Unknown";networkText.text="$ssid   $activeRssi dBm"+(if(surveying)" • ${overlay.surveyCount()} RF points" else "")}catch(_:SecurityException){networkText.text="Wi-Fi permission required"}}
    private fun updateLocator(rssi:Int){if(rssi>bestRssi)bestRssi=rssi;if(previousRssi<=-100){previousRssi=rssi;locateStatus.text="$rssi dBm • baseline captured\nMove several feet…";return};val d=rssi-previousRssi;overlay.setRssi(rssi,false);when{d>=2->{directionArrow.text="↑";directionArrow.setTextColor(0xFF55FF88.toInt());locateStatus.text="$rssi dBm • GETTING STRONGER ↑\nKeep moving this way"};d<=-2->{directionArrow.text="↶";directionArrow.setTextColor(0xFFFFB23E.toInt());locateStatus.text="$rssi dBm • GETTING WEAKER\nTurn / try another direction"};else->{directionArrow.text="↗";directionArrow.setTextColor(0xFF35AFFF.toInt());locateStatus.text="$rssi dBm • sampling…\nMove farther"}};previousRssi=rssi}
    override fun onSensorChanged(e:SensorEvent){when(e.sensor.type){Sensor.TYPE_STEP_COUNTER->{val raw=e.values[0].toInt();if(stepBase<0)stepBase=raw;stepCount=raw-stepBase};Sensor.TYPE_ROTATION_VECTOR->{val r=FloatArray(9);val o=FloatArray(3);SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,o);azimuth=o[0]}}}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    override fun onResume(){super.onResume();stepSensor?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)};rotationSensor?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}}
    override fun onPause(){sensorManager.unregisterListener(this);super.onPause()}
    override fun onDestroy(){handler.removeCallbacks(poller);super.onDestroy()}
}

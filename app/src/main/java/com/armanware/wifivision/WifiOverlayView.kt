package com.armanware.wifivision

import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class WifiOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Mode { AR, HEATMAP, PROPAGATION }
    data class RfSample(val x: Float, val y: Float, val rssi: Int)

    var mode = Mode.AR; set(v) { field=v; invalidate() }
    private var rssi=-100
    private val history=ArrayDeque<Int>()
    private val samples=ArrayList<RfSample>()
    private var px=0f; private var py=0f; private var lastStep=0
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=52f; textAlign=Paint.Align.CENTER; isFakeBoldText=true }

    fun setRssi(v:Int, record:Boolean=false) { rssi=v.coerceIn(-100,-20); if(record || mode!=Mode.AR){ history.addLast(rssi); if(history.size>80) history.removeFirst() }; invalidate() }
    fun clearSurvey(){ samples.clear(); px=0f; py=0f; lastStep=0; invalidate() }
    fun addSurveySample(v:Int, stepCount:Int, azimuthRad:Float){
        if(samples.isEmpty()){ samples.add(RfSample(0f,0f,v)); lastStep=stepCount; invalidate(); return }
        val ds=(stepCount-lastStep).coerceIn(0,3); if(ds>0){ val meters=ds*.72f; px += (-sin(azimuthRad)*meters); py += (-cos(azimuthRad)*meters); lastStep=stepCount }
        if(ds>0 || samples.size<3 || abs(samples.last().rssi-v)>=2){ samples.add(RfSample(px,py,v)); if(samples.size>500)samples.removeAt(0); invalidate() }
    }
    fun surveyCount()=samples.size
    private fun color(q:Float):Int = when { q>.8f->Color.rgb(255,35,20); q>.62f->Color.rgb(255,190,0); q>.45f->Color.rgb(60,235,75); q>.27f->Color.rgb(0,190,255); else->Color.rgb(45,20,180) }
    private fun alphaColor(a:Int,b:Int)=Color.argb(a,Color.red(b),Color.green(b),Color.blue(b))

    override fun onDraw(c:Canvas){ super.onDraw(c); val q=((rssi+100)/80f).coerceIn(0f,1f); val cx=width/2f; val cy=height/2f
        when(mode){
            Mode.AR->{ val radius=min(width,height)*(.18f+q*.3f); paint.shader=RadialGradient(cx,cy,radius,intArrayOf(alphaColor(210,color(q)),Color.argb(100,0,180,255),Color.TRANSPARENT),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,cy,radius,paint);paint.shader=null;c.drawText("$rssi dBm",cx,cy,text) }
            Mode.HEATMAP->drawHeat(c)
            Mode.PROPAGATION->drawSurveyMap(c)
        }; drawLegend(c)
    }
    private fun drawHeat(c:Canvas){ c.drawColor(Color.argb(110,0,0,15));if(history.isEmpty())history.add(rssi);val cols=6;history.forEachIndexed{i,v->val x=((i%cols)+.5f)*width/cols;val y=height-((i/cols)+1)*height/15f;val q=((v+100)/80f).coerceIn(0f,1f);val rad=width*.24f;paint.shader=RadialGradient(x,y,rad,intArrayOf(alphaColor(210,color(q)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawCircle(x,y,rad,paint)};paint.shader=null;c.drawText("LIVE RSSI HEATMAP",width/2f,height*.52f,text) }
    private fun drawSurveyMap(c:Canvas){
        c.drawColor(Color.argb(205,0,5,15)); if(samples.size<2){ val p=Paint(text).apply{textSize=34f};c.drawText("NO RF SURVEY YET",width/2f,height*.42f,p);p.textSize=25f;c.drawText("Tap SURVEY and walk around",width/2f,height*.48f,p);c.drawText("Then return to RF VIEW",width/2f,height*.53f,p);return }
        val minX=samples.minOf{it.x};val maxX=samples.maxOf{it.x};val minY=samples.minOf{it.y};val maxY=samples.maxOf{it.y};val span=max(maxX-minX,maxY-minY).coerceAtLeast(3f);val scale=min(width*.72f,height*.62f)/span;val ox=width/2f-(minX+maxX)/2f*scale;val oy=height*.45f-(minY+maxY)/2f*scale
        samples.forEach{s->val x=ox+s.x*scale;val y=oy+s.y*scale;val q=((s.rssi+100)/80f).coerceIn(0f,1f);val rad=(width*.18f).coerceAtMost(150f);paint.shader=RadialGradient(x,y,rad,intArrayOf(alphaColor(185,color(q)),alphaColor(70,color(q)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawCircle(x,y,rad,paint)};paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=5f;paint.color=Color.WHITE;val path=Path();samples.forEachIndexed{i,s->val x=ox+s.x*scale;val y=oy+s.y*scale;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};c.drawPath(path,paint);paint.style=Paint.Style.FILL
        val best=samples.maxByOrNull{it.rssi}!!;val bx=ox+best.x*scale;val by=oy+best.y*scale;paint.color=Color.WHITE;c.drawCircle(bx,by,18f,paint);val small=Paint(text).apply{textSize=28f};c.drawText("AP likely near strongest area • ${best.rssi} dBm",width/2f,height*.84f,small);small.textSize=23f;c.drawText("${samples.size} RF samples • estimated walking map",width/2f,height*.89f,small)
    }
    private fun drawLegend(c:Canvas){val left=24f;val top=height*.58f;val h=height*.28f;val grad=LinearGradient(left,top,left,top+h,intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE),null,Shader.TileMode.CLAMP);paint.shader=grad;c.drawRect(left,top,left+30,top+h,paint);paint.shader=null;val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=25f};c.drawText("-30",62f,top+8,p);c.drawText("-60",62f,top+h/2,p);c.drawText("-90",62f,top+h,p)}
}

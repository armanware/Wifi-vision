package com.armanware.wifivision

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class WifiOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Mode { AR, HEATMAP, PROPAGATION }
    var mode = Mode.AR; set(v) { field=v; invalidate() }
    private var rssi=-100
    private val history=ArrayDeque<Int>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=52f; textAlign=Paint.Align.CENTER; isFakeBoldText=true }

    fun setRssi(v:Int, record:Boolean=false) { rssi=v.coerceIn(-100,-20); if(record || mode!=Mode.AR){ history.addLast(rssi); if(history.size>80) history.removeFirst() }; invalidate() }
    private fun color(q:Float):Int = when {
        q>.8f -> Color.rgb(255,35,20); q>.62f -> Color.rgb(255,190,0); q>.45f -> Color.rgb(60,235,75); q>.27f -> Color.rgb(0,190,255); else -> Color.rgb(45,20,180)
    }
    override fun onDraw(c:Canvas){ super.onDraw(c); val q=((rssi+100)/80f).coerceIn(0f,1f); val cx=width/2f; val cy=height/2f
        when(mode){
            Mode.AR -> {
                val radius=min(width,height)*(.18f+q*.3f)
                paint.shader=RadialGradient(cx,cy,radius,intArrayOf(Color.argb(210,color(q)),Color.argb(100,0,180,255),Color.TRANSPARENT),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
                c.drawCircle(cx,cy,radius,paint); paint.shader=null
                c.drawText("$rssi dBm",cx,cy,text)
            }
            Mode.HEATMAP -> drawHeat(c)
            Mode.PROPAGATION -> drawPropagation(c,q)
        }
        drawLegend(c)
    }
    private fun drawHeat(c:Canvas){
        c.drawColor(Color.argb(110,0,0,15)); if(history.isEmpty()) history.add(rssi)
        val cols=6; history.forEachIndexed { i,v -> val x=((i%cols)+.5f)*width/cols; val y=height-((i/cols)+1)*height/15f; val q=((v+100)/80f).coerceIn(0f,1f); val rad=width*.24f
            paint.shader=RadialGradient(x,y,rad,intArrayOf(Color.argb(210,color(q)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP); c.drawCircle(x,y,rad,paint)
        }; paint.shader=null; c.drawText("LIVE RSSI HEATMAP",width/2f,height*.52f,text)
    }
    private fun drawPropagation(c:Canvas,q:Float){
        c.drawColor(Color.argb(175,0,0,20)); val ox=width*.12f; val oy=height*.2f
        for(i in 1..18){ val ang=(-1.15f+i*.13f); val len=width*(.32f+q*.55f); paint.color=Color.argb(150,color(q)); paint.strokeWidth=8f
            val path=Path(); path.moveTo(ox,oy); for(s in 1..22){ val d=len*s/22f; val wave=sin(s*1.8f)*12f; path.lineTo(ox+cos(ang)*d-wave*sin(ang),oy+sin(ang)*d+wave*cos(ang)) }; c.drawPath(path,paint)
        }; paint.style=Paint.Style.STROKE; paint.strokeWidth=5f; paint.color=Color.WHITE; c.drawRect(width*.08f,height*.12f,width*.92f,height*.78f,paint); paint.style=Paint.Style.FILL; c.drawText("RF PROPAGATION",width/2f,height*.88f,text)
    }
    private fun drawLegend(c:Canvas){ val left=24f; val top=height*.58f; val h=height*.28f; val grad=LinearGradient(left,top,left,top+h,intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE),null,Shader.TileMode.CLAMP); paint.shader=grad; c.drawRect(left,top,left+30,top+h,paint); paint.shader=null
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=25f}; c.drawText("-30",62f,top+8,p); c.drawText("-60",62f,top+h/2,p); c.drawText("-90",62f,top+h,p)
    }
}

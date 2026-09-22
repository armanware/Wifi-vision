package com.armanware.wifivision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class WifiOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var rssi = -100
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        alpha = 130
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 58f
        isFakeBoldText = true
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }

    fun setRssi(value: Int) {
        rssi = value.coerceIn(-100, -20)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val quality = ((rssi + 100) / 80f).coerceIn(0f, 1f)
        val radius = min(width, height) * (0.13f + quality * 0.24f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, max(radius, 1f),
                intArrayOf(Color.argb((55 + quality * 90).toInt(), 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, glow)
        for (i in 1..3) canvas.drawCircle(cx, cy, radius * i / 3f, ringPaint)
        canvas.drawText("$rssi dBm", cx, cy + 18f, textPaint)
        val label = when {
            rssi >= -50 -> "VERY STRONG"
            rssi >= -60 -> "STRONG"
            rssi >= -70 -> "GOOD"
            rssi >= -80 -> "WEAK"
            else -> "VERY WEAK"
        }
        canvas.drawText(label, cx, cy + 64f, smallText)
    }
}

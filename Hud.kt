package com.vaibhav.jarvis
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
class GridBg(c: Context) : View(c) {
private val p = Paint().apply { color = Color.parseColor("#0B2230"); strokeWidth = 1f }
override fun onDraw(cv: Canvas) {
val s = 56f
var x = 0f
while (x < width) { cv.drawLine(x, 0f, x, height.toFloat(), p); x += s }
var y = 0f
while (y < height) { cv.drawLine(0f, y, width.toFloat(), y, p); y += s }
}
}
class ReactorView(c: Context) : View(c) {
var on = false
private var t = 0f
private val p = Paint(Paint.ANTI_ALIAS_FLAG)
private val an = ValueAnimator.ofFloat(0f, 360f).apply {
duration = 7000
repeatCount = ValueAnimator.INFINITE
interpolator = LinearInterpolator()
addUpdateListener { t = it.animatedValue as Float; invalidate() }
}
override fun onAttachedToWindow() { super.onAttachedToWindow(); an.start() }
override fun onDetachedFromWindow() { an.cancel(); super.onDetachedFromWindow() }
override fun onMeasure(w: Int, h: Int) { val s = MeasureSpec.getSize(w); setMeasuredDimension(s, s) }
override fun onDraw(cv: Canvas) {
val cx = width / 2f
val cy = height / 2f
val r = width / 2f
val col = if (on) Color.parseColor("#00E5FF") else Color.parseColor("#3A566E")
val pulse = if (on) 0.6f + 0.4f * Math.sin(Math.toRadians((t * 4f).toDouble())).toFloat() else 0.25f
p.style = Paint.Style.STROKE
p.color = col
p.strokeWidth = 4f
p.pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
cv.save(); cv.rotate(t, cx, cy); cv.drawCircle(cx, cy, r * 0.96f, p); cv.restore()
p.strokeWidth = 3f
p.pathEffect = DashPathEffect(floatArrayOf(44f, 22f), 0f)
cv.save(); cv.rotate(-t * 1.6f, cx, cy); cv.drawCircle(cx, cy, r * 0.86f, p); cv.restore()
p.pathEffect = null
p.strokeWidth = 2f
cv.drawCircle(cx, cy, r * 0.74f, p)
val rr = r * 0.58f
val rect = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
p.strokeWidth = r * 0.17f
p.alpha = if (on) 170 else 90
for (i in 0 until 10) cv.drawArc(rect, i * 36f + t / 5f, 28f, false, p)
p.alpha = 255
p.strokeWidth = 3f
cv.drawCircle(cx, cy, r * 0.42f, p)
p.style = Paint.Style.FILL
p.shader = RadialGradient(cx, cy, r * 0.4f, intArrayOf(Color.WHITE, col, Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
p.alpha = (255 * pulse).toInt()
cv.drawCircle(cx, cy, r * 0.4f, p)
p.shader = null
p.alpha = 255
}
}

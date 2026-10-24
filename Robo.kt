package com.vaibhav.jarvis
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.View
class RoboHead(c: Context) : View(c) {
@Volatile var on = false
private val t0 = System.currentTimeMillis()
private val p = Paint(Paint.ANTI_ALIAS_FLAG)
private val cy0 = Color.parseColor("#00E5FF")
private val dark = Color.parseColor("#0A1722")
private val dim = Color.parseColor("#1D4A55")
private val r = RectF()
init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
private fun box(l: Float, t: Float, rr: Float, b: Float, rad: Float, fill: Int, stroke: Int, sw: Float, glow: Float) {
r.set(l, t, rr, b)
p.pathEffect = null
if (glow > 0f) p.setShadowLayer(glow, 0f, 0f, cy0) else p.clearShadowLayer()
p.style = Paint.Style.FILL; p.color = fill
canvas.drawRoundRect(r, rad, rad, p)
if (sw > 0f) { p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = stroke; canvas.drawRoundRect(r, rad, rad, p) }
p.clearShadowLayer()
}
private lateinit var canvas: Canvas
override fun onDraw(cv: Canvas) {
canvas = cv
val w = width.toFloat(); val h = height.toFloat()
val s = minOf(w, h * 1.1f)
val cx = w / 2f; val cy = h / 2f
val t = (System.currentTimeMillis() - t0) / 1000f
p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.008f
p.color = Color.argb(90, 0, 229, 255)
p.pathEffect = DashPathEffect(floatArrayOf(s * 0.03f, s * 0.02f), 0f)
cv.save(); cv.rotate(t * 20f, cx, cy); cv.drawCircle(cx, cy, s * 0.46f, p); cv.restore()
p.pathEffect = DashPathEffect(floatArrayOf(s * 0.06f, s * 0.03f), 0f)
cv.save(); cv.rotate(-t * 30f, cx, cy); cv.drawCircle(cx, cy, s * 0.40f, p); cv.restore()
p.pathEffect = null
val pulse = 0.5f + 0.5f * Math.sin(t * 3.0).toFloat()
box(cx - 0.25f * s, cy + 0.2f * s, cx + 0.25f * s, cy + 0.32f * s, 0.05f * s, dark, cy0, s * 0.006f, 0f)
box(cx - 0.06f * s, cy + 0.15f * s, cx + 0.06f * s, cy + 0.22f * s, 0.01f * s, dark, cy0, s * 0.005f, 0f)
box(cx - 0.29f * s, cy - 0.08f * s, cx - 0.23f * s, cy + 0.06f * s, 0.015f * s, dark, cy0, s * 0.006f, 0f)
box(cx + 0.23f * s, cy - 0.08f * s, cx + 0.29f * s, cy + 0.06f * s, 0.015f * s, dark, cy0, s * 0.006f, 0f)
p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.008f; p.color = cy0
cv.drawLine(cx, cy - 0.2f * s, cx, cy - 0.28f * s, p)
p.style = Paint.Style.FILL
p.setShadowLayer(s * 0.03f, 0f, 0f, cy0)
p.color = Color.argb((120 + 135 * pulse).toInt(), 0, 229, 255)
cv.drawCircle(cx, cy - 0.29f * s, s * 0.02f, p)
p.clearShadowLayer()
box(cx - 0.24f * s, cy - 0.2f * s, cx + 0.24f * s, cy + 0.16f * s, 0.07f * s, dark, cy0, s * 0.01f, s * 0.03f)
val blink = if (t % 3.5f > 3.35f) 0.12f else 1f
val ec = if (on) cy0 else dim
for (sx in floatArrayOf(-1f, 1f)) {
val ex = cx + sx * 0.1f * s
val eh = 0.035f * s * blink
box(ex - 0.05f * s, cy - 0.06f * s - eh, ex + 0.05f * s, cy - 0.06f * s + eh, 0.02f * s, ec, ec, 0f, if (on) s * 0.04f else 0f)
}
for (i in 0 until 5) {
val bx = cx - 0.09f * s + i * 0.045f * s
val bh = 0.01f * s + (if (on) 0.025f * s * Math.abs(Math.sin(t * 6.0 + i)).toFloat() else 0f)
box(bx - 0.012f * s, cy + 0.07f * s - bh, bx + 0.012f * s, cy + 0.07f * s + bh, 0.005f * s, ec, ec, 0f, 0f)
}
val sy = cy - 0.2f * s + ((t * 0.5f) % 1f) * 0.36f * s
p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.004f; p.color = Color.argb(70, 0, 229, 255)
cv.drawLine(cx - 0.22f * s, sy, cx + 0.22f * s, sy, p)
postInvalidateOnAnimation()
}
}
object RoboSound {
fun boot() {
Thread {
try {
val sr = 22050
val seg = ArrayList<Short>()
fun tone(f0: Double, f1: Double, ms: Int, vol: Double) {
val n = sr * ms / 1000
var ph = 0.0
for (i in 0 until n) {
val f = f0 + (f1 - f0) * i / n
ph += 2 * Math.PI * f / sr
val env = minOf(1.0, i / 200.0, (n - i) / 400.0)
val v = Math.signum(Math.sin(ph)) * 0.25 + Math.sin(ph) * 0.75
seg.add((v * env * vol * 20000).toInt().toShort())
}
}
fun gap(ms: Int) { repeat(sr * ms / 1000) { seg.add(0) } }
tone(180.0, 900.0, 450, 0.5); gap(60)
tone(1200.0, 1200.0, 70, 0.4); gap(50)
tone(1500.0, 1500.0, 70, 0.4); gap(50)
tone(1900.0, 1900.0, 140, 0.4)
val arr = ShortArray(seg.size) { seg[it] }
val at = AudioTrack.Builder()
.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
.setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
.setBufferSizeInBytes(arr.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
at.write(arr, 0, arr.size)
at.play()
Thread.sleep(arr.size * 1000L / sr + 300)
at.release()
} catch (e: Exception) { }
}.start()
}
}

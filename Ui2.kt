package com.vaibhav.jarvis

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

/** Voice waveform bars. Moves with the microphone while listening and with a synthetic wave while thinking / speaking. */
class WaveView(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var g: LinearGradient? = null
    private var lv = 0f
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        g = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0xFF2FD0FF.toInt(), 0xFF4F7CFF.toInt(), 0xFFB05BFF.toInt(), 0xFF4F7CFF.toInt(), 0xFF2FD0FF.toInt()), null, Shader.TileMode.CLAMP)
    }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val now = System.currentTimeMillis()
        val t = (now - t0) / 1000f
        val st = JarvisService.orbState()
        val mic = if (now - JarvisService.orbLevelAt < 400) JarvisService.orbLevel else 0f
        val target = when (st) { 1 -> 0.12f + mic * 0.88f; 3 -> 0.35f + 0.65f * abs(sin(t * 9f) * sin(t * 5.3f + 1f)); 2 -> 0.28f; else -> 0.06f }
        lv += (target - lv) * 0.2f
        val n = 41
        val gap = w / n
        p.reset(); p.isAntiAlias = true; p.shader = g
        for (i in 0 until n) {
            val x = (i + 0.5f) * gap
            val d = (i - n / 2f) / (n / 2f)
            val env = (1f - d * d).coerceIn(0.08f, 1f)
            val ph = if (st == 2) sin(t * 3f - i * 0.35f) * 0.5f + 0.5f else abs(sin(t * 7f + i * 0.9f) * sin(t * 3.1f + i * 0.37f))
            val bh = (h * 0.12f + h * 0.88f * env * lv * (0.35f + 0.65f * ph)).coerceAtMost(h)
            r.set(x - gap * 0.22f, h / 2f - bh / 2f, x + gap * 0.22f, h / 2f + bh / 2f)
            cv.drawRoundRect(r, gap, gap, p)
        }
        postInvalidateOnAnimation()
    }
}

/** Round microphone button. Pulses while listening. */
class MicBtn(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var ring: SweepGradient? = null
    @Volatile var on = false
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ring = SweepGradient(w / 2f, h / 2f, intArrayOf(0xFF3D7BFF.toInt(), 0xFFB05BFF.toInt(), 0xFF2FA8FF.toInt(), 0xFF3D7BFF.toInt()), null)
    }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f
        val t = (System.currentTimeMillis() - t0) / 1000f
        val st = if (on) JarvisService.orbState() else 0
        val R = s * 0.36f
        val pulse = if (st == 1) 1f + 0.06f * sin(t * 6f) else 1f
        p.reset(); p.isAntiAlias = true; p.color = 0x223D7BFF
        cv.drawCircle(cx, cy, R * 1.28f * pulse, p)
        p.reset(); p.isAntiAlias = true; p.color = 0xFF080B26.toInt()
        cv.drawCircle(cx, cy, R, p)
        val m = Matrix(); m.setRotate(t * 40f, cx, cy); ring?.setLocalMatrix(m)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.035f; p.shader = ring
        p.setShadowLayer(s * 0.05f, 0f, 0f, 0xFF5B7CFF.toInt())
        cv.drawCircle(cx, cy, R, p)
        p.clearShadowLayer()
        val col = if (on) 0xFFFFFFFF.toInt() else 0xFF8A8FB5.toInt()
        p.reset(); p.isAntiAlias = true; p.color = col
        r.set(cx - s * 0.06f, cy - s * 0.17f, cx + s * 0.06f, cy + s * 0.05f)
        cv.drawRoundRect(r, s * 0.06f, s * 0.06f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.028f; p.strokeCap = Paint.Cap.ROUND
        r.set(cx - s * 0.115f, cy - s * 0.11f, cx + s * 0.115f, cy + s * 0.11f)
        cv.drawArc(r, 20f, 140f, false, p)
        cv.drawLine(cx, cy + s * 0.11f, cx, cy + s * 0.17f, p)
        cv.drawLine(cx - s * 0.05f, cy + s * 0.17f, cx + s * 0.05f, cy + s * 0.17f, p)
        postInvalidateOnAnimation()
    }
}

/** Always-on-top floating orb with a small peek card showing what Jarvis heard and answered. */
object FloatOrb {
    private var root: LinearLayout? = null
    private var card: TextView? = null
    private var wm: WindowManager? = null
    private var lp: WindowManager.LayoutParams? = null
    private var lastActive = 0L

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun update(c: Context, want: Boolean) {
        try {
            if (want && Settings.canDrawOverlays(c)) { if (root == null) show(c); refresh() } else hide()
        } catch (e: Exception) { JarvisService.lg("orb: " + e) }
    }

    private fun show(c: Context) {
        val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = w
        val box = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val bot = BotView(c)
        box.addView(bot, LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)))
        val tv = TextView(c).apply {
            setTextColor(Color.WHITE); textSize = 12f; maxLines = 4; visibility = View.GONE
            setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8))
            background = GradientDrawable().apply { setColor(0xEE0B1038.toInt()); setStroke(dp(c, 1), 0xFF3D4BA0.toInt()); cornerRadius = dp(c, 16).toFloat() }
        }
        box.addView(tv, LinearLayout.LayoutParams(dp(c, 200), -2))
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val l = WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        l.gravity = Gravity.TOP or Gravity.START
        val sp = c.getSharedPreferences("j", Context.MODE_PRIVATE)
        l.x = sp.getInt("orbx", dp(c, 8)); l.y = sp.getInt("orby", dp(c, 200))
val dm = c.resources.displayMetrics
fun clampPos() { l.x = l.x.coerceIn(0, max(0, dm.widthPixels - dp(c, 72))); l.y = l.y.coerceIn(dp(c, 40), max(dp(c, 40), dm.heightPixels - dp(c, 160))) }
clampPos()
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        box.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = l.x; sy = l.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - dx) > 12 || abs(e.rawY - dy) > 12) moved = true
                    if (moved) { l.x = sx + (e.rawX - dx).toInt(); l.y = sy + (e.rawY - dy).toInt(); clampPos(); try { w.updateViewLayout(box, l) } catch (x: Exception) { } }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) sp.edit().putInt("orbx", l.x).putInt("orby", l.y).apply()
                    else { try { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (x: Exception) { } }
                }
            }
            true
        }
        w.addView(box, l)
        root = box; card = tv; lp = l
    }

    private fun refresh() {
        val tv = card ?: return
        val st = JarvisService.orbState()
        val now = System.currentTimeMillis()
        if (st != 0) lastActive = now
        val txt = when (st) {
            1 -> "Listening…"
            2 -> JarvisService.lastQ.ifEmpty { "Thinking…" }
            3 -> JarvisService.lastA
            else -> JarvisService.lastA
        }
        val showIt = (st != 0 || now - lastActive < 4000) && txt.isNotEmpty()
        tv.visibility = if (showIt) View.VISIBLE else View.GONE
        if (showIt && tv.text.toString() != txt) tv.text = txt
    }

    fun hide() {
        try { root?.let { wm?.removeView(it) } } catch (e: Exception) { }
        root = null; card = null
    }
}

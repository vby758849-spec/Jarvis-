package com.vaibhav.jarvis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * Siri-style ring: while Jarvis is speaking, a glowing golden ring fades in at the centre of the screen,
 * pulses with the voice, keeps turning round, and throws sparkles. It never takes touches.
 *
 * Needs the picture res/drawable-nodpi/ring.png (copied by the build from ring.png in the repo).
 * If the picture is missing it draws a simple golden ring instead, so the app never breaks.
 * Turn off with the "ring" preference (default on).
 */
object SpeakRing {
    private var view: RingView? = null
    private var wm: WindowManager? = null
    private var ctx: Context? = null
    private var polling = false
    private val h = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            val c = ctx
            if (c != null) { try { step(c) } catch (e: Exception) { JarvisService.lg("ring: " + e) } }
            h.postDelayed(this, 120)
        }
    }

    /** Call regularly from the service. The first call starts a fast (120 ms) loop of its own. */
    fun tick(c: Context) {
        ctx = c
        if (!polling) { polling = true; h.post(poll) }
    }

    private fun step(c: Context) {
        val on = JarvisService.orbSpeak && Settings.canDrawOverlays(c) &&
            c.getSharedPreferences("j", Context.MODE_PRIVATE).getBoolean("ring", true)
        val v = view
        if (on) { if (v == null) show(c) else v.active = true }
        else if (v != null) v.active = false
    }

    private fun show(c: Context) {
        val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = c.resources.displayMetrics
        val size = (min(dm.widthPixels, dm.heightPixels) * 1.05f).toInt()
        val v = RingView(c) { hide() }
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        lp.y = (dm.heightPixels * 0.10f).toInt()
        w.addView(v, lp)
        wm = w; view = v
        JarvisService.lg("ring: dikha")
    }

    private fun hide() {
        try { view?.let { wm?.removeView(it) } } catch (e: Exception) { }
        view = null
    }

    private class RingView(c: Context, val onGone: () -> Unit) : View(c) {
        @Volatile var active = true
        private val bmp: Bitmap? = try {
            val id = c.resources.getIdentifier("ring", "drawable", c.packageName)
            if (id != 0) BitmapFactory.decodeResource(c.resources, id) else null
        } catch (e: Exception) { null }
        private val dens = c.resources.displayMetrics.density
        private val img = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flash = Paint(Paint.ANTI_ALIAS_FLAG)
        private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 1.5f * dens }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFC107.toInt() }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D8BFF.toInt() }
        private val dst = RectF()
        private var last = SystemClock.uptimeMillis()
        private var t = 0f
        private var appear = 0f
        private var lvl = 0f
        private var sinceOn = 0f
        private var gone = false

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val r = w * 0.33f
            glow.shader = RadialGradient(w / 2f, h / 2f, r * 1.55f,
                intArrayOf(0xCCFFB300.toInt(), 0x66FF8F00.toInt(), 0x00FF6F00), floatArrayOf(0.5f, 0.78f, 1f), Shader.TileMode.CLAMP)
            flash.shader = RadialGradient(w / 2f, h / 2f, r * 1.2f,
                intArrayOf(0xFFFFFFFF.toInt(), 0x99FFE082.toInt(), 0x00FFE082), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }

        private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val u = x - 1f; return 1f + c3 * u * u * u + c1 * u * u }

        override fun onDraw(canvas: Canvas) {
            if (gone) return
            val now = SystemClock.uptimeMillis()
            val dt = min(50L, now - last) / 1000f
            last = now; t += dt
            if (active) { appear = min(1f, appear + dt / 0.35f); sinceOn += dt }
            else { appear = max(0f, appear - dt / 0.40f); sinceOn = 0f }
            if (!active && appear <= 0f) { gone = true; post { onGone() }; return }

            // voice level: real mic level if there is one, otherwise a speech-like pulse
            val mic = if (now - JarvisService.orbLevelAt < 400) JarvisService.orbLevel else 0f
            val sim = 0.5f + 0.5f * (sin(t * 9.3f) * 0.6f + sin(t * 5.1f + 1.3f) * 0.4f)
            val target = if (active) max(mic, sim * 0.85f) else 0f
            lvl += (target - lvl) * min(1f, dt * 10f)

            val cx = width / 2f; val cy = height / 2f; val r = width * 0.33f
            val sc = (0.55f + 0.45f * easeOutBack(appear)) * (1f + 0.07f * lvl)
            val spin = (t * 40f) % 360f // full turn every 9 seconds, same picture, just rotating
            val a = appear.coerceIn(0f, 1f)

            // soft golden glow that breathes with the voice
            canvas.save(); canvas.scale(sc, sc, cx, cy)
            glow.alpha = (255f * (0.30f + 0.70f * lvl) * a).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * 1.55f, glow)
            canvas.restore()

            // the ring itself: the picture exactly as it is, turning round and round
            if (bmp != null) {
                canvas.save(); canvas.scale(sc, sc, cx, cy); canvas.rotate(spin, cx, cy)
                dst.set(cx - r, cy - r, cx + r, cy + r)
                img.alpha = (255f * a).toInt().coerceIn(0, 255)
                canvas.drawBitmap(bmp, null, dst, img)
                canvas.restore()
            } else {
                canvas.save(); canvas.scale(sc, sc, cx, cy); canvas.rotate(spin, cx, cy)
                edge.alpha = (255f * a).toInt().coerceIn(0, 255); core.alpha = edge.alpha
                edge.strokeWidth = r * 0.07f; canvas.drawCircle(cx, cy, r * 0.95f, edge)
                edge.strokeWidth = r * 0.03f; canvas.drawCircle(cx, cy, r * 0.62f, edge)
                canvas.drawCircle(cx, cy, r * (0.22f + 0.04f * lvl), core)
                canvas.restore()
            }

            // sparkles orbiting the rim
            for (i in 0 until 6) {
                val ang = t * (0.8f + 0.1f * i) + i * 1.0472f
                val tw = max(0f, sin(t * (2.5f + i * 0.7f) + i * 2f)).pow(6f)
                if (tw < 0.05f) continue
                val px = cx + cos(ang) * r * sc * 0.97f; val py = cy + sin(ang) * r * sc * 0.97f
                val len = (4f + 9f * tw) * dens
                spark.alpha = (255f * tw * a).toInt().coerceIn(0, 255)
                canvas.drawCircle(px, py, (1.5f + 1.8f * tw) * dens, spark)
                canvas.drawLine(px - len, py, px + len, py, spark)
                canvas.drawLine(px, py - len, px, py + len, spark)
            }

            // one bright flash when it appears
            if (sinceOn < 0.5f) {
                flash.alpha = (255f * 0.75f * (1f - sinceOn / 0.5f)).toInt().coerceIn(0, 255)
                canvas.save(); canvas.scale(sc, sc, cx, cy); canvas.drawCircle(cx, cy, r * 1.2f, flash); canvas.restore()
            }
            postInvalidateOnAnimation()
        }
    }
}

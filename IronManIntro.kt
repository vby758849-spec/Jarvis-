package com.vaibhav.jarvis

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Iron Man urta hua door se screen ki taraf aata hai, flash hota hai,
 * phir JARVIS HUD dikhta hai aur overlay fade out ho jata hai.
 */
class IronManIntroView(context: Context, private val onDone: () -> Unit) : View(context) {

    private var t = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rnd = Random(7)
    private val streaks = List(80) { floatArrayOf(rnd.nextFloat() * 6.2831f, rnd.nextFloat()) }

    private val red = Color.parseColor("#B71C1C")
    private val darkRed = Color.parseColor("#7F0000")
    private val gold = Color.parseColor("#FFC107")
    private val cyan = Color.parseColor("#00E5FF")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onDone() }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.removeAllListeners()
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val bgAlpha = if (t < 0.85f) 255 else (255 * (1f - (t - 0.85f) / 0.15f)).toInt().coerceIn(0, 255)
        c.drawColor(Color.argb(bgAlpha, 3, 8, 16))

        // speed streaks (tez hawa jaisi lines)
        if (t < 0.78f) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            val m = max(w, h)
            for (s in streaks) {
                val z = (s[1] + t * 1.6f) % 1f
                val r0 = z * z * m
                val z1 = z + 0.06f
                val r1 = z1 * z1 * m
                paint.color = Color.argb((z * 150).toInt(), 0, 229, 255)
                paint.strokeWidth = 1f + 3f * z
                c.drawLine(cx + cos(s[0]) * r0, cy + sin(s[0]) * r0, cx + cos(s[0]) * r1, cy + sin(s[0]) * r1, paint)
            }
        }

        // Iron Man door se paas aata hua
        val f = (t / 0.7f).coerceIn(0f, 1f)
        if (t < 0.74f) {
            val sc = w * (0.03f + 1.1f * f * f * f)
            c.save()
            c.translate(cx, cy + (1f - f) * h * 0.1f)
            c.rotate(sin(t * 9f) * 4f * (1f - f))
            c.scale(sc, sc)
            drawSuit(c)
            c.restore()
        }

        // flash jab screen se takrata hai
        if (t in 0.62f..0.80f) {
            val a = (1f - abs(t - 0.70f) / 0.09f).coerceIn(0f, 1f)
            c.drawColor(Color.argb((a * 255).toInt(), 255, 255, 255))
        }

        // JARVIS HUD
        if (t >= 0.72f) {
            val hud = ((t - 0.72f) / 0.1f).coerceIn(0f, 1f) * (bgAlpha / 255f)
            val a = (hud * 255).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.shader = null
            for (i in 0..2) {
                val r = w * (0.22f + i * 0.07f)
                paint.color = Color.argb(a, 0, 229, 255)
                paint.strokeWidth = 4f - i
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                val dir = if (i % 2 == 0) 1 else -1
                c.drawArc(rect, dir * t * 720f + i * 60f, 100f, false, paint)
                c.drawArc(rect, dir * t * 720f + i * 60f + 180f, 60f, false, paint)
            }
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.argb(a, 0, 229, 255)
            paint.textSize = w * 0.085f
            c.drawText("J.A.R.V.I.S", cx, cy + w * 0.03f, paint)
            paint.textSize = w * 0.04f
            c.drawText("SYSTEMS ONLINE", cx, cy + w * 0.11f, paint)
        }
    }

    private fun drawSuit(c: Canvas) {
        paint.shader = null
        paint.style = Paint.Style.FILL

        // pair ke neeche thruster glow
        paint.shader = RadialGradient(0f, 0.95f, 0.5f, Color.argb(210, 0, 229, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0.95f, 0.5f, paint)
        paint.shader = null

        // legs
        paint.color = red
        c.drawRoundRect(-0.20f, 0.40f, -0.03f, 1.00f, 0.06f, 0.06f, paint)
        c.drawRoundRect(0.03f, 0.40f, 0.20f, 1.00f, 0.06f, 0.06f, paint)
        paint.color = gold
        c.drawRoundRect(-0.20f, 0.70f, -0.03f, 0.78f, 0.03f, 0.03f, paint)
        c.drawRoundRect(0.03f, 0.70f, 0.20f, 0.78f, 0.03f, 0.03f, paint)

        // arms
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.15f
        paint.color = red
        c.drawLine(-0.36f, -0.30f, -0.80f, 0.05f, paint)
        c.drawLine(0.36f, -0.30f, 0.80f, 0.05f, paint)
        paint.strokeWidth = 0.05f
        paint.color = gold
        c.drawLine(-0.50f, -0.19f, -0.62f, -0.09f, paint)
        c.drawLine(0.50f, -0.19f, 0.62f, -0.09f, paint)

        // hands (repulsor glow)
        paint.style = Paint.Style.FILL
        for (sx in floatArrayOf(-1f, 1f)) {
            paint.shader = RadialGradient(sx * 0.82f, 0.07f, 0.17f, Color.argb(230, 0, 229, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(sx * 0.82f, 0.07f, 0.17f, paint)
            paint.shader = null
            paint.color = gold
            c.drawCircle(sx * 0.82f, 0.07f, 0.07f, paint)
        }

        // torso
        paint.color = red
        path.reset()
        path.moveTo(-0.40f, -0.35f); path.lineTo(0.40f, -0.35f)
        path.lineTo(0.22f, 0.45f); path.lineTo(-0.22f, 0.45f); path.close()
        c.drawPath(path, paint)

        // gold waist plate
        paint.color = gold
        path.reset()
        path.moveTo(-0.19f, 0.16f); path.lineTo(0.19f, 0.16f)
        path.lineTo(0.15f, 0.43f); path.lineTo(-0.15f, 0.43f); path.close()
        c.drawPath(path, paint)

        // shoulders
        c.drawCircle(-0.40f, -0.33f, 0.10f, paint)
        c.drawCircle(0.40f, -0.33f, 0.10f, paint)

        // arc reactor
        paint.shader = RadialGradient(0f, -0.12f, 0.13f, intArrayOf(Color.WHITE, cyan, Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, -0.12f, 0.13f, paint)
        paint.shader = null

        // head
        paint.color = red
        c.drawCircle(0f, -0.55f, 0.23f, paint)
        paint.color = gold
        path.reset()
        path.moveTo(-0.15f, -0.68f); path.lineTo(0.15f, -0.68f)
        path.lineTo(0.12f, -0.42f); path.lineTo(0f, -0.35f); path.lineTo(-0.12f, -0.42f); path.close()
        c.drawPath(path, paint)

        // glowing eyes
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.035f
        paint.color = Color.WHITE
        c.drawLine(-0.12f, -0.57f, -0.04f, -0.55f, paint)
        c.drawLine(0.12f, -0.57f, 0.04f, -0.55f, paint)
        paint.style = Paint.Style.FILL
    }
}

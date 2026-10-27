package com.vaibhav.jarvis

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Friendly white robot inside a glowing ring. Blinks, smiles, and talks (mouth + glow) when Jarvis speaks. */
class BotView(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val clip = Path()
    private val mx = Matrix()
    @Volatile var on = false
    private var lv = 0f
    private var ringG: SweepGradient? = null
    private var bgG: RadialGradient? = null
    private var headG: RadialGradient? = null
    private var bodyG: LinearGradient? = null
    private var visG: LinearGradient? = null
    private var earG: LinearGradient? = null

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = min(w, h).toFloat(); val cx = w / 2f; val cy = h / 2f
        ringG = SweepGradient(cx, cy, intArrayOf(0xFF4F8CFF.toInt(), 0xFFA06BFF.toInt(), 0xFF6A5BFF.toInt(), 0xFF4F8CFF.toInt()), null)
        bgG = RadialGradient(cx, cy - s * 0.1f, s * 0.5f, intArrayOf(0xFF121C4A.toInt(), 0xFF070A22.toInt(), 0xFF04061A.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        headG = RadialGradient(cx - s * 0.1f, cy - s * 0.16f, s * 0.42f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6E9F2.toInt(), 0xFFB4BACB.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        bodyG = LinearGradient(0f, cy + s * 0.27f, 0f, cy + s * 0.5f, 0xFFF2F4FA.toInt(), 0xFF9AA1B5.toInt(), Shader.TileMode.CLAMP)
        visG = LinearGradient(0f, cy - s * 0.17f, 0f, cy + s * 0.12f, 0xFF10142A.toInt(), 0xFF010208.toInt(), Shader.TileMode.CLAMP)
        earG = LinearGradient(0f, cy - s * 0.1f, 0f, cy + s * 0.07f, 0xFFD5D9E6.toInt(), 0xFF8A91A6.toInt(), Shader.TileMode.CLAMP)
        clip.reset(); clip.addCircle(cx, cy, s * 0.46f - 1f, Path.Direction.CW)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f
        val now = System.currentTimeMillis()
        val t = (now - t0) / 1000f
        val speaking = JarvisService.orbSpeak
        val mic = if (now - JarvisService.orbLevelAt < 400) JarvisService.orbLevel else 0f
        val target = if (speaking) 0.35f + 0.65f * abs(sin(t * 10f) * sin(t * 6.1f + 1f)) else mic * 0.6f
        lv += (target - lv) * 0.22f
        val R = s * 0.46f
        val eye = 0xFF39B8FF.toInt()

        // dark disc
        p.reset(); p.isAntiAlias = true; p.shader = bgG
        cv.drawCircle(cx, cy, R, p)

        cv.save()
        cv.clipPath(clip)
        cv.translate(0f, sin(t * 1.6f) * s * 0.006f)
        cv.rotate(sin(t * 6.5f) * lv * 1.6f, cx, cy + s * 0.2f)

        // shoulders + neck
        p.reset(); p.isAntiAlias = true; p.shader = bodyG
        r.set(cx - s * 0.30f, cy + s * 0.27f, cx + s * 0.30f, cy + s * 0.62f); cv.drawRoundRect(r, s * 0.14f, s * 0.14f, p)
        p.reset(); p.isAntiAlias = true; p.color = 0xFF23263A.toInt()
        r.set(cx - s * 0.085f, cy + s * 0.19f, cx + s * 0.085f, cy + s * 0.30f); cv.drawRoundRect(r, s * 0.03f, s * 0.03f, p)

        // ears
        p.reset(); p.isAntiAlias = true; p.shader = earG
        r.set(cx - s * 0.285f, cy - s * 0.1f, cx - s * 0.215f, cy + s * 0.07f); cv.drawRoundRect(r, s * 0.035f, s * 0.035f, p)
        r.set(cx + s * 0.215f, cy - s * 0.1f, cx + s * 0.285f, cy + s * 0.07f); cv.drawRoundRect(r, s * 0.035f, s * 0.035f, p)

        // head
        p.reset(); p.isAntiAlias = true; p.shader = headG
        r.set(cx - s * 0.25f, cy - s * 0.215f, cx + s * 0.25f, cy + s * 0.17f); cv.drawRoundRect(r, s * 0.19f, s * 0.19f, p)

        // visor
        p.reset(); p.isAntiAlias = true; p.shader = visG
        r.set(cx - s * 0.2f, cy - s * 0.17f, cx + s * 0.2f, cy + s * 0.12f); cv.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
        p.reset(); p.isAntiAlias = true; p.color = 0x22FFFFFF
        r.set(cx - s * 0.17f, cy - s * 0.155f, cx + s * 0.02f, cy - s * 0.105f); cv.drawRoundRect(r, s * 0.03f, s * 0.03f, p)

        // eyes (glow, blink)
        val blink = (t % 3.6f) < 0.14f
        val eh = if (blink) s * 0.006f else s * 0.03f * (1f + 0.15f * lv)
        p.reset(); p.isAntiAlias = true; p.color = eye
        p.setShadowLayer(s * (0.035f + 0.03f * lv), 0f, 0f, eye)
        for (sx in floatArrayOf(-1f, 1f)) {
            r.set(cx + sx * s * 0.085f - s * 0.03f, cy - s * 0.05f - eh, cx + sx * s * 0.085f + s * 0.03f, cy - s * 0.05f + eh)
            cv.drawOval(r, p)
        }

        // mouth: smile when quiet, opens and closes when speaking
        if (lv > 0.12f) {
            val mh = s * (0.01f + 0.05f * lv)
            r.set(cx - s * 0.035f, cy + s * 0.04f - mh / 2f, cx + s * 0.035f, cy + s * 0.04f + mh / 2f)
            cv.drawOval(r, p)
        } else {
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.012f; p.strokeCap = Paint.Cap.ROUND
            r.set(cx - s * 0.04f, cy + s * 0.005f, cx + s * 0.04f, cy + s * 0.06f)
            cv.drawArc(r, 25f, 130f, false, p)
        }
        p.clearShadowLayer()
        cv.restore()

        // glowing ring
        mx.setRotate(t * 22f, cx, cy); ringG?.setLocalMatrix(mx)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.014f; p.shader = ringG
        p.setShadowLayer(s * (0.03f + 0.05f * lv), 0f, 0f, 0xFF5B7CFF.toInt())
        cv.drawCircle(cx, cy, R, p)
        p.clearShadowLayer()
        postInvalidateOnAnimation()
    }
}

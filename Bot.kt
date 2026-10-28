package com.vaibhav.jarvis

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Dark helmet robot with headphones inside a glowing ring. Reacts to Jarvis state: idle, listening, thinking, speaking. */
class BotView(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val mx = Matrix()
    @Volatile var on = true
    private var lv = 0f
    private var ringG: SweepGradient? = null
    private var sphG: RadialGradient? = null
    private var glowG: RadialGradient? = null
    private var faceG: RadialGradient? = null
    private var cA = 0xFF3D7BFF.toInt()
    private var cB = 0xFF8A5BFF.toInt()
    private var cE = 0xFF39B8FF.toInt()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private fun mix(a: Int, b: Int, f: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = min(w, h).toFloat(); val cx = w / 2f; val cy = h / 2f
        val R = s * 0.34f
        sphG = RadialGradient(cx - R * 0.25f, cy - R * 0.35f, R * 1.25f, intArrayOf(0xFF26337F.toInt(), 0xFF0B1038.toInt(), 0xFF03051A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        faceG = RadialGradient(cx, cy - R * 0.1f, R * 0.85f, intArrayOf(0xFF05071C.toInt(), 0xFF02030F.toInt()), null, Shader.TileMode.CLAMP)
        glowG = RadialGradient(cx, cy, s * 0.5f, intArrayOf(0x553D7BFF, 0x00000000), null, Shader.TileMode.CLAMP)
        ringG = SweepGradient(cx, cy, intArrayOf(0xFF3D7BFF.toInt(), 0xFF8A5BFF.toInt(), 0xFF2FA8FF.toInt(), 0xFF3D7BFF.toInt()), null)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f
        val now = System.currentTimeMillis()
        val t = (now - t0) / 1000f
        val st = if (on) JarvisService.orbState() else 0
        val speaking = st == 3
        val mic = if (st == 1 && now - JarvisService.orbLevelAt < 400) JarvisService.orbLevel else 0f
        val target = if (speaking) 0.35f + 0.65f * abs(sin(t * 10f) * sin(t * 6.1f + 1f)) else mic * 0.7f
        lv += (target - lv) * 0.22f
        val tA = when (st) { 1 -> 0xFF2FA8FF.toInt(); 2 -> 0xFF7B4DFF.toInt(); 3 -> 0xFFA055FF.toInt(); else -> 0xFF3D7BFF.toInt() }
        val tB = when (st) { 1 -> 0xFF6A5BFF.toInt(); 2 -> 0xFFB04DFF.toInt(); 3 -> 0xFF5B7CFF.toInt(); else -> 0xFF8A5BFF.toInt() }
        val tE = when (st) { 1 -> 0xFF39D0FF.toInt(); 2 -> 0xFF3D8BFF.toInt(); 3 -> 0xFF4FB8FF.toInt(); else -> 0xFF39B8FF.toInt() }
        cA = mix(cA, tA, 0.08f); cB = mix(cB, tB, 0.08f); cE = mix(cE, tE, 0.1f)
        val R = s * 0.34f
        val fy = cy + sin(t * 1.6f) * s * 0.008f
        val a = if (on) 255 else 120

        // soft glow
        p.reset(); p.isAntiAlias = true; p.shader = glowG; p.alpha = a
        cv.drawCircle(cx, cy, s * 0.5f, p)

        // listening ripples
        if (st == 1) for (i in 0 until 3) {
            val f = ((t * 0.7f) + i / 3f) % 1f
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.008f
            p.color = cE; p.alpha = ((1f - f) * 110).toInt()
            cv.drawCircle(cx, fy, R * (1.15f + f * 0.55f), p)
        }

        // headphones
        for (sx in floatArrayOf(-1f, 1f)) {
            val ex = cx + sx * R * 1.02f
            r.set(ex - s * 0.035f, fy - R * 0.32f, ex + s * 0.035f, fy + R * 0.32f)
            p.reset(); p.isAntiAlias = true; p.color = 0xFF0B1038.toInt(); p.alpha = a
            cv.drawRoundRect(r, s * 0.04f, s * 0.04f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.01f; p.color = cA; p.alpha = a
            p.setShadowLayer(s * 0.02f, 0f, 0f, cA)
            cv.drawRoundRect(r, s * 0.04f, s * 0.04f, p)
        }

        // sphere
        p.reset(); p.isAntiAlias = true; p.shader = sphG; p.alpha = a
        cv.drawCircle(cx, fy, R, p)

        // glowing ring
        mx.setRotate(t * (if (st == 2) 70f else 22f), cx, fy); ringG?.setLocalMatrix(mx)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.022f; p.shader = ringG; p.alpha = a
        p.setShadowLayer(s * (0.03f + 0.04f * lv), 0f, 0f, cA)
        cv.drawCircle(cx, fy, R, p)
        p.clearShadowLayer()

        // inner face
        p.reset(); p.isAntiAlias = true; p.shader = faceG; p.alpha = a
        cv.drawCircle(cx, fy, R * 0.8f, p)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.004f; p.color = 0x44FFFFFF
        cv.drawCircle(cx, fy, R * 0.8f, p)

        // eyes
        val blink = (t % 3.8f) < 0.14f
        p.reset(); p.isAntiAlias = true; p.color = cE; p.alpha = a
        p.setShadowLayer(s * (0.03f + 0.03f * lv), 0f, 0f, cE)
        val ey = fy - R * 0.04f
        val ed = R * 0.36f
        val round = st == 2 || st == 3
        for (sx in floatArrayOf(-1f, 1f)) {
            val ex = cx + sx * ed
            if (round) {
                val eh = if (blink) s * 0.006f else s * 0.034f * (1f + 0.35f * lv)
                r.set(ex - s * 0.024f, ey - eh, ex + s * 0.024f, ey + eh)
                p.style = Paint.Style.FILL
                cv.drawOval(r, p)
            } else {
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.026f; p.strokeCap = Paint.Cap.ROUND
                if (blink) cv.drawLine(ex - s * 0.04f, ey, ex + s * 0.04f, ey, p)
                else { r.set(ex - s * 0.045f, ey - s * 0.03f, ex + s * 0.045f, ey + s * 0.045f); cv.drawArc(r, 200f, 140f, false, p) }
            }
        }
        p.clearShadowLayer()
        postInvalidateOnAnimation()
    }
}

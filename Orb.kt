package com.vaibhav.jarvis

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Majin intro: background + glowing logo + MAJIN letters + loading bar. Images: hud.jpg (bg) and blueprint.jpg (logo). */
class MajinIntro(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bg: Bitmap? = load("hud")
    private var lg: Bitmap? = load("blueprint")
    private val rnd = java.util.Random(7)
    private val dx = FloatArray(40) { rnd.nextFloat() }
    private val dy = FloatArray(40) { rnd.nextFloat() }
    private val ds = FloatArray(40) { 0.02f + rnd.nextFloat() * 0.05f }
    private val letters = "MAJIN"
    private var orb1: RadialGradient? = null
    private var orb2: RadialGradient? = null
    private var flareG: RadialGradient? = null
    private var barG: LinearGradient? = null
    private var txtG: LinearGradient? = null
    private var barL = 0f
    private var barW = 0f

    private fun load(n: String): Bitmap? = try {
        val id = resources.getIdentifier(n, "drawable", context.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(resources, id)
    } catch (e: Throwable) { null }

    private fun cl(x: Float) = max(0f, min(1f, x))

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val u = min(h / 1672f, w / 941f)
        val R = 45f * u
        orb1 = RadialGradient(0f, 0f, R, intArrayOf(0xFFFFB060.toInt(), 0x887A4DFF.toInt(), 0x00000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        orb2 = RadialGradient(0f, 0f, R, intArrayOf(0xFF9AB0FF.toInt(), 0x88B06BFF.toInt(), 0x00000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        flareG = RadialGradient(0f, 0f, 95f * u, intArrayOf(0xFFFF8A1A.toInt(), 0x00FF8A1A), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        barW = 353f * u; barL = (w - barW) / 2f
        barG = LinearGradient(barL, 0f, barL + barW, 0f, intArrayOf(0xFFFFA31F.toInt(), 0xFFFF5A5A.toInt(), 0xFF8A4DFF.toInt(), 0xFF4A62FF.toInt()), floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        val top = h * (965f / 1672f)
        txtG = LinearGradient(0f, top, 0f, top + 80f * u, 0xFFFFFFFF.toInt(), 0xFFAAB6E6.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val u = min(h / 1672f, w / 941f)
        val t = (System.currentTimeMillis() - t0) / 1000f
        cv.drawColor(0xFF02030A.toInt())

        // background (centre-crop, slow drift)
        bg?.let { b ->
            val s = max(w / b.width, h / b.height) * (1f + 0.035f * (0.5f + 0.5f * sin(t * 0.45f)))
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            p.alpha = (255 * cl((t - 0.1f) / 1.2f)).toInt()
            cv.save(); cv.translate(w / 2, h / 2); cv.scale(s, s)
            cv.drawBitmap(b, -b.width / 2f, -b.height / 2f, p)
            cv.restore()
        }

        // dust
        p.reset(); p.isAntiAlias = true; p.color = 0xFF8FA0FF.toInt(); p.alpha = 70
        for (i in 0 until 40) {
            val y = ((dy[i] - t * ds[i]) % 1f + 1f) % 1f
            cv.drawCircle(dx[i] * w, y * h, (1.2f + ds[i] * 30f) * u, p)
        }

        // logo
        val e = 1f - (1f - cl(t / 2.2f)).pow(3)
        val cx = w / 2f; val cy = h * (625f / 1672f)
        lg?.let { b ->
            val lw = 640f * u; val lh = lw * b.height / b.width
            val flash = 0.9f * exp(-((t - 2.2f) * 3.5f).pow(2))
            val g = 1f + 0.06f * sin(t * 2.1f) + flash
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            p.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            p.alpha = (255 * cl(t / 0.5f)).toInt()
            val sx = max(0.06f, cos((1f - e) * 1.4f))
            val sz = 0.35f + 0.65f * e
            cv.save()
            cv.translate(cx, cy)
            cv.rotate((1f - e) * 20f + sin(t * 0.6f) * 1.5f)
            cv.scale(sz * sx, sz)
            cv.drawBitmap(b, null, RectF(-lw / 2, -lh / 2, lw / 2, lh / 2), p)
            cv.restore()
        }

        // orbs + flare
        if (e > 0.6f) {
            val k = (e - 0.6f) / 0.4f
            val orbs = arrayOf(orb1, orb2)
            val a0 = floatArrayOf(0.96f, 3.99f)
            p.reset(); p.isAntiAlias = true
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            for (i in 0..1) {
                val a = a0[i] + t * 0.6f
                p.shader = orbs[i]; p.alpha = (255 * k).toInt()
                cv.save(); cv.translate(cx + cos(a) * 314f * u, cy + sin(a) * 314f * u * 0.95f)
                cv.drawCircle(0f, 0f, 45f * u, p); cv.restore()
            }
            p.shader = flareG; p.alpha = (255 * (0.28f + 0.12f * sin(t * 2.6f)) * k).toInt()
            cv.save(); cv.translate(cx + 297f * u, cy - 50f * u); cv.drawCircle(0f, 0f, 95f * u, p); cv.restore()
        }

        // MAJIN letters
        p.reset(); p.isAntiAlias = true
        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        p.textSize = 80f * u * 1.28f
        p.shader = txtG
        val ws = FloatArray(5) { p.measureText(letters[it].toString()) }
        val gap = max(0f, (545f * u - ws.sum()) / 4f)
        var x = (w - (ws.sum() + gap * 4f)) / 2f
        val base = h * (965f / 1672f) + 80f * u
        for (i in 0 until 5) {
            val q = cl((t - (2.25f + i * 0.2f)) / 0.8f)
            if (q > 0f) {
                p.alpha = (255 * q).toInt()
                cv.save(); cv.clipRect(x - 4f * u, base - 100f * u, x + ws[i] * q + 2f * u, base + 20f * u)
                cv.drawText(letters[i].toString(), x, base, p)
                cv.restore()
            }
            x += ws[i] + gap
        }

        // tagline
        val ta = cl((t - 3.4f) / 1.4f)
        if (ta > 0f) {
            p.reset(); p.isAntiAlias = true; p.color = 0xFF8F99BA.toInt(); p.alpha = (255 * ta).toInt()
            p.textSize = 19f * u; p.letterSpacing = 0.3f - 0.25f * (1f - ta); p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            cv.drawText("IDEAS  |  TOOLS  |  REAL IMPACT", w / 2f, h * (1085f / 1672f) + 19f * u, p)
        }

        // loading bar
        val pr = cl((t - 3.2f) / 4.5f); val ep = 1f - (1f - pr).pow(2.2f)
        val ba = cl((t - 3.1f) / 0.8f)
        if (ba > 0f) {
            val by = h * (1432f / 1672f); val bh = 15f * u
            p.reset(); p.isAntiAlias = true; p.color = 0xFF1A2036.toInt(); p.alpha = (255 * ba).toInt()
            cv.drawRoundRect(RectF(barL, by, barL + barW, by + bh), bh, bh, p)
            p.shader = barG; p.alpha = (255 * ba).toInt()
            cv.drawRoundRect(RectF(barL, by, barL + barW * ep, by + bh), bh, bh, p)
            p.reset(); p.isAntiAlias = true; p.color = 0xFFA9B2CF.toInt(); p.alpha = (255 * cl((t - 3.3f) / 0.8f)).toInt()
            p.textSize = 21f * u; p.letterSpacing = 0.14f; p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            cv.drawText(if (pr >= 1f) "Ready" else "Loading...", w / 2f, h * (1474f / 1672f) + 21f * u, p)
        }
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bg?.recycle(); lg?.recycle(); bg = null; lg = null
    }
}

/** Glowing orb: breathes while idle, moves with the voice when Jarvis speaks or you speak. */
class OrbView(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val mx = Matrix()
    private val rect = RectF()
    @Volatile var on = false
    private var lv = 0f
    private var sweep: SweepGradient? = null
    private var dark: RadialGradient? = null

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f; val cy = h / 2f; val r = min(w, h) * 0.30f
        sweep = SweepGradient(cx, cy, intArrayOf(0xFF4B5BFF.toInt(), 0xFFA06BFF.toInt(), 0xFFFF7AD9.toInt(), 0xFFFFFFFF.toInt(), 0xFF6FD0FF.toInt(), 0xFFFFA040.toInt(), 0xFF4B5BFF.toInt()), null)
        dark = RadialGradient(cx + r * 0.08f, cy + r * 0.05f, r * 0.85f, intArrayOf(0xFF04050B.toInt(), 0xF004050B.toInt(), 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val now = System.currentTimeMillis()
        val t = (now - t0) / 1000f
        val speaking = JarvisService.orbSpeak
        val mic = if (now - JarvisService.orbLevelAt < 400) JarvisService.orbLevel else 0f
        val target = if (speaking) 0.5f + 0.5f * abs(sin(t * 9f) * sin(t * 5.3f + 1f)) else mic * 0.8f
        lv += (target - lv) * 0.14f
        val r = min(w, h) * 0.30f * (1f + 0.05f * sin(t * 1.6f) + 0.10f * lv)

        // ripples
        if (lv > 0.05f) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.02f; p.color = 0xFF9FB0FF.toInt()
            for (i in 0..1) {
                val f = (t * 0.7f + i * 0.5f) % 1f
                p.alpha = (255 * (1f - f) * 0.35f * min(1f, lv * 2f)).toInt()
                cv.drawCircle(cx, cy, r * (1.1f + f * 0.45f), p)
            }
        }

        // blob outline
        path.reset()
        val n = 72
        val amp = 0.035f + 0.11f * lv
        for (i in 0..n) {
            val a = (2.0 * PI * i / n).toFloat()
            val wob = amp * (sin(a * 3f + t * 1.7f) + 0.7f * sin(a * 5f - t * 2.3f) + 0.5f * sin(a * 2f + t * 2.9f * (1f + lv)))
            val rr = r * (1f + wob)
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        // glowing fill
        mx.setRotate(t * 28f, cx, cy); sweep?.setLocalMatrix(mx)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.shader = sweep
        p.setShadowLayer(r * (0.35f + 0.3f * lv), 0f, 0f, 0xAA7A8CFF.toInt())
        cv.drawPath(path, p)
        p.clearShadowLayer()

        // dark centre
        cv.save(); cv.clipPath(path)
        p.reset(); p.isAntiAlias = true; p.shader = dark
        cv.drawRect(0f, 0f, w, h, p)
        cv.restore()

        // rim light
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, r * 0.012f); p.color = 0x66FFFFFF
        cv.drawPath(path, p)
        p.strokeWidth = r * 0.035f; p.color = 0xBBFFFFFF.toInt(); p.strokeCap = Paint.Cap.ROUND
        rect.set(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
        cv.drawArc(rect, 200f + t * 20f, 40f, false, p)
        postInvalidateOnAnimation()
    }
}

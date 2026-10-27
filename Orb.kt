package com.vaibhav.jarvis

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Majin intro, laid out like the design: stage 941 x 1672 units, scaled to cover the screen. */
class MajinIntro(c: Context) : View(c) {
    private var t0 = 0L
    private val tfL = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val scr = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val cmx = ColorMatrix()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bg: Bitmap? = load("hud")
    private var lg: Bitmap? = load("blueprint")
    private val rnd = java.util.Random(7)
    private val dx = FloatArray(40) { rnd.nextFloat() }
    private val dy = FloatArray(40) { rnd.nextFloat() }
    private val ds = FloatArray(40) { 0.02f + rnd.nextFloat() * 0.05f }
    private var glow1: RadialGradient? = null
    private var glow2: RadialGradient? = null
    private var sph1: RadialGradient? = null
    private var sph2: RadialGradient? = null
    private var flareG: RadialGradient? = null
    private var barG: LinearGradient? = null
    private var txtG: LinearGradient? = null
    private val letters = arrayOfNulls<Path>(5)
    private val tri = Path()
    private var u = 1f
    private var offX = 0f
    private var offY = 0f

    private fun load(n: String): Bitmap? = try {
        val id = resources.getIdentifier(n, "drawable", context.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(resources, id)
    } catch (e: Throwable) { null }

    private fun cl(x: Float) = max(0f, min(1f, x))

    private fun build(ch: Char): Path {
        val q = Path()
        when (ch) {
            'M' -> { q.moveTo(0f, 80f); q.lineTo(0f, 0f); q.lineTo(24f, 0f); q.lineTo(55f, 48f); q.lineTo(86f, 0f); q.lineTo(110f, 0f); q.lineTo(110f, 80f); q.lineTo(90f, 80f); q.lineTo(90f, 30f); q.lineTo(55f, 76f); q.lineTo(20f, 30f); q.lineTo(20f, 80f) }
            'A' -> { q.moveTo(0f, 80f); q.lineTo(55f, 0f); q.lineTo(110f, 80f); q.lineTo(86f, 80f); q.lineTo(55f, 36f); q.lineTo(24f, 80f) }
            'J' -> { q.moveTo(40f, 0f); q.lineTo(62f, 0f); q.lineTo(62f, 52f); q.quadTo(62f, 80f, 30f, 80f); q.lineTo(0f, 80f); q.lineTo(0f, 62f); q.lineTo(28f, 62f); q.quadTo(40f, 62f, 40f, 50f) }
            'I' -> { q.moveTo(0f, 0f); q.lineTo(18f, 0f); q.lineTo(18f, 80f); q.lineTo(0f, 80f) }
            else -> { q.moveTo(0f, 80f); q.lineTo(0f, 0f); q.lineTo(20f, 0f); q.lineTo(70f, 52f); q.lineTo(70f, 0f); q.lineTo(90f, 0f); q.lineTo(90f, 80f); q.lineTo(70f, 80f); q.lineTo(20f, 28f); q.lineTo(20f, 80f) }
        }
        q.close()
        return q
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        u = max(h / 1672f, w / 941f)
        offX = (w - 941f * u) / 2f; offY = (h - 1672f * u) / 2f
        glow1 = RadialGradient(0f, 0f, 45f * u, intArrayOf(0x66FFB060, 0x227A4DFF, 0x00000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        glow2 = RadialGradient(0f, 0f, 45f * u, intArrayOf(0x669AB0FF, 0x22B06BFF, 0x00000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        sph1 = RadialGradient(-6f * u, -7f * u, 26f * u, intArrayOf(0xFFFFE2C0.toInt(), 0xFFB06BFF.toInt(), 0xFF2A1670.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        sph2 = RadialGradient(-6f * u, -7f * u, 26f * u, intArrayOf(0xFFDCE8FF.toInt(), 0xFF6A8CFF.toInt(), 0xFF1A2478.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        flareG = RadialGradient(0f, 0f, 95f * u, intArrayOf(0xFFFF8A1A.toInt(), 0x00FF8A1A), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        val bl = offX + 293f * u; val bw = 353f * u
        barG = LinearGradient(bl, 0f, bl + bw, 0f, intArrayOf(0xFFFFA31F.toInt(), 0xFFFF5A5A.toInt(), 0xFF8A4DFF.toInt(), 0xFF4A62FF.toInt()), floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        val top = offY + 965f * u
        txtG = LinearGradient(0f, top, 0f, top + 80f * u, 0xFFFFFFFF.toInt(), 0xFFAAB6E6.toInt(), Shader.TileMode.CLAMP)
        val ws = floatArrayOf(110f, 110f, 62f, 18f, 90f)
        val gap = (545f - ws.sum()) / 4f
        var x = 14f + (927f - 545f) / 2f + 0f
        x = 470.5f - 545f / 2f
        val m = Matrix()
        for (i in 0 until 5) {
            val pa = build("MAJIN"[i])
            m.reset(); m.postScale(u, u); m.postTranslate(offX + x * u, top)
            pa.transform(m)
            letters[i] = pa
            if (i == 1) {
                tri.reset(); tri.moveTo(55f, 56f); tri.lineTo(68f, 78f); tri.lineTo(42f, 78f); tri.close()
                tri.transform(m)
            }
            x += ws[i] + gap
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        if (t0 == 0L) t0 = System.currentTimeMillis()
        val t = (System.currentTimeMillis() - t0) / 1000f
        cv.drawColor(0xFF02030A.toInt())

        // background photo
        bg?.let { b ->
            val s = u * 941f / b.width * (1f + 0.035f * (0.5f + 0.5f * sin(t * 0.45f)))
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
        val cx = offX + 460f * u; val cy = offY + 625f * u
        lg?.let { b ->
            val lw = 640f * u; val lh = 610f * u
            val g = 1f + 0.06f * sin(t * 2.1f) + 0.9f * exp(-((t - 2.2f) * 3.5f).pow(2))
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            p.xfermode = scr
            if (g > 1.03f) { cmx.setScale(g, g, g, 1f); p.colorFilter = ColorMatrixColorFilter(cmx) }
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

        // glossy orbs + flare on the ring tip
        if (e > 0.6f) {
            val k = (e - 0.6f) / 0.4f
            // 4 balls: two on the big circle, two swinging around the M on a tilted orbit (left and right side)
            for (i in 0..3) {
                val outer = i < 2
                val a = if (outer) 0.96f + i * 3.0316f + t * 0.6f else i * 3.1416f - t * 0.95f
                val rr = if (outer) 314f else 255f
                val ry = if (outer) 1f else 0.38f
                var px = cos(a) * rr * u
                var py = sin(a) * rr * ry * u
                var depth = 1f
                if (!outer) {
                    val tl = -0.32f
                    val x2 = px * cos(tl) - py * sin(tl); val y2 = px * sin(tl) + py * cos(tl)
                    px = x2; py = y2
                    depth = 0.82f + 0.25f * sin(a)
                }
                val ox = cx + px; val oy = cy - (if (outer) py else -py)
                cv.save(); cv.translate(ox, oy); cv.scale(depth, depth)
                p.reset(); p.isAntiAlias = true; p.xfermode = scr; p.shader = if (i % 2 == 0) glow1 else glow2; p.alpha = (255 * k).toInt()
                cv.drawCircle(0f, 0f, 45f * u, p)
                p.reset(); p.isAntiAlias = true; p.shader = if (i % 2 == 0) sph1 else sph2; p.alpha = (255 * k).toInt()
                cv.drawCircle(0f, 0f, (if (outer) 18f else 15f) * u, p)
                cv.restore()
            }
            p.reset(); p.isAntiAlias = true; p.xfermode = scr; p.shader = flareG; p.alpha = (255 * (0.28f + 0.12f * sin(t * 2.6f)) * k).toInt()
            cv.save(); cv.translate(cx + 297f * u, cy - 50f * u); cv.drawCircle(0f, 0f, 95f * u, p); cv.restore()
        }

        // MAJIN letters (same shapes as the design)
        for (i in 0 until 5) {
            val q = cl((t - (2.25f + i * 0.2f)) / 0.8f)
            val pa = letters[i] ?: continue
            if (q > 0f) {
                val qe = 1f - (1f - q) * (1f - q)
                p.reset(); p.isAntiAlias = true; p.shader = txtG; p.alpha = (255 * qe).toInt()
                cv.save(); cv.translate(-(1f - qe) * 18f * u, 0f)
                cv.drawPath(pa, p)
                if (i == 1) { p.reset(); p.isAntiAlias = true; p.color = 0xFFFF9A1F.toInt(); p.alpha = (255 * qe).toInt(); cv.drawPath(tri, p) }
                cv.restore()
            }
        }

        // tagline: IDEAS | TOOLS | REAL IMPACT, spread across the width
        val ta = cl((t - 3.4f) / 1.4f)
        if (ta > 0f) {
            p.reset(); p.isAntiAlias = true; p.color = 0xFF8F99BA.toInt(); p.alpha = (255 * ta).toInt()
            p.textSize = 19f * u; p.letterSpacing = 0.3f; p.typeface = tfL
            val parts = arrayOf("IDEAS", "|", "TOOLS", "|", "REAL IMPACT")
            val wd = FloatArray(5) { p.measureText(parts[it]) }
            val gap = (551f * u - wd.sum()) / 4f
            var x = offX + 197f * u
            val by = offY + 1085f * u + 19f * u
            for (i in 0 until 5) { cv.drawText(parts[i], x, by, p); x += wd[i] + gap }
        }

        // loading bar + label
        val pr = cl((t - 3.2f) / 4.5f); val ep = 1f - (1f - pr).pow(2.2f)
        val ba = cl((t - 3.1f) / 0.8f)
        if (ba > 0f) {
            val bl = offX + 293f * u; val bw = 353f * u
            val by = offY + 1432f * u; val bh = 15f * u
            p.reset(); p.isAntiAlias = true; p.color = 0xFF1A2036.toInt(); p.alpha = (255 * ba).toInt()
            cv.drawRoundRect(RectF(bl, by, bl + bw, by + bh), bh, bh, p)
            p.shader = barG; p.alpha = (255 * ba).toInt()
            cv.drawRoundRect(RectF(bl, by, bl + bw * ep, by + bh), bh, bh, p)
            p.reset(); p.isAntiAlias = true; p.color = 0xFFA9B2CF.toInt(); p.alpha = (255 * cl((t - 3.3f) / 0.8f)).toInt()
            p.textSize = 21f * u; p.letterSpacing = 0.14f; p.textAlign = Paint.Align.CENTER; p.typeface = tfL
            cv.drawText(if (pr >= 1f) "Ready" else "Loading...", offX + 470.5f * u, offY + 1474f * u + 21f * u, p)
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

/** Premium dark background: deep indigo gradient with slow violet and amber glows. */
class PremiumBg(c: Context) : View(c) {
    private val t0 = System.currentTimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lin: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        lin = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF06071A.toInt(), 0xFF0A0B24.toInt(), 0xFF05060F.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val t = (System.currentTimeMillis() - t0) / 1000f
        p.reset(); p.isAntiAlias = true; p.shader = lin
        cv.drawRect(0f, 0f, w, h, p)
        val r = max(w, h) * 0.55f
        val ax = w * (0.85f + 0.06f * sin(t * 0.25f)); val ay = h * (0.08f + 0.03f * cos(t * 0.2f))
        p.shader = RadialGradient(ax, ay, r, intArrayOf(0x447A5CFF, 0x00000000), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        val bx = w * (0.1f + 0.06f * cos(t * 0.22f)); val by = h * (0.95f + 0.03f * sin(t * 0.27f))
        p.shader = RadialGradient(bx, by, r * 0.9f, intArrayOf(0x33FF9A3C, 0x00000000), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        postInvalidateOnAnimation()
    }
}

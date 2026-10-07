package fr.legiondesportes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dessine le monde en fausse 3D.
 * Un point (x, z, h) — latéral, profondeur relative au joueur, hauteur — est projeté avec
 * un facteur de perspective k = Z0 / (Z0 + z).
 */
class Renderer(private val sp: Sprites) {

    companion object {
        const val Z0 = 27f
        const val ZFAR = 19f
        const val ZNEAR = -3.4f
    }

    var w = 1f; private set
    var h = 1f; private set
    private var cx = 0f
    var wp = 1f; private set          // pixels par unité latérale à z = 0
    private var a = 0f
    private var b = 1f
    private var shakeX = 0f
    private var shakeY = 0f
    var shake = 0f
    var hurt = 0f
    private var lastTraveled = 0f
    private var time = 0f

    // ------------------------------------------------------------------ peintures (réutilisées)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private var fog: LinearGradient? = null
    private var vignette: RadialGradient? = null
    private var lines = FloatArray(4096)
    private var roadLight: LinearGradient? = null

    fun setSize(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        cx = w / 2f
        wp = w * 0.40f
        val yNear = h * 0.78f
        val yFar = h * 0.07f
        val kFar = Z0 / (Z0 + ZFAR)
        b = (yNear - yFar) / (1f - kFar)
        a = yNear - b
        fog = LinearGradient(0f, 0f, 0f, h * 0.2f,
            intArrayOf(Color.rgb(196, 206, 222), Color.argb(170, 196, 206, 222), Color.argb(0, 196, 206, 222)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        roadLight = LinearGradient(0f, 0f, w, 0f,
            intArrayOf(Color.argb(55, 255, 220, 160), Color.argb(0, 255, 220, 160), Color.argb(45, 40, 30, 60)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        vignette = RadialGradient(cx, h * 0.55f, max(w, h) * 0.75f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 10, 5, 20)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }

    // ------------------------------------------------------------------ projection

    fun k(z: Float): Float = Z0 / (Z0 + max(z, -Z0 * 0.85f))
    fun sx(x: Float, k: Float): Float = cx + x * wp * k + shakeX
    fun sy(k: Float, hh: Float): Float = a + b * k - hh * wp * k + shakeY

    /** Écran → x monde au niveau du joueur (pour le toucher). */
    fun pxToUnits(dx: Float): Float = dx / wp

    private fun quad(x0: Float, z0: Float, h0: Float, x1: Float, z1: Float, h1: Float,
                     x2: Float, z2: Float, h2: Float, x3: Float, z3: Float, h3: Float) {
        path.reset()
        var kk = k(z0); path.moveTo(sx(x0, kk), sy(kk, h0))
        kk = k(z1); path.lineTo(sx(x1, kk), sy(kk, h1))
        kk = k(z2); path.lineTo(sx(x2, kk), sy(kk, h2))
        kk = k(z3); path.lineTo(sx(x3, kk), sy(kk, h3))
        path.close()
    }

    // ------------------------------------------------------------------ particules

    private val PN = 700
    private val pX = FloatArray(PN); private val pZ = FloatArray(PN); private val pH = FloatArray(PN)
    private val pVX = FloatArray(PN); private val pVZ = FloatArray(PN); private val pVH = FloatArray(PN)
    private val pLife = FloatArray(PN); private val pMax = FloatArray(PN); private val pSize = FloatArray(PN)
    private val pType = IntArray(PN); private val pColor = IntArray(PN)
    private var pN = 0

    /** type : 0 lueur feu, 1 lueur bleue, 2 lueur or, 3 débris solide (gravité), 4 fumée */
    private fun addP(type: Int, x: Float, z: Float, hh: Float, vx: Float, vz: Float, vh: Float, life: Float, size: Float, color: Int = 0) {
        if (pN >= PN) return
        val i = pN++
        pX[i] = x; pZ[i] = z; pH[i] = hh; pVX[i] = vx; pVZ[i] = vz; pVH[i] = vh
        pLife[i] = life; pMax[i] = life; pSize[i] = size; pType[i] = type; pColor[i] = color
    }

    private fun burst(type: Int, x: Float, z: Float, hh: Float, n: Int, speed: Float, life: Float, size: Float, color: Int = 0) {
        repeat(n) {
            val vx = (Random.nextFloat() - 0.5f) * speed
            val vz = (Random.nextFloat() - 0.5f) * speed
            val vh = Random.nextFloat() * speed * 0.9f + speed * 0.2f
            addP(type, x, z, hh, vx, vz, vh, life * (0.6f + Random.nextFloat() * 0.6f), size * (0.6f + Random.nextFloat() * 0.8f), color)
        }
    }

    private fun updateParticles(dt: Float, scrollDz: Float) {
        var i = 0
        while (i < pN) {
            pLife[i] -= dt
            if (pLife[i] <= 0f) {
                val l = --pN
                pX[i] = pX[l]; pZ[i] = pZ[l]; pH[i] = pH[l]; pVX[i] = pVX[l]; pVZ[i] = pVZ[l]; pVH[i] = pVH[l]
                pLife[i] = pLife[l]; pMax[i] = pMax[l]; pSize[i] = pSize[l]; pType[i] = pType[l]; pColor[i] = pColor[l]
                continue
            }
            pZ[i] -= scrollDz
            pX[i] += pVX[i] * dt
            pZ[i] += pVZ[i] * dt
            pH[i] += pVH[i] * dt
            when (pType[i]) {
                3 -> { pVH[i] -= 3.2f * dt; if (pH[i] < 0f) { pH[i] = 0f; pVH[i] = -pVH[i] * 0.3f; pVX[i] *= 0.6f; pVZ[i] *= 0.6f } }
                4 -> { pVH[i] *= 0.97f; pSize[i] += dt * 0.12f }
                else -> { pVX[i] *= 0.94f; pVZ[i] *= 0.94f; pVH[i] *= 0.94f }
            }
            i++
        }
    }

    // ------------------------------------------------------------------ textes flottants

    private class Popup(val text: String, val color: Int, val x: Float, var z: Float, var hh: Float, var life: Float, val size: Float)
    private val popups = ArrayList<Popup>()

    private fun popup(t: String, color: Int, x: Float, z: Float, hh: Float, size: Float) {
        popups.add(Popup(t, color, x, z, hh, 1.3f, size))
    }

    // ------------------------------------------------------------------ événements

    private fun rewardText(r: Reward, amount: Int): String = when (r) {
        Reward.SOLDIERS -> "+$amount SOLDATS"
        Reward.DAMAGE -> "DÉGÂTS ×1.5"
        Reward.RATE -> "CADENCE +35%"
        Reward.DRAGON -> "DRAGON !"
    }

    private fun consume(world: World) {
        for (e in world.events) {
            when (e.type) {
                Ev.HIT -> {
                    val t = when (e.value) { 0 -> 0; 1 -> 1; else -> 0 }
                    burst(t, e.x, e.z, 0.12f, if (e.value == 1) 2 else 4, 0.9f, 0.25f, 0.05f)
                }
                Ev.ENEMY_DIE -> {
                    burst(3, e.x, e.z, 0.1f, 4, 1.4f, 0.8f, 0.025f, Color.rgb(225, 220, 205))
                    addP(4, e.x, e.z, 0.08f, 0f, 0f, 0.15f, 0.6f, 0.1f)
                }
                Ev.BARREL_HIT -> burst(3, e.x, e.z, 0.2f, 1, 1.2f, 0.6f, 0.02f, Color.rgb(150, 95, 50))
                Ev.BARREL_BREAK -> {
                    burst(3, e.x, e.z, 0.15f, 22, 2.6f, 1.2f, 0.04f, Color.rgb(160, 100, 50))
                    burst(2, e.x, e.z, 0.2f, 18, 2.0f, 0.7f, 0.12f)
                    addP(2, e.x, e.z, 0.2f, 0f, 0f, 0f, 0.35f, 0.9f)
                    popup(rewardText(e.reward ?: Reward.SOLDIERS, e.value), Color.rgb(255, 215, 80), e.x, e.z, 0.5f, 0.16f)
                    shake = max(shake, 0.25f)
                }
                Ev.GATE -> {
                    val good = e.value >= 0
                    popup((if (good) "+" else "") + e.value, if (good) Color.rgb(120, 200, 255) else Color.rgb(255, 90, 90), e.x, 0.6f, 0.45f, 0.2f)
                    burst(if (good) 1 else 0, e.x, 0.2f, 0.15f, 20, 2.2f, 0.6f, 0.08f)
                }
                Ev.GATE_HIT -> burst(1, e.x, e.z, 0.2f, 2, 0.8f, 0.25f, 0.05f)
                Ev.SOLDIER_LOST -> {
                    burst(0, e.x, max(e.z, -0.6f), 0.1f, 2, 1.0f, 0.3f, 0.06f)
                    hurt = min(0.5f, hurt + 0.06f)
                }
                Ev.BOSS_HIT -> {
                    burst(if (e.value == 1) 1 else 0, e.x, e.z - 0.2f, 0.3f + Random.nextFloat() * 0.4f, 2, 1.0f, 0.3f, 0.06f)
                }
                Ev.BOSS_DIE -> {
                    burst(0, 0f, e.z, 0.4f, 60, 4f, 1.4f, 0.22f)
                    burst(4, 0f, e.z, 0.3f, 20, 1.5f, 1.6f, 0.25f)
                    shake = 0.9f
                }
            }
        }
        world.events.clear()
    }

    // ------------------------------------------------------------------ rendu principal

    fun draw(c: Canvas, world: World, dt: Float) {
        time += dt
        consume(world)
        val dz = world.traveled - lastTraveled
        lastTraveled = world.traveled
        updateParticles(dt, if (abs(dz) < 5f) dz else 0f)
        for (p in popups) { p.life -= dt; p.hh += dt * 0.5f; p.z -= if (abs(dz) < 5f) dz * 0.3f else 0f }
        popups.removeAll { it.life <= 0f }

        if (shake > 0f) {
            shake = max(0f, shake - dt * 1.6f)
            val s = shake * w * 0.02f
            shakeX = (Random.nextFloat() - 0.5f) * s
            shakeY = (Random.nextFloat() - 0.5f) * s
        } else { shakeX = 0f; shakeY = 0f }
        if (hurt > 0f) hurt = max(0f, hurt - dt * 0.9f)

        c.drawColor(Color.rgb(128, 120, 108))
        drawScenery(c, world.traveled)
        drawRoad(c, world.traveled)
        drawEntities(c, world)
        drawProjectiles(c, world)
        drawParticles(c)
        drawDragon(c, world)
        drawPopups(c)

        fill.color = Color.WHITE
        fill.shader = fog
        c.drawRect(0f, 0f, w, h * 0.2f, fill)
        fill.shader = vignette
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        if (hurt > 0f) {
            fill.color = Color.argb((hurt * 160).toInt().coerceIn(0, 255), 200, 0, 0)
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    // ------------------------------------------------------------------ décor

    private fun hash(n: Int): Int {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return x xor (x ushr 16)
    }

    private val wallColors = intArrayOf(Color.rgb(232, 222, 198), Color.rgb(222, 205, 176), Color.rgb(238, 230, 214), Color.rgb(214, 196, 170))
    private val roofColors = intArrayOf(Color.rgb(84, 98, 128), Color.rgb(150, 70, 50), Color.rgb(96, 110, 140), Color.rgb(120, 60, 45))

    private fun shade(col: Int, f: Float): Int =
        Color.rgb((Color.red(col) * f).toInt().coerceIn(0, 255), (Color.green(col) * f).toInt().coerceIn(0, 255), (Color.blue(col) * f).toInt().coerceIn(0, 255))

    private fun drawScenery(c: Canvas, traveled: Float) {
        // sol pavé hors route, avec bandes de pavés
        fill.shader = null
        val seg = 5.5f
        val nFirst = floor((traveled + ZNEAR - seg) / seg).toInt()
        val nLast = floor((traveled + ZFAR) / seg).toInt()
        for (n in nLast downTo nFirst) {
            val z0 = n * seg - traveled
            val z1 = z0 + seg - 0.5f
            if (z1 < ZNEAR) continue
            for (side in intArrayOf(-1, 1)) {
                val hs = hash(n * 2 + (if (side > 0) 1 else 0))
                val kind = (hs ushr 3) % 7
                if (kind == 6) drawStall(c, side, z0, z1, hs) else drawHouse(c, side, z0, max(z1, ZNEAR), hs)
            }
        }
    }

    private fun drawHouse(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        val x0 = s * 1.32f
        val xo = s * 3.6f
        val hh = 0.62f + ((hs ushr 5) % 5) * 0.07f
        val wall = wallColors[(hs ushr 8) % wallColors.size]
        val roof = roofColors[(hs ushr 11) % roofColors.size]
        val timber = Color.rgb(78, 52, 34)

        // façade avant (face à la caméra), visible si devant
        if (z0 > ZNEAR) {
            fill.color = shade(wall, 0.78f)
            quad(x0, z0, 0f, xo, z0, 0f, xo, z0, hh, x0, z0, hh)
            c.drawPath(path, fill)
            // pignon
            fill.color = shade(roof, 0.7f)
            val xr = s * 2.4f
            quad(x0 - s * 0.08f, z0, hh, xo, z0, hh, xo, z0, hh + 0.05f, xr, z0, hh + 0.42f)
            c.drawPath(path, fill)
        }
        // façade côté route
        fill.color = wall
        quad(x0, z0, 0f, x0, z1, 0f, x0, z1, hh, x0, z0, hh)
        c.drawPath(path, fill)
        // soubassement en pierre
        fill.color = Color.rgb(150, 140, 125)
        quad(x0, z0, 0f, x0, z1, 0f, x0, z1, 0.12f, x0, z0, 0.12f)
        c.drawPath(path, fill)

        // colombages
        var li = 0
        fun seg3(xa: Float, za: Float, ha: Float, xb: Float, zb: Float, hb: Float) {
            if (li + 4 > lines.size) return
            var kk = k(za); lines[li++] = sx(xa, kk); lines[li++] = sy(kk, ha)
            kk = k(zb); lines[li++] = sx(xb, kk); lines[li++] = sy(kk, hb)
        }
        val mid = hh * 0.5f
        seg3(x0, z0, mid, x0, z1, mid)
        seg3(x0, z0, hh, x0, z1, hh)
        val len = z1 - z0
        val nb = 4
        for (i in 0..nb) {
            val zz = z0 + len * i / nb
            seg3(x0, zz, 0.12f, x0, zz, hh)
        }
        for (i in 0 until nb) {
            val za = z0 + len * i / nb
            val zb = z0 + len * (i + 1) / nb
            if (i % 2 == 0) seg3(x0, za, mid, x0, zb, hh) else seg3(x0, za, hh, x0, zb, mid)
        }
        stroke.color = timber
        stroke.strokeWidth = max(2f, wp * k(z0) * 0.022f)
        stroke.strokeCap = Paint.Cap.BUTT
        c.drawLines(lines, 0, li, stroke)

        // fenêtres (étage) et porte
        for (i in 0 until nb) {
            val za = z0 + len * (i + 0.3f) / nb
            val zb = z0 + len * (i + 0.7f) / nb
            if ((hs ushr (i + 14)) and 1 == 0) {
                fill.color = Color.rgb(50, 60, 80)
                quad(x0, za, mid + 0.08f, x0, zb, mid + 0.08f, x0, zb, hh - 0.08f, x0, za, hh - 0.08f)
                c.drawPath(path, fill)
                fill.color = Color.argb(120, 160, 200, 230)
                quad(x0, za, hh - 0.14f, x0, zb, hh - 0.14f, x0, zb, hh - 0.08f, x0, za, hh - 0.08f)
                c.drawPath(path, fill)
            }
        }
        val dz0 = z0 + len * 0.42f
        val dz1 = z0 + len * 0.58f
        fill.color = Color.rgb(90, 55, 32)
        quad(x0, dz0, 0f, x0, dz1, 0f, x0, dz1, mid - 0.05f, x0, dz0, mid - 0.05f)
        c.drawPath(path, fill)

        // toit côté route
        val xr = s * 2.4f
        fill.color = roof
        quad(x0 - s * 0.1f, z0, hh, x0 - s * 0.1f, z1, hh, xr, z1, hh + 0.42f, xr, z0, hh + 0.42f)
        c.drawPath(path, fill)
        // rangées de tuiles
        li = 0
        for (r in 1..3) {
            val f = r / 4f
            val xx = (x0 - s * 0.1f) + (xr - (x0 - s * 0.1f)) * f
            seg3(xx, z0, hh + 0.42f * f, xx, z1, hh + 0.42f * f)
        }
        stroke.color = shade(roof, 0.7f)
        stroke.strokeWidth = max(1.5f, wp * k(z0) * 0.012f)
        c.drawLines(lines, 0, li, stroke)
    }

    private fun drawStall(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        // mur bas du fond
        fill.color = Color.rgb(170, 158, 140)
        quad(s * 2.2f, z0, 0f, s * 2.2f, z1, 0f, s * 2.2f, z1, 0.35f, s * 2.2f, z0, 0.35f)
        c.drawPath(path, fill)
        // caisses
        for (i in 0..1) {
            val za = z0 + 0.8f + i * 2.2f
            if (za < ZNEAR) continue
            val xa = s * (1.45f + ((hs ushr (i + 4)) and 1) * 0.3f)
            drawBox(c, xa, za, 0.22f, 0.22f, Color.rgb(160, 115, 70))
        }
        // auvent rayé
        val stripes = 6
        val zs = max(z0 + 0.3f, ZNEAR)
        val ze = z1 - 0.3f
        val green = (hs and 1) == 0
        for (i in 0 until stripes) {
            val za = zs + (ze - zs) * i / stripes
            val zb = zs + (ze - zs) * (i + 1) / stripes
            fill.color = if (i % 2 == 0) Color.rgb(245, 245, 240) else if (green) Color.rgb(70, 140, 95) else Color.rgb(190, 70, 60)
            quad(s * 1.25f, za, 0.62f, s * 1.25f, zb, 0.62f, s * 2.0f, zb, 0.85f, s * 2.0f, za, 0.85f)
            c.drawPath(path, fill)
        }
        // poteaux
        stroke.color = Color.rgb(90, 60, 35)
        stroke.strokeWidth = max(2f, wp * k(zs) * 0.02f)
        var kk = k(zs)
        c.drawLine(sx(s * 1.27f, kk), sy(kk, 0f), sx(s * 1.27f, kk), sy(kk, 0.62f), stroke)
        kk = k(ze)
        c.drawLine(sx(s * 1.27f, kk), sy(kk, 0f), sx(s * 1.27f, kk), sy(kk, 0.62f), stroke)
    }

    private fun drawBox(c: Canvas, x: Float, z: Float, size: Float, hh: Float, col: Int) {
        val x0 = x - size / 2f; val x1 = x + size / 2f
        // dessus
        fill.color = shade(col, 1.15f)
        quad(x0, z, hh, x1, z, hh, x1, z + size, hh, x0, z + size, hh)
        c.drawPath(path, fill)
        // face avant
        fill.color = col
        quad(x0, z, 0f, x1, z, 0f, x1, z, hh, x0, z, hh)
        c.drawPath(path, fill)
        // face latérale visible (côté route)
        fill.color = shade(col, 0.8f)
        val xs = if (x > 0f) x0 else x1
        quad(xs, z, 0f, xs, z + size, 0f, xs, z + size, hh, xs, z, hh)
        c.drawPath(path, fill)
    }

    // ------------------------------------------------------------------ route

    private fun drawRoad(c: Canvas, traveled: Float) {
        // trottoirs
        fill.color = Color.rgb(140, 132, 118)
        quad(-1.32f, ZNEAR, 0f, 1.32f, ZNEAR, 0f, 1.32f, ZFAR, 0f, -1.32f, ZFAR, 0f)
        c.drawPath(path, fill)
        // chaussée
        fill.color = Color.rgb(186, 174, 152)
        quad(-1f, ZNEAR, 0f, 1f, ZNEAR, 0f, 1f, ZFAR, 0f, -1f, ZFAR, 0f)
        c.drawPath(path, fill)

        // rangées de pavés
        val rowLen = 0.42f
        val r0 = floor((traveled + ZNEAR) / rowLen).toInt()
        val r1 = ceil((traveled + ZFAR) / rowLen).toInt()
        // teinte alternée des rangées
        for (r in r0..r1) {
            if (r % 3 != 0) continue
            val za = max(r * rowLen - traveled, ZNEAR)
            val zb = min(za + rowLen, ZFAR)
            if (zb <= za) continue
            fill.color = Color.argb(28, 60, 50, 40)
            quad(-1f, za, 0f, 1f, za, 0f, 1f, zb, 0f, -1f, zb, 0f)
            c.drawPath(path, fill)
        }
        // pierres plus claires / plus sombres, réparties au hasard
        for (r in r0..r1) {
            val za = r * rowLen - traveled
            val zb = za + rowLen
            if (za < ZNEAR || zb > ZFAR) continue
            val off = if (r % 2 == 0) 0f else 0.11f
            for (j in 0 until 3) {
                val hv = hash(r * 7 + j)
                val col = (hv ushr 4) % 9
                val x0 = -1f + off + col * 0.22f
                val x1 = min(x0 + 0.22f, 1f)
                if (x0 >= 1f) continue
                fill.color = if ((hv and 1) == 0) Color.argb(40, 255, 245, 225) else Color.argb(36, 70, 55, 40)
                quad(x0, za, 0f, x1, za, 0f, x1, zb, 0f, x0, zb, 0f)
                c.drawPath(path, fill)
            }
        }
        var li = 0
        for (r in r0..r1) {
            val za = r * rowLen - traveled
            val zb = za + rowLen
            if (zb < ZNEAR || za > ZFAR) continue
            val ka = k(max(za, ZNEAR)); val kb = k(min(zb, ZFAR))
            if (li + 4 * 12 > lines.size) break
            // joint transversal
            lines[li++] = sx(-1f, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(1f, ka); lines[li++] = sy(ka, 0f)
            // joints longitudinaux décalés
            val off = if (r % 2 == 0) 0f else 0.11f
            var x = -1f + off + 0.22f
            while (x < 1f) {
                lines[li++] = sx(x, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(x, kb); lines[li++] = sy(kb, 0f)
                x += 0.22f
            }
        }
        stroke.color = Color.argb(70, 70, 60, 50)
        stroke.strokeWidth = max(1.2f, w * 0.0018f)
        stroke.strokeCap = Paint.Cap.BUTT
        c.drawLines(lines, 0, li, stroke)

        // lumière chaude venant de la gauche
        fill.color = Color.WHITE
        fill.shader = roadLight
        quad(-1f, ZNEAR, 0f, 1f, ZNEAR, 0f, 1f, ZFAR, 0f, -1f, ZFAR, 0f)
        c.drawPath(path, fill)
        fill.shader = null

        // murets latéraux
        for (side in intArrayOf(-1, 1)) {
            val s = side.toFloat()
            fill.color = Color.rgb(205, 196, 178)
            quad(s * 1.0f, ZNEAR, 0.1f, s * 1.0f, ZFAR, 0.1f, s * 1.1f, ZFAR, 0.1f, s * 1.1f, ZNEAR, 0.1f)
            c.drawPath(path, fill)
            fill.color = Color.rgb(165, 156, 140)
            quad(s * 1.0f, ZNEAR, 0f, s * 1.0f, ZFAR, 0f, s * 1.0f, ZFAR, 0.1f, s * 1.0f, ZNEAR, 0.1f)
            c.drawPath(path, fill)
            // merlons
            val m = 1.1f
            val mr0 = floor((traveled + ZFAR) / m).toInt()
            val mr1 = floor((traveled + ZNEAR) / m).toInt()
            for (r in mr0 downTo mr1) {
                val za = r * m - traveled
                if (za < ZNEAR || za > ZFAR) continue
                val zb = za + 0.5f
                fill.color = Color.rgb(225, 217, 200)
                quad(s * 0.98f, za, 0.19f, s * 0.98f, zb, 0.19f, s * 1.12f, zb, 0.19f, s * 1.12f, za, 0.19f)
                c.drawPath(path, fill)
                fill.color = Color.rgb(185, 176, 160)
                quad(s * 0.98f, za, 0.1f, s * 1.12f, za, 0.1f, s * 1.12f, za, 0.19f, s * 0.98f, za, 0.19f)
                c.drawPath(path, fill)
                fill.color = Color.rgb(160, 150, 135)
                quad(s * 0.98f, za, 0.1f, s * 0.98f, zb, 0.1f, s * 0.98f, zb, 0.19f, s * 0.98f, za, 0.19f)
                c.drawPath(path, fill)
            }
        }

        // muret central
        val zs = World.DIVIDER_END
        fill.color = Color.rgb(212, 203, 185)
        quad(-0.06f, zs, 0.17f, 0.06f, zs, 0.17f, 0.06f, ZFAR, 0.17f, -0.06f, ZFAR, 0.17f)
        c.drawPath(path, fill)
        fill.color = Color.rgb(170, 160, 144)
        quad(-0.06f, zs, 0f, 0.06f, zs, 0f, 0.06f, zs, 0.17f, -0.06f, zs, 0.17f)
        c.drawPath(path, fill)
        li = 0
        val bl = 0.55f
        var r = ceil((traveled + zs) / bl).toInt()
        while (true) {
            val zz = r * bl - traveled
            if (zz > ZFAR) break
            val kk = k(zz)
            lines[li++] = sx(-0.06f, kk); lines[li++] = sy(kk, 0.17f); lines[li++] = sx(0.06f, kk); lines[li++] = sy(kk, 0.17f)
            r++
            if (li + 4 > lines.size) break
        }
        stroke.color = Color.argb(110, 90, 80, 65)
        c.drawLines(lines, 0, li, stroke)
    }

    // ------------------------------------------------------------------ entités triées en profondeur

    private val NB = 160
    private val bucketHead = IntArray(NB)
    private var itemCode = IntArray(1024)
    private var itemNext = IntArray(1024)
    private var nItems = 0

    private fun bucketOf(z: Float): Int = ((z - ZNEAR) / (ZFAR - ZNEAR) * (NB - 1)).toInt().coerceIn(0, NB - 1)

    private fun push(z: Float, code: Int) {
        if (nItems >= itemCode.size) {
            itemCode = itemCode.copyOf(itemCode.size * 2)
            itemNext = itemNext.copyOf(itemNext.size * 2)
        }
        val bk = bucketOf(z)
        itemCode[nItems] = code
        itemNext[nItems] = bucketHead[bk]
        bucketHead[bk] = nItems
        nItems++
    }

    private val T_ENEMY = 0
    private val T_BARREL = 1
    private val T_GATE = 2
    private val T_BOSS = 3
    private val T_ALLY = 4
    private val T_HERO = 5

    private fun drawEntities(c: Canvas, world: World) {
        bucketHead.fill(-1)
        nItems = 0
        val tr = world.traveled
        for ((i, e) in world.enemies.withIndex()) {
            if (!e.alive) continue
            val z = e.z - tr
            if (z > ZFAR || z < ZNEAR) continue
            push(z, T_ENEMY shl 24 or i)
        }
        for ((i, br) in world.barrels.withIndex()) {
            val z = br.z - tr
            if (!br.alive || z > ZFAR || z < ZNEAR) continue
            push(z, T_BARREL shl 24 or i)
        }
        for ((i, g) in world.gates.withIndex()) {
            val z = g.z - tr
            if (z > ZFAR || z < ZNEAR || z < -0.5f) continue
            push(z, T_GATE shl 24 or i)
        }
        val bs = world.boss
        if (bs != null && bs.alive && bs.z - tr < ZFAR) push(bs.z - tr, T_BOSS shl 24)
        val shown = min(world.count, World.MAX_SHOWN)
        for (i in 0 until shown) push(world.slotZ[i], T_ALLY shl 24 or i)
        push(0f, T_HERO shl 24)

        // du plus loin au plus proche
        for (bk in NB - 1 downTo 0) {
            var it = bucketHead[bk]
            while (it >= 0) {
                val code = itemCode[it]
                val idx = code and 0xFFFFFF
                when (code ushr 24) {
                    T_ENEMY -> drawEnemy(c, world.enemies[idx], tr)
                    T_BARREL -> drawBarrel(c, world.barrels[idx], tr)
                    T_GATE -> drawGate(c, world.gates[idx], tr)
                    T_BOSS -> drawBoss(c, world.boss!!, tr)
                    T_ALLY -> drawAlly(c, world, idx)
                    T_HERO -> drawHero(c, world)
                }
                it = itemNext[it]
            }
        }
    }

    private fun sprite(c: Canvas, b: Bitmap, x: Float, z: Float, hh: Float, heightUnits: Float, alpha: Int = 255, lift: Float = 0f, flipX: Boolean = false) {
        val kk = k(z)
        val ph = heightUnits * wp * kk
        val pw = ph * b.width / b.height
        val px = sx(x, kk)
        val py = sy(kk, hh + lift)
        rect.set(px - pw / 2f, py - ph, px + pw / 2f, py)
        bmp.alpha = alpha
        if (flipX) {
            c.save()
            c.scale(-1f, 1f, px, py)
            c.drawBitmap(b, null, rect, bmp)
            c.restore()
        } else {
            c.drawBitmap(b, null, rect, bmp)
        }
        bmp.alpha = 255
    }

    private fun shadow(c: Canvas, x: Float, z: Float, widthUnits: Float) {
        val kk = k(z)
        val pw = widthUnits * wp * kk
        val px = sx(x, kk); val py = sy(kk, 0f)
        fill.color = Color.argb(70, 0, 0, 0)
        rect.set(px - pw / 2f, py - pw * 0.18f, px + pw / 2f, py + pw * 0.18f)
        c.drawOval(rect, fill)
    }

    private fun drawEnemy(c: Canvas, e: Enemy, tr: Float) {
        val z = e.z - tr
        val bob = abs(sin(time * 9f + e.phase)) * 0.02f
        shadow(c, e.x, z, 0.12f)
        sprite(c, sp.enemy, e.x, z, 0f, 0.19f, lift = bob, flipX = sin(e.phase) > 0f)
        if (e.flash > 0f) sprite(c, sp.enemyWhite, e.x, z, 0f, 0.19f, alpha = (e.flash / 0.12f * 220).toInt().coerceIn(0, 255), lift = bob)
    }

    private fun drawBarrel(c: Canvas, br: Barrel, tr: Float) {
        val z = br.z - tr
        val shakeB = if (br.flash > 0f) (Random.nextFloat() - 0.5f) * 0.02f else 0f
        val bm = if (br.chest) sp.chest else sp.barrel
        val bw = if (br.chest) sp.chestWhite else sp.barrelWhite
        val hu = if (br.chest) 0.3f else 0.36f
        shadow(c, br.x, z, 0.4f)
        sprite(c, bm, br.x + shakeB, z, 0f, hu)
        if (br.flash > 0f) sprite(c, bw, br.x + shakeB, z, 0f, hu, alpha = 120)
        val kk = k(z)
        // nombre (points de vie)
        outlined(c, ceil(br.hp).toInt().toString(), sx(br.x, kk), sy(kk, hu * 0.42f), 0.13f * wp * kk, Color.WHITE)
        // bannière de récompense
        drawRewardBanner(c, br, z, hu + 0.1f)
    }

    private fun drawRewardBanner(c: Canvas, br: Barrel, z: Float, hh: Float) {
        val kk = k(z)
        val px = sx(br.x, kk)
        val py = sy(kk, hh) - sin(time * 3f) * wp * kk * 0.015f
        val size = 0.11f * wp * kk
        val label = when (br.reward) {
            Reward.SOLDIERS -> "+${br.amount}"
            Reward.DAMAGE -> "×1.5"
            Reward.RATE -> "+35%"
            Reward.DRAGON -> "DRAGON"
        }
        text.textSize = size * 0.8f
        val tw = text.measureText(label)
        val iconW = size * 1.1f
        val bw = tw + iconW + size * 0.9f
        val bh = size * 1.35f
        rect.set(px - bw / 2f, py - bh, px + bw / 2f, py)
        fill.color = Color.argb(215, 30, 22, 50)
        c.drawRoundRect(rect, bh * 0.35f, bh * 0.35f, fill)
        stroke.color = Color.rgb(255, 200, 70)
        stroke.strokeWidth = max(2f, size * 0.08f)
        c.drawRoundRect(rect, bh * 0.35f, bh * 0.35f, stroke)
        val icon = when (br.reward) {
            Reward.SOLDIERS -> sp.ally
            Reward.DAMAGE -> sp.iconSword
            Reward.RATE -> sp.iconBolt
            Reward.DRAGON -> sp.dragon
        }
        val ih = bh * 0.82f
        val iw = min(ih * icon.width / icon.height, iconW * 1.2f)
        val ihh = iw * icon.height / icon.width
        val ix = rect.left + size * 0.35f
        rect.set(ix, py - bh / 2f - ihh / 2f, ix + iw, py - bh / 2f + ihh / 2f)
        c.drawBitmap(icon, null, rect, bmp)
        outlined(c, label, ix + iw + size * 0.15f + tw / 2f, py - bh / 2f, size * 0.8f, Color.rgb(255, 225, 120))
    }

    private fun drawGate(c: Canvas, g: Gate, tr: Float) {
        val z = g.z - tr
        val s = g.lane.toFloat()
        val xa = min(s * 0.08f, s * 0.97f)
        val xb = max(s * 0.08f, s * 0.97f)
        val gh = 0.36f
        val good = g.value >= 0f
        val alphaMul = if (g.used) 0.35f else 1f
        val kk = k(z)
        val top = sy(kk, gh); val bot = sy(kk, 0f)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, top, 0f, bot,
            if (good) Color.argb((235 * alphaMul).toInt(), 80, 160, 255) else Color.argb((235 * alphaMul).toInt(), 255, 80, 80),
            if (good) Color.argb((210 * alphaMul).toInt(), 20, 60, 210) else Color.argb((210 * alphaMul).toInt(), 160, 15, 25),
            Shader.TileMode.CLAMP)
        // dessus (épaisseur)
        val topCol = if (good) Color.argb((230 * alphaMul).toInt(), 170, 215, 255) else Color.argb((230 * alphaMul).toInt(), 255, 170, 170)
        quad(xa, z, 0f, xb, z, 0f, xb, z, gh, xa, z, gh)
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = topCol
        quad(xa, z, gh, xb, z, gh, xb, z + 0.35f, gh, xa, z + 0.35f, gh)
        c.drawPath(path, fill)
        quad(xa, z, 0f, xb, z, 0f, xb, z, gh, xa, z, gh)
        if (g.flash > 0f) {
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawPath(path, fill)
        }
        // blocs empilés
        stroke.color = Color.argb((150 * alphaMul).toInt(), 255, 255, 255)
        stroke.strokeWidth = max(1.5f, wp * kk * 0.008f)
        for (i in 1..2) {
            val yy = sy(kk, gh * i / 3f)
            c.drawLine(sx(xa, kk), yy, sx(xb, kk), yy, stroke)
        }
        for (i in 1..4) {
            val xx = sx(xa + (xb - xa) * i / 5f, kk)
            c.drawLine(xx, top, xx, bot, stroke)
        }
        stroke.color = Color.argb((255 * alphaMul).toInt(), 255, 255, 255)
        stroke.strokeWidth = max(2.5f, wp * kk * 0.018f)
        c.drawPath(path, stroke)
        // nombre
        val v = g.value.roundToInt()
        val label = (if (v >= 0) "+" else "") + v
        if (!g.used) outlined(c, label, sx((xa + xb) / 2f, kk), sy(kk, gh * 0.5f), 0.17f * wp * kk, Color.WHITE)
    }

    private fun drawBoss(c: Canvas, bs: Boss, tr: Float) {
        val z = bs.z - tr
        val stomp = abs(sin(time * 3f)) * 0.03f
        shadow(c, 0f, z, 0.8f)
        sprite(c, sp.boss, 0f, z, 0f, 0.9f, lift = stomp)
        if (bs.flash > 0f) sprite(c, sp.bossWhite, 0f, z, 0f, 0.9f, alpha = 110, lift = stomp)
        // barre de vie
        val kk = k(z)
        val bw = 0.9f * wp * kk
        val px = sx(0f, kk)
        val py = sy(kk, 0.98f)
        val bh = max(8f, 0.05f * wp * kk)
        rect.set(px - bw / 2f, py - bh, px + bw / 2f, py)
        fill.color = Color.argb(200, 20, 10, 10)
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        val f = (bs.hp / bs.maxHp).coerceIn(0f, 1f)
        rect.set(px - bw / 2f, py - bh, px - bw / 2f + bw * f, py)
        fill.color = Color.rgb(230, 50, 50)
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        outlined(c, ceil(bs.hp).toInt().toString(), px, py - bh * 2.2f, 0.12f * wp * kk, Color.WHITE)
    }

    private fun drawAlly(c: Canvas, world: World, i: Int) {
        val x = world.playerX + world.slotX[i]
        val z = world.slotZ[i]
        val bob = abs(sin(time * 10f + i * 1.7f)) * 0.018f
        shadow(c, x, z, 0.11f)
        sprite(c, sp.ally, x, z, 0f, 0.18f, lift = bob)
    }

    private fun drawHero(c: Canvas, world: World) {
        val x = world.playerX
        val kk = k(0f)
        val px = sx(x, kk); val py = sy(kk, 0f)
        // cercle magique tournant, aplati par la perspective
        val size = 0.62f * wp * kk
        c.save()
        c.translate(px, py)
        c.scale(1f, 0.36f)
        c.rotate(time * 50f)
        rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
        bmp.alpha = 220
        c.drawBitmap(sp.circle, null, rect, bmp)
        bmp.alpha = 255
        c.restore()
        sprite(c, sp.hero, x, 0f, 0f, 0.3f, lift = abs(sin(time * 4f)) * 0.01f)
        // bulle du nombre de soldats
        val label = world.count.toString()
        val ts = 0.085f * wp
        text.textSize = ts
        val tw = text.measureText(label) + ts * 1.2f
        val by = sy(kk, 0.44f)
        rect.set(px - tw / 2f, by - ts * 1.3f, px + tw / 2f, by)
        fill.color = Color.argb(225, 25, 70, 190)
        c.drawRoundRect(rect, ts * 0.65f, ts * 0.65f, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = max(2f, ts * 0.08f)
        c.drawRoundRect(rect, ts * 0.65f, ts * 0.65f, stroke)
        outlined(c, label, px, by - ts * 0.65f, ts, Color.WHITE)
    }

    // ------------------------------------------------------------------ projectiles, particules, dragon

    private fun glow(c: Canvas, b: Bitmap, x: Float, z: Float, hh: Float, sizeUnits: Float, alpha: Int = 255, stretchY: Float = 1f) {
        val kk = k(z)
        val s = sizeUnits * wp * kk
        val px = sx(x, kk); val py = sy(kk, hh)
        rect.set(px - s / 2f, py - s * stretchY / 2f, px + s / 2f, py + s * stretchY / 2f)
        bmp.alpha = alpha
        c.drawBitmap(b, null, rect, bmp)
        bmp.alpha = 255
    }

    private fun drawProjectiles(c: Canvas, world: World) {
        for (p in world.projs) {
            if (!p.alive) continue
            when (p.kind) {
                0 -> {
                    for (t in 3 downTo 1) glow(c, sp.glowFire, p.x, p.z - t * 0.28f, 0.16f, 0.13f - t * 0.025f, 160 - t * 35)
                    glow(c, sp.glowFire, p.x, p.z, 0.16f, 0.16f)
                }
                1 -> {
                    val k0 = k(p.z - 0.6f); val k1 = k(p.z)
                    stroke.color = Color.argb(150, 140, 220, 255)
                    stroke.strokeWidth = max(2f, 0.012f * wp * k1)
                    stroke.strokeCap = Paint.Cap.ROUND
                    c.drawLine(sx(p.x, k0), sy(k0, 0.12f), sx(p.x, k1), sy(k1, 0.12f), stroke)
                    glow(c, sp.glowBlue, p.x, p.z, 0.12f, 0.07f)
                }
                else -> {
                    val hh = max(0.12f, 0.95f - (p.z - 1f) * 0.12f)
                    glow(c, sp.glowFire, p.x, p.z, hh, 0.24f, 230)
                    if (Random.nextFloat() < 0.3f) addP(0, p.x, p.z, hh, 0f, 0f, 0.1f, 0.3f, 0.12f)
                }
            }
        }
    }

    private fun drawParticles(c: Canvas) {
        for (i in 0 until pN) {
            val life = pLife[i] / pMax[i]
            val z = pZ[i]
            if (z < ZNEAR || z > ZFAR) continue
            when (pType[i]) {
                0 -> glow(c, sp.glowFire, pX[i], z, pH[i], pSize[i] * (0.5f + life * 0.5f), (255 * life).toInt())
                1 -> glow(c, sp.glowBlue, pX[i], z, pH[i], pSize[i] * (0.5f + life * 0.5f), (255 * life).toInt())
                2 -> glow(c, sp.glowGold, pX[i], z, pH[i], pSize[i] * (0.5f + life * 0.5f), (255 * life).toInt())
                3 -> {
                    val kk = k(z)
                    val s = pSize[i] * wp * kk
                    fill.color = pColor[i]
                    fill.alpha = (255 * min(1f, life * 2f)).toInt()
                    val px = sx(pX[i], kk); val py = sy(kk, pH[i])
                    rect.set(px - s, py - s * 0.6f, px + s, py + s * 0.6f)
                    c.drawRect(rect, fill)
                    fill.alpha = 255
                }
                4 -> glow(c, sp.smoke, pX[i], z, pH[i], pSize[i], (200 * life).toInt())
            }
        }
    }

    private var dragonAlpha = 0f

    private fun drawDragon(c: Canvas, world: World) {
        val target = if (world.dragonTime > 0f) 1f else 0f
        dragonAlpha += (target - dragonAlpha) * 0.08f
        if (dragonAlpha < 0.02f) return
        val x = sin(time * 1.3f) * 0.35f
        val z = 1.3f - (1f - dragonAlpha) * 2.5f
        val hh = 0.85f + sin(time * 3f) * 0.05f
        val kk = k(z)
        val pw = 1.15f * wp * kk
        val flap = 0.75f + 0.25f * sin(time * 10f)
        val ph = pw * sp.dragon.height / sp.dragon.width
        val px = sx(x, kk); val py = sy(kk, hh)
        // ombre au sol
        fill.color = Color.argb((60 * dragonAlpha).toInt(), 0, 0, 0)
        rect.set(px - pw * 0.35f, sy(kk, 0f) - pw * 0.06f, px + pw * 0.35f, sy(kk, 0f) + pw * 0.06f)
        c.drawOval(rect, fill)
        c.save()
        c.scale(1f, flap, px, py)
        rect.set(px - pw / 2f, py - ph / 2f, px + pw / 2f, py + ph / 2f)
        bmp.alpha = (255 * dragonAlpha).toInt()
        c.drawBitmap(sp.dragon, null, rect, bmp)
        bmp.alpha = 255
        c.restore()
    }

    private fun drawPopups(c: Canvas) {
        for (p in popups) {
            val kk = k(p.z)
            val a = (255 * min(1f, p.life * 2f)).toInt()
            val scale = 1f + max(0f, 0.25f - (1.3f - p.life)) * 2f
            outlined(c, p.text, sx(p.x, kk), sy(kk, p.hh), p.size * wp * kk * scale, p.color, a)
        }
    }

    // ------------------------------------------------------------------ texte

    fun outlined(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, alpha: Int = 255) {
        text.textSize = size
        textStroke.textSize = size
        textStroke.strokeWidth = size * 0.16f
        textStroke.color = Color.argb(alpha, 25, 20, 35)
        text.color = color
        text.alpha = alpha
        val base = y - (text.ascent() + text.descent()) / 2f
        c.drawText(t, x, base, textStroke)
        c.drawText(t, x, base, text)
        text.alpha = 255
    }
}

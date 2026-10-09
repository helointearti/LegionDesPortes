package fr.legiondesportes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

        fun fmt(n: Int): String = when {
            n < 10_000 -> n.toString()
            n < 1_000_000 -> "%.1fK".format(n / 1000f).replace(".0K", "K")
            n < 1_000_000_000 -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
            else -> "%.1fG".format(n / 1_000_000_000f)
        }

        val HERO_COLORS = intArrayOf(Color.rgb(255, 120, 50), Color.rgb(180, 130, 255), Color.rgb(120, 220, 255), Color.rgb(140, 220, 100), Color.rgb(255, 215, 90))
    }

    // ------------------------------------------------------------------ styles de biome

    private class Style(
        val ground: Int, val road: Int, val curb: Int, val wallTop: Int, val wallFace: Int,
        val fog: Int, val tint: Int
    )

    private val styles = arrayOf(
        Style(Color.rgb(128, 120, 108), Color.rgb(186, 174, 152), Color.rgb(140, 132, 118), Color.rgb(225, 217, 200), Color.rgb(170, 160, 144), Color.rgb(200, 208, 222), Color.argb(0, 0, 0, 0)),
        Style(Color.rgb(36, 112, 140), Color.rgb(150, 108, 66), Color.rgb(112, 80, 48), Color.rgb(212, 206, 192), Color.rgb(160, 154, 140), Color.rgb(205, 228, 238), Color.argb(25, 120, 200, 255)),
        Style(Color.rgb(74, 124, 58), Color.rgb(170, 140, 98), Color.rgb(120, 98, 66), Color.rgb(150, 110, 70), Color.rgb(110, 80, 50), Color.rgb(196, 220, 190), Color.argb(30, 60, 140, 40)),
        Style(Color.rgb(42, 32, 36), Color.rgb(96, 86, 88), Color.rgb(62, 52, 56), Color.rgb(110, 98, 100), Color.rgb(66, 58, 62), Color.rgb(70, 28, 22), Color.argb(45, 255, 60, 0))
    )

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
    private val add = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
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
    private val fogs = arrayOfNulls<LinearGradient>(4)
    private var vignette: RadialGradient? = null
    private var lavaGlow: LinearGradient? = null
    private var lines = FloatArray(8192)
    private var roadLight: LinearGradient? = null

    fun setSize(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        cx = w / 2f
        wp = w * 0.40f
        val yNear = h * 0.70f
        val yFar = h * 0.05f
        val kFar = Z0 / (Z0 + ZFAR)
        b = (yNear - yFar) / (1f - kFar)
        a = yNear - b
        for (i in 0 until 4) {
            val f = styles[i].fog
            fogs[i] = LinearGradient(0f, 0f, 0f, h * 0.13f,
                intArrayOf(Color.argb(220, Color.red(f), Color.green(f), Color.blue(f)), Color.argb(110, Color.red(f), Color.green(f), Color.blue(f)), Color.argb(0, Color.red(f), Color.green(f), Color.blue(f))),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        roadLight = LinearGradient(0f, 0f, w, 0f,
            intArrayOf(Color.argb(55, 255, 220, 160), Color.argb(0, 255, 220, 160), Color.argb(45, 40, 30, 60)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        vignette = RadialGradient(cx, h * 0.55f, max(w, h) * 0.75f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 10, 5, 20)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        lavaGlow = LinearGradient(0f, h * 0.5f, 0f, h,
            Color.argb(0, 255, 80, 0), Color.argb(70, 255, 70, 0), Shader.TileMode.CLAMP)
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

    /** Quadrilatère plat au sol, de x0 à x1 et de z0 à z1. */
    private fun flat(x0: Float, x1: Float, z0: Float, z1: Float, hh: Float = 0f) =
        quad(x0, z0, hh, x1, z0, hh, x1, z1, hh, x0, z1, hh)

    // ------------------------------------------------------------------ particules

    private val PN = 1000
    private val pX = FloatArray(PN); private val pZ = FloatArray(PN); private val pH = FloatArray(PN)
    private val pVX = FloatArray(PN); private val pVZ = FloatArray(PN); private val pVH = FloatArray(PN)
    private val pLife = FloatArray(PN); private val pMax = FloatArray(PN); private val pSize = FloatArray(PN)
    private val pType = IntArray(PN); private val pColor = IntArray(PN)
    private var pN = 0

    /** type : 0 feu, 1 bleu, 2 or, 3 débris (gravité), 4 fumée, 5 violet, 6 glace, 7 vert */
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

    // ------------------------------------------------------------------ effets temporaires

    private class Popup(val text: String, val color: Int, val x: Float, var z: Float, var hh: Float, var life: Float, val size: Float)
    private val popups = ArrayList<Popup>()

    private class Bolt(val pts: FloatArray, var life: Float)
    private val bolts = ArrayList<Bolt>()

    private class Ring(val x: Float, val z: Float, var life: Float, val max: Float, val radius: Float, val color: Int, val kind: Int)
    private val rings = ArrayList<Ring>()

    private class Nova(val range: Float, var life: Float)
    private val novas = ArrayList<Nova>()

    private var blessLife = 0f

    private fun popup(t: String, color: Int, x: Float, z: Float, hh: Float, size: Float) {
        popups.add(Popup(t, color, x, z, hh, 1.4f, size))
    }

    // ------------------------------------------------------------------ événements

    private fun glowType(heroTag: Int) = when (heroTag) { 0 -> 0; 1 -> 5; 2 -> 6; 3 -> 7; else -> 2 }

    private fun consume(world: World) {
        for (e in world.events) {
            when (e.type) {
                Ev.HIT -> {
                    val t = if (e.value == 1) 1 else glowType(e.f.toInt())
                    burst(t, e.x, e.z, 0.12f, if (e.value == 1) 2 else 4, 0.9f, 0.25f, 0.05f)
                }
                Ev.ENEMY_DIE -> {
                    val bone = Color.rgb(225, 220, 205)
                    val col = when (e.value) { 1 -> Color.rgb(110, 170, 70); 2 -> Color.rgb(120, 150, 90); 3 -> Color.rgb(60, 60, 75); else -> bone }
                    burst(3, e.x, e.z, 0.1f, if (e.value >= 2) 10 else 4, 1.4f, 0.8f, 0.025f, col)
                    addP(4, e.x, e.z, 0.08f, 0f, 0f, 0.15f, 0.6f, if (e.value >= 2) 0.25f else 0.1f)
                    if (e.value == 3) shake = max(shake, 0.3f)
                }
                Ev.BARREL_HIT -> burst(3, e.x, e.z, 0.2f, 1, 1.2f, 0.6f, 0.02f, Color.rgb(150, 95, 50))
                Ev.BARREL_BREAK -> {
                    burst(3, e.x, e.z, 0.15f, 22, 2.6f, 1.2f, 0.04f, if (e.value == 1) Color.rgb(230, 180, 60) else Color.rgb(160, 100, 50))
                    burst(2, e.x, e.z, 0.2f, 22, 2.2f, 0.8f, 0.12f)
                    addP(2, e.x, e.z, 0.2f, 0f, 0f, 0f, 0.35f, 1.0f)
                    shake = max(shake, 0.25f)
                }
                Ev.REWARD -> {
                    val r = e.reward ?: continue
                    val col = when (r.kind) {
                        RewardKind.SKILL, RewardKind.RECRUIT -> Color.rgb(220, 170, 255)
                        RewardKind.HEAL -> Color.rgb(140, 255, 160)
                        else -> Color.rgb(255, 215, 80)
                    }
                    popup(r.longLabel(), col, e.x * 0.5f, max(e.z, 1.2f), 0.5f, 0.15f)
                    if (r.kind == RewardKind.SKILL || r.kind == RewardKind.RECRUIT) {
                        burst(5, world.playerX, 0f, 0.2f, 30, 2.5f, 0.9f, 0.1f)
                        rings.add(Ring(world.playerX, 0f, 0.6f, 0.6f, 0.6f, Color.rgb(200, 150, 255), 0))
                    }
                }
                Ev.GATE -> {
                    if (e.f > 1f) {
                        popup("×" + e.f.toInt(), Color.rgb(255, 225, 90), e.x, 0.6f, 0.45f, 0.24f)
                        burst(2, e.x, 0.2f, 0.15f, 30, 2.4f, 0.7f, 0.1f)
                    } else {
                        val good = e.value >= 0
                        popup((if (good) "+" else "") + fmt(e.value), if (good) Color.rgb(120, 200, 255) else Color.rgb(255, 90, 90), e.x, 0.6f, 0.45f, 0.2f)
                        burst(if (good) 1 else 0, e.x, 0.2f, 0.15f, 20, 2.2f, 0.6f, 0.08f)
                    }
                }
                Ev.GATE_HIT -> burst(1, e.x, e.z, 0.2f, 2, 0.8f, 0.25f, 0.05f)
                Ev.SOLDIER_LOST -> {
                    burst(0, e.x, max(e.z, -0.6f), 0.1f, 2, 1.0f, 0.3f, 0.06f)
                    hurt = min(0.5f, hurt + 0.05f)
                }
                Ev.BOSS_HIT -> {
                    if (Random.nextFloat() < 0.5f)
                        burst(if (e.value == 1) 1 else 0, e.x * 0.6f, e.z - 0.2f, 0.3f + Random.nextFloat() * 0.4f, 2, 1.0f, 0.3f, 0.06f)
                }
                Ev.BOSS_DIE -> {
                    burst(0, 0f, e.z, 0.4f, 80, 4f, 1.4f, 0.24f)
                    burst(2, 0f, e.z, 0.4f, 40, 3f, 1.6f, 0.15f)
                    burst(4, 0f, e.z, 0.3f, 24, 1.5f, 1.8f, 0.3f)
                    rings.add(Ring(0f, e.z, 0.9f, 0.9f, 2.5f, Color.rgb(255, 200, 80), 0))
                    shake = 1.0f
                }
                Ev.EXPLODE -> {
                    burst(0, e.x, e.z, 0.12f, 26, 2.6f, 0.6f, 0.16f)
                    burst(4, e.x, e.z, 0.1f, 6, 0.8f, 1.1f, 0.18f)
                    burst(3, e.x, e.z, 0.05f, 8, 2.2f, 0.8f, 0.025f, Color.rgb(80, 60, 50))
                    addP(0, e.x, e.z, 0.15f, 0f, 0f, 0f, 0.25f, e.f * 2.6f)
                    rings.add(Ring(e.x, e.z, 0.45f, 0.45f, e.f * 1.2f, Color.rgb(255, 170, 60), 0))
                    shake = max(shake, 0.18f)
                }
                Ev.LIGHTNING -> {
                    val pts = e.pts ?: continue
                    bolts.add(Bolt(pts, 0.32f))
                    var i = 2
                    while (i + 1 < pts.size) {
                        burst(5, pts[i], pts[i + 1], 0.15f, 5, 1.6f, 0.35f, 0.08f)
                        addP(5, pts[i], pts[i + 1], 0.2f, 0f, 0f, 0f, 0.2f, 0.45f)
                        i += 2
                    }
                }
                Ev.NOVA -> {
                    novas.add(Nova(e.f, 0.7f))
                    burst(6, e.x, 0f, 0.2f, 30, 3f, 0.8f, 0.1f)
                }
                Ev.BLESS -> {
                    blessLife = 1.2f
                    burst(2, world.playerX, -0.4f, 0.1f, 40, 1.6f, 1.2f, 0.07f)
                    popup("+" + fmt(e.value), Color.rgb(255, 235, 140), world.playerX, -0.2f, 0.55f, 0.14f)
                }
                Ev.HERO_HURT -> burst(0, e.x, e.z, 0.2f, 6, 1.2f, 0.3f, 0.07f)
                Ev.HERO_DOWN -> {
                    burst(4, e.x, e.z, 0.1f, 10, 1.2f, 1.2f, 0.18f)
                    popup("HÉROS À TERRE", Color.rgb(255, 110, 100), e.x, 0.5f, 0.6f, 0.12f)
                    shake = max(shake, 0.4f)
                }
                Ev.IMPACT -> {
                    when (e.value) {
                        0 -> {
                            burst(3, e.x, 0f, 0.05f, 24, 2.6f, 1.0f, 0.04f, Color.rgb(120, 110, 100))
                            burst(4, e.x, 0f, 0.05f, 10, 1.4f, 1.2f, 0.2f)
                            shake = max(shake, 0.5f)
                        }
                        1 -> burst(0, e.x, 0f, 0.1f, 30, 2.4f, 0.7f, 0.16f)
                        else -> {
                            rings.add(Ring(e.x, 0f, 0.5f, 0.5f, 0.9f, Color.rgb(255, 80, 60), 0))
                            burst(3, e.x, 0f, 0.05f, 16, 2.4f, 0.8f, 0.03f, Color.rgb(90, 60, 50))
                            shake = max(shake, 0.6f)
                        }
                    }
                }
                Ev.SUMMON -> {
                    burst(7, e.x, e.z, 0.1f, 30, 2.4f, 1.0f, 0.18f)
                    burst(4, e.x, e.z, 0.1f, 10, 1.2f, 1.2f, 0.25f)
                }
                Ev.BOSS_CAST -> {
                    val t = when (e.value) { 0 -> 0; 1 -> 6; 2 -> 7; else -> 0 }
                    burst(t, 0f, e.z, 0.6f, 20, 2f, 0.6f, 0.14f)
                }
            }
        }
        world.events.clear()
    }

    // ------------------------------------------------------------------ rendu principal

    private var world: World? = null

    fun draw(c: Canvas, world: World, dt: Float) {
        this.world = world
        time += dt
        consume(world)
        val dz = world.traveled - lastTraveled
        lastTraveled = world.traveled
        val sdz = if (abs(dz) < 5f) dz else 0f
        updateParticles(dt, sdz)
        for (p in popups) { p.life -= dt; p.hh += dt * 0.45f; p.z -= sdz * 0.3f }
        popups.removeAll { it.life <= 0f }
        for (bo in bolts) bo.life -= dt
        bolts.removeAll { it.life <= 0f }
        for (r in rings) r.life -= dt
        rings.removeAll { it.life <= 0f }
        for (n in novas) n.life -= dt
        novas.removeAll { it.life <= 0f }
        if (blessLife > 0f) blessLife -= dt

        if (shake > 0f) {
            shake = max(0f, shake - dt * 1.6f)
            val s = shake * w * 0.02f
            shakeX = (Random.nextFloat() - 0.5f) * s
            shakeY = (Random.nextFloat() - 0.5f) * s
        } else { shakeX = 0f; shakeY = 0f }
        if (hurt > 0f) hurt = max(0f, hurt - dt * 0.9f)

        val tr = world.traveled
        val nearBiome = world.biomeAt(tr)
        c.drawColor(styles[nearBiome].ground)

        // sections de biome visibles
        secN = 0
        var z0 = ZNEAR
        for (az in world.biomeArches) {
            val rel = az - tr
            if (rel > ZNEAR && rel < ZFAR) {
                addSection(z0, rel, world.biomeAt(tr + (z0 + rel) / 2f))
                z0 = rel
            }
        }
        addSection(z0, ZFAR, world.biomeAt(tr + (z0 + ZFAR) / 2f))

        for (s in 0 until secN) drawGround(c, secZ0[s], secZ1[s], secB[s], tr)
        for (s in 0 until secN) drawRoad(c, secZ0[s], secZ1[s], secB[s], tr)
        drawScenery(c, world, tr)
        drawHazards(c, world)
        drawRings(c, true)
        drawNovas(c)
        drawEntities(c, world)
        drawStrikes(c, world)
        drawProjectiles(c, world)
        drawBolts(c, world)
        drawBless(c, world)
        drawParticles(c)
        drawDragon(c, world)
        drawPopups(c)

        val farBiome = world.biomeAt(tr + ZFAR)
        fill.color = Color.WHITE
        fill.shader = fogs[farBiome]
        c.drawRect(0f, 0f, w, h * 0.13f, fill)
        fill.shader = null
        val tint = styles[nearBiome].tint
        if (Color.alpha(tint) > 0) {
            fill.color = tint
            c.drawRect(0f, 0f, w, h, fill)
        }
        if (nearBiome == 3) {
            fill.color = Color.WHITE
            fill.shader = lavaGlow
            c.drawRect(0f, h * 0.5f, w, h, fill)
            fill.shader = null
        }
        fill.color = Color.WHITE
        fill.shader = vignette
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        if (hurt > 0f) {
            fill.color = Color.argb((hurt * 160).toInt().coerceIn(0, 255), 200, 0, 0)
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    private val secZ0 = FloatArray(8)
    private val secZ1 = FloatArray(8)
    private val secB = IntArray(8)
    private var secN = 0

    private fun addSection(z0: Float, z1: Float, biome: Int) {
        if (secN >= 8 || z1 <= z0) return
        secZ0[secN] = z0; secZ1[secN] = z1; secB[secN] = biome; secN++
    }

    // ------------------------------------------------------------------ outils

    private fun hash(n: Int): Int {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return x xor (x ushr 16)
    }

    private fun shadeC(col: Int, f: Float): Int =
        Color.rgb((Color.red(col) * f).toInt().coerceIn(0, 255), (Color.green(col) * f).toInt().coerceIn(0, 255), (Color.blue(col) * f).toInt().coerceIn(0, 255))

    private fun withA(col: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col))

    // ------------------------------------------------------------------ sol (hors route)

    private fun drawGround(c: Canvas, z0: Float, z1: Float, biome: Int, tr: Float) {
        val st = styles[biome]
        fill.color = st.ground
        flat(-12f, 12f, z0, z1)
        c.drawPath(path, fill)
        when (biome) {
            1 -> { // eau : reflets qui ondulent
                var li = 0
                val step = 0.9f
                val n0 = floor((tr + z0) / step).toInt()
                val n1 = ceil((tr + z1) / step).toInt()
                for (n in n0..n1) {
                    val zz = n * step - tr + sin(time * 1.3f + n) * 0.15f
                    if (zz < z0 || zz > z1) continue
                    val kk = k(zz)
                    val hs = hash(n)
                    for (side in intArrayOf(-1, 1)) {
                        val xa = side * (1.5f + (hs ushr 4 and 7) * 0.25f + sin(time + n) * 0.1f)
                        val xb = xa + side * (0.3f + (hs ushr 8 and 3) * 0.15f)
                        if (li + 4 > lines.size) break
                        lines[li++] = sx(xa, kk); lines[li++] = sy(kk, 0f); lines[li++] = sx(xb, kk); lines[li++] = sy(kk, 0f)
                    }
                }
                stroke.color = Color.argb(110, 200, 240, 255)
                stroke.strokeWidth = max(2f, w * 0.004f)
                stroke.strokeCap = Paint.Cap.ROUND
                c.drawLines(lines, 0, li, stroke)
                // rives lointaines
                for (side in intArrayOf(-1, 1)) {
                    fill.color = Color.rgb(84, 140, 70)
                    flat(side * 3.6f, side * 12f, z0, z1)
                    c.drawPath(path, fill)
                    fill.color = Color.rgb(200, 190, 140)
                    flat(side * 3.4f, side * 3.6f, z0, z1)
                    c.drawPath(path, fill)
                }
                // ombre du pont sur l'eau
                fill.color = Color.argb(70, 0, 30, 50)
                flat(-1.55f, 1.55f, z0, z1)
                c.drawPath(path, fill)
            }
            2 -> { // herbe : touffes
                val step = 0.7f
                val n0 = floor((tr + z0) / step).toInt()
                val n1 = ceil((tr + z1) / step).toInt()
                for (n in n0..n1) {
                    val zz = n * step - tr
                    if (zz < z0 || zz > z1) continue
                    val hs = hash(n * 3 + 11)
                    for (side in intArrayOf(-1, 1)) {
                        val x = side * (1.4f + (hs ushr (if (side > 0) 3 else 9) and 15) * 0.18f)
                        val kk = k(zz)
                        fill.color = if ((hs and 1) == 0) Color.rgb(96, 150, 70) else Color.rgb(60, 104, 46)
                        rect.set(sx(x, kk) - 0.12f * wp * kk, sy(kk, 0f) - 0.03f * wp * kk, sx(x, kk) + 0.12f * wp * kk, sy(kk, 0f) + 0.03f * wp * kk)
                        c.drawOval(rect, fill)
                    }
                }
            }
            3 -> { // rivières de lave
                for (side in intArrayOf(-1, 1)) {
                    val s = side.toFloat()
                    fill.color = Color.rgb(255, 110 + (40 * sin(time * 3f)).toInt(), 20)
                    flat(s * 1.7f, s * 2.5f, z0, z1)
                    c.drawPath(path, fill)
                    fill.color = Color.argb(160, 255, 220, 120)
                    flat(s * 1.95f, s * 2.25f, z0, z1)
                    c.drawPath(path, fill)
                    fill.color = Color.rgb(30, 22, 25)
                    flat(s * 1.55f, s * 1.7f, z0, z1)
                    c.drawPath(path, fill)
                    flat(s * 2.5f, s * 2.65f, z0, z1)
                    c.drawPath(path, fill)
                }
                // bulles qui éclatent
                if (Random.nextFloat() < 0.3f) {
                    val side = if (Random.nextBoolean()) -1f else 1f
                    addP(0, side * (1.75f + Random.nextFloat() * 0.7f), z0 + Random.nextFloat() * (z1 - z0), 0.02f, 0f, 0f, 0.5f, 0.5f, 0.08f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ route

    private fun drawRoad(c: Canvas, z0: Float, z1: Float, biome: Int, tr: Float) {
        val st = styles[biome]
        fill.color = st.curb
        flat(-1.32f, 1.32f, z0, z1)
        c.drawPath(path, fill)
        fill.color = st.road
        flat(-1f, 1f, z0, z1)
        c.drawPath(path, fill)

        var li = 0
        when (biome) {
            0 -> { // pavés
                val rowLen = 0.42f
                val r0 = floor((tr + z0) / rowLen).toInt()
                val r1 = ceil((tr + z1) / rowLen).toInt()
                for (r in r0..r1) {
                    val za = r * rowLen - tr
                    val zb = za + rowLen
                    if (za < z0 || zb > z1) continue
                    val off = if (r % 2 == 0) 0f else 0.11f
                    for (j in 0 until 3) {
                        val hv = hash(r * 7 + j)
                        val col = (hv ushr 4) % 9
                        val x0 = -1f + off + col * 0.22f
                        val x1 = min(x0 + 0.22f, 1f)
                        if (x0 >= 1f) continue
                        fill.color = if ((hv and 1) == 0) Color.argb(40, 255, 245, 225) else Color.argb(36, 70, 55, 40)
                        flat(x0, x1, za, zb)
                        c.drawPath(path, fill)
                    }
                }
                for (r in r0..r1) {
                    val za = r * rowLen - tr
                    val zb = za + rowLen
                    if (zb < z0 || za > z1) continue
                    val ka = k(max(za, z0)); val kb = k(min(zb, z1))
                    if (li + 48 > lines.size) break
                    if (za >= z0) { lines[li++] = sx(-1f, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(1f, ka); lines[li++] = sy(ka, 0f) }
                    val off = if (r % 2 == 0) 0f else 0.11f
                    var x = -1f + off + 0.22f
                    while (x < 1f) {
                        lines[li++] = sx(x, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(x, kb); lines[li++] = sy(kb, 0f)
                        x += 0.22f
                    }
                }
                stroke.color = Color.argb(70, 70, 60, 50)
            }
            1 -> { // planches
                val pl = 0.28f
                val r0 = floor((tr + z0) / pl).toInt()
                val r1 = ceil((tr + z1) / pl).toInt()
                for (r in r0..r1) {
                    val za = max(r * pl - tr, z0)
                    val zb = min(za + pl, z1)
                    if (zb <= za) continue
                    val hv = hash(r)
                    fill.color = if ((hv and 3) == 0) Color.argb(50, 255, 230, 180) else if ((hv and 3) == 1) Color.argb(45, 50, 25, 10) else Color.argb(0, 0, 0, 0)
                    if ((hv and 3) <= 1) { flat(-1f, 1f, za, zb); c.drawPath(path, fill) }
                    val kk = k(za)
                    if (li + 4 > lines.size) break
                    lines[li++] = sx(-1f, kk); lines[li++] = sy(kk, 0f); lines[li++] = sx(1f, kk); lines[li++] = sy(kk, 0f)
                }
                // poutres longitudinales
                for (x in floatArrayOf(-0.75f, -0.25f, 0.25f, 0.75f)) {
                    val ka = k(z0); val kb = k(z1)
                    lines[li++] = sx(x, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(x, kb); lines[li++] = sy(kb, 0f)
                }
                stroke.color = Color.argb(90, 60, 35, 15)
            }
            2 -> { // chemin de terre
                for (x in floatArrayOf(-0.7f, -0.3f, 0.3f, 0.7f)) {
                    fill.color = Color.argb(40, 80, 55, 30)
                    flat(x - 0.07f, x + 0.07f, z0, z1)
                    c.drawPath(path, fill)
                }
                val step = 0.5f
                val r0 = floor((tr + z0) / step).toInt()
                val r1 = ceil((tr + z1) / step).toInt()
                for (r in r0..r1) {
                    val zz = r * step - tr
                    if (zz < z0 || zz > z1) continue
                    val hv = hash(r * 5 + 3)
                    for (j in 0 until 3) {
                        val x = -0.95f + ((hv ushr (j * 5)) and 31) / 31f * 1.9f
                        val kk = k(zz + j * 0.15f)
                        val s = (0.025f + (hv ushr (j + 20) and 3) * 0.008f) * wp * kk
                        fill.color = if (j == 0) Color.rgb(140, 130, 115) else Color.argb(80, 90, 65, 40)
                        rect.set(sx(x, kk) - s, sy(kk, 0f) - s * 0.5f, sx(x, kk) + s, sy(kk, 0f) + s * 0.5f)
                        c.drawOval(rect, fill)
                    }
                }
                stroke.color = Color.argb(0, 0, 0, 0)
            }
            else -> { // dalles de basalte fissurées
                val rowLen = 0.6f
                val r0 = floor((tr + z0) / rowLen).toInt()
                val r1 = ceil((tr + z1) / rowLen).toInt()
                for (r in r0..r1) {
                    val za = r * rowLen - tr
                    val zb = za + rowLen
                    if (zb < z0 || za > z1) continue
                    val ka = k(max(za, z0)); val kb = k(min(zb, z1))
                    if (li + 40 > lines.size) break
                    if (za >= z0) { lines[li++] = sx(-1f, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(1f, ka); lines[li++] = sy(ka, 0f) }
                    val off = if (r % 2 == 0) 0f else 0.17f
                    var x = -1f + off + 0.34f
                    while (x < 1f) {
                        lines[li++] = sx(x, ka); lines[li++] = sy(ka, 0f); lines[li++] = sx(x, kb); lines[li++] = sy(kb, 0f)
                        x += 0.34f
                    }
                    val hv = hash(r * 13)
                    if ((hv and 3) == 0 && za >= z0 && zb <= z1) {
                        val x0 = -0.9f + (hv ushr 4 and 15) / 15f * 1.6f
                        fill.color = Color.argb((140 + 80 * sin(time * 2f + r)).toInt().coerceIn(0, 255), 255, 100, 20)
                        quad(x0, za + 0.1f, 0f, x0 + 0.25f, za + 0.3f, 0f, x0 + 0.1f, za + 0.5f, 0f, x0 + 0.04f, za + 0.32f, 0f)
                        c.drawPath(path, fill)
                    }
                }
                stroke.color = Color.argb(150, 20, 15, 18)
            }
        }
        stroke.strokeWidth = max(1.2f, w * 0.0018f)
        stroke.strokeCap = Paint.Cap.BUTT
        if (li > 0 && Color.alpha(stroke.color) > 0) c.drawLines(lines, 0, li, stroke)

        // lumière d'ambiance
        fill.color = Color.WHITE
        fill.shader = roadLight
        flat(-1f, 1f, z0, z1)
        c.drawPath(path, fill)
        fill.shader = null

        // muret central
        val zs = max(World.DIVIDER_END, z0)
        if (zs < z1) {
            fill.color = st.wallTop
            quad(-0.06f, zs, 0.17f, 0.06f, zs, 0.17f, 0.06f, z1, 0.17f, -0.06f, z1, 0.17f)
            c.drawPath(path, fill)
            fill.color = st.wallFace
            if (zs == World.DIVIDER_END) {
                quad(-0.06f, zs, 0f, 0.06f, zs, 0f, 0.06f, zs, 0.17f, -0.06f, zs, 0.17f)
                c.drawPath(path, fill)
            }
        }
    }

    // ------------------------------------------------------------------ décor (murs, maisons, arbres…)

    private fun drawScenery(c: Canvas, world: World, tr: Float) {
        val seg = 5.5f
        val nFirst = floor((tr + ZNEAR - seg) / seg).toInt()
        val nLast = floor((tr + ZFAR) / seg).toInt()
        for (n in nLast downTo nFirst) {
            val z0 = n * seg - tr
            val z1 = z0 + seg
            if (z1 < ZNEAR) continue
            val biome = world.biomeAt(n * seg + seg * 0.5f)
            for (side in intArrayOf(-1, 1)) {
                val hs = hash(n * 2 + (if (side > 0) 1 else 0))
                when (biome) {
                    0 -> { val kind = (hs ushr 3) % 7; if (kind == 6) drawStall(c, side, z0, z1 - 0.5f, hs) else drawHouse(c, side, z0, max(z1 - 0.5f, ZNEAR), hs) }
                    1 -> drawBridgeSide(c, side, z0, z1, hs)
                    2 -> drawForestSide(c, side, z0, z1, hs)
                    else -> drawLavaSide(c, side, z0, z1, hs)
                }
            }
            drawWalls(c, biome, max(z0, ZNEAR), min(z1, ZFAR), tr)
        }
    }

    private fun billboard(c: Canvas, b: Bitmap, x: Float, z: Float, heightUnits: Float, alpha: Int = 255) {
        if (z < ZNEAR || z > ZFAR) return
        val kk = k(z)
        val ph = heightUnits * wp * kk
        val pw = ph * b.width / b.height
        val px = sx(x, kk); val py = sy(kk, 0f)
        rect.set(px - pw / 2f, py - ph, px + pw / 2f, py)
        bmp.alpha = alpha
        c.drawBitmap(b, null, rect, bmp)
        bmp.alpha = 255
    }

    private fun drawBridgeSide(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        // piliers du pont dans l'eau
        billboard(c, sp.post, s * 1.45f, z0 + 0.5f, 0.42f)
        if ((hs and 1) == 0) billboard(c, sp.reeds, s * (2.0f + (hs ushr 4 and 3) * 0.3f), z0 + 2.5f, 0.32f)
        // rive : arbres lointains
        billboard(c, if ((hs and 2) == 0) sp.pine else sp.oak, s * (4.2f + (hs ushr 6 and 3) * 0.4f), z0 + 1.5f, 1.3f)
        billboard(c, sp.pine, s * (5.2f + (hs ushr 9 and 3) * 0.3f), z0 + 3.8f, 1.5f)
    }

    private fun drawForestSide(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        val n = 2 + (hs ushr 2 and 1)
        for (i in 0 until n) {
            val hv = hash(hs + i * 97)
            val z = z0 + 0.6f + i * (5f / n) + (hv and 7) * 0.1f
            val x = s * (1.75f + (hv ushr 4 and 7) * 0.25f)
            val pine = (hv ushr 8 and 1) == 0
            billboard(c, if (pine) sp.pine else sp.oak, x, z, if (pine) 1.25f + (hv ushr 10 and 3) * 0.1f else 1.0f)
        }
        if ((hs and 1) == 0) billboard(c, sp.bush, s * 1.45f, z0 + 3.1f, 0.18f)
        if ((hs ushr 5 and 3) == 0) billboard(c, sp.rock, s * 1.5f, z0 + 1.7f, 0.16f)
    }

    private fun drawLavaSide(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        billboard(c, sp.darkRock, s * (3.0f + (hs ushr 3 and 3) * 0.4f), z0 + 1f, 1.2f + (hs ushr 6 and 3) * 0.15f)
        if ((hs and 1) == 0) billboard(c, sp.darkRock, s * 1.45f, z0 + 3.5f, 0.45f)
    }

    private fun drawWalls(c: Canvas, biome: Int, z0: Float, z1: Float, tr: Float) {
        if (z1 <= z0) return
        val st = styles[biome]
        for (side in intArrayOf(-1, 1)) {
            val s = side.toFloat()
            if (biome == 2) {
                // barrière en bois
                val m = 1.1f
                val r0 = ceil((tr + z0) / m).toInt()
                val r1 = floor((tr + z1) / m).toInt()
                var li = 0
                for (r in r1 downTo r0) {
                    val zz = r * m - tr
                    val kk = k(zz)
                    fill.color = st.wallFace
                    val px = sx(s * 1.05f, kk)
                    val pw = 0.035f * wp * kk
                    rect.set(px - pw, sy(kk, 0.24f), px + pw, sy(kk, 0f))
                    c.drawRect(rect, fill)
                    fill.color = st.wallTop
                    rect.set(px - pw, sy(kk, 0.25f), px + pw, sy(kk, 0.21f))
                    c.drawRect(rect, fill)
                }
                for (hh in floatArrayOf(0.08f, 0.18f)) {
                    val ka = k(z0); val kb = k(z1)
                    lines[li++] = sx(s * 1.05f, ka); lines[li++] = sy(ka, hh); lines[li++] = sx(s * 1.05f, kb); lines[li++] = sy(kb, hh)
                }
                stroke.color = st.wallTop
                stroke.strokeWidth = max(3f, wp * 0.02f)
                stroke.strokeCap = Paint.Cap.ROUND
                c.drawLines(lines, 0, li, stroke)
                continue
            }
            fill.color = st.wallTop
            quad(s * 1.0f, z0, 0.1f, s * 1.0f, z1, 0.1f, s * 1.1f, z1, 0.1f, s * 1.1f, z0, 0.1f)
            c.drawPath(path, fill)
            fill.color = st.wallFace
            quad(s * 1.0f, z0, 0f, s * 1.0f, z1, 0f, s * 1.0f, z1, 0.1f, s * 1.0f, z0, 0.1f)
            c.drawPath(path, fill)
            val m = 1.1f
            val mr0 = floor((tr + z1) / m).toInt()
            val mr1 = floor((tr + z0) / m).toInt()
            for (r in mr0 downTo mr1) {
                val za = r * m - tr
                if (za < z0 || za > z1) continue
                val zb = za + 0.5f
                fill.color = st.wallTop
                quad(s * 0.98f, za, 0.19f, s * 0.98f, zb, 0.19f, s * 1.12f, zb, 0.19f, s * 1.12f, za, 0.19f)
                c.drawPath(path, fill)
                fill.color = shadeC(st.wallTop, 0.85f)
                quad(s * 0.98f, za, 0.1f, s * 1.12f, za, 0.1f, s * 1.12f, za, 0.19f, s * 0.98f, za, 0.19f)
                c.drawPath(path, fill)
                fill.color = st.wallFace
                quad(s * 0.98f, za, 0.1f, s * 0.98f, zb, 0.1f, s * 0.98f, zb, 0.19f, s * 0.98f, za, 0.19f)
                c.drawPath(path, fill)
                if (biome == 3 && r % 3 == 0) {
                    billboard(c, sp.brazier, s * 1.05f, za + 0.25f, 0.26f)
                    val kk = k(za + 0.25f)
                    val fl = 0.85f + 0.15f * sin(time * 9f + r)
                    val gs = 0.11f * wp * kk * fl
                    val px = sx(s * 1.05f, kk); val py = sy(kk, 0.24f)
                    rect.set(px - gs, py - gs * 1.6f, px + gs, py + gs * 0.4f)
                    add.alpha = 170
                    c.drawBitmap(sp.glowFire, null, rect, add)
                    add.alpha = 255
                    if (Random.nextFloat() < 0.15f) addP(0, s * 1.05f, za + 0.25f, 0.26f, 0f, 0f, 0.4f, 0.4f, 0.05f)
                }
                if (biome == 0 && r % 7 == 0) billboard(c, sp.banner, s * 1.05f, za + 0.25f, 0.55f)
            }
        }
    }

    private val wallColors = intArrayOf(Color.rgb(232, 222, 198), Color.rgb(222, 205, 176), Color.rgb(238, 230, 214), Color.rgb(214, 196, 170))
    private val roofColors = intArrayOf(Color.rgb(84, 98, 128), Color.rgb(150, 70, 50), Color.rgb(96, 110, 140), Color.rgb(120, 60, 45))

    private fun drawHouse(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        val x0 = s * 1.32f
        val xo = s * 3.6f
        val hh = 0.62f + ((hs ushr 5) % 5) * 0.07f
        val wall = wallColors[(hs ushr 8) % wallColors.size]
        val roof = roofColors[(hs ushr 11) % roofColors.size]
        val timber = Color.rgb(78, 52, 34)
        if (z0 > ZNEAR) {
            fill.color = shadeC(wall, 0.78f)
            quad(x0, z0, 0f, xo, z0, 0f, xo, z0, hh, x0, z0, hh)
            c.drawPath(path, fill)
            fill.color = shadeC(roof, 0.7f)
            val xr = s * 2.4f
            quad(x0 - s * 0.08f, z0, hh, xo, z0, hh, xo, z0, hh + 0.05f, xr, z0, hh + 0.42f)
            c.drawPath(path, fill)
        }
        fill.color = wall
        quad(x0, z0, 0f, x0, z1, 0f, x0, z1, hh, x0, z0, hh)
        c.drawPath(path, fill)
        fill.color = Color.rgb(150, 140, 125)
        quad(x0, z0, 0f, x0, z1, 0f, x0, z1, 0.12f, x0, z0, 0.12f)
        c.drawPath(path, fill)

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
        for (i in 0..nb) { val zz = z0 + len * i / nb; seg3(x0, zz, 0.12f, x0, zz, hh) }
        for (i in 0 until nb) {
            val za = z0 + len * i / nb
            val zb = z0 + len * (i + 1) / nb
            if (i % 2 == 0) seg3(x0, za, mid, x0, zb, hh) else seg3(x0, za, hh, x0, zb, mid)
        }
        stroke.color = timber
        stroke.strokeWidth = max(2f, wp * k(z0) * 0.022f)
        stroke.strokeCap = Paint.Cap.BUTT
        c.drawLines(lines, 0, li, stroke)

        for (i in 0 until nb) {
            val za = z0 + len * (i + 0.3f) / nb
            val zb = z0 + len * (i + 0.7f) / nb
            if ((hs ushr (i + 14)) and 1 == 0) {
                fill.color = Color.rgb(50, 60, 80)
                quad(x0, za, mid + 0.08f, x0, zb, mid + 0.08f, x0, zb, hh - 0.08f, x0, za, hh - 0.08f)
                c.drawPath(path, fill)
                fill.color = Color.argb(140, 255, 210, 120)
                quad(x0, za, mid + 0.1f, x0, zb, mid + 0.1f, x0, zb, mid + 0.16f, x0, za, mid + 0.16f)
                c.drawPath(path, fill)
            }
        }
        val dz0 = z0 + len * 0.42f
        val dz1 = z0 + len * 0.58f
        fill.color = Color.rgb(90, 55, 32)
        quad(x0, dz0, 0f, x0, dz1, 0f, x0, dz1, mid - 0.05f, x0, dz0, mid - 0.05f)
        c.drawPath(path, fill)

        val xr = s * 2.4f
        fill.color = roof
        quad(x0 - s * 0.1f, z0, hh, x0 - s * 0.1f, z1, hh, xr, z1, hh + 0.42f, xr, z0, hh + 0.42f)
        c.drawPath(path, fill)
        li = 0
        for (r in 1..3) {
            val f = r / 4f
            val xx = (x0 - s * 0.1f) + (xr - (x0 - s * 0.1f)) * f
            seg3(xx, z0, hh + 0.42f * f, xx, z1, hh + 0.42f * f)
        }
        stroke.color = shadeC(roof, 0.7f)
        stroke.strokeWidth = max(1.5f, wp * k(z0) * 0.012f)
        c.drawLines(lines, 0, li, stroke)
    }

    private fun drawStall(c: Canvas, side: Int, z0: Float, z1: Float, hs: Int) {
        val s = side.toFloat()
        fill.color = Color.rgb(170, 158, 140)
        quad(s * 2.2f, z0, 0f, s * 2.2f, z1, 0f, s * 2.2f, z1, 0.35f, s * 2.2f, z0, 0.35f)
        c.drawPath(path, fill)
        for (i in 0..1) {
            val za = z0 + 0.8f + i * 2.2f
            if (za < ZNEAR) continue
            val xa = s * (1.45f + ((hs ushr (i + 4)) and 1) * 0.3f)
            drawBox(c, xa, za, 0.22f, 0.22f, Color.rgb(160, 115, 70))
        }
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
        stroke.color = Color.rgb(90, 60, 35)
        stroke.strokeWidth = max(2f, wp * k(zs) * 0.02f)
        var kk = k(zs)
        c.drawLine(sx(s * 1.27f, kk), sy(kk, 0f), sx(s * 1.27f, kk), sy(kk, 0.62f), stroke)
        kk = k(ze)
        c.drawLine(sx(s * 1.27f, kk), sy(kk, 0f), sx(s * 1.27f, kk), sy(kk, 0.62f), stroke)
    }

    private fun drawBox(c: Canvas, x: Float, z: Float, size: Float, hh: Float, col: Int) {
        val x0 = x - size / 2f; val x1 = x + size / 2f
        fill.color = shadeC(col, 1.15f)
        quad(x0, z, hh, x1, z, hh, x1, z + size, hh, x0, z + size, hh)
        c.drawPath(path, fill)
        fill.color = col
        quad(x0, z, 0f, x1, z, 0f, x1, z, hh, x0, z, hh)
        c.drawPath(path, fill)
        fill.color = shadeC(col, 0.8f)
        val xs = if (x > 0f) x0 else x1
        quad(xs, z, 0f, xs, z + size, 0f, xs, z + size, hh, xs, z, hh)
        c.drawPath(path, fill)
    }

    // ------------------------------------------------------------------ dangers annoncés

    private fun drawHazards(c: Canvas, world: World) {
        for (hz in world.hazards) {
            if (hz.done) continue
            val x = hz.lane * 0.5f
            if (hz.delay > 0f && hz.delay < hz.telegraph) {
                val prog = 1f - hz.delay / hz.telegraph
                val pulse = 0.5f + 0.5f * sin(time * 18f)
                fill.color = Color.argb((60 + 90 * prog + 30 * pulse).toInt().coerceIn(0, 255), 255, 40, 30)
                ellipse(c, x, -0.15f, 0.46f, 1.1f, fill)
                stroke.color = Color.argb(230, 255, 230, 200)
                stroke.strokeWidth = max(3f, wp * 0.012f)
                ellipseStroke(c, x, -0.15f, 0.46f * (1f - prog * 0.9f) + 0.04f, 1.1f * (1f - prog * 0.9f) + 0.1f)
                stroke.color = Color.argb(200, 255, 80, 60)
                ellipseStroke(c, x, -0.15f, 0.46f, 1.1f)
                if (hz.kind == 0) {
                    // rocher qui tombe
                    val hh = hz.delay * 2.4f
                    val kk = k(0f)
                    val s = 0.22f * wp * kk
                    val px = sx(x, kk); val py = sy(kk, hh)
                    c.save()
                    c.rotate(time * 200f, px, py - s / 2f)
                    rect.set(px - s / 2f, py - s, px + s / 2f, py)
                    c.drawBitmap(sp.boulder, null, rect, bmp)
                    c.restore()
                }
            }
            if (hz.kind == 1 && hz.delay <= 0f && hz.delay > -hz.window) {
                // souffle du dragon : flammes de la gueule jusqu'à la voie
                val bz = world.boss?.let { it.z - world.traveled } ?: 6f
                repeat(6) {
                    val t = Random.nextFloat()
                    addP(0, x * t + (Random.nextFloat() - 0.5f) * 0.3f, bz * (1f - t), 0.9f * (1f - t) + 0.05f,
                        (Random.nextFloat() - 0.5f) * 0.6f, -1f, -0.3f, 0.4f, 0.18f + 0.1f * t)
                }
                fill.color = Color.argb(110, 255, 120, 20)
                ellipse(c, x, -0.15f, 0.48f, 1.2f, fill)
            }
        }
    }

    private fun ellipse(c: Canvas, x: Float, z: Float, rx: Float, rz: Float, p: Paint) {
        path.reset()
        for (i in 0..24) {
            val an = i / 24f * 6.2832f
            val zz = z + sin(an) * rz
            val kk = k(zz)
            val px = sx(x + cos(an) * rx, kk); val py = sy(kk, 0f)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }

    private fun ellipseStroke(c: Canvas, x: Float, z: Float, rx: Float, rz: Float) = ellipse(c, x, z, rx, rz, stroke)

    private fun drawRings(c: Canvas, @Suppress("UNUSED_PARAMETER") ground: Boolean) {
        for (r in rings) {
            val prog = 1f - r.life / r.max
            stroke.color = withA(r.color, (230 * (1f - prog)).toInt())
            stroke.strokeWidth = max(2f, wp * 0.03f * (1f - prog))
            ellipseStroke(c, r.x, r.z, r.radius * (0.2f + prog), r.radius * 2.4f * (0.2f + prog))
        }
    }

    private fun drawNovas(c: Canvas) {
        for (n in novas) {
            val prog = 1f - n.life / 0.7f
            val zz = n.range * prog
            val alpha = (200 * (1f - prog * 0.7f)).toInt()
            fill.color = Color.argb(alpha / 2, 160, 230, 255)
            flat(-1f, 1f, -0.3f, zz)
            c.drawPath(path, fill)
            fill.color = Color.argb(alpha, 230, 250, 255)
            flat(-1f, 1f, zz, zz + 0.45f, 0.02f)
            c.drawPath(path, fill)
            for (i in 0 until 3) addP(6, -0.95f + Random.nextFloat() * 1.9f, zz, 0.05f, 0f, 0f, 0.6f, 0.5f, 0.08f)
        }
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
    private val T_ARCH = 6

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
            if (z > ZFAR || z < -0.5f) continue
            push(z, T_GATE shl 24 or i)
        }
        for ((i, az) in world.biomeArches.withIndex()) {
            val z = az - tr
            if (z > ZFAR || z < -1.5f) continue
            push(z, T_ARCH shl 24 or i)
        }
        val bs = world.boss
        if (bs != null && bs.alive && bs.z - tr < ZFAR) push(bs.z - tr, T_BOSS shl 24)
        val shown = min(world.count, World.MAX_SHOWN)
        for (i in 0 until shown) push(world.slotZ[i], T_ALLY shl 24 or i)
        for (i in world.heroes.indices) push(world.heroZ(i) + 0.001f * i, T_HERO shl 24 or i)

        for (bk in NB - 1 downTo 0) {
            var it = bucketHead[bk]
            while (it >= 0) {
                val code = itemCode[it]
                val idx = code and 0xFFFFFF
                when (code ushr 24) {
                    T_ENEMY -> drawEnemy(c, world.enemies[idx], tr)
                    T_BARREL -> drawBarrel(c, world.barrels[idx], tr)
                    T_GATE -> drawGate(c, world, world.gates[idx], tr)
                    T_BOSS -> drawBoss(c, world.boss!!, tr)
                    T_ALLY -> drawAlly(c, world, idx)
                    T_HERO -> drawHero(c, world, idx)
                    T_ARCH -> drawArch(c, world.biomeArches[idx] - tr, world.biomeAt(world.biomeArches[idx] + 0.1f))
                }
                it = itemNext[it]
            }
        }
    }

    private fun sprite(c: Canvas, b: Bitmap, x: Float, z: Float, hh: Float, heightUnits: Float, alpha: Int = 255, lift: Float = 0f, flipX: Boolean = false, p: Paint = bmp) {
        val kk = k(z)
        val ph = heightUnits * wp * kk
        val pw = ph * b.width / b.height
        val px = sx(x, kk)
        val py = sy(kk, hh + lift)
        rect.set(px - pw / 2f, py - ph, px + pw / 2f, py)
        p.alpha = alpha
        if (flipX) {
            c.save()
            c.scale(-1f, 1f, px, py)
            c.drawBitmap(b, null, rect, p)
            c.restore()
        } else {
            c.drawBitmap(b, null, rect, p)
        }
        p.alpha = 255
    }

    private fun shadow(c: Canvas, x: Float, z: Float, widthUnits: Float) {
        val kk = k(z)
        val pw = widthUnits * wp * kk
        val px = sx(x, kk); val py = sy(kk, 0f)
        rect.set(px - pw / 2f, py - pw * 0.2f, px + pw / 2f, py + pw * 0.2f)
        c.drawBitmap(sp.shadowBlob, null, rect, bmp)
    }

    private fun glowAt(c: Canvas, b: Bitmap, x: Float, z: Float, hh: Float, sizeUnits: Float, alpha: Int = 255, stretchY: Float = 1f) {
        val kk = k(z)
        val s = sizeUnits * wp * kk
        val px = sx(x, kk); val py = sy(kk, hh)
        rect.set(px - s / 2f, py - s * stretchY / 2f, px + s / 2f, py + s * stretchY / 2f)
        add.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(b, null, rect, add)
        add.alpha = 255
    }

    private fun drawEnemy(c: Canvas, e: Enemy, tr: Float) {
        val z = e.z - tr
        val t = e.type.ordinal
        val frozen = e.slow > 0f
        val speed = if (frozen) 2.5f else if (e.type == EnemyType.RUNNER) 16f else 9f
        val bob = abs(sin(time * speed + e.phase)) * 0.02f
        val size = e.type.size
        shadow(c, e.x, z, size * 0.7f)
        sprite(c, sp.enemies[t], e.x, z, 0f, size, lift = bob, flipX = sin(e.phase) > 0f && t < 2)
        if (frozen) sprite(c, sp.enemyIce[t], e.x, z, 0f, size, alpha = 150, lift = bob, flipX = sin(e.phase) > 0f && t < 2)
        if (e.flash > 0f) sprite(c, sp.enemyWhite[t], e.x, z, 0f, size, alpha = (e.flash / 0.12f * (if (t >= 2) 90 else 180)).toInt().coerceIn(0, 255), lift = bob, flipX = sin(e.phase) > 0f && t < 2)
        if (t >= 2 && e.hp < e.maxHp) {
            val kk = k(z)
            val bw = size * 0.7f * wp * kk
            val px = sx(e.x, kk); val py = sy(kk, size + 0.03f)
            val bh = max(4f, 0.02f * wp * kk)
            rect.set(px - bw / 2f, py - bh, px + bw / 2f, py)
            fill.color = Color.argb(200, 20, 10, 10)
            c.drawRect(rect, fill)
            rect.set(px - bw / 2f, py - bh, px - bw / 2f + bw * (e.hp / e.maxHp).coerceIn(0f, 1f), py)
            fill.color = Color.rgb(230, 60, 50)
            c.drawRect(rect, fill)
        }
    }

    private fun drawBarrel(c: Canvas, br: Barrel, tr: Float) {
        val z = br.z - tr
        val shakeB = if (br.flash > 0f) (Random.nextFloat() - 0.5f) * 0.02f else 0f
        val bm = if (br.chest) sp.chest else sp.barrel
        val bw = if (br.chest) sp.chestWhite else sp.barrelWhite
        val hu = if (br.chest) 0.3f else 0.36f
        shadow(c, br.x, z, 0.45f)
        if (br.chest) glowAt(c, sp.glowGold, br.x, z, 0.12f, 0.7f, (90 + 50 * sin(time * 4f)).toInt())
        sprite(c, bm, br.x + shakeB, z, 0f, hu)
        if (br.flash > 0f) sprite(c, bw, br.x + shakeB, z, 0f, hu, alpha = 120)
        val kk = k(z)
        outlined(c, fmt(ceil(br.hp).toInt()), sx(br.x, kk), sy(kk, hu * 0.42f), 0.13f * wp * kk, Color.WHITE)
        drawRewardBanner(c, br.reward, br.x, z, hu + 0.1f)
    }

    private fun rewardIcon(r: Reward): Bitmap = when (r.kind) {
        RewardKind.SOLDIERS -> sp.ally
        RewardKind.DAMAGE -> sp.iconSword
        RewardKind.RATE -> sp.iconBolt
        RewardKind.DRAGON -> sp.dragon
        RewardKind.SKILL -> sp.skillIcons[r.hero!!.ordinal]
        RewardKind.RECRUIT -> sp.heroes[r.hero!!.ordinal]
        RewardKind.HEAL -> sp.iconHeart
    }

    private fun drawRewardBanner(c: Canvas, r: Reward, x: Float, z: Float, hh: Float) {
        val kk = k(z)
        val px = sx(x, kk)
        val py = sy(kk, hh) - sin(time * 3f) * wp * kk * 0.015f
        val size = 0.11f * wp * kk
        val label = r.label()
        text.textSize = size * 0.8f
        val tw = text.measureText(label)
        val icon = rewardIcon(r)
        val bh = size * 1.35f
        val ih = bh * 0.86f
        val iw = min(ih * icon.width / icon.height, size * 1.6f)
        val ihh = iw * icon.height / icon.width
        val bw = tw + iw + size * 0.9f
        val special = r.kind == RewardKind.SKILL || r.kind == RewardKind.RECRUIT
        rect.set(px - bw / 2f, py - bh, px + bw / 2f, py)
        fill.color = if (special) Color.argb(225, 60, 25, 90) else Color.argb(215, 30, 22, 50)
        c.drawRoundRect(rect, bh * 0.35f, bh * 0.35f, fill)
        stroke.color = if (special) Color.rgb(210, 150, 255) else Color.rgb(255, 200, 70)
        stroke.strokeWidth = max(2f, size * 0.08f)
        c.drawRoundRect(rect, bh * 0.35f, bh * 0.35f, stroke)
        val ix = rect.left + size * 0.35f
        rect.set(ix, py - bh / 2f - ihh / 2f, ix + iw, py - bh / 2f + ihh / 2f)
        c.drawBitmap(icon, null, rect, bmp)
        outlined(c, label, ix + iw + size * 0.15f + tw / 2f, py - bh / 2f, size * 0.8f, if (special) Color.rgb(235, 210, 255) else Color.rgb(255, 225, 120))
    }

    private fun drawGate(c: Canvas, world: World, g: Gate, tr: Float) {
        val z = g.z - tr
        val s = g.lane.toFloat()
        val xa = min(s * 0.08f, s * 0.97f)
        val xb = max(s * 0.08f, s * 0.97f)
        val gh = 0.36f
        val alphaMul = if (g.used) 0.3f else 1f
        val kk = k(z)
        val top = sy(kk, gh); val bot = sy(kk, 0f)
        val (c0, c1, topC) = when (g.kind) {
            GateKind.SOLDIERS -> if (g.value >= 0f) Triple(Color.rgb(80, 160, 255), Color.rgb(20, 60, 210), Color.rgb(170, 215, 255))
            else Triple(Color.rgb(255, 80, 80), Color.rgb(160, 15, 25), Color.rgb(255, 170, 170))
            GateKind.MULT -> Triple(Color.rgb(255, 210, 70), Color.rgb(200, 120, 10), Color.rgb(255, 235, 150))
            GateKind.SKILL -> Triple(Color.rgb(190, 110, 255), Color.rgb(80, 20, 170), Color.rgb(225, 190, 255))
        }
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, top, 0f, bot, withA(c0, (235 * alphaMul).toInt()), withA(c1, (210 * alphaMul).toInt()), Shader.TileMode.CLAMP)
        quad(xa, z, 0f, xb, z, 0f, xb, z, gh, xa, z, gh)
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = withA(topC, (230 * alphaMul).toInt())
        quad(xa, z, gh, xb, z, gh, xb, z + 0.35f, gh, xa, z + 0.35f, gh)
        c.drawPath(path, fill)
        quad(xa, z, 0f, xb, z, 0f, xb, z, gh, xa, z, gh)
        if (g.flash > 0f) {
            fill.color = Color.argb(90, 255, 255, 255)
            c.drawPath(path, fill)
        }
        // reflets animés
        if (!g.used) {
            val sh = (time * 0.8f + g.z * 0.1f) % 1.4f - 0.2f
            fill.color = Color.argb(70, 255, 255, 255)
            val x0 = xa + (xb - xa) * sh
            quad(x0, z, 0f, min(x0 + 0.12f, xb), z, 0f, min(x0 + 0.2f, xb), z, gh, min(x0 + 0.08f, xb), z, gh)
            if (x0 < xb && x0 > xa) c.drawPath(path, fill)
        }
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
        quad(xa, z, 0f, xb, z, 0f, xb, z, gh, xa, z, gh)
        stroke.color = Color.argb((255 * alphaMul).toInt(), 255, 255, 255)
        stroke.strokeWidth = max(2.5f, wp * kk * 0.018f)
        c.drawPath(path, stroke)
        if (g.used) return
        val mx = sx((xa + xb) / 2f, kk)
        when (g.kind) {
            GateKind.SOLDIERS -> {
                val v = g.value.toInt()
                outlined(c, (if (v >= 0) "+" else "") + fmt(v), mx, sy(kk, gh * 0.5f), 0.17f * wp * kk, Color.WHITE)
            }
            GateKind.MULT -> outlined(c, "×" + g.value.toInt(), mx, sy(kk, gh * 0.5f), 0.22f * wp * kk, Color.WHITE)
            GateKind.SKILL -> {
                val r = g.reward!!
                val icon = if (r.kind == RewardKind.RECRUIT) sp.heroes[r.hero!!.ordinal] else rewardIcon(r)
                val ih = gh * 0.8f * wp * kk
                val iw = ih * icon.width / icon.height
                val iy = sy(kk, gh * 0.5f)
                rect.set(mx - iw - 0.02f * wp * kk, iy - ih / 2f, mx - 0.02f * wp * kk, iy + ih / 2f)
                c.drawBitmap(icon, null, rect, bmp)
                val lab = if (r.kind == RewardKind.RECRUIT) "RECRUE" else "NIV +1"
                outlined(c, lab, mx + 0.13f * wp * kk, iy, 0.085f * wp * kk, Color.WHITE)
                val name = if (r.kind == RewardKind.RECRUIT) r.hero!!.title else r.hero!!.skill
                outlined(c, name, mx, sy(kk, gh + 0.06f), 0.06f * wp * kk, Color.rgb(230, 200, 255))
            }
        }
    }

    private fun drawArch(c: Canvas, z: Float, biome: Int) {
        val st = styles[biome]
        val hh = 1.15f
        for (side in intArrayOf(-1, 1)) {
            val s = side.toFloat()
            // pilier
            fill.color = st.wallFace
            quad(s * 1.0f, z, 0f, s * 1.3f, z, 0f, s * 1.3f, z, hh, s * 1.0f, z, hh)
            c.drawPath(path, fill)
            fill.color = shadeC(st.wallTop, 0.9f)
            quad(s * 1.0f, z, 0f, s * 1.0f, z + 0.3f, 0f, s * 1.0f, z + 0.3f, hh, s * 1.0f, z, hh)
            c.drawPath(path, fill)
            billboard(c, sp.banner, s * 1.15f, z - 0.05f, 0.75f)
        }
        // linteau
        fill.color = st.wallTop
        quad(-1.3f, z, hh, 1.3f, z, hh, 1.3f, z, hh + 0.22f, -1.3f, z, hh + 0.22f)
        c.drawPath(path, fill)
        fill.color = shadeC(st.wallTop, 1.08f)
        quad(-1.3f, z, hh + 0.22f, 1.3f, z, hh + 0.22f, 1.3f, z + 0.3f, hh + 0.22f, -1.3f, z + 0.3f, hh + 0.22f)
        c.drawPath(path, fill)
        val kk = k(z)
        val name = when (biome) { 0 -> "VILLAGE"; 1 -> "PONT DES BRUMES"; 2 -> "FORÊT SOMBRE"; else -> "FORTERESSE DE LAVE" }
        outlined(c, name, sx(0f, kk), sy(kk, hh + 0.11f), 0.1f * wp * kk, Color.rgb(255, 225, 140))
    }

    private fun drawBoss(c: Canvas, bs: Boss, tr: Float) {
        val z = bs.z - tr
        val t = bs.type.ordinal
        val scale = if (bs.mini) 0.72f else 1f
        val hu = when (bs.type) { BossType.DRAGON -> 0.95f; BossType.NECRO -> 0.95f; BossType.GOLEM -> 1.0f; else -> 0.9f } * scale
        val hover = when (bs.type) {
            BossType.DRAGON -> 0.25f + sin(time * 2.5f) * 0.05f
            BossType.NECRO -> 0.08f + sin(time * 2f) * 0.04f
            else -> abs(sin(time * 3f)) * 0.03f
        }
        shadow(c, 0f, z, 0.9f * scale)
        val casting = bs.anim < 0.5f
        if (casting) {
            val g = when (bs.type) { BossType.GOLEM -> sp.glowIce; BossType.NECRO -> sp.glowGreen; BossType.DRAGON -> sp.glowFire; else -> sp.glowRed }
            glowAt(c, g, 0f, z, hu * 0.5f + hover, hu * 1.1f, (150 * (1f - bs.anim * 2f)).toInt())
        }
        if (bs.type == BossType.NECRO) {
            for (i in 0 until 3) {
                val an = time * 1.5f + i * 2.09f
                glowAt(c, sp.glowGreen, cos(an) * 0.45f * scale, z + sin(an) * 0.3f, hu * 0.7f + sin(an * 2f) * 0.05f, 0.18f)
            }
        }
        if (bs.type == BossType.DRAGON) {
            val flap = 0.85f + 0.15f * sin(time * 6f)
            val kk = k(z)
            val ph = hu * wp * kk
            val pw = ph * sp.bosses[t].width / sp.bosses[t].height
            val px = sx(0f, kk); val py = sy(kk, hover)
            c.save()
            c.scale(1f, flap, px, py - ph / 2f)
            rect.set(px - pw / 2f, py - ph, px + pw / 2f, py)
            c.drawBitmap(sp.bosses[t], null, rect, bmp)
            if (bs.flash > 0.05f) { bmp.alpha = 45; c.drawBitmap(sp.bossWhite[t], null, rect, bmp); bmp.alpha = 255 }
            c.restore()
        } else {
            sprite(c, sp.bosses[t], 0f, z, 0f, hu, lift = hover)
            if (bs.flash > 0.05f) sprite(c, sp.bossWhite[t], 0f, z, 0f, hu, alpha = 45, lift = hover)
        }
        if (bs.slow > 0f) glowAt(c, sp.glowIce, 0f, z, hu * 0.4f, hu * 1.3f, 120)
        val kk = k(z)
        val bw = 0.9f * wp * kk
        val px = sx(0f, kk)
        val py = sy(kk, hu + hover + 0.08f)
        val bh = max(8f, 0.05f * wp * kk)
        rect.set(px - bw / 2f, py - bh, px + bw / 2f, py)
        fill.color = Color.argb(200, 20, 10, 10)
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        val f = (bs.hp / bs.maxHp).coerceIn(0f, 1f)
        rect.set(px - bw / 2f, py - bh, px - bw / 2f + bw * f, py)
        fill.color = Color.rgb(230, 50, 50)
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        outlined(c, fmt(ceil(bs.hp).toInt()), px, py - bh * 2.2f, 0.12f * wp * kk, Color.WHITE)
    }

    private fun drawAlly(c: Canvas, world: World, i: Int) {
        val x = world.playerX + world.slotX[i]
        val z = world.slotZ[i]
        val bob = abs(sin(time * 10f + i * 1.7f)) * 0.018f
        shadow(c, x, z, 0.11f)
        sprite(c, sp.ally, x, z, 0f, 0.18f, lift = bob)
    }

    private fun drawHero(c: Canvas, world: World, i: Int) {
        val h = world.heroes[i]
        val x = world.heroX(i)
        val z = world.heroZ(i)
        val kk = k(z)
        val px = sx(x, kk); val py = sy(kk, 0f)
        if (!h.alive) {
            // héros à terre : petite tombe lumineuse
            glowAt(c, sp.glowBlue, x, z, 0.05f, 0.25f, 90)
            fill.color = Color.rgb(120, 120, 130)
            rect.set(px - 0.04f * wp * kk, py - 0.1f * wp * kk, px + 0.04f * wp * kk, py)
            c.drawRoundRect(rect, 0.04f * wp * kk, 0.04f * wp * kk, fill)
            return
        }
        val main = i == 0
        val size = (if (main) 0.62f else 0.4f) * wp * kk
        c.save()
        c.translate(px, py)
        c.scale(1f, 0.36f)
        c.rotate(time * (if (main) 50f else -40f))
        rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
        add.alpha = if (main) 200 else 140
        c.drawBitmap(if (main) sp.circle else sp.circleBlue, null, rect, add)
        add.alpha = 255
        c.restore()
        val pulse = if (h.cast < 0.35f) 1f + (0.35f - h.cast) * 0.4f else 1f
        val hu = (if (main) 0.3f else 0.27f) * pulse
        val lift = abs(sin(time * 4f + i)) * 0.01f
        sprite(c, sp.heroes[h.type.ordinal], x, z, 0f, hu, lift = lift)
        if (h.hurt > 0f) sprite(c, sp.heroWhite[h.type.ordinal], x, z, 0f, hu, alpha = (h.hurt * 400).toInt().coerceIn(0, 200), lift = lift)
        // lueur du bâton pendant le sort
        if (h.cast < 0.5f) {
            val g = when (h.type) { HeroType.FIRE -> sp.glowFire; HeroType.STORM -> sp.glowPurple; HeroType.FROST -> sp.glowIce; HeroType.RANGER -> sp.glowGreen; HeroType.PRIEST -> sp.glowGold }
            glowAt(c, g, x + 0.06f, z, hu * 0.88f, 0.35f * (1f - h.cast * 1.6f) + 0.1f, 255)
        }
        // barre de vie si blessé
        if (h.hp < h.maxHp) {
            val bw = 0.16f * wp * kk
            val by = sy(kk, hu + 0.05f)
            val bh = max(4f, 0.018f * wp * kk)
            rect.set(px - bw / 2f, by - bh, px + bw / 2f, by)
            fill.color = Color.argb(200, 20, 10, 10)
            c.drawRect(rect, fill)
            rect.set(px - bw / 2f, by - bh, px - bw / 2f + bw * (h.hp / h.maxHp).coerceIn(0f, 1f), by)
            fill.color = Color.rgb(90, 230, 110)
            c.drawRect(rect, fill)
        }
        if (main) {
            val label = fmt(world.count)
            val ts = 0.085f * wp
            text.textSize = ts
            val tw = text.measureText(label) + ts * 1.2f
            val by = sy(k(0f), 0.44f)
            val bx = sx(world.playerX, k(0f))
            rect.set(bx - tw / 2f, by - ts * 1.3f, bx + tw / 2f, by)
            fill.color = Color.argb(225, 25, 70, 190)
            c.drawRoundRect(rect, ts * 0.65f, ts * 0.65f, fill)
            stroke.color = Color.WHITE
            stroke.strokeWidth = max(2f, ts * 0.08f)
            c.drawRoundRect(rect, ts * 0.65f, ts * 0.65f, stroke)
            outlined(c, label, bx, by - ts * 0.65f, ts, Color.WHITE)
        }
    }

    // ------------------------------------------------------------------ projectiles et sorts

    private fun drawStrikes(c: Canvas, world: World) {
        for (s in world.strikes) {
            val z = s.z - world.traveled
            if (z < ZNEAR || z > ZFAR) continue
            val hh = s.delay * 3.2f
            sprite(c, sp.arrow, s.x, z, hh, 0.22f)
            if (s.delay < 0.3f) {
                val kk = k(z)
                val r = 0.05f * wp * kk * (1f - s.delay * 2f)
                fill.color = Color.argb(70, 0, 0, 0)
                rect.set(sx(s.x, kk) - r, sy(kk, 0f) - r * 0.4f, sx(s.x, kk) + r, sy(kk, 0f) + r * 0.4f)
                c.drawOval(rect, fill)
            }
        }
    }

    private fun drawProjectiles(c: Canvas, world: World) {
        for (p in world.projs) {
            if (!p.alive) continue
            when (p.kind) {
                0 -> {
                    val g = when (p.tag) { 0 -> sp.glowFire; 1 -> sp.glowPurple; 2 -> sp.glowIce; 3 -> sp.glowGreen; else -> sp.glowGold }
                    for (t in 3 downTo 1) glowAt(c, g, p.x, p.z - t * 0.28f, 0.16f, 0.13f - t * 0.025f, 160 - t * 35)
                    glowAt(c, g, p.x, p.z, 0.16f, 0.16f)
                }
                1 -> {
                    val k0 = k(p.z - 0.6f); val k1 = k(p.z)
                    stroke.color = Color.argb(150, 140, 220, 255)
                    stroke.strokeWidth = max(2f, 0.012f * wp * k1)
                    stroke.strokeCap = Paint.Cap.ROUND
                    c.drawLine(sx(p.x, k0), sy(k0, 0.12f), sx(p.x, k1), sy(k1, 0.12f), stroke)
                    glowAt(c, sp.glowBlue, p.x, p.z, 0.12f, 0.07f)
                }
                2 -> {
                    val hh = max(0.12f, 0.95f - (p.z - 1f) * 0.12f)
                    glowAt(c, sp.glowFire, p.x, p.z, hh, 0.26f, 230)
                    if (Random.nextFloat() < 0.3f) addP(0, p.x, p.z, hh, 0f, 0f, 0.1f, 0.3f, 0.12f)
                }
                else -> {
                    // boule de feu : grosse, avec traînée
                    for (t in 5 downTo 1) glowAt(c, sp.glowFire, p.x, p.z - t * 0.35f, 0.25f + t * 0.01f, 0.32f - t * 0.04f, 200 - t * 30)
                    glowAt(c, sp.glowRed, p.x, p.z, 0.25f, 0.5f, 200)
                    glowAt(c, sp.glowFire, p.x, p.z, 0.25f, 0.34f)
                    if (Random.nextFloat() < 0.6f) addP(0, p.x, p.z, 0.25f, (Random.nextFloat() - 0.5f) * 0.4f, -1f, 0.2f, 0.4f, 0.12f)
                    if (Random.nextFloat() < 0.3f) addP(4, p.x, p.z - 0.3f, 0.3f, 0f, -0.5f, 0.3f, 0.6f, 0.1f)
                }
            }
        }
    }

    private val boltPath = Path()

    private fun drawBolts(c: Canvas, world: World) {
        for (bo in bolts) {
            val pts = bo.pts
            val alpha = (255 * (bo.life / 0.32f)).toInt().coerceIn(0, 255)
            boltPath.reset()
            var i = 0
            var first = true
            while (i + 3 < pts.size) {
                val x0 = pts[i]; val z0 = pts[i + 1]; val x1 = pts[i + 2]; val z1 = pts[i + 3]
                val h0 = if (i == 0) 0.32f else 0.14f
                val k0 = k(z0); val k1 = k(z1)
                val ax = sx(x0, k0); val ay = sy(k0, h0)
                val bx = sx(x1, k1); val by = sy(k1, 0.14f)
                if (first) { boltPath.moveTo(ax, ay); first = false } else boltPath.lineTo(ax, ay)
                val segs = 6
                val dx = bx - ax; val dy = by - ay
                val len = kotlin.math.sqrt(dx * dx + dy * dy) + 0.01f
                val nx = -dy / len; val ny = dx / len
                for (sg in 1 until segs) {
                    val t = sg / segs.toFloat()
                    val j = (Random.nextFloat() - 0.5f) * len * 0.18f
                    boltPath.lineTo(ax + dx * t + nx * j, ay + dy * t + ny * j)
                }
                boltPath.lineTo(bx, by)
                i += 2
            }
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.color = Color.argb(alpha / 2, 160, 110, 255)
            stroke.strokeWidth = max(6f, w * 0.018f)
            c.drawPath(boltPath, stroke)
            stroke.color = Color.argb(alpha, 230, 210, 255)
            stroke.strokeWidth = max(3f, w * 0.007f)
            c.drawPath(boltPath, stroke)
            stroke.color = Color.argb(alpha, 255, 255, 255)
            stroke.strokeWidth = max(1.5f, w * 0.0025f)
            c.drawPath(boltPath, stroke)
        }
        if (bolts.isNotEmpty()) {
            // éclair qui illumine l'écran
            fill.color = Color.argb((40 * bolts.maxOf { it.life } / 0.32f).toInt().coerceIn(0, 60), 200, 180, 255)
            c.drawRect(0f, 0f, w, h, fill)
        }
        @Suppress("UNUSED_VARIABLE") val unused = world
    }

    private fun drawBless(c: Canvas, world: World) {
        if (blessLife <= 0f) return
        val kk = k(-0.3f)
        val px = sx(world.playerX, kk)
        val bw = 0.55f * wp * kk
        val a = (160 * min(1f, blessLife)).toInt()
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, 0f, 0f, sy(kk, 0f), Color.argb(0, 255, 240, 160), Color.argb(a, 255, 230, 140), Shader.TileMode.CLAMP)
        rect.set(px - bw / 2f, 0f, px + bw / 2f, sy(kk, 0f))
        c.drawRect(rect, fill)
        fill.shader = null
        if (Random.nextFloat() < 0.8f) addP(2, world.playerX + (Random.nextFloat() - 0.5f) * 0.5f, -0.4f + Random.nextFloat() * 0.5f, 0f, 0f, 0f, 1.2f, 0.8f, 0.06f)
    }

    private fun drawParticles(c: Canvas) {
        for (i in 0 until pN) {
            val life = pLife[i] / pMax[i]
            val z = pZ[i]
            if (z < ZNEAR || z > ZFAR) continue
            val a = (255 * life).toInt()
            val s = pSize[i] * (0.5f + life * 0.5f)
            when (pType[i]) {
                0 -> glowAt(c, sp.glowFire, pX[i], z, pH[i], s, a)
                1 -> glowAt(c, sp.glowBlue, pX[i], z, pH[i], s, a)
                2 -> glowAt(c, sp.glowGold, pX[i], z, pH[i], s, a)
                5 -> glowAt(c, sp.glowPurple, pX[i], z, pH[i], s, a)
                6 -> glowAt(c, sp.glowIce, pX[i], z, pH[i], s, a)
                7 -> glowAt(c, sp.glowGreen, pX[i], z, pH[i], s, a)
                3 -> {
                    val kk = k(z)
                    val ss = pSize[i] * wp * kk
                    fill.color = pColor[i]
                    fill.alpha = (255 * min(1f, life * 2f)).toInt()
                    val px = sx(pX[i], kk); val py = sy(kk, pH[i])
                    rect.set(px - ss, py - ss * 0.6f, px + ss, py + ss * 0.6f)
                    c.drawRect(rect, fill)
                    fill.alpha = 255
                }
                4 -> {
                    val kk = k(z)
                    val ss = pSize[i] * wp * kk
                    val px = sx(pX[i], kk); val py = sy(kk, pH[i])
                    rect.set(px - ss / 2f, py - ss / 2f, px + ss / 2f, py + ss / 2f)
                    bmp.alpha = (200 * life).toInt()
                    c.drawBitmap(sp.smoke, null, rect, bmp)
                    bmp.alpha = 255
                }
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
            val scale = 1f + max(0f, 0.25f - (1.4f - p.life)) * 2f
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

    fun measure(t: String, size: Float): Float {
        text.textSize = size
        return text.measureText(t)
    }
}

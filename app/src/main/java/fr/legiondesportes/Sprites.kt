package fr.legiondesportes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tous les sprites sont dessinés une seule fois au lancement, à partir de formes simples.
 * Convention : le point d'ancrage d'un personnage est le bas-centre du bitmap (ses pieds).
 */
class Sprites {

    // ------------------------------------------------------------------ personnages
    val ally: Bitmap = make(96, 128) { c, p -> drawAlly(c, p) }

    val heroes: Array<Bitmap> = arrayOf(
        make(128, 180) { c, p -> drawHero(c, p, HeroType.FIRE) },
        make(128, 180) { c, p -> drawHero(c, p, HeroType.STORM) },
        make(128, 180) { c, p -> drawHero(c, p, HeroType.FROST) },
        make(128, 180) { c, p -> drawHero(c, p, HeroType.RANGER) },
        make(128, 180) { c, p -> drawHero(c, p, HeroType.PRIEST) }
    )
    val heroWhite: Array<Bitmap> = Array(5) { silhouette(heroes[it], Color.WHITE) }

    val enemies: Array<Bitmap> = arrayOf(
        make(96, 128) { c, p -> drawSkeleton(c, p) },
        make(96, 112) { c, p -> drawGoblin(c, p) },
        make(160, 170) { c, p -> drawBrute(c, p) },
        make(160, 210) { c, p -> drawKnight(c, p) }
    )
    val enemyWhite: Array<Bitmap> = Array(4) { silhouette(enemies[it], Color.WHITE) }
    val enemyIce: Array<Bitmap> = Array(4) { silhouette(enemies[it], Color.rgb(150, 230, 255)) }

    val bosses: Array<Bitmap> = arrayOf(
        make(300, 330) { c, p -> drawDemon(c, p) },
        make(320, 320) { c, p -> drawGolem(c, p) },
        make(280, 340) { c, p -> drawNecro(c, p) },
        make(440, 300) { c, p -> drawBlackDragon(c, p) }
    )
    val bossWhite: Array<Bitmap> = Array(4) { silhouette(bosses[it], Color.WHITE) }

    val dragon: Bitmap = make(420, 260) { c, p -> drawDragon(c, p) }

    // ------------------------------------------------------------------ objets
    val barrel: Bitmap = make(128, 140) { c, p -> drawBarrel(c, p) }
    val barrelWhite: Bitmap = silhouette(barrel, Color.WHITE)
    val chest: Bitmap = make(170, 130) { c, p -> drawChest(c, p) }
    val chestWhite: Bitmap = silhouette(chest, Color.WHITE)
    val circle: Bitmap = make(256, 256) { c, p -> drawMagicCircle(c, p, Color.rgb(255, 210, 110), Color.argb(150, 255, 170, 60)) }
    val circleBlue: Bitmap = make(256, 256) { c, p -> drawMagicCircle(c, p, Color.rgb(150, 220, 255), Color.argb(120, 80, 170, 255)) }
    val boulder: Bitmap = make(120, 110) { c, p -> drawBoulder(c, p) }
    val arrow: Bitmap = make(24, 120) { c, p -> drawArrow(c, p) }

    // ------------------------------------------------------------------ décor
    val pine: Bitmap = make(160, 260) { c, p -> drawPine(c, p) }
    val oak: Bitmap = make(220, 240) { c, p -> drawOak(c, p) }
    val bush: Bitmap = make(140, 80) { c, p -> drawBush(c, p) }
    val rock: Bitmap = make(140, 90) { c, p -> drawRock(c, p, Color.rgb(140, 135, 128), Color.rgb(80, 76, 72)) }
    val darkRock: Bitmap = make(140, 200) { c, p -> drawSpire(c, p) }
    val reeds: Bitmap = make(120, 120) { c, p -> drawReeds(c, p) }
    val brazier: Bitmap = make(80, 140) { c, p -> drawBrazier(c, p) }
    val post: Bitmap = make(40, 160) { c, p -> drawPost(c, p) }
    val banner: Bitmap = make(70, 220) { c, p -> drawBanner(c, p) }

    // ------------------------------------------------------------------ lueurs
    val glowFire: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(255, 236, 140), Color.rgb(255, 140, 30), Color.argb(0, 255, 60, 0)))
    val glowBlue: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(170, 235, 255), Color.rgb(60, 150, 255), Color.argb(0, 30, 60, 255)))
    val glowGold: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(255, 245, 170), Color.rgb(255, 200, 40), Color.argb(0, 255, 160, 0)))
    val glowPurple: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(230, 200, 255), Color.rgb(150, 90, 255), Color.argb(0, 90, 40, 255)))
    val glowIce: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(220, 250, 255), Color.rgb(120, 220, 255), Color.argb(0, 80, 200, 255)))
    val glowGreen: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(200, 255, 200), Color.rgb(80, 230, 110), Color.argb(0, 40, 200, 80)))
    val glowRed: Bitmap = glow(64, intArrayOf(Color.rgb(255, 220, 200), Color.rgb(255, 80, 60), Color.rgb(200, 20, 20), Color.argb(0, 150, 0, 0)))
    val smoke: Bitmap = glow(64, intArrayOf(Color.argb(170, 120, 115, 110), Color.argb(110, 100, 95, 90), Color.argb(0, 90, 85, 80)))
    val shadowBlob: Bitmap = glow(64, intArrayOf(Color.argb(120, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)))

    // ------------------------------------------------------------------ icônes
    val iconSword: Bitmap = make(96, 96) { c, p -> drawSwordIcon(c, p) }
    val iconBolt: Bitmap = make(96, 96) { c, p -> drawBoltIcon(c, p) }
    val iconHeart: Bitmap = make(96, 96) { c, p -> drawHeartIcon(c, p) }
    val skillIcons: Array<Bitmap> = arrayOf(
        make(96, 96) { c, p -> drawFireIcon(c, p) },
        make(96, 96) { c, p -> drawStormIcon(c, p) },
        make(96, 96) { c, p -> drawFrostIcon(c, p) },
        make(96, 96) { c, p -> drawArrowsIcon(c, p) },
        make(96, 96) { c, p -> drawHolyIcon(c, p) }
    )

    // ================================================================== outils

    private fun make(w: Int, h: Int, f: (Canvas, Paint) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        f(c, p)
        return b
    }

    private fun silhouette(src: Bitmap, color: Int): Bitmap {
        val b = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        return b
    }

    private fun glow(size: Int, colors: IntArray): Bitmap = make(size, size) { c, p ->
        val r = size / 2f
        val stops = FloatArray(colors.size) { it / (colors.size - 1f) }
        shade(p, RadialGradient(r, r, r, colors, stops, Shader.TileMode.CLAMP))
        c.drawCircle(r, r, r, p)
    }

    private fun shade(p: Paint, s: Shader) {
        p.color = Color.WHITE
        p.style = Paint.Style.FILL
        p.shader = s
    }

    private fun vgrad(p: Paint, y0: Float, y1: Float, c0: Int, c1: Int) =
        shade(p, LinearGradient(0f, y0, 0f, y1, c0, c1, Shader.TileMode.CLAMP))

    private fun rgrad(p: Paint, x: Float, y: Float, r: Float, c0: Int, c1: Int) =
        shade(p, RadialGradient(x, y, r, c0, c1, Shader.TileMode.CLAMP))

    private fun orb(c: Canvas, p: Paint, x: Float, y: Float, r: Float, mid: Int) {
        shade(p, RadialGradient(x, y, r, intArrayOf(Color.WHITE, mid, Color.argb(0, Color.red(mid), Color.green(mid), Color.blue(mid))),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP))
        c.drawCircle(x, y, r, p)
    }

    private fun solid(p: Paint, col: Int) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = col
    }

    private fun line(c: Canvas, p: Paint, col: Int, w: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = col
        c.drawLine(x0, y0, x1, y1, p)
        p.style = Paint.Style.FILL
    }

    private fun outline(c: Canvas, p: Paint, path: Path, col: Int, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeJoin = Paint.Join.ROUND
        p.color = col
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun poly(vararg v: Float): Path {
        val path = Path()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i + 1 < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
        path.close()
        return path
    }

    // ================================================================== allié (vu de dos)

    private fun drawAlly(c: Canvas, p: Paint) {
        solid(p, Color.rgb(30, 35, 60))
        c.drawRoundRect(RectF(34f, 104f, 46f, 126f), 5f, 5f, p)
        c.drawRoundRect(RectF(50f, 104f, 62f, 126f), 5f, 5f, p)
        line(c, p, Color.rgb(110, 75, 40), 5f, 74f, 112f, 80f, 14f)
        orb(c, p, 80f, 14f, 13f, Color.rgb(120, 220, 255))
        val robe = Path().apply {
            moveTo(30f, 46f); lineTo(66f, 46f)
            quadTo(74f, 80f, 78f, 112f)
            quadTo(48f, 122f, 18f, 112f)
            quadTo(22f, 80f, 30f, 46f); close()
        }
        vgrad(p, 46f, 115f, Color.rgb(70, 125, 255), Color.rgb(22, 50, 160))
        c.drawPath(robe, p)
        line(c, p, Color.argb(90, 10, 20, 70), 3f, 40f, 70f, 36f, 110f)
        line(c, p, Color.argb(90, 10, 20, 70), 3f, 56f, 70f, 60f, 110f)
        val cape = Path().apply { moveTo(32f, 46f); lineTo(64f, 46f); lineTo(60f, 96f); quadTo(48f, 102f, 36f, 96f); close() }
        vgrad(p, 46f, 100f, Color.rgb(230, 235, 250), Color.rgb(160, 170, 210))
        c.drawPath(cape, p)
        solid(p, Color.rgb(230, 180, 60))
        c.drawRect(26f, 78f, 70f, 84f, p)
        vgrad(p, 40f, 58f, Color.rgb(200, 210, 230), Color.rgb(110, 120, 150))
        c.drawOval(RectF(20f, 42f, 42f, 58f), p)
        c.drawOval(RectF(54f, 42f, 76f, 58f), p)
        rgrad(p, 44f, 22f, 22f, Color.rgb(90, 140, 255), Color.rgb(20, 40, 130))
        c.drawCircle(48f, 30f, 18f, p)
        solid(p, Color.argb(70, 255, 255, 255))
        c.drawCircle(42f, 22f, 6f, p)
    }

    // ================================================================== héros (vus de dos)

    private fun drawHero(c: Canvas, p: Paint, t: HeroType) {
        val robeTop: Int; val robeBot: Int; val trim: Int; val orbCol: Int
        when (t) {
            HeroType.FIRE -> { robeTop = Color.rgb(230, 50, 45); robeBot = Color.rgb(120, 15, 20); trim = Color.rgb(240, 190, 70); orbCol = Color.rgb(255, 120, 40) }
            HeroType.STORM -> { robeTop = Color.rgb(120, 80, 230); robeBot = Color.rgb(40, 20, 110); trim = Color.rgb(255, 230, 90); orbCol = Color.rgb(200, 150, 255) }
            HeroType.FROST -> { robeTop = Color.rgb(230, 245, 255); robeBot = Color.rgb(90, 160, 210); trim = Color.rgb(120, 220, 255); orbCol = Color.rgb(120, 230, 255) }
            HeroType.RANGER -> { robeTop = Color.rgb(70, 150, 70); robeBot = Color.rgb(25, 70, 35); trim = Color.rgb(170, 120, 60); orbCol = Color.rgb(200, 255, 150) }
            HeroType.PRIEST -> { robeTop = Color.rgb(255, 250, 235); robeBot = Color.rgb(210, 190, 140); trim = Color.rgb(255, 205, 70); orbCol = Color.rgb(255, 230, 120) }
        }
        // arme (derrière ou à droite)
        if (t == HeroType.RANGER) {
            // arc tenu à gauche
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = Color.rgb(120, 70, 30); p.strokeCap = Paint.Cap.ROUND
            c.drawArc(RectF(-6f, 40f, 46f, 150f), 280f, 160f, false, p)
            p.strokeWidth = 2f; p.color = Color.rgb(240, 240, 230)
            c.drawLine(28f, 44f, 28f, 146f, p)
            p.style = Paint.Style.FILL
            // carquois
            vgrad(p, 50f, 120f, Color.rgb(150, 95, 45), Color.rgb(90, 50, 20))
            c.save(); c.rotate(18f, 80f, 80f)
            c.drawRoundRect(RectF(72f, 40f, 92f, 118f), 8f, 8f, p)
            for (k in 0..3) line(c, p, Color.rgb(240, 240, 240), 3f, 76f + k * 4f, 40f, 76f + k * 4f, 26f)
            for (k in 0..3) { solid(p, Color.rgb(220, 60, 50)); c.drawRect(73f + k * 4f, 22f, 79f + k * 4f, 30f, p) }
            c.restore()
        } else {
            line(c, p, Color.rgb(90, 50, 25), 7f, 98f, 168f, 110f, 26f)
            solid(p, trim)
            c.drawCircle(109f, 34f, 9f, p)
            when (t) {
                HeroType.FROST -> {
                    vgrad(p, 0f, 34f, Color.WHITE, Color.rgb(100, 210, 255))
                    c.drawPath(poly(110f, 0f, 120f, 18f, 110f, 34f, 100f, 18f), p)
                    orb(c, p, 110f, 18f, 22f, Color.argb(200, 120, 230, 255))
                }
                HeroType.PRIEST -> {
                    p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = trim
                    c.drawCircle(110f, 18f, 13f, p)
                    p.style = Paint.Style.FILL
                    for (k in 0 until 8) {
                        val a = k * Math.PI / 4
                        line(c, p, trim, 3f, 110f + 15f * cos(a).toFloat(), 18f + 15f * sin(a).toFloat(), 110f + 21f * cos(a).toFloat(), 18f + 21f * sin(a).toFloat())
                    }
                    orb(c, p, 110f, 18f, 20f, orbCol)
                }
                else -> orb(c, p, 110f, 18f, 22f, orbCol)
            }
        }
        // robe / tunique
        val long = t != HeroType.RANGER
        val robe = if (long) Path().apply {
            moveTo(42f, 60f); lineTo(86f, 60f)
            quadTo(98f, 120f, 108f, 172f)
            quadTo(64f, 182f, 20f, 172f)
            quadTo(30f, 120f, 42f, 60f); close()
        } else Path().apply {
            moveTo(44f, 60f); lineTo(84f, 60f); quadTo(92f, 100f, 94f, 130f); quadTo(64f, 138f, 34f, 130f); quadTo(36f, 100f, 44f, 60f); close()
        }
        if (!long) {
            // jambes et bottes
            vgrad(p, 120f, 176f, Color.rgb(90, 70, 50), Color.rgb(50, 35, 25))
            c.drawRoundRect(RectF(44f, 120f, 60f, 176f), 7f, 7f, p)
            c.drawRoundRect(RectF(68f, 120f, 84f, 176f), 7f, 7f, p)
        }
        vgrad(p, 60f, 176f, robeTop, robeBot)
        c.drawPath(robe, p)
        outline(c, p, robe, Color.argb(70, 0, 0, 0), 2f)
        // bordure
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = trim
        if (long) c.drawPath(Path().apply { moveTo(22f, 170f); quadTo(64f, 180f, 106f, 170f) }, p)
        else c.drawPath(Path().apply { moveTo(36f, 128f); quadTo(64f, 136f, 92f, 128f) }, p)
        p.style = Paint.Style.FILL
        line(c, p, Color.argb(80, 0, 0, 0), 4f, 54f, 90f, 46f, if (long) 166f else 126f)
        line(c, p, Color.argb(80, 0, 0, 0), 4f, 74f, 90f, 82f, if (long) 166f else 126f)
        // cape pour certains
        if (t == HeroType.STORM || t == HeroType.FROST) {
            val cape = Path().apply { moveTo(40f, 62f); lineTo(88f, 62f); quadTo(96f, 120f, 92f, 160f); lineTo(36f, 160f); quadTo(32f, 120f, 40f, 62f); close() }
            vgrad(p, 62f, 160f, if (t == HeroType.STORM) Color.rgb(60, 40, 140) else Color.rgb(170, 220, 250), if (t == HeroType.STORM) Color.rgb(25, 15, 70) else Color.rgb(90, 150, 210))
            c.drawPath(cape, p)
            // motifs
            if (t == HeroType.STORM) {
                solid(p, Color.rgb(255, 230, 90))
                c.drawPath(poly(64f, 90f, 54f, 112f, 64f, 112f, 58f, 134f, 74f, 106f, 64f, 106f), p)
            } else {
                for (k in 0 until 3) {
                    val cy = 92f + k * 24f
                    line(c, p, Color.WHITE, 2.5f, 58f, cy, 70f, cy)
                    line(c, p, Color.WHITE, 2.5f, 64f, cy - 6f, 64f, cy + 6f)
                }
            }
        }
        // ceinture
        solid(p, trim)
        c.drawRect(36f, 100f, 92f, 108f, p)
        // bras levé
        vgrad(p, 50f, 90f, robeTop, robeBot)
        c.drawRoundRect(RectF(80f, 56f, 100f, 96f), 10f, 10f, p)
        // épaules
        vgrad(p, 52f, 76f, robeTop, robeBot)
        c.drawOval(RectF(32f, 52f, 96f, 80f), p)
        // tête
        when (t) {
            HeroType.FIRE -> {
                rgrad(p, 58f, 30f, 28f, Color.rgb(255, 170, 80), Color.rgb(170, 70, 20))
                c.drawCircle(64f, 40f, 22f, p)
                solid(p, Color.rgb(255, 210, 80))
                c.drawPath(poly(46f, 30f, 50f, 16f, 57f, 26f, 64f, 12f, 71f, 26f, 78f, 16f, 82f, 30f), p)
            }
            HeroType.STORM -> {
                rgrad(p, 58f, 36f, 26f, Color.rgb(230, 230, 240), Color.rgb(150, 150, 170))
                c.drawCircle(64f, 44f, 19f, p)
                vgrad(p, -4f, 44f, Color.rgb(140, 100, 255), Color.rgb(50, 25, 130))
                c.drawPath(Path().apply { moveTo(36f, 44f); quadTo(64f, 30f, 92f, 44f); lineTo(76f, 30f); quadTo(74f, 10f, 92f, 0f); quadTo(58f, 4f, 52f, 30f); close() }, p)
                solid(p, Color.rgb(255, 230, 90))
                c.drawRect(44f, 36f, 84f, 42f, p)
            }
            HeroType.FROST -> {
                vgrad(p, 16f, 66f, Color.rgb(240, 250, 255), Color.rgb(150, 200, 235))
                c.drawPath(Path().apply { moveTo(40f, 62f); quadTo(36f, 26f, 64f, 16f); quadTo(92f, 26f, 88f, 62f); quadTo(64f, 70f, 40f, 62f); close() }, p)
                line(c, p, Color.rgb(120, 220, 255), 3f, 64f, 18f, 64f, 60f)
            }
            HeroType.RANGER -> {
                rgrad(p, 58f, 30f, 28f, Color.rgb(250, 220, 120), Color.rgb(170, 110, 30))
                c.drawCircle(64f, 40f, 20f, p)
                // queue de cheval
                vgrad(p, 40f, 100f, Color.rgb(240, 200, 100), Color.rgb(160, 100, 30))
                c.drawPath(Path().apply { moveTo(58f, 50f); quadTo(48f, 80f, 60f, 100f); quadTo(74f, 80f, 70f, 50f); close() }, p)
                // capuche verte rabattue
                vgrad(p, 50f, 70f, Color.rgb(80, 160, 80), Color.rgb(30, 80, 40))
                c.drawOval(RectF(40f, 54f, 88f, 72f), p)
            }
            HeroType.PRIEST -> {
                p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(255, 215, 90)
                c.drawOval(RectF(42f, 6f, 86f, 20f), p)
                p.style = Paint.Style.FILL
                vgrad(p, 16f, 70f, Color.rgb(255, 255, 250), Color.rgb(220, 205, 170))
                c.drawPath(Path().apply { moveTo(38f, 66f); quadTo(36f, 26f, 64f, 20f); quadTo(92f, 26f, 90f, 66f); quadTo(64f, 74f, 38f, 66f); close() }, p)
                solid(p, Color.rgb(255, 205, 70))
                c.drawRect(60f, 26f, 68f, 56f, p)
                c.drawRect(54f, 34f, 74f, 40f, p)
            }
        }
    }

    // ================================================================== ennemis (vus de face)

    private fun drawSkeleton(c: Canvas, p: Paint) {
        line(c, p, Color.rgb(200, 195, 180), 7f, 40f, 100f, 38f, 124f)
        line(c, p, Color.rgb(200, 195, 180), 7f, 56f, 100f, 58f, 124f)
        line(c, p, Color.rgb(210, 215, 225), 6f, 78f, 70f, 90f, 20f)
        line(c, p, Color.rgb(120, 90, 50), 6f, 72f, 72f, 84f, 66f)
        val body = Path().apply { moveTo(30f, 48f); lineTo(66f, 48f); lineTo(70f, 92f); quadTo(48f, 104f, 26f, 92f); close() }
        vgrad(p, 48f, 100f, Color.rgb(95, 100, 115), Color.rgb(40, 42, 52))
        c.drawPath(body, p)
        for (i in 0..2) line(c, p, Color.argb(160, 220, 215, 200), 3f, 38f, 60f + i * 9f, 58f, 60f + i * 9f)
        line(c, p, Color.rgb(200, 195, 180), 6f, 66f, 54f, 76f, 72f)
        rgrad(p, 24f, 72f, 22f, Color.rgb(150, 95, 50), Color.rgb(80, 45, 20))
        c.drawCircle(24f, 74f, 20f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(170, 170, 180)
        c.drawCircle(24f, 74f, 19f, p)
        p.style = Paint.Style.FILL
        solid(p, Color.rgb(190, 190, 200))
        c.drawCircle(24f, 74f, 5f, p)
        rgrad(p, 44f, 26f, 22f, Color.rgb(250, 245, 230), Color.rgb(180, 172, 150))
        c.drawCircle(48f, 32f, 17f, p)
        solid(p, Color.rgb(40, 20, 20))
        c.drawCircle(42f, 33f, 5f, p)
        c.drawCircle(54f, 33f, 5f, p)
        solid(p, Color.rgb(255, 60, 40))
        c.drawCircle(42f, 33f, 2.5f, p)
        c.drawCircle(54f, 33f, 2.5f, p)
        vgrad(p, 10f, 28f, Color.rgb(120, 125, 140), Color.rgb(50, 52, 62))
        c.drawArc(RectF(30f, 12f, 66f, 44f), 180f, 180f, true, p)
        solid(p, Color.rgb(230, 225, 205))
        c.drawPath(Path().apply { moveTo(32f, 24f); quadTo(18f, 18f, 20f, 4f); quadTo(28f, 16f, 36f, 18f); close() }, p)
        c.drawPath(Path().apply { moveTo(64f, 24f); quadTo(78f, 18f, 76f, 4f); quadTo(68f, 16f, 60f, 18f); close() }, p)
    }

    private fun drawGoblin(c: Canvas, p: Paint) {
        // jambes fines
        line(c, p, Color.rgb(70, 120, 50), 7f, 40f, 84f, 34f, 108f)
        line(c, p, Color.rgb(70, 120, 50), 7f, 56f, 84f, 62f, 108f)
        // dague
        line(c, p, Color.rgb(220, 225, 235), 5f, 78f, 62f, 90f, 40f)
        line(c, p, Color.rgb(90, 60, 30), 5f, 74f, 68f, 80f, 60f)
        // corps
        vgrad(p, 46f, 90f, Color.rgb(140, 95, 55), Color.rgb(80, 50, 25))
        c.drawRoundRect(RectF(30f, 48f, 66f, 90f), 14f, 14f, p)
        line(c, p, Color.rgb(60, 35, 15), 3f, 34f, 60f, 62f, 76f)
        // bras
        line(c, p, Color.rgb(90, 150, 60), 6f, 64f, 56f, 76f, 66f)
        line(c, p, Color.rgb(90, 150, 60), 6f, 32f, 56f, 22f, 72f)
        // tête
        rgrad(p, 44f, 30f, 24f, Color.rgb(140, 200, 90), Color.rgb(60, 120, 40))
        c.drawCircle(48f, 36f, 16f, p)
        // oreilles
        c.drawPath(poly(34f, 34f, 8f, 22f, 34f, 44f), p)
        c.drawPath(poly(62f, 34f, 88f, 22f, 62f, 44f), p)
        solid(p, Color.rgb(255, 230, 60))
        c.drawCircle(42f, 34f, 4f, p)
        c.drawCircle(54f, 34f, 4f, p)
        solid(p, Color.BLACK)
        c.drawCircle(42f, 34f, 1.8f, p)
        c.drawCircle(54f, 34f, 1.8f, p)
        solid(p, Color.rgb(250, 250, 240))
        c.drawPath(poly(42f, 44f, 45f, 48f, 48f, 44f, 51f, 48f, 54f, 44f), p)
        // bonnet
        vgrad(p, 10f, 30f, Color.rgb(170, 40, 40), Color.rgb(100, 20, 20))
        c.drawPath(Path().apply { moveTo(32f, 30f); quadTo(48f, 14f, 64f, 30f); quadTo(60f, 10f, 74f, 6f); quadTo(50f, 2f, 32f, 30f); close() }, p)
    }

    private fun drawBrute(c: Canvas, p: Paint) {
        // jambes
        vgrad(p, 120f, 170f, Color.rgb(90, 70, 50), Color.rgb(50, 35, 25))
        c.drawRoundRect(RectF(50f, 118f, 76f, 168f), 10f, 10f, p)
        c.drawRoundRect(RectF(86f, 118f, 112f, 168f), 10f, 10f, p)
        // massue
        line(c, p, Color.rgb(110, 70, 35), 12f, 132f, 120f, 150f, 30f)
        rgrad(p, 150f, 30f, 26f, Color.rgb(140, 95, 55), Color.rgb(70, 40, 20))
        c.drawCircle(150f, 32f, 22f, p)
        solid(p, Color.rgb(200, 200, 210))
        for (a in 0 until 6) {
            val an = a * Math.PI / 3
            c.drawCircle(150f + 24f * cos(an).toFloat(), 32f + 24f * sin(an).toFloat(), 4.5f, p)
        }
        // torse
        rgrad(p, 80f, 80f, 70f, Color.rgb(130, 160, 100), Color.rgb(60, 85, 45))
        c.drawPath(Path().apply { moveTo(30f, 60f); quadTo(80f, 30f, 130f, 60f); lineTo(122f, 126f); quadTo(80f, 140f, 38f, 126f); close() }, p)
        // armure
        vgrad(p, 64f, 120f, Color.rgb(120, 120, 135), Color.rgb(55, 55, 65))
        c.drawRoundRect(RectF(50f, 72f, 110f, 120f), 12f, 12f, p)
        solid(p, Color.rgb(180, 180, 190))
        for (k in 0..2) c.drawCircle(60f + k * 20f, 80f, 3.5f, p)
        // ceinture
        solid(p, Color.rgb(90, 55, 25))
        c.drawRect(44f, 112f, 116f, 122f, p)
        solid(p, Color.rgb(220, 180, 60))
        c.drawRect(72f, 110f, 88f, 124f, p)
        // bras
        rgrad(p, 24f, 90f, 30f, Color.rgb(130, 160, 100), Color.rgb(60, 85, 45))
        c.drawRoundRect(RectF(10f, 62f, 40f, 122f), 14f, 14f, p)
        rgrad(p, 136f, 90f, 30f, Color.rgb(130, 160, 100), Color.rgb(60, 85, 45))
        c.drawRoundRect(RectF(120f, 62f, 150f, 120f), 14f, 14f, p)
        // épaulières
        vgrad(p, 50f, 76f, Color.rgb(160, 160, 175), Color.rgb(70, 70, 80))
        c.drawOval(RectF(8f, 50f, 50f, 78f), p)
        c.drawOval(RectF(110f, 50f, 152f, 78f), p)
        // tête
        rgrad(p, 76f, 34f, 30f, Color.rgb(140, 170, 105), Color.rgb(70, 95, 50))
        c.drawCircle(80f, 42f, 24f, p)
        solid(p, Color.rgb(250, 245, 225))
        c.drawPath(poly(66f, 52f, 70f, 40f, 74f, 52f), p)
        c.drawPath(poly(86f, 52f, 90f, 40f, 94f, 52f), p)
        solid(p, Color.rgb(255, 80, 40))
        c.drawCircle(70f, 38f, 4f, p)
        c.drawCircle(90f, 38f, 4f, p)
        solid(p, Color.rgb(50, 60, 40))
        c.drawRect(62f, 30f, 98f, 34f, p)
        // casque
        vgrad(p, 14f, 34f, Color.rgb(140, 140, 150), Color.rgb(60, 60, 70))
        c.drawArc(RectF(54f, 16f, 106f, 56f), 180f, 180f, true, p)
    }

    private fun drawKnight(c: Canvas, p: Paint) {
        // cape rouge
        vgrad(p, 50f, 205f, Color.rgb(170, 20, 30), Color.rgb(70, 5, 10))
        c.drawPath(Path().apply { moveTo(40f, 56f); lineTo(120f, 56f); lineTo(140f, 204f); lineTo(20f, 204f); close() }, p)
        // jambes
        vgrad(p, 140f, 208f, Color.rgb(70, 72, 85), Color.rgb(25, 25, 32))
        c.drawRoundRect(RectF(54f, 138f, 76f, 206f), 8f, 8f, p)
        c.drawRoundRect(RectF(84f, 138f, 106f, 206f), 8f, 8f, p)
        // épée levée
        vgrad(p, 0f, 120f, Color.rgb(240, 240, 250), Color.rgb(140, 145, 165))
        c.save(); c.rotate(14f, 132f, 110f)
        c.drawPath(poly(126f, 6f, 138f, 6f, 138f, 108f, 126f, 108f), p)
        c.drawPath(poly(126f, 6f, 132f, -6f, 138f, 6f), p)
        solid(p, Color.rgb(220, 180, 60))
        c.drawRect(114f, 106f, 150f, 114f, p)
        solid(p, Color.rgb(60, 40, 20))
        c.drawRect(128f, 114f, 136f, 134f, p)
        c.restore()
        // torse en plaques
        vgrad(p, 56f, 146f, Color.rgb(85, 88, 105), Color.rgb(28, 28, 38))
        c.drawPath(Path().apply { moveTo(44f, 62f); quadTo(80f, 46f, 116f, 62f); lineTo(110f, 146f); quadTo(80f, 156f, 50f, 146f); close() }, p)
        line(c, p, Color.argb(120, 200, 205, 220), 2.5f, 56f, 90f, 104f, 90f)
        line(c, p, Color.argb(120, 200, 205, 220), 2.5f, 54f, 112f, 106f, 112f)
        solid(p, Color.rgb(200, 30, 40))
        c.drawPath(poly(80f, 70f, 90f, 86f, 80f, 102f, 70f, 86f), p)
        // bouclier
        vgrad(p, 80f, 160f, Color.rgb(60, 62, 75), Color.rgb(20, 20, 28))
        val shield = Path().apply { moveTo(8f, 84f); lineTo(56f, 84f); lineTo(56f, 128f); quadTo(32f, 160f, 8f, 128f); close() }
        c.drawPath(shield, p)
        outline(c, p, shield, Color.rgb(180, 150, 70), 4f)
        solid(p, Color.rgb(180, 30, 40))
        c.drawPath(poly(32f, 94f, 42f, 114f, 32f, 134f, 22f, 114f), p)
        // épaulières à pointes
        vgrad(p, 50f, 82f, Color.rgb(110, 112, 130), Color.rgb(40, 40, 50))
        c.drawOval(RectF(30f, 54f, 66f, 82f), p)
        c.drawOval(RectF(94f, 54f, 130f, 82f), p)
        solid(p, Color.rgb(200, 200, 210))
        c.drawPath(poly(36f, 60f, 30f, 44f, 46f, 56f), p)
        c.drawPath(poly(124f, 60f, 130f, 44f, 114f, 56f), p)
        // casque
        vgrad(p, 10f, 60f, Color.rgb(100, 102, 120), Color.rgb(30, 30, 40))
        c.drawRoundRect(RectF(60f, 14f, 100f, 60f), 14f, 14f, p)
        solid(p, Color.rgb(10, 10, 14))
        c.drawRect(64f, 32f, 96f, 38f, p)
        solid(p, Color.rgb(255, 60, 40))
        c.drawRect(68f, 33f, 92f, 37f, p)
        // plumet
        vgrad(p, -4f, 20f, Color.rgb(240, 40, 50), Color.rgb(140, 10, 20))
        c.drawPath(Path().apply { moveTo(78f, 16f); quadTo(70f, -2f, 96f, 2f); quadTo(86f, 8f, 84f, 16f); close() }, p)
    }

    // ================================================================== boss

    private fun drawDemon(c: Canvas, p: Paint) {
        vgrad(p, 230f, 330f, Color.rgb(90, 30, 30), Color.rgb(40, 10, 12))
        c.drawRoundRect(RectF(95f, 230f, 140f, 328f), 18f, 18f, p)
        c.drawRoundRect(RectF(160f, 230f, 205f, 328f), 18f, 18f, p)
        line(c, p, Color.rgb(80, 50, 30), 14f, 250f, 250f, 280f, 80f)
        rgrad(p, 282f, 70f, 34f, Color.rgb(140, 140, 150), Color.rgb(50, 50, 60))
        c.drawCircle(282f, 70f, 30f, p)
        solid(p, Color.rgb(200, 200, 210))
        for (a in 0 until 8) {
            val ang = a * Math.PI / 4
            c.drawCircle(282f + 34f * cos(ang).toFloat(), 70f + 34f * sin(ang).toFloat(), 6f, p)
        }
        val torso = Path().apply { moveTo(70f, 110f); quadTo(150f, 70f, 230f, 110f); lineTo(215f, 245f); quadTo(150f, 265f, 85f, 245f); close() }
        rgrad(p, 150f, 150f, 120f, Color.rgb(190, 60, 50), Color.rgb(80, 15, 15))
        c.drawPath(torso, p)
        vgrad(p, 120f, 200f, Color.rgb(70, 70, 85), Color.rgb(25, 25, 35))
        c.drawRoundRect(RectF(105f, 130f, 195f, 210f), 20f, 20f, p)
        orb(c, p, 150f, 168f, 18f, Color.rgb(255, 140, 40))
        rgrad(p, 60f, 150f, 50f, Color.rgb(180, 55, 45), Color.rgb(80, 15, 15))
        c.drawRoundRect(RectF(30f, 110f, 80f, 240f), 24f, 24f, p)
        rgrad(p, 240f, 150f, 50f, Color.rgb(180, 55, 45), Color.rgb(80, 15, 15))
        c.drawRoundRect(RectF(220f, 110f, 268f, 250f), 24f, 24f, p)
        rgrad(p, 140f, 70f, 55f, Color.rgb(200, 70, 55), Color.rgb(90, 20, 18))
        c.drawCircle(150f, 82f, 46f, p)
        solid(p, Color.rgb(235, 225, 200))
        c.drawPath(Path().apply { moveTo(112f, 60f); quadTo(70f, 40f, 78f, 0f); quadTo(96f, 36f, 126f, 46f); close() }, p)
        c.drawPath(Path().apply { moveTo(188f, 60f); quadTo(230f, 40f, 222f, 0f); quadTo(204f, 36f, 174f, 46f); close() }, p)
        for (x in floatArrayOf(132f, 168f)) orb(c, p, x, 80f, 14f, Color.rgb(255, 230, 60))
        solid(p, Color.rgb(40, 5, 5))
        c.drawRoundRect(RectF(128f, 100f, 172f, 114f), 6f, 6f, p)
        solid(p, Color.rgb(240, 235, 220))
        for (x in floatArrayOf(134f, 146f, 158f)) c.drawPath(poly(x, 100f, x + 8f, 100f, x + 4f, 108f), p)
    }

    private fun drawGolem(c: Canvas, p: Paint) {
        fun stone(r: RectF, light: Int, dark: Int) {
            rgrad(p, r.left + r.width() * 0.35f, r.top + r.height() * 0.3f, Math.max(r.width(), r.height()), light, dark)
            c.drawRoundRect(r, 18f, 18f, p)
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.argb(110, 20, 15, 10)
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.FILL
        }
        val l = Color.rgb(160, 150, 135)
        val d = Color.rgb(70, 64, 58)
        // jambes
        stone(RectF(80f, 230f, 140f, 318f), l, d)
        stone(RectF(180f, 230f, 240f, 318f), l, d)
        // corps
        stone(RectF(70f, 100f, 250f, 250f), l, d)
        // bras énormes
        stone(RectF(4f, 100f, 78f, 200f), l, d)
        stone(RectF(10f, 190f, 84f, 270f), l, d)
        stone(RectF(242f, 100f, 316f, 200f), l, d)
        stone(RectF(236f, 190f, 310f, 270f), l, d)
        // mousse
        solid(p, Color.argb(160, 90, 140, 70))
        c.drawOval(RectF(80f, 96f, 150f, 116f), p)
        c.drawOval(RectF(250f, 96f, 300f, 112f), p)
        // runes lumineuses
        val rune = Color.rgb(90, 230, 255)
        for (pt in arrayOf(floatArrayOf(160f, 150f), floatArrayOf(40f, 140f), floatArrayOf(280f, 150f), floatArrayOf(110f, 270f), floatArrayOf(210f, 270f))) {
            orb(c, p, pt[0], pt[1], 22f, Color.argb(200, 60, 200, 255))
        }
        line(c, p, rune, 5f, 140f, 140f, 180f, 160f)
        line(c, p, rune, 5f, 180f, 140f, 140f, 160f)
        line(c, p, rune, 5f, 160f, 130f, 160f, 200f)
        // tête
        stone(RectF(120f, 36f, 200f, 110f), l, d)
        solid(p, Color.rgb(30, 25, 20))
        c.drawRect(132f, 64f, 188f, 80f, p)
        orb(c, p, 145f, 72f, 14f, rune)
        orb(c, p, 175f, 72f, 14f, rune)
    }

    private fun drawNecro(c: Canvas, p: Paint) {
        // bâton
        line(c, p, Color.rgb(60, 50, 50), 9f, 230f, 330f, 250f, 40f)
        solid(p, Color.rgb(230, 225, 210))
        c.drawCircle(250f, 40f, 16f, p)
        solid(p, Color.BLACK)
        c.drawCircle(244f, 38f, 4f, p); c.drawCircle(256f, 38f, 4f, p)
        orb(c, p, 250f, 40f, 40f, Color.argb(180, 80, 255, 120))
        // robe
        vgrad(p, 90f, 340f, Color.rgb(80, 40, 110), Color.rgb(20, 8, 30))
        c.drawPath(Path().apply { moveTo(90f, 100f); lineTo(190f, 100f); quadTo(230f, 220f, 240f, 335f); lineTo(40f, 335f); quadTo(50f, 220f, 90f, 100f); close() }, p)
        // bandes
        line(c, p, Color.rgb(120, 230, 140), 4f, 140f, 120f, 140f, 330f)
        for (k in 0 until 4) {
            val y = 150f + k * 45f
            line(c, p, Color.argb(160, 120, 230, 140), 3f, 120f, y, 160f, y)
        }
        // manches
        vgrad(p, 100f, 220f, Color.rgb(90, 45, 125), Color.rgb(30, 12, 45))
        c.drawPath(Path().apply { moveTo(90f, 110f); quadTo(40f, 160f, 30f, 220f); lineTo(80f, 210f); quadTo(90f, 160f, 110f, 130f); close() }, p)
        c.drawPath(Path().apply { moveTo(190f, 110f); quadTo(230f, 140f, 240f, 190f); lineTo(214f, 196f); quadTo(200f, 150f, 176f, 130f); close() }, p)
        // mains osseuses
        solid(p, Color.rgb(220, 215, 195))
        c.drawCircle(55f, 222f, 12f, p)
        c.drawCircle(236f, 196f, 11f, p)
        orb(c, p, 55f, 236f, 26f, Color.argb(200, 80, 255, 120))
        // capuche et crâne
        vgrad(p, 20f, 120f, Color.rgb(95, 50, 130), Color.rgb(30, 12, 45))
        c.drawPath(Path().apply { moveTo(80f, 120f); quadTo(70f, 30f, 140f, 14f); quadTo(210f, 30f, 200f, 120f); quadTo(140f, 134f, 80f, 120f); close() }, p)
        solid(p, Color.rgb(15, 5, 20))
        c.drawOval(RectF(102f, 50f, 178f, 124f), p)
        rgrad(p, 134f, 74f, 40f, Color.rgb(245, 240, 225), Color.rgb(170, 160, 140))
        c.drawOval(RectF(112f, 58f, 168f, 116f), p)
        solid(p, Color.rgb(20, 10, 10))
        c.drawCircle(128f, 84f, 9f, p); c.drawCircle(152f, 84f, 9f, p)
        orb(c, p, 128f, 84f, 12f, Color.rgb(80, 255, 120))
        orb(c, p, 152f, 84f, 12f, Color.rgb(80, 255, 120))
        solid(p, Color.rgb(20, 10, 10))
        c.drawRect(126f, 102f, 154f, 108f, p)
        // couronne
        solid(p, Color.rgb(140, 230, 160))
        c.drawPath(poly(112f, 56f, 116f, 30f, 126f, 48f, 140f, 22f, 154f, 48f, 164f, 30f, 168f, 56f), p)
    }

    private fun drawBlackDragon(c: Canvas, p: Paint) {
        val cx = 220f
        for (side in intArrayOf(-1, 1)) {
            val w = Path().apply {
                moveTo(cx + side * 40f, 140f)
                quadTo(cx + side * 120f, 20f, cx + side * 215f, 10f)
                lineTo(cx + side * 195f, 70f)
                lineTo(cx + side * 215f, 110f)
                lineTo(cx + side * 170f, 130f)
                lineTo(cx + side * 190f, 175f)
                lineTo(cx + side * 120f, 170f)
                quadTo(cx + side * 80f, 200f, cx + side * 40f, 190f)
                close()
            }
            shade(p, LinearGradient(cx, 20f, cx + side * 210f, 180f, Color.rgb(90, 60, 120), Color.rgb(25, 15, 35), Shader.TileMode.CLAMP))
            c.drawPath(w, p)
            for (k in 0..3) line(c, p, Color.rgb(15, 8, 20), 5f, cx + side * 42f, 146f, cx + side * (215f - k * 22f), 10f + k * 40f)
        }
        // corps
        rgrad(p, cx, 170f, 90f, Color.rgb(70, 55, 90), Color.rgb(15, 10, 20))
        c.drawOval(RectF(cx - 70f, 110f, cx + 70f, 296f), p)
        // ventre
        vgrad(p, 150f, 290f, Color.rgb(160, 110, 60), Color.rgb(80, 50, 25))
        c.drawOval(RectF(cx - 36f, 150f, cx + 36f, 290f), p)
        for (k in 0 until 6) line(c, p, Color.argb(120, 40, 20, 10), 3f, cx - 28f, 168f + k * 20f, cx + 28f, 168f + k * 20f)
        // griffes
        solid(p, Color.rgb(30, 20, 35))
        c.drawRoundRect(RectF(cx - 90f, 230f, cx - 50f, 298f), 16f, 16f, p)
        c.drawRoundRect(RectF(cx + 50f, 230f, cx + 90f, 298f), 16f, 16f, p)
        // cou + tête
        rgrad(p, cx, 80f, 70f, Color.rgb(80, 60, 100), Color.rgb(20, 12, 28))
        c.drawRoundRect(RectF(cx - 34f, 70f, cx + 34f, 160f), 30f, 30f, p)
        c.drawOval(RectF(cx - 56f, 20f, cx + 56f, 110f), p)
        // cornes
        solid(p, Color.rgb(220, 210, 190))
        c.drawPath(Path().apply { moveTo(cx - 40f, 40f); quadTo(cx - 80f, 20f, cx - 76f, -4f); quadTo(cx - 60f, 20f, cx - 26f, 30f); close() }, p)
        c.drawPath(Path().apply { moveTo(cx + 40f, 40f); quadTo(cx + 80f, 20f, cx + 76f, -4f); quadTo(cx + 60f, 20f, cx + 26f, 30f); close() }, p)
        // yeux et gueule de feu
        orb(c, p, cx - 22f, 56f, 13f, Color.rgb(255, 160, 30))
        orb(c, p, cx + 22f, 56f, 13f, Color.rgb(255, 160, 30))
        solid(p, Color.rgb(30, 5, 5))
        c.drawRoundRect(RectF(cx - 30f, 78f, cx + 30f, 102f), 10f, 10f, p)
        orb(c, p, cx, 92f, 24f, Color.rgb(255, 120, 20))
        solid(p, Color.rgb(240, 235, 220))
        for (k in 0 until 5) {
            val x = cx - 26f + k * 12f
            c.drawPath(poly(x, 78f, x + 8f, 78f, x + 4f, 88f), p)
        }
    }

    // ================================================================== dragon allié (de dos)

    private fun drawDragon(c: Canvas, p: Paint) {
        val cx = 210f
        for (side in intArrayOf(-1, 1)) {
            val w = Path().apply {
                moveTo(cx + side * 20f, 110f)
                quadTo(cx + side * 120f, 10f, cx + side * 205f, 40f)
                lineTo(cx + side * 175f, 95f)
                lineTo(cx + side * 190f, 120f)
                lineTo(cx + side * 140f, 140f)
                lineTo(cx + side * 150f, 170f)
                lineTo(cx + side * 90f, 160f)
                quadTo(cx + side * 50f, 170f, cx + side * 20f, 150f)
                close()
            }
            shade(p, LinearGradient(cx, 40f, cx + side * 200f, 150f, Color.rgb(230, 80, 50), Color.rgb(110, 20, 20), Shader.TileMode.CLAMP))
            c.drawPath(w, p)
            for (k in 0..3) line(c, p, Color.rgb(70, 15, 15), 4f, cx + side * 22f, 112f, cx + side * (205f - k * 30f), 40f + k * 32f)
        }
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.rgb(140, 30, 25); p.strokeWidth = 18f
        c.drawPath(Path().apply { moveTo(cx, 170f); quadTo(cx + 40f, 215f, cx - 10f, 250f) }, p)
        p.style = Paint.Style.FILL
        rgrad(p, cx, 120f, 60f, Color.rgb(230, 90, 55), Color.rgb(120, 25, 20))
        c.drawOval(RectF(cx - 38f, 70f, cx + 38f, 185f), p)
        solid(p, Color.rgb(255, 200, 80))
        for (k in 0..4) {
            val y = 85f + k * 20f
            c.drawPath(poly(cx - 7f, y + 10f, cx, y - 6f, cx + 7f, y + 10f), p)
        }
        rgrad(p, cx, 55f, 30f, Color.rgb(240, 100, 60), Color.rgb(130, 30, 22))
        c.drawOval(RectF(cx - 24f, 30f, cx + 24f, 80f), p)
        solid(p, Color.rgb(240, 225, 200))
        c.drawPath(poly(cx - 16f, 40f, cx - 34f, 14f, cx - 8f, 34f), p)
        c.drawPath(poly(cx + 16f, 40f, cx + 34f, 14f, cx + 8f, 34f), p)
    }

    // ================================================================== objets

    private fun drawBarrel(c: Canvas, p: Paint) {
        val body = Path().apply { moveTo(18f, 30f); quadTo(4f, 80f, 18f, 132f); lineTo(110f, 132f); quadTo(124f, 80f, 110f, 30f); close() }
        shade(p, LinearGradient(4f, 0f, 124f, 0f,
            intArrayOf(Color.rgb(90, 50, 25), Color.rgb(185, 120, 65), Color.rgb(150, 92, 48), Color.rgb(80, 44, 22)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP))
        c.drawPath(body, p)
        for (i in 1..6) { val x = 18f + i * 13.2f; line(c, p, Color.argb(90, 50, 25, 10), 2f, x, 32f, x, 130f) }
        for (y in floatArrayOf(44f, 118f)) {
            shade(p, LinearGradient(8f, 0f, 120f, 0f, Color.rgb(60, 60, 70), Color.rgb(150, 150, 165), Shader.TileMode.MIRROR))
            c.drawRect(RectF(10f, y - 5f, 118f, y + 5f), p)
        }
        vgrad(p, 18f, 42f, Color.rgb(210, 150, 90), Color.rgb(140, 85, 45))
        c.drawOval(RectF(18f, 18f, 110f, 42f), p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(70, 70, 80)
        c.drawOval(RectF(18f, 18f, 110f, 42f), p)
        p.style = Paint.Style.FILL
    }

    private fun drawChest(c: Canvas, p: Paint) {
        vgrad(p, 50f, 128f, Color.rgb(170, 105, 55), Color.rgb(95, 55, 25))
        c.drawRoundRect(RectF(8f, 52f, 162f, 128f), 8f, 8f, p)
        vgrad(p, 8f, 56f, Color.rgb(200, 135, 75), Color.rgb(130, 78, 38))
        c.drawRoundRect(RectF(8f, 10f, 162f, 62f), 30f, 30f, p)
        for (x in floatArrayOf(50f, 85f, 120f)) line(c, p, Color.argb(90, 50, 25, 10), 3f, x, 14f, x, 126f)
        vgrad(p, 10f, 128f, Color.rgb(250, 210, 90), Color.rgb(170, 110, 20))
        for (x in floatArrayOf(8f, 148f)) c.drawRect(x, 10f, x + 14f, 128f, p)
        c.drawRect(8f, 56f, 162f, 64f, p)
        rgrad(p, 85f, 66f, 16f, Color.rgb(255, 235, 140), Color.rgb(200, 140, 30))
        c.drawRoundRect(RectF(72f, 52f, 98f, 82f), 6f, 6f, p)
        solid(p, Color.rgb(60, 40, 10))
        c.drawCircle(85f, 64f, 4f, p)
        c.drawRect(83f, 64f, 87f, 74f, p)
    }

    private fun drawMagicCircle(c: Canvas, p: Paint, lineCol: Int, glowCol: Int) {
        val cx = 128f
        shade(p, RadialGradient(cx, cx, 128f, intArrayOf(glowCol, Color.argb(Color.alpha(glowCol) / 3, Color.red(glowCol), Color.green(glowCol), Color.blue(glowCol)), Color.argb(0, 0, 0, 0)),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP))
        c.drawCircle(cx, cx, 126f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.color = lineCol
        p.strokeWidth = 5f
        c.drawCircle(cx, cx, 118f, p)
        p.strokeWidth = 3f
        c.drawCircle(cx, cx, 100f, p)
        c.drawCircle(cx, cx, 60f, p)
        val t1 = Path(); val t2 = Path()
        for (k in 0..2) {
            val a1 = k * 2 * Math.PI / 3 - Math.PI / 2
            val a2 = a1 + Math.PI / 3
            val x1 = cx + 100f * cos(a1).toFloat(); val y1 = cx + 100f * sin(a1).toFloat()
            val x2 = cx + 100f * cos(a2).toFloat(); val y2 = cx + 100f * sin(a2).toFloat()
            if (k == 0) { t1.moveTo(x1, y1); t2.moveTo(x2, y2) } else { t1.lineTo(x1, y1); t2.lineTo(x2, y2) }
        }
        t1.close(); t2.close()
        c.drawPath(t1, p); c.drawPath(t2, p)
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            c.drawLine(cx + 104f * cos(a).toFloat(), cx + 104f * sin(a).toFloat(), cx + 114f * cos(a + 0.1).toFloat(), cx + 114f * sin(a + 0.1).toFloat(), p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawBoulder(c: Canvas, p: Paint) {
        val path = poly(20f, 40f, 50f, 10f, 92f, 14f, 114f, 50f, 104f, 92f, 60f, 106f, 16f, 86f)
        rgrad(p, 46f, 36f, 90f, Color.rgb(170, 160, 145), Color.rgb(60, 55, 50))
        c.drawPath(path, p)
        outline(c, p, path, Color.rgb(40, 35, 30), 3f)
        line(c, p, Color.argb(120, 40, 35, 30), 3f, 50f, 30f, 70f, 60f)
        line(c, p, Color.argb(120, 40, 35, 30), 3f, 70f, 60f, 92f, 64f)
    }

    private fun drawArrow(c: Canvas, p: Paint) {
        line(c, p, Color.rgb(150, 100, 50), 4f, 12f, 10f, 12f, 100f)
        solid(p, Color.rgb(220, 220, 230))
        c.drawPath(poly(12f, 120f, 4f, 98f, 20f, 98f), p)
        solid(p, Color.rgb(240, 240, 240))
        c.drawPath(poly(12f, 0f, 2f, 18f, 12f, 12f), p)
        c.drawPath(poly(12f, 0f, 22f, 18f, 12f, 12f), p)
    }

    // ================================================================== décor

    private fun drawPine(c: Canvas, p: Paint) {
        vgrad(p, 200f, 260f, Color.rgb(110, 75, 45), Color.rgb(60, 40, 25))
        c.drawRect(70f, 200f, 90f, 260f, p)
        val layers = arrayOf(floatArrayOf(10f, 215f, 150f, 120f), floatArrayOf(22f, 160f, 138f, 70f), floatArrayOf(36f, 108f, 124f, 24f), floatArrayOf(50f, 60f, 110f, 0f))
        for ((k, l) in layers.withIndex()) {
            val path = poly(l[0], l[1], 80f, l[3], l[2], l[1])
            shade(p, LinearGradient(l[0], 0f, l[2], 0f, Color.rgb(40 + k * 8, 110 + k * 10, 60), Color.rgb(15, 55, 30), Shader.TileMode.CLAMP))
            c.drawPath(path, p)
        }
        solid(p, Color.argb(60, 255, 255, 220))
        c.drawPath(poly(80f, 4f, 60f, 60f, 72f, 56f), p)
    }

    private fun drawOak(c: Canvas, p: Paint) {
        vgrad(p, 140f, 240f, Color.rgb(120, 85, 55), Color.rgb(60, 40, 25))
        c.drawPath(poly(96f, 240f, 124f, 240f, 118f, 140f, 102f, 140f), p)
        val blobs = arrayOf(floatArrayOf(110f, 70f, 62f), floatArrayOf(60f, 110f, 52f), floatArrayOf(160f, 110f, 54f), floatArrayOf(110f, 130f, 56f), floatArrayOf(70f, 60f, 40f), floatArrayOf(150f, 56f, 42f))
        for (b in blobs) {
            rgrad(p, b[0] - b[2] * 0.3f, b[1] - b[2] * 0.4f, b[2] * 1.3f, Color.rgb(120, 180, 70), Color.rgb(30, 80, 35))
            c.drawCircle(b[0], b[1], b[2], p)
        }
    }

    private fun drawBush(c: Canvas, p: Paint) {
        for (b in arrayOf(floatArrayOf(40f, 52f, 30f), floatArrayOf(100f, 50f, 32f), floatArrayOf(70f, 38f, 34f))) {
            rgrad(p, b[0] - 8f, b[1] - 12f, b[2] * 1.3f, Color.rgb(110, 170, 70), Color.rgb(30, 80, 35))
            c.drawCircle(b[0], b[1], b[2], p)
        }
        solid(p, Color.rgb(230, 80, 90))
        c.drawCircle(56f, 30f, 4f, p); c.drawCircle(92f, 40f, 4f, p); c.drawCircle(74f, 22f, 4f, p)
    }

    private fun drawRock(c: Canvas, p: Paint, light: Int, dark: Int) {
        val path = poly(10f, 88f, 20f, 40f, 54f, 10f, 96f, 18f, 128f, 56f, 134f, 88f)
        rgrad(p, 50f, 30f, 110f, light, dark)
        c.drawPath(path, p)
        line(c, p, Color.argb(90, 0, 0, 0), 3f, 54f, 10f, 70f, 88f)
    }

    private fun drawSpire(c: Canvas, p: Paint) {
        val path = poly(10f, 200f, 40f, 100f, 60f, 10f, 82f, 80f, 100f, 40f, 130f, 200f)
        shade(p, LinearGradient(10f, 0f, 130f, 0f, Color.rgb(70, 55, 55), Color.rgb(20, 15, 18), Shader.TileMode.CLAMP))
        c.drawPath(path, p)
        line(c, p, Color.rgb(255, 120, 30), 3f, 58f, 40f, 66f, 120f)
        line(c, p, Color.rgb(255, 120, 30), 3f, 66f, 120f, 56f, 190f)
        line(c, p, Color.rgb(255, 170, 60), 2f, 98f, 70f, 104f, 150f)
    }

    private fun drawReeds(c: Canvas, p: Paint) {
        for (k in 0 until 9) {
            val x = 20f + k * 10f
            val top = 10f + (k * 37 % 30)
            line(c, p, Color.rgb(120 + k * 6, 150, 60), 4f, x, 120f, x + (k % 3 - 1) * 8f, top)
            if (k % 2 == 0) {
                solid(p, Color.rgb(120, 80, 40))
                c.drawRoundRect(RectF(x + (k % 3 - 1) * 8f - 4f, top, x + (k % 3 - 1) * 8f + 4f, top + 22f), 4f, 4f, p)
            }
        }
    }

    private fun drawBrazier(c: Canvas, p: Paint) {
        vgrad(p, 60f, 140f, Color.rgb(90, 85, 90), Color.rgb(30, 28, 32))
        c.drawPath(poly(30f, 60f, 50f, 60f, 46f, 140f, 34f, 140f), p)
        c.drawRect(20f, 130f, 60f, 140f, p)
        vgrad(p, 40f, 70f, Color.rgb(120, 115, 120), Color.rgb(40, 38, 42))
        c.drawPath(poly(6f, 44f, 74f, 44f, 60f, 70f, 20f, 70f), p)
        solid(p, Color.rgb(255, 140, 30))
        c.drawOval(RectF(10f, 38f, 70f, 52f), p)
    }

    private fun drawPost(c: Canvas, p: Paint) {
        shade(p, LinearGradient(0f, 0f, 40f, 0f, Color.rgb(150, 105, 60), Color.rgb(70, 45, 25), Shader.TileMode.CLAMP))
        c.drawRoundRect(RectF(6f, 8f, 34f, 160f), 6f, 6f, p)
        solid(p, Color.argb(90, 40, 80, 120))
        c.drawRect(6f, 120f, 34f, 160f, p)
    }

    private fun drawBanner(c: Canvas, p: Paint) {
        line(c, p, Color.rgb(80, 60, 40), 6f, 12f, 220f, 12f, 4f)
        solid(p, Color.rgb(230, 190, 70))
        c.drawCircle(12f, 6f, 6f, p)
        vgrad(p, 16f, 150f, Color.rgb(40, 80, 200), Color.rgb(15, 30, 100))
        c.drawPath(poly(14f, 16f, 66f, 16f, 66f, 150f, 40f, 130f, 14f, 150f), p)
        solid(p, Color.rgb(255, 210, 80))
        c.drawPath(poly(40f, 40f, 50f, 64f, 40f, 88f, 30f, 64f), p)
    }

    // ================================================================== icônes

    private fun drawSwordIcon(c: Canvas, p: Paint) {
        shade(p, LinearGradient(38f, 0f, 58f, 0f, Color.rgb(250, 250, 255), Color.rgb(150, 160, 180), Shader.TileMode.CLAMP))
        c.drawPath(poly(48f, 6f, 58f, 20f, 56f, 64f, 40f, 64f, 38f, 20f), p)
        solid(p, Color.rgb(230, 180, 50))
        c.drawRoundRect(RectF(24f, 62f, 72f, 72f), 5f, 5f, p)
        solid(p, Color.rgb(120, 70, 35))
        c.drawRect(43f, 72f, 53f, 88f, p)
        solid(p, Color.rgb(230, 180, 50))
        c.drawCircle(48f, 90f, 6f, p)
    }

    private fun drawBoltIcon(c: Canvas, p: Paint) {
        val b = poly(58f, 4f, 22f, 54f, 46f, 54f, 36f, 92f, 76f, 38f, 52f, 38f)
        vgrad(p, 4f, 92f, Color.rgb(255, 250, 170), Color.rgb(255, 170, 20))
        c.drawPath(b, p)
        outline(c, p, b, Color.rgb(160, 90, 0), 4f)
    }

    private fun drawHeartIcon(c: Canvas, p: Paint) {
        val h = Path().apply { moveTo(48f, 86f); quadTo(4f, 54f, 14f, 28f); quadTo(30f, 6f, 48f, 28f); quadTo(66f, 6f, 82f, 28f); quadTo(92f, 54f, 48f, 86f); close() }
        vgrad(p, 10f, 86f, Color.rgb(255, 120, 130), Color.rgb(200, 20, 50))
        c.drawPath(h, p)
        outline(c, p, h, Color.rgb(120, 0, 20), 3f)
        solid(p, Color.argb(140, 255, 255, 255))
        c.drawCircle(30f, 30f, 7f, p)
    }

    private fun drawFireIcon(c: Canvas, p: Paint) {
        orb(c, p, 48f, 56f, 40f, Color.rgb(255, 120, 30))
        val f = Path().apply { moveTo(48f, 6f); quadTo(78f, 40f, 70f, 64f); quadTo(62f, 86f, 48f, 86f); quadTo(26f, 86f, 26f, 62f); quadTo(26f, 44f, 40f, 30f); quadTo(42f, 46f, 50f, 50f); quadTo(54f, 30f, 48f, 6f); close() }
        vgrad(p, 6f, 86f, Color.rgb(255, 240, 120), Color.rgb(230, 60, 20))
        c.drawPath(f, p)
    }

    private fun drawStormIcon(c: Canvas, p: Paint) {
        orb(c, p, 48f, 48f, 44f, Color.rgb(170, 110, 255))
        val b = poly(56f, 4f, 24f, 50f, 44f, 50f, 34f, 92f, 72f, 40f, 52f, 40f)
        vgrad(p, 4f, 92f, Color.WHITE, Color.rgb(200, 170, 255))
        c.drawPath(b, p)
        outline(c, p, b, Color.rgb(90, 40, 200), 3f)
    }

    private fun drawFrostIcon(c: Canvas, p: Paint) {
        orb(c, p, 48f, 48f, 44f, Color.rgb(100, 210, 255))
        for (k in 0 until 6) {
            val a = k * Math.PI / 3
            val x = 48f + 36f * cos(a).toFloat()
            val y = 48f + 36f * sin(a).toFloat()
            line(c, p, Color.WHITE, 6f, 48f, 48f, x, y)
            val mx = 48f + 22f * cos(a).toFloat()
            val my = 48f + 22f * sin(a).toFloat()
            line(c, p, Color.WHITE, 4f, mx, my, mx + 10f * cos(a + 0.8).toFloat(), my + 10f * sin(a + 0.8).toFloat())
            line(c, p, Color.WHITE, 4f, mx, my, mx + 10f * cos(a - 0.8).toFloat(), my + 10f * sin(a - 0.8).toFloat())
        }
    }

    private fun drawArrowsIcon(c: Canvas, p: Paint) {
        orb(c, p, 48f, 48f, 44f, Color.rgb(150, 230, 110))
        for (k in 0 until 3) {
            val x = 26f + k * 22f
            line(c, p, Color.rgb(120, 75, 35), 5f, x, 8f, x, 70f)
            solid(p, Color.rgb(230, 230, 240))
            c.drawPath(poly(x, 90f, x - 9f, 66f, x + 9f, 66f), p)
            solid(p, Color.rgb(240, 70, 60))
            c.drawPath(poly(x, 6f, x - 8f, 20f, x, 16f, x + 8f, 20f), p)
        }
    }

    private fun drawHolyIcon(c: Canvas, p: Paint) {
        orb(c, p, 48f, 48f, 46f, Color.rgb(255, 220, 90))
        for (k in 0 until 12) {
            val a = k * Math.PI / 6
            line(c, p, Color.rgb(255, 240, 160), 4f, 48f + 22f * cos(a).toFloat(), 48f + 22f * sin(a).toFloat(), 48f + 40f * cos(a).toFloat(), 48f + 40f * sin(a).toFloat())
        }
        solid(p, Color.WHITE)
        c.drawRect(43f, 22f, 53f, 74f, p)
        c.drawRect(30f, 36f, 66f, 46f, p)
    }
}

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

    val ally: Bitmap = make(96, 128) { c, p -> drawAlly(c, p) }
    val enemy: Bitmap = make(96, 128) { c, p -> drawEnemy(c, p) }
    val enemyWhite: Bitmap = silhouette(enemy)
    val hero: Bitmap = make(128, 180) { c, p -> drawHero(c, p) }
    val barrel: Bitmap = make(128, 140) { c, p -> drawBarrel(c, p) }
    val barrelWhite: Bitmap = silhouette(barrel)
    val chest: Bitmap = make(170, 130) { c, p -> drawChest(c, p) }
    val chestWhite: Bitmap = silhouette(chest)
    val boss: Bitmap = make(300, 330) { c, p -> drawBoss(c, p) }
    val bossWhite: Bitmap = silhouette(boss)
    val dragon: Bitmap = make(420, 260) { c, p -> drawDragon(c, p) }
    val circle: Bitmap = make(256, 256) { c, p -> drawMagicCircle(c, p) }

    val glowFire: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(255, 236, 140), Color.rgb(255, 140, 30), Color.argb(0, 255, 60, 0)))
    val glowBlue: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(170, 235, 255), Color.rgb(60, 150, 255), Color.argb(0, 30, 60, 255)))
    val glowGold: Bitmap = glow(64, intArrayOf(Color.WHITE, Color.rgb(255, 245, 170), Color.rgb(255, 200, 40), Color.argb(0, 255, 160, 0)))
    val smoke: Bitmap = glow(64, intArrayOf(Color.argb(170, 120, 115, 110), Color.argb(110, 100, 95, 90), Color.argb(0, 90, 85, 80)))

    val iconSword: Bitmap = make(96, 96) { c, p -> drawSwordIcon(c, p) }
    val iconBolt: Bitmap = make(96, 96) { c, p -> drawBoltIcon(c, p) }

    // ------------------------------------------------------------------ outils

    private fun make(w: Int, h: Int, f: (Canvas, Paint) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        f(c, p)
        return b
    }

    private fun silhouette(src: Bitmap): Bitmap {
        val b = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        return b
    }

    private fun glow(size: Int, colors: IntArray): Bitmap = make(size, size) { c, p ->
        val r = size / 2f
        val stops = FloatArray(colors.size) { it / (colors.size - 1f) }
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(r, r, r, colors, stops, Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r, p)
    }

    private fun vgrad(p: Paint, y0: Float, y1: Float, c0: Int, c1: Int) {
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y0, 0f, y1, c0, c1, Shader.TileMode.CLAMP)
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

    // ------------------------------------------------------------------ allié (vu de dos)

    private fun drawAlly(c: Canvas, p: Paint) {
        // jambes
        solid(p, Color.rgb(30, 35, 60))
        c.drawRoundRect(RectF(34f, 104f, 46f, 126f), 5f, 5f, p)
        c.drawRoundRect(RectF(50f, 104f, 62f, 126f), 5f, 5f, p)
        // bâton
        line(c, p, Color.rgb(110, 75, 40), 5f, 74f, 112f, 80f, 14f)
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(80f, 14f, 13f, intArrayOf(Color.WHITE, Color.rgb(120, 220, 255), Color.argb(0, 60, 140, 255)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(80f, 14f, 13f, p)
        // robe
        val robe = Path().apply {
            moveTo(30f, 46f); lineTo(66f, 46f)
            quadTo(74f, 80f, 78f, 112f)
            quadTo(48f, 122f, 18f, 112f)
            quadTo(22f, 80f, 30f, 46f); close()
        }
        vgrad(p, 46f, 115f, Color.rgb(70, 125, 255), Color.rgb(22, 50, 160))
        c.drawPath(robe, p)
        // plis
        line(c, p, Color.argb(90, 10, 20, 70), 3f, 40f, 70f, 36f, 110f)
        line(c, p, Color.argb(90, 10, 20, 70), 3f, 56f, 70f, 60f, 110f)
        // cape
        val cape = Path().apply {
            moveTo(32f, 46f); lineTo(64f, 46f); lineTo(60f, 96f); quadTo(48f, 102f, 36f, 96f); close()
        }
        vgrad(p, 46f, 100f, Color.rgb(230, 235, 250), Color.rgb(160, 170, 210))
        c.drawPath(cape, p)
        // ceinture
        solid(p, Color.rgb(230, 180, 60))
        c.drawRect(26f, 78f, 70f, 84f, p)
        // épaulières
        vgrad(p, 40f, 58f, Color.rgb(200, 210, 230), Color.rgb(110, 120, 150))
        c.drawOval(RectF(20f, 42f, 42f, 58f), p)
        c.drawOval(RectF(54f, 42f, 76f, 58f), p)
        // capuche
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(44f, 22f, 22f, Color.rgb(90, 140, 255), Color.rgb(20, 40, 130), Shader.TileMode.CLAMP)
        c.drawCircle(48f, 30f, 18f, p)
        solid(p, Color.argb(70, 255, 255, 255))
        c.drawCircle(42f, 22f, 6f, p)
    }

    // ------------------------------------------------------------------ ennemi (vu de face)

    private fun drawEnemy(c: Canvas, p: Paint) {
        // jambes osseuses
        line(c, p, Color.rgb(200, 195, 180), 7f, 40f, 100f, 38f, 124f)
        line(c, p, Color.rgb(200, 195, 180), 7f, 56f, 100f, 58f, 124f)
        // épée (à droite)
        line(c, p, Color.rgb(210, 215, 225), 6f, 78f, 70f, 90f, 20f)
        line(c, p, Color.rgb(120, 90, 50), 6f, 72f, 72f, 84f, 66f)
        // corps : armure sombre
        val body = Path().apply {
            moveTo(30f, 48f); lineTo(66f, 48f); lineTo(70f, 92f); quadTo(48f, 104f, 26f, 92f); close()
        }
        vgrad(p, 48f, 100f, Color.rgb(95, 100, 115), Color.rgb(40, 42, 52))
        c.drawPath(body, p)
        // côtes
        for (i in 0..2) line(c, p, Color.argb(160, 220, 215, 200), 3f, 38f, 60f + i * 9f, 58f, 60f + i * 9f)
        // bras
        line(c, p, Color.rgb(200, 195, 180), 6f, 66f, 54f, 76f, 72f)
        // bouclier (à gauche)
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(24f, 72f, 22f, Color.rgb(150, 95, 50), Color.rgb(80, 45, 20), Shader.TileMode.CLAMP)
        c.drawCircle(24f, 74f, 20f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(170, 170, 180)
        c.drawCircle(24f, 74f, 19f, p)
        p.style = Paint.Style.FILL
        solid(p, Color.rgb(190, 190, 200))
        c.drawCircle(24f, 74f, 5f, p)
        // crâne
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(44f, 26f, 22f, Color.rgb(250, 245, 230), Color.rgb(180, 172, 150), Shader.TileMode.CLAMP)
        c.drawCircle(48f, 32f, 17f, p)
        solid(p, Color.rgb(40, 20, 20))
        c.drawCircle(42f, 33f, 5f, p)
        c.drawCircle(54f, 33f, 5f, p)
        solid(p, Color.rgb(255, 60, 40))
        c.drawCircle(42f, 33f, 2.5f, p)
        c.drawCircle(54f, 33f, 2.5f, p)
        // casque à cornes
        vgrad(p, 10f, 28f, Color.rgb(120, 125, 140), Color.rgb(50, 52, 62))
        c.drawArc(RectF(30f, 12f, 66f, 44f), 180f, 180f, true, p)
        solid(p, Color.rgb(230, 225, 205))
        val hornL = Path().apply { moveTo(32f, 24f); quadTo(18f, 18f, 20f, 4f); quadTo(28f, 16f, 36f, 18f); close() }
        val hornR = Path().apply { moveTo(64f, 24f); quadTo(78f, 18f, 76f, 4f); quadTo(68f, 16f, 60f, 18f); close() }
        c.drawPath(hornL, p); c.drawPath(hornR, p)
    }

    // ------------------------------------------------------------------ héros mage (de dos)

    private fun drawHero(c: Canvas, p: Paint) {
        // bâton avec cristal
        line(c, p, Color.rgb(90, 50, 25), 7f, 98f, 168f, 110f, 26f)
        solid(p, Color.rgb(200, 160, 60))
        c.drawCircle(109f, 34f, 9f, p)
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(110f, 18f, 22f, intArrayOf(Color.WHITE, Color.rgb(255, 120, 60), Color.argb(0, 255, 40, 0)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(110f, 18f, 22f, p)
        // robe longue rouge
        val robe = Path().apply {
            moveTo(42f, 60f); lineTo(86f, 60f)
            quadTo(98f, 120f, 108f, 172f)
            quadTo(64f, 182f, 20f, 172f)
            quadTo(30f, 120f, 42f, 60f); close()
        }
        vgrad(p, 60f, 176f, Color.rgb(230, 50, 45), Color.rgb(120, 15, 20))
        c.drawPath(robe, p)
        // bordure dorée
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = Color.rgb(240, 190, 70)
        val hem = Path().apply { moveTo(22f, 170f); quadTo(64f, 180f, 106f, 170f) }
        c.drawPath(hem, p)
        p.style = Paint.Style.FILL
        line(c, p, Color.argb(110, 80, 0, 0), 4f, 54f, 90f, 46f, 166f)
        line(c, p, Color.argb(110, 80, 0, 0), 4f, 74f, 90f, 82f, 166f)
        // ceinture
        solid(p, Color.rgb(240, 190, 70))
        c.drawRect(36f, 100f, 92f, 108f, p)
        // bras levé
        vgrad(p, 50f, 90f, Color.rgb(220, 45, 40), Color.rgb(140, 20, 20))
        c.drawRoundRect(RectF(80f, 56f, 100f, 96f), 10f, 10f, p)
        // épaules
        vgrad(p, 52f, 76f, Color.rgb(240, 70, 60), Color.rgb(150, 20, 20))
        c.drawOval(RectF(32f, 52f, 96f, 80f), p)
        // cheveux / tête
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(58f, 30f, 28f, Color.rgb(255, 170, 80), Color.rgb(170, 70, 20), Shader.TileMode.CLAMP)
        c.drawCircle(64f, 40f, 22f, p)
        // couronne
        solid(p, Color.rgb(255, 210, 80))
        val crown = Path().apply {
            moveTo(46f, 30f); lineTo(50f, 16f); lineTo(57f, 26f); lineTo(64f, 12f); lineTo(71f, 26f); lineTo(78f, 16f); lineTo(82f, 30f); close()
        }
        c.drawPath(crown, p)
    }

    // ------------------------------------------------------------------ tonneau et coffre

    private fun drawBarrel(c: Canvas, p: Paint) {
        val body = Path().apply {
            moveTo(18f, 30f); quadTo(4f, 80f, 18f, 132f); lineTo(110f, 132f); quadTo(124f, 80f, 110f, 30f); close()
        }
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = LinearGradient(4f, 0f, 124f, 0f,
            intArrayOf(Color.rgb(90, 50, 25), Color.rgb(185, 120, 65), Color.rgb(150, 92, 48), Color.rgb(80, 44, 22)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        // planches
        for (i in 1..6) {
            val x = 18f + i * 13.2f
            line(c, p, Color.argb(90, 50, 25, 10), 2f, x, 32f, x, 130f)
        }
        // cerclages
        for (y in floatArrayOf(44f, 118f)) {
            p.color = Color.WHITE; p.style = Paint.Style.FILL
            p.shader = LinearGradient(8f, 0f, 120f, 0f, Color.rgb(60, 60, 70), Color.rgb(150, 150, 165), Shader.TileMode.MIRROR)
            c.drawRect(RectF(10f, y - 5f, 118f, y + 5f), p)
        }
        // couvercle
        vgrad(p, 18f, 42f, Color.rgb(210, 150, 90), Color.rgb(140, 85, 45))
        c.drawOval(RectF(18f, 18f, 110f, 42f), p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(70, 70, 80)
        c.drawOval(RectF(18f, 18f, 110f, 42f), p)
        p.style = Paint.Style.FILL
    }

    private fun drawChest(c: Canvas, p: Paint) {
        // corps
        vgrad(p, 50f, 128f, Color.rgb(170, 105, 55), Color.rgb(95, 55, 25))
        c.drawRoundRect(RectF(8f, 52f, 162f, 128f), 8f, 8f, p)
        // couvercle bombé
        vgrad(p, 8f, 56f, Color.rgb(200, 135, 75), Color.rgb(130, 78, 38))
        c.drawRoundRect(RectF(8f, 10f, 162f, 62f), 30f, 30f, p)
        // planches
        for (x in floatArrayOf(50f, 85f, 120f)) line(c, p, Color.argb(90, 50, 25, 10), 3f, x, 14f, x, 126f)
        // ferrures
        solid(p, Color.rgb(70, 70, 80))
        for (x in floatArrayOf(8f, 148f)) c.drawRect(x, 10f, x + 14f, 128f, p)
        c.drawRect(8f, 56f, 162f, 64f, p)
        // serrure dorée
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(85f, 66f, 16f, Color.rgb(255, 235, 140), Color.rgb(200, 140, 30), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(72f, 52f, 98f, 82f), 6f, 6f, p)
        solid(p, Color.rgb(60, 40, 10))
        c.drawCircle(85f, 64f, 4f, p)
        c.drawRect(83f, 64f, 87f, 74f, p)
    }

    // ------------------------------------------------------------------ boss

    private fun drawBoss(c: Canvas, p: Paint) {
        // jambes
        vgrad(p, 230f, 330f, Color.rgb(90, 30, 30), Color.rgb(40, 10, 12))
        c.drawRoundRect(RectF(95f, 230f, 140f, 328f), 18f, 18f, p)
        c.drawRoundRect(RectF(160f, 230f, 205f, 328f), 18f, 18f, p)
        // masse d'armes
        line(c, p, Color.rgb(80, 50, 30), 14f, 250f, 250f, 280f, 80f)
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(282f, 70f, 34f, Color.rgb(140, 140, 150), Color.rgb(50, 50, 60), Shader.TileMode.CLAMP)
        c.drawCircle(282f, 70f, 30f, p)
        solid(p, Color.rgb(200, 200, 210))
        for (a in 0 until 8) {
            val ang = a * Math.PI / 4
            c.drawCircle(282f + 34f * cos(ang).toFloat(), 70f + 34f * sin(ang).toFloat(), 6f, p)
        }
        // torse
        val torso = Path().apply {
            moveTo(70f, 110f); quadTo(150f, 70f, 230f, 110f); lineTo(215f, 245f); quadTo(150f, 265f, 85f, 245f); close()
        }
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(150f, 150f, 120f, Color.rgb(190, 60, 50), Color.rgb(80, 15, 15), Shader.TileMode.CLAMP)
        c.drawPath(torso, p)
        // plastron et pics
        vgrad(p, 120f, 200f, Color.rgb(70, 70, 85), Color.rgb(25, 25, 35))
        c.drawRoundRect(RectF(105f, 130f, 195f, 210f), 20f, 20f, p)
        solid(p, Color.rgb(255, 140, 40))
        c.drawCircle(150f, 168f, 12f, p)
        // bras
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(60f, 150f, 50f, Color.rgb(180, 55, 45), Color.rgb(80, 15, 15), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(30f, 110f, 80f, 240f), 24f, 24f, p)
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(240f, 150f, 50f, Color.rgb(180, 55, 45), Color.rgb(80, 15, 15), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(220f, 110f, 268f, 250f), 24f, 24f, p)
        // tête
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(140f, 70f, 55f, Color.rgb(200, 70, 55), Color.rgb(90, 20, 18), Shader.TileMode.CLAMP)
        c.drawCircle(150f, 82f, 46f, p)
        // cornes
        solid(p, Color.rgb(235, 225, 200))
        val hl = Path().apply { moveTo(112f, 60f); quadTo(70f, 40f, 78f, 0f); quadTo(96f, 36f, 126f, 46f); close() }
        val hr = Path().apply { moveTo(188f, 60f); quadTo(230f, 40f, 222f, 0f); quadTo(204f, 36f, 174f, 46f); close() }
        c.drawPath(hl, p); c.drawPath(hr, p)
        // yeux
        for (x in floatArrayOf(132f, 168f)) {
            p.color = Color.WHITE; p.style = Paint.Style.FILL
            p.shader = RadialGradient(x, 80f, 14f, intArrayOf(Color.WHITE, Color.rgb(255, 230, 60), Color.argb(0, 255, 120, 0)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, 80f, 14f, p)
        }
        // bouche
        solid(p, Color.rgb(40, 5, 5))
        c.drawRoundRect(RectF(128f, 100f, 172f, 114f), 6f, 6f, p)
        solid(p, Color.rgb(240, 235, 220))
        for (x in floatArrayOf(134f, 146f, 158f)) {
            val t = Path().apply { moveTo(x, 100f); lineTo(x + 8f, 100f); lineTo(x + 4f, 108f); close() }
            c.drawPath(t, p)
        }
    }

    // ------------------------------------------------------------------ dragon (vu de dos, en vol)

    private fun drawDragon(c: Canvas, p: Paint) {
        val cx = 210f
        // ailes
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
            p.color = Color.WHITE; p.style = Paint.Style.FILL
            p.shader = LinearGradient(cx, 40f, cx + side * 200f, 150f, Color.rgb(230, 80, 50), Color.rgb(110, 20, 20), Shader.TileMode.CLAMP)
            c.drawPath(w, p)
            for (k in 0..3) {
                val tx = cx + side * (205f - k * 30f)
                val ty = 40f + k * 32f
                line(c, p, Color.rgb(70, 15, 15), 4f, cx + side * 22f, 112f, tx, ty)
            }
        }
        // queue
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.rgb(140, 30, 25); p.strokeWidth = 18f
        val tail = Path().apply { moveTo(cx, 170f); quadTo(cx + 40f, 215f, cx - 10f, 250f) }
        c.drawPath(tail, p)
        p.style = Paint.Style.FILL
        // corps
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, 120f, 60f, Color.rgb(230, 90, 55), Color.rgb(120, 25, 20), Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - 38f, 70f, cx + 38f, 185f), p)
        // crête dorsale
        solid(p, Color.rgb(255, 200, 80))
        for (k in 0..4) {
            val y = 85f + k * 20f
            val sp = Path().apply { moveTo(cx - 7f, y + 10f); lineTo(cx, y - 6f); lineTo(cx + 7f, y + 10f); close() }
            c.drawPath(sp, p)
        }
        // tête
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, 55f, 30f, Color.rgb(240, 100, 60), Color.rgb(130, 30, 22), Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - 24f, 30f, cx + 24f, 80f), p)
        solid(p, Color.rgb(240, 225, 200))
        val h1 = Path().apply { moveTo(cx - 16f, 40f); lineTo(cx - 34f, 14f); lineTo(cx - 8f, 34f); close() }
        val h2 = Path().apply { moveTo(cx + 16f, 40f); lineTo(cx + 34f, 14f); lineTo(cx + 8f, 34f); close() }
        c.drawPath(h1, p); c.drawPath(h2, p)
    }

    // ------------------------------------------------------------------ cercle magique

    private fun drawMagicCircle(c: Canvas, p: Paint) {
        val cx = 128f
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cx, 128f, intArrayOf(Color.argb(150, 255, 170, 60), Color.argb(60, 255, 90, 20), Color.argb(0, 255, 60, 0)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, 126f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(255, 210, 110)
        p.strokeWidth = 5f
        c.drawCircle(cx, cx, 118f, p)
        p.strokeWidth = 3f
        c.drawCircle(cx, cx, 100f, p)
        c.drawCircle(cx, cx, 60f, p)
        // deux triangles
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
        // runes
        p.strokeWidth = 3f
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            val r0 = 104f; val r1 = 114f
            val x0 = cx + r0 * cos(a).toFloat(); val y0 = cx + r0 * sin(a).toFloat()
            val x1 = cx + r1 * cos(a + 0.1).toFloat(); val y1 = cx + r1 * sin(a + 0.1).toFloat()
            c.drawLine(x0, y0, x1, y1, p)
        }
        p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------ icônes

    private fun drawSwordIcon(c: Canvas, p: Paint) {
        val blade = Path().apply { moveTo(48f, 6f); lineTo(58f, 20f); lineTo(56f, 64f); lineTo(40f, 64f); lineTo(38f, 20f); close() }
        p.color = Color.WHITE; p.style = Paint.Style.FILL
        p.shader = LinearGradient(38f, 0f, 58f, 0f, Color.rgb(250, 250, 255), Color.rgb(150, 160, 180), Shader.TileMode.CLAMP)
        c.drawPath(blade, p)
        solid(p, Color.rgb(230, 180, 50))
        c.drawRoundRect(RectF(24f, 62f, 72f, 72f), 5f, 5f, p)
        solid(p, Color.rgb(120, 70, 35))
        c.drawRect(43f, 72f, 53f, 88f, p)
        solid(p, Color.rgb(230, 180, 50))
        c.drawCircle(48f, 90f, 6f, p)
    }

    private fun drawBoltIcon(c: Canvas, p: Paint) {
        val b = Path().apply {
            moveTo(58f, 4f); lineTo(22f, 54f); lineTo(46f, 54f); lineTo(36f, 92f); lineTo(76f, 38f); lineTo(52f, 38f); close()
        }
        vgrad(p, 4f, 92f, Color.rgb(255, 250, 170), Color.rgb(255, 170, 20))
        c.drawPath(b, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.rgb(160, 90, 0)
        c.drawPath(b, p)
        p.style = Paint.Style.FILL
    }
}

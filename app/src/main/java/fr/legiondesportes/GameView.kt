package fr.legiondesportes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vue principale : boucle de jeu, contrôles, interface et écrans.
 * La logique est dans [World], le dessin du monde dans [Renderer].
 */
class GameView(context: Context) : View(context) {

    private enum class Screen { MENU, PLAYING, WON, LOST }

    private val prefs = context.getSharedPreferences("save", Context.MODE_PRIVATE)
    private var level = prefs.getInt("level", 1)
    private var bestLevel = prefs.getInt("best", 1)
    private var bestDist = prefs.getInt("bestDist", 0)
    private var bestBoss = prefs.getInt("bestBoss", 0)
    private var bestWave = prefs.getInt("bestWave", 0)

    private val sprites = Sprites()
    private val renderer = Renderer(sprites)
    private var world = World(Mode.CAMPAIGN, level)
    private var mode = Mode.CAMPAIGN
    private var newRecord = false

    private var screen = Screen.MENU
    private var screenTime = 0f
    private var lastFrame = 0L
    private var time = 0f
    private var paused = false
    private var userPaused = false
    private var lastTouchX = 0f

    /** Pas de temps fixe (uniquement pour les captures de test). */
    var testDt = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // zones cliquables
    private val btnA = RectF()
    private val btnB = RectF()
    private val btnC = RectF()
    private val btnPause = RectF()
    private val cardRects = Array(3) { RectF() }

    fun pause() {
        paused = true
        if (screen == Screen.PLAYING) userPaused = true
    }

    fun resume() {
        paused = false
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    /** Bouton retour d'Android. Renvoie true si géré. */
    fun onBack(): Boolean {
        return when (screen) {
            Screen.PLAYING -> { userPaused = !userPaused; true }
            Screen.MENU -> false
            else -> { goMenu(); true }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) renderer.setSize(w, h)
    }

    private fun setScreen(s: Screen) { screen = s; screenTime = 0f }

    private fun start(m: Mode) {
        mode = m
        world = World(m, level)
        userPaused = false
        newRecord = false
        setScreen(Screen.PLAYING)
    }

    private fun goMenu() {
        world = World(Mode.CAMPAIGN, level)
        userPaused = false
        setScreen(Screen.MENU)
    }

    // ------------------------------------------------------------------ entrées

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                when (screen) {
                    Screen.MENU -> {
                        if (btnA.contains(x, y)) start(Mode.CAMPAIGN)
                        else if (btnB.contains(x, y)) start(Mode.ENDLESS)
                        else if (btnC.contains(x, y)) start(Mode.INVASION)
                    }
                    Screen.PLAYING -> {
                        if (userPaused) {
                            if (btnA.contains(x, y)) userPaused = false
                            else if (btnB.contains(x, y)) goMenu()
                        } else if (world.state == World.State.CHOOSING) {
                            for (i in cardRects.indices) if (i < world.cards.size && cardRects[i].contains(x, y)) world.chooseCard(i)
                        } else if (btnPause.contains(x, y)) {
                            userPaused = true
                        }
                    }
                    Screen.WON, Screen.LOST -> if (screenTime > 0.8f) {
                        if (btnA.contains(x, y)) start(mode)
                        else if (btnB.contains(x, y)) goMenu()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (screen == Screen.PLAYING && !userPaused && world.state == World.State.RUNNING) {
                    world.targetX = (world.targetX + renderer.pxToUnits(x - lastTouchX) * 1.3f).coerceIn(-0.8f, 0.8f)
                }
                lastTouchX = x
            }
        }
        return true
    }

    // ------------------------------------------------------------------ boucle

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (testDt > 0f) testDt else if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.05f)
        lastFrame = now
        val step = if (paused) 0f else dt
        time += step
        screenTime += step

        val frozen = screen == Screen.PLAYING && (userPaused || world.state == World.State.CHOOSING)
        if (screen == Screen.PLAYING && !userPaused) {
            world.update(step)
            when (world.state) {
                World.State.WON -> {
                    level++
                    bestLevel = max(bestLevel, level)
                    prefs.edit().putInt("level", level).putInt("best", bestLevel).apply()
                    setScreen(Screen.WON)
                }
                World.State.LOST -> {
                    if (mode == Mode.INVASION) {
                        val v = world.bossKills
                        if (v > bestWave) { bestWave = v; newRecord = true }
                        prefs.edit().putInt("bestWave", bestWave).apply()
                    }
                    if (mode == Mode.ENDLESS) {
                        val d = world.traveled.toInt()
                        if (d > bestDist) { bestDist = d; newRecord = true }
                        bestBoss = max(bestBoss, world.bossKills)
                        prefs.edit().putInt("bestDist", bestDist).putInt("bestBoss", bestBoss).apply()
                    }
                    setScreen(Screen.LOST)
                }
                else -> {}
            }
        }

        renderer.draw(canvas, world, if (frozen) 0f else step)
        if (screen == Screen.PLAYING) {
            drawHud(canvas)
            drawHeroBar(canvas)
            if (world.state == World.State.CHOOSING && !userPaused) drawCards(canvas)
            if (userPaused) drawPause(canvas)
        }
        drawOverlay(canvas)

        if (!paused) postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------ interface en jeu

    private fun txt(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, alpha: Int = 255) =
        renderer.outlined(c, t, x, y, size, color, alpha)

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val u = w / 1000f
        val top = 70f * u
        if (mode == Mode.CAMPAIGN) {
            rect.set(40f * u, top, 960f * u, top + 30f * u)
            fill.color = Color.argb(150, 0, 0, 0)
            c.drawRoundRect(rect, 15f * u, 15f * u, fill)
            val prog = (world.traveled / world.levelEnd).coerceIn(0f, 1f)
            rect.set(40f * u, top, 40f * u + 920f * u * prog, top + 30f * u)
            fill.color = Color.WHITE
            fill.shader = LinearGradient(40f * u, 0f, 960f * u, 0f, Color.rgb(255, 210, 80), Color.rgb(255, 120, 40), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 15f * u, 15f * u, fill)
            fill.shader = null
            rect.set(895f * u, top - 34f * u, 985f * u, top + 60f * u)
            c.drawBitmap(sprites.bosses[(level - 1) % 4], null, rect, bmp)
            txt(c, "NIVEAU $level", w / 2f, top + 80f * u, 52f * u, Color.WHITE)
        } else if (mode == Mode.INVASION) {
            txt(c, "VAGUE ${max(1, world.wave)}", w / 2f, top + 20f * u, 64f * u, Color.WHITE)
            val rec = if (bestWave > 0) "  ·  Record $bestWave" else ""
            txt(c, "Chapitre ${world.chapter} · boss final à la vague ${world.chapter * 4}$rec", w / 2f, top + 80f * u, 32f * u, Color.rgb(255, 225, 120))
        } else {
            txt(c, "${world.traveled.toInt()} m", w / 2f, top + 20f * u, 64f * u, Color.WHITE)
            val rec = if (bestDist > 0) "Record $bestDist m  ·  " else ""
            txt(c, "${rec}Boss vaincus ${world.bossKills}", w / 2f, top + 80f * u, 34f * u, Color.rgb(255, 225, 120))
        }

        // bouton pause
        btnPause.set(880f * u, top + 50f * u, 960f * u, top + 130f * u)
        fill.color = Color.argb(150, 20, 15, 40)
        c.drawRoundRect(btnPause, 20f * u, 20f * u, fill)
        fill.color = Color.WHITE
        c.drawRect(btnPause.left + 26f * u, btnPause.top + 22f * u, btnPause.left + 36f * u, btnPause.bottom - 22f * u, fill)
        c.drawRect(btnPause.right - 36f * u, btnPause.top + 22f * u, btnPause.right - 26f * u, btnPause.bottom - 22f * u, fill)

        // bonus actifs
        var bx = 40f * u
        val by = top + 160f * u
        if (world.dmgMul > 1.01f) { chip(c, bx, by, u, sprites.iconSword, "×" + fmt(world.dmgMul)); bx += 220f * u }
        if (world.rateMul > 1.01f) { chip(c, bx, by, u, sprites.iconBolt, "×" + fmt(world.rateMul)); bx += 220f * u }
        if (world.dragonTime > 0f) chip(c, bx, by, u, sprites.dragon, (world.dragonTime + 0.99f).toInt().toString() + "s")

        // barre du boss
        val b = world.boss
        if (b != null && b.alive && b.z - world.traveled < 20f) {
            val y = by + 70f * u
            val tag = if (b.chief) "BOSS FINAL · " else "GARDIEN · "
            txt(c, tag + b.type.title.uppercase(), w / 2f, y, 40f * u, if (b.chief) Color.rgb(255, 90, 70) else Color.rgb(255, 160, 120))
            rect.set(120f * u, y + 30f * u, 880f * u, y + 62f * u)
            fill.color = Color.argb(180, 30, 0, 0)
            c.drawRoundRect(rect, 16f * u, 16f * u, fill)
            val f = (b.hp / b.maxHp).coerceIn(0f, 1f)
            rect.set(120f * u, y + 30f * u, 120f * u + 760f * u * f, y + 62f * u)
            fill.color = Color.rgb(220, 40, 40)
            c.drawRoundRect(rect, 16f * u, 16f * u, fill)
        }

        // annonce
        val an = world.announce
        if (an != null && world.announceTime > 0f) {
            val a = (min(1f, world.announceTime) * 255).toInt()
            val y = height * 0.33f
            val tw = renderer.measure(an, 50f * u) + 80f * u
            rect.set(w / 2f - tw / 2f, y - 50f * u, w / 2f + tw / 2f, y + 50f * u)
            fill.color = Color.argb(a * 180 / 255, 20, 10, 30)
            c.drawRoundRect(rect, 50f * u, 50f * u, fill)
            stroke.color = Color.argb(a, 255, 200, 70)
            stroke.strokeWidth = 4f * u
            c.drawRoundRect(rect, 50f * u, 50f * u, stroke)
            txt(c, an, w / 2f, y, 50f * u, Color.rgb(255, 230, 150), a)
        }

        if (world.time < 4f) {
            val a = ((4f - world.time) * 255).toInt().coerceIn(0, 255)
            txt(c, "← Glisse pour changer de voie →", w / 2f, height * 0.6f, 42f * u, Color.WHITE, a)
        }
    }

    private fun fmt(v: Float): String {
        val r = (v * 10).toInt() / 10f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    private fun chip(c: Canvas, x: Float, y: Float, u: Float, icon: Bitmap, label: String) {
        rect.set(x, y - 34f * u, x + 200f * u, y + 34f * u)
        fill.color = Color.argb(170, 20, 15, 40)
        c.drawRoundRect(rect, 34f * u, 34f * u, fill)
        stroke.color = Color.rgb(255, 200, 70)
        stroke.strokeWidth = 3f * u
        c.drawRoundRect(rect, 34f * u, 34f * u, stroke)
        val ih = 54f * u
        val iw = min(ih * icon.width / icon.height, 80f * u)
        val ihh = iw * icon.height / icon.width
        rect.set(x + 14f * u, y - ihh / 2f, x + 14f * u + iw, y + ihh / 2f)
        c.drawBitmap(icon, null, rect, bmp)
        txt(c, label, x + 135f * u, y, 38f * u, Color.rgb(255, 225, 120))
    }

    private fun drawHeroBar(c: Canvas) {
        val w = width.toFloat()
        val u = w / 1000f
        val n = world.heroes.size
        val slot = 180f * u
        val gap = 14f * u
        val total = 5 * slot + 4 * gap
        val x0 = (w - total) / 2f
        val y1 = height - 24f * u
        val y0 = y1 - 165f * u
        for (i in 0 until 5) {
            val sx = x0 + i * (slot + gap)
            rect.set(sx, y0, sx + slot, y1)
            if (i >= n) {
                fill.color = Color.argb(90, 10, 10, 20)
                c.drawRoundRect(rect, 24f * u, 24f * u, fill)
                stroke.color = Color.argb(90, 255, 255, 255)
                stroke.strokeWidth = 2f * u
                c.drawRoundRect(rect, 24f * u, 24f * u, stroke)
                txt(c, "?", sx + slot / 2f, (y0 + y1) / 2f, 60f * u, Color.argb(255, 120, 120, 140), 140)
                continue
            }
            val h = world.heroes[i]
            val col = Renderer.HERO_COLORS[h.type.ordinal]
            fill.color = if (h.alive) Color.argb(200, 25, 18, 45) else Color.argb(200, 40, 20, 20)
            c.drawRoundRect(rect, 24f * u, 24f * u, fill)
            // portrait
            val hb = sprites.heroes[h.type.ordinal]
            val ph = 120f * u
            val pw = ph * hb.width / hb.height
            rect.set(sx + 10f * u, y0 + 10f * u, sx + 10f * u + pw, y0 + 10f * u + ph)
            bmp.alpha = if (h.alive) 255 else 90
            c.drawBitmap(hb, null, rect, bmp)
            bmp.alpha = 255
            // icône du sort et recharge
            val icx = sx + slot - 50f * u
            val icy = y0 + 52f * u
            val ir = 36f * u
            rect.set(icx - ir, icy - ir, icx + ir, icy + ir)
            bmp.alpha = if (h.alive) 255 else 80
            c.drawBitmap(sprites.skillIcons[h.type.ordinal], null, rect, bmp)
            bmp.alpha = 255
            if (h.alive) {
                val f = (h.cd / h.cooldown()).coerceIn(0f, 1f)
                if (f > 0.01f) {
                    fill.color = Color.argb(150, 0, 0, 0)
                    c.drawArc(rect, -90f, 360f * f, true, fill)
                } else {
                    stroke.color = col
                    stroke.strokeWidth = 4f * u
                    c.drawCircle(icx, icy, ir + 3f * u, stroke)
                }
            }
            // niveau
            for (l in 0 until 5) {
                fill.color = if (l < h.level) Color.rgb(255, 210, 80) else Color.rgb(70, 60, 90)
                c.drawCircle(icx - 32f * u + l * 16f * u, icy + 52f * u, 6.5f * u, fill)
            }
            // vie
            val hy = y1 - 20f * u
            rect.set(sx + 14f * u, hy, sx + slot - 14f * u, hy + 12f * u)
            fill.color = Color.argb(200, 10, 10, 10)
            c.drawRoundRect(rect, 6f * u, 6f * u, fill)
            rect.set(sx + 14f * u, hy, sx + 14f * u + (slot - 28f * u) * (h.hp / h.maxHp).coerceIn(0f, 1f), hy + 12f * u)
            fill.color = if (h.hp > h.maxHp * 0.35f) Color.rgb(90, 220, 110) else Color.rgb(240, 80, 60)
            c.drawRoundRect(rect, 6f * u, 6f * u, fill)
            rect.set(sx, y0, sx + slot, y1)
            stroke.color = if (h.alive) col else Color.rgb(160, 40, 40)
            stroke.strokeWidth = 3f * u
            c.drawRoundRect(rect, 24f * u, 24f * u, stroke)
            if (!h.alive) txt(c, "K.O.", sx + slot / 2f, (y0 + y1) / 2f, 50f * u, Color.rgb(255, 90, 80))
        }
    }

    private fun cardIcon(card: Card): Bitmap = when (card.kind) {
        CardKind.RECRUIT -> sprites.heroes[card.hero!!.ordinal]
        CardKind.UPGRADE -> sprites.skillIcons[card.hero!!.ordinal]
        CardKind.SOLDIERS -> sprites.ally
        CardKind.DAMAGE -> sprites.iconSword
        CardKind.RATE -> sprites.iconBolt
        CardKind.HEAL -> sprites.iconHeart
        CardKind.DRAGON -> sprites.dragon
    }

    private fun drawCards(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 1000f
        fill.color = Color.argb(170, 8, 5, 20)
        c.drawRect(0f, 0f, w, h, fill)
        txt(c, "BOSS VAINCU !", w / 2f, h * 0.2f, 90f * u, Color.rgb(255, 210, 80))
        txt(c, "Choisis une récompense", w / 2f, h * 0.2f + 90f * u, 44f * u, Color.WHITE)
        val cw = 300f * u
        val ch = 520f * u
        val gap = 30f * u
        val x0 = (w - 3 * cw - 2 * gap) / 2f
        val y0 = h * 0.33f
        for ((i, card) in world.cards.withIndex()) {
            val r = cardRects[i]
            val bounce = sin(time * 3f + i) * 6f * u
            r.set(x0 + i * (cw + gap), y0 + bounce, x0 + i * (cw + gap) + cw, y0 + ch + bounce)
            val (cTop, cBot, edge) = when (card.kind) {
                CardKind.RECRUIT, CardKind.UPGRADE -> Triple(Color.rgb(90, 45, 150), Color.rgb(30, 12, 60), Color.rgb(210, 160, 255))
                CardKind.HEAL -> Triple(Color.rgb(40, 120, 70), Color.rgb(10, 40, 25), Color.rgb(140, 255, 170))
                CardKind.DRAGON -> Triple(Color.rgb(150, 50, 30), Color.rgb(50, 10, 10), Color.rgb(255, 150, 90))
                else -> Triple(Color.rgb(140, 100, 30), Color.rgb(50, 30, 10), Color.rgb(255, 210, 90))
            }
            fill.color = Color.WHITE
            fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, cTop, cBot, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 30f * u, 30f * u, fill)
            fill.shader = null
            stroke.color = edge
            stroke.strokeWidth = 6f * u
            c.drawRoundRect(r, 30f * u, 30f * u, stroke)
            // icône
            val icon = cardIcon(card)
            val ih = 200f * u
            val iw = min(ih * icon.width / icon.height, cw - 40f * u)
            val ihh = iw * icon.height / icon.width
            rect.set(r.centerX() - iw / 2f, r.top + 40f * u + (ih - ihh) / 2f, r.centerX() + iw / 2f, r.top + 40f * u + (ih + ihh) / 2f)
            c.drawBitmap(icon, null, rect, bmp)
            val kindLabel = when (card.kind) { CardKind.RECRUIT -> "NOUVEAU HÉROS"; CardKind.UPGRADE -> "AMÉLIORATION"; else -> "BONUS" }
            txt(c, kindLabel, r.centerX(), r.top + 275f * u, 26f * u, edge)
            txt(c, world.cardTitle(card), r.centerX(), r.top + 325f * u, fitSize(world.cardTitle(card), 40f * u, cw - 30f * u), Color.WHITE)
            val lines = world.cardDesc(card).split("|")
            for ((k, l) in lines.withIndex()) txt(c, l, r.centerX(), r.top + 390f * u + k * 44f * u, fitSize(l, 32f * u, cw - 30f * u), Color.rgb(230, 225, 240))
            if (card.kind == CardKind.UPGRADE) {
                val lv = world.hero(card.hero!!)?.level ?: 1
                for (l in 0 until 5) {
                    fill.color = when { l < lv -> Color.rgb(255, 210, 80); l == lv -> Color.rgb(140, 255, 160); else -> Color.argb(110, 255, 255, 255) }
                    c.drawCircle(r.centerX() - 60f * u + l * 30f * u, r.bottom - 36f * u, 10f * u, fill)
                }
            }
        }
    }

    private fun fitSize(t: String, size: Float, maxW: Float): Float {
        val wv = renderer.measure(t, size)
        return if (wv > maxW) size * maxW / wv else size
    }

    private fun drawPause(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 1000f
        fill.color = Color.argb(170, 8, 5, 20)
        c.drawRect(0f, 0f, w, h, fill)
        txt(c, "PAUSE", w / 2f, h * 0.35f, 120f * u, Color.WHITE)
        button(c, btnA, w / 2f, h * 0.5f, u, "REPRENDRE", Color.rgb(60, 160, 90))
        button(c, btnB, w / 2f, h * 0.5f + 170f * u, u, "MENU", Color.rgb(90, 80, 130))
    }

    private fun button(c: Canvas, r: RectF, cx: Float, cy: Float, u: Float, label: String, col: Int, sub: String? = null) {
        val bw = 700f * u
        val bh = if (sub != null) 150f * u else 120f * u
        r.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, col, Color.rgb(Color.red(col) / 3, Color.green(col) / 3, Color.blue(col) / 3), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 40f * u, 40f * u, fill)
        fill.shader = null
        stroke.color = Color.rgb(255, 220, 120)
        stroke.strokeWidth = 5f * u
        c.drawRoundRect(r, 40f * u, 40f * u, stroke)
        if (sub != null) {
            txt(c, label, cx, cy - 22f * u, 56f * u, Color.WHITE)
            txt(c, sub, cx, cy + 38f * u, 32f * u, Color.rgb(255, 230, 160))
        } else txt(c, label, cx, cy, 56f * u, Color.WHITE)
    }

    // ------------------------------------------------------------------ écrans

    private fun drawOverlay(c: Canvas) {
        if (screen == Screen.PLAYING) return
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 1000f
        fill.color = Color.argb(if (screen == Screen.MENU) 110 else 160, 10, 8, 25)
        c.drawRect(0f, 0f, w, h, fill)
        val blink = sin(time * 5f) * 0.5f + 0.5f

        when (screen) {
            Screen.MENU -> {
                val cy = h * 0.19f
                rect.set(300f * u, cy - 250f * u, 700f * u, cy - 50f * u)
                c.drawBitmap(sprites.dragon, null, rect, bmp)
                txt(c, "LÉGION", w / 2f, cy, 150f * u, Color.WHITE)
                txt(c, "DES PORTES", w / 2f, cy + 125f * u, 100f * u, Color.rgb(255, 200, 70))
                // héros
                val hy = cy + 360f * u
                for (i in 0 until 5) {
                    val hb = sprites.heroes[i]
                    val ph = 190f * u + (if (i == 2) 30f * u else 0f)
                    val pw = ph * hb.width / hb.height
                    val x = w / 2f + (i - 2) * 170f * u
                    rect.set(x - pw / 2f, hy - ph + sin(time * 2f + i) * 6f * u, x + pw / 2f, hy + sin(time * 2f + i) * 6f * u)
                    c.drawBitmap(hb, null, rect, bmp)
                }
                txt(c, "5 héros · 3 modes · 4 régions · 4 boss", w / 2f, hy + 50f * u, 38f * u, Color.rgb(230, 220, 255))
                button(c, btnA, w / 2f, h * 0.54f, u, "CAMPAGNE", Color.rgb(200, 90, 40), "Niveau $level  ·  Record niv. $bestLevel")
                button(c, btnB, w / 2f, h * 0.54f + 180f * u, u, "SANS LIMITE", Color.rgb(110, 60, 200),
                    if (bestDist > 0) "Record $bestDist m  ·  $bestBoss boss" else "Jusqu'au dernier héros")
                button(c, btnC, w / 2f, h * 0.54f + 360f * u, u, "INVASION", Color.rgb(200, 40, 50),
                    if (bestWave > 0) "Record : $bestWave vagues" else "Repousse le flot, récolte les portes")
                txt(c, "Glisse pour changer de voie · Casse les tonneaux", w / 2f, h * 0.92f, 34f * u, Color.WHITE, (150 + 100 * blink).toInt())
            }
            Screen.WON -> {
                val cy = h * 0.32f
                txt(c, "VICTOIRE !", w / 2f, cy, 130f * u, Color.rgb(255, 210, 80))
                txt(c, "Région libérée", w / 2f, cy + 110f * u, 46f * u, Color.WHITE)
                stats(c, cy + 220f * u, u)
                if (screenTime > 0.8f) {
                    button(c, btnA, w / 2f, h * 0.68f, u, "NIVEAU $level  →", Color.rgb(60, 160, 90))
                    button(c, btnB, w / 2f, h * 0.68f + 170f * u, u, "MENU", Color.rgb(90, 80, 130))
                }
            }
            Screen.LOST -> {
                val cy = h * 0.3f
                txt(c, if (mode == Mode.ENDLESS) "FIN DU VOYAGE" else if (mode == Mode.INVASION) "SUBMERGÉ !" else "DÉFAITE", w / 2f, cy, if (mode == Mode.ENDLESS) 100f * u else 140f * u, Color.rgb(255, 90, 90))
                txt(c, "Tous tes héros sont tombés…", w / 2f, cy + 110f * u, 44f * u, Color.WHITE)
                if (mode == Mode.ENDLESS) {
                    txt(c, "${world.traveled.toInt()} m", w / 2f, cy + 230f * u, 110f * u, Color.rgb(255, 225, 120))
                    if (newRecord) txt(c, "NOUVEAU RECORD !", w / 2f, cy + 320f * u, 50f * u, Color.rgb(140, 255, 160), (150 + 100 * blink).toInt())
                    else txt(c, "Record : $bestDist m", w / 2f, cy + 320f * u, 40f * u, Color.WHITE)
                    stats(c, cy + 400f * u, u)
                } else if (mode == Mode.INVASION) {
                    txt(c, "${world.bossKills} vagues", w / 2f, cy + 230f * u, 110f * u, Color.rgb(255, 225, 120))
                    if (newRecord) txt(c, "NOUVEAU RECORD !", w / 2f, cy + 320f * u, 50f * u, Color.rgb(140, 255, 160), (150 + 100 * blink).toInt())
                    else txt(c, "Record : $bestWave vagues", w / 2f, cy + 320f * u, 40f * u, Color.WHITE)
                    stats(c, cy + 400f * u, u)
                } else {
                    stats(c, cy + 220f * u, u)
                    txt(c, "Astuce : recrute des héros et", w / 2f, cy + 340f * u, 38f * u, Color.rgb(255, 225, 120))
                    txt(c, "esquive les zones rouges !", w / 2f, cy + 390f * u, 38f * u, Color.rgb(255, 225, 120))
                }
                if (screenTime > 0.8f) {
                    button(c, btnA, w / 2f, h * 0.72f, u, "REJOUER", Color.rgb(200, 90, 40))
                    button(c, btnB, w / 2f, h * 0.72f + 170f * u, u, "MENU", Color.rgb(90, 80, 130))
                }
            }
            else -> {}
        }
    }

    private fun stats(c: Canvas, y: Float, u: Float) {
        val w = width.toFloat()
        txt(c, "Ennemis vaincus : ${Renderer.fmt(world.kills)}   ·   Boss : ${world.bossKills}", w / 2f, y, 38f * u, Color.WHITE)
        val n = world.heroes.size
        for ((i, h) in world.heroes.withIndex()) {
            val hb = sprites.heroes[h.type.ordinal]
            val ph = 130f * u
            val pw = ph * hb.width / hb.height
            val x = w / 2f + (i - (n - 1) / 2f) * 120f * u
            rect.set(x - pw / 2f, y + 40f * u, x + pw / 2f, y + 40f * u + ph)
            c.drawBitmap(hb, null, rect, bmp)
            txt(c, "niv ${h.level}", x, y + 195f * u, 28f * u, Color.rgb(255, 210, 80))
        }
    }
}

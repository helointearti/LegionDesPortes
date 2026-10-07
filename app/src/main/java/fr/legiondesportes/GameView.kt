package fr.legiondesportes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Légion des Portes — runner à portes.
 *
 * Le monde utilise des "unités" : la largeur de l'écran vaut toujours 1000 unités,
 * la hauteur dépend du ratio de l'écran. Le canvas est mis à l'échelle au début de onDraw.
 */
class GameView(context: Context) : View(context) {

    // ------------------------------------------------------------------ modèle

    private enum class State { MENU, PLAYING, FIGHTING, LOST, WON }

    private class Op(val sign: Char, val value: Int) {
        fun apply(n: Int): Int {
            val r = when (sign) {
                '+' -> n + value
                '-' -> n - value
                '×' -> n * value
                else -> n / value
            }
            return r.coerceIn(0, MAX_COUNT)
        }

        val good: Boolean get() = sign == '+' || sign == '×'
        val label: String get() = "$sign$value"
    }

    private abstract class Entity(val z: Float) {
        var done = false
    }

    private class Gates(z: Float, val left: Op, val right: Op) : Entity(z) {
        var chosen = -1
    }

    private class Enemy(z: Float, var count: Int, val boss: Boolean) : Entity(z)

    private class Popup(val text: String, val color: Int, val x: Float, var y: Float, var life: Float)

    companion object {
        const val MAX_COUNT = 9999
        const val ROAD_L = 110f
        const val ROAD_R = 890f
        const val MID = 500f
        const val GATE_H = 170f
        const val SPACING = 1000f
        const val START_COUNT = 10
    }

    // ------------------------------------------------------------------ état

    private val prefs = context.getSharedPreferences("save", Context.MODE_PRIVATE)
    private var level = prefs.getInt("level", 1)
    private var best = prefs.getInt("best", 1)

    private var state = State.MENU
    private var stateTime = 0f
    private var count = START_COUNT
    private var playerX = MID
    private var traveled = 0f
    private var levelLength = 1f
    private val entities = mutableListOf<Entity>()
    private var fighting: Enemy? = null
    private var fightAcc = 0f
    private val popups = mutableListOf<Popup>()
    private var flash = 0f
    private var flashColor = Color.WHITE

    private var lastTouchX = 0f
    private var lastFrame = 0L
    private var time = 0f
    private var paused = false

    private var s = 1f          // pixels par unité
    private var viewH = 2000f   // hauteur de l'écran en unités
    private val playerLine: Float get() = viewH * 0.76f
    private val speed: Float get() = min(520f + level * 15f, 820f)

    // ------------------------------------------------------------------ peintures

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }
    private val rect = RectF()

    private val cGrass = Color.rgb(46, 125, 79)
    private val cGrassDark = Color.rgb(38, 108, 67)
    private val cRoad = Color.rgb(91, 98, 112)
    private val cGood = Color.rgb(61, 139, 255)
    private val cBad = Color.rgb(255, 77, 94)
    private val cPlayer = Color.rgb(80, 170, 255)
    private val cPlayerDark = Color.rgb(25, 90, 180)
    private val cEnemy = Color.rgb(235, 70, 70)
    private val cEnemyDark = Color.rgb(140, 25, 30)
    private val cGold = Color.rgb(255, 200, 60)

    init {
        newLevel()
    }

    // ------------------------------------------------------------------ cycle de vie

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            s = w / 1000f
            viewH = h / s
        }
    }

    // ------------------------------------------------------------------ génération

    private fun goodOp(est: Int): Op {
        return if (Random.nextFloat() < 0.35f && est < 500) {
            Op('×', if (Random.nextFloat() < 0.7f) 2 else 3)
        } else {
            val hi = 10 + level * 4 + est / 6
            Op('+', Random.nextInt(5, max(6, hi)))
        }
    }

    private fun badOp(est: Int): Op {
        return if (Random.nextFloat() < 0.3f && est > 8) {
            Op('÷', 2)
        } else {
            val hi = 8 + level * 3 + est / 5
            Op('-', Random.nextInt(3, max(4, hi)))
        }
    }

    /** Construit un niveau gagnable si le joueur choisit bien ses portes. */
    private fun newLevel() {
        entities.clear()
        popups.clear()
        traveled = 0f
        count = START_COUNT
        playerX = MID
        fighting = null
        fightAcc = 0f

        var est = START_COUNT
        val nGates = 6 + min(level, 10)
        var z = 900f
        for (i in 0 until nGates) {
            val a = goodOp(est)
            val b = if (Random.nextFloat() < 0.55f) badOp(est) else goodOp(est)
            val leftFirst = Random.nextBoolean()
            val l = if (leftFirst) a else b
            val r = if (leftFirst) b else a
            entities.add(Gates(z, l, r))
            est = max(l.apply(est), r.apply(est))
            z += SPACING

            // une troupe ennemie toutes les 3 portes
            if (i % 3 == 2 && i < nGates - 1) {
                val frac = 0.3f + Random.nextFloat() * 0.2f
                val enemy = max(2, (est * frac).toInt())
                entities.add(Enemy(z - SPACING / 2f, enemy, false))
                est -= enemy
            }
        }
        // boss final : demande de bien jouer, de plus en plus serré
        val bossFrac = min(0.55f + level * 0.025f, 0.85f)
        val boss = max(5, (est * bossFrac).toInt())
        val bossZ = z + 150f
        entities.add(Enemy(bossZ, boss, true))
        levelLength = bossZ
    }

    // ------------------------------------------------------------------ logique

    private fun setState(st: State) {
        state = st
        stateTime = 0f
    }

    private fun popup(t: String, color: Int, x: Float, y: Float) {
        popups.add(Popup(t, color, x, y, 1.0f))
    }

    private fun lose() {
        count = 0
        flash = 0.5f
        flashColor = cBad
        setState(State.LOST)
    }

    private fun win() {
        flash = 0.5f
        flashColor = cGold
        level++
        best = max(best, level)
        prefs.edit().putInt("level", level).putInt("best", best).apply()
        setState(State.WON)
    }

    private fun update(dt: Float) {
        time += dt
        stateTime += dt
        if (flash > 0f) flash -= dt

        val it = popups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y -= 140f * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }

        when (state) {
            State.PLAYING -> {
                traveled += speed * dt
                for (e in entities) {
                    if (e.done || traveled < e.z) continue
                    when (e) {
                        is Gates -> {
                            e.done = true
                            val left = playerX < MID
                            e.chosen = if (left) 0 else 1
                            val op = if (left) e.left else e.right
                            count = op.apply(count)
                            popup(op.label, if (op.good) cGood else cBad, playerX, playerLine - 200f)
                            if (count <= 0) {
                                lose()
                                return
                            }
                        }
                        is Enemy -> {
                            traveled = e.z
                            fighting = e
                            fightAcc = 0f
                            setState(State.FIGHTING)
                            return
                        }
                    }
                }
            }

            State.FIGHTING -> {
                val e = fighting ?: run { setState(State.PLAYING); return }
                // la bataille dure environ 0,6 s quelle que soit la taille des armées
                fightAcc += dt * max(12f, min(count, e.count) * 1.6f)
                val k = fightAcc.toInt()
                if (k > 0) {
                    fightAcc -= k
                    val kill = min(k, min(count, e.count))
                    count -= kill
                    e.count -= kill
                }
                if (e.count <= 0) {
                    e.done = true
                    fighting = null
                    if (e.boss) win() else setState(State.PLAYING)
                } else if (count <= 0) {
                    lose()
                }
            }

            else -> {}
        }
    }

    // ------------------------------------------------------------------ entrées

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                when (state) {
                    State.MENU -> setState(State.PLAYING)
                    State.LOST, State.WON -> if (stateTime > 0.7f) {
                        newLevel()
                        setState(State.PLAYING)
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (state == State.PLAYING || state == State.FIGHTING) {
                    playerX += (ev.x - lastTouchX) / s * 1.15f
                    playerX = playerX.coerceIn(ROAD_L + 90f, ROAD_R - 90f)
                }
                lastTouchX = ev.x
            }
        }
        return true
    }

    // ------------------------------------------------------------------ rendu

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.05f)
        lastFrame = now
        if (!paused) update(dt)

        canvas.save()
        canvas.scale(s, s)
        drawWorld(canvas)
        drawHud(canvas)
        drawOverlay(canvas)
        canvas.restore()

        if (!paused) postInvalidateOnAnimation()
    }

    private fun screenY(z: Float): Float = playerLine - (z - traveled)

    private fun drawWorld(c: Canvas) {
        // herbe avec bandes qui défilent
        fill.color = cGrass
        c.drawRect(0f, 0f, 1000f, viewH, fill)
        fill.color = cGrassDark
        val band = 240f
        var y = -band * 2 + (traveled % (band * 2))
        while (y < viewH) {
            c.drawRect(0f, y, 1000f, y + band, fill)
            y += band * 2
        }

        // route
        fill.color = cRoad
        c.drawRect(ROAD_L, 0f, ROAD_R, viewH, fill)
        fill.color = Color.argb(200, 255, 255, 255)
        c.drawRect(ROAD_L - 12f, 0f, ROAD_L, viewH, fill)
        c.drawRect(ROAD_R, 0f, ROAD_R + 12f, viewH, fill)
        fill.color = Color.argb(110, 255, 255, 255)
        val dash = 170f
        y = -dash + (traveled % dash)
        while (y < viewH) {
            c.drawRect(MID - 6f, y, MID + 6f, y + dash * 0.5f, fill)
            y += dash
        }

        // entités, de la plus lointaine à la plus proche
        for (i in entities.indices.reversed()) {
            val e = entities[i]
            val sy = screenY(e.z)
            if (sy < -400f || sy > viewH + 300f) continue
            when (e) {
                is Gates -> drawGates(c, e, sy)
                is Enemy -> if (!e.done) {
                    if (e.boss) drawCastle(c, e, sy) else drawCrowd(c, MID, sy - 120f, e.count, cEnemy, cEnemyDark)
                }
            }
        }

        // armée du joueur
        if (count > 0) drawCrowd(c, playerX, playerLine, count, cPlayer, cPlayerDark)

        // textes flottants
        for (p in popups) {
            text.textSize = 110f
            text.color = p.color
            text.alpha = (255 * p.life.coerceIn(0f, 1f)).toInt()
            stroke.color = Color.argb(text.alpha, 20, 20, 30)
            drawOutlined(c, p.text, p.x, p.y, 110f)
        }
        text.alpha = 255
    }

    private fun drawGates(c: Canvas, g: Gates, sy: Float) {
        val top = sy - GATE_H
        for (side in 0..1) {
            val op = if (side == 0) g.left else g.right
            val l = if (side == 0) ROAD_L else MID
            val r = if (side == 0) MID else ROAD_R
            val base = if (op.good) cGood else cBad
            val alpha = when {
                !g.done -> 170
                g.chosen == side -> 220
                else -> 60
            }
            fill.color = base
            fill.alpha = alpha
            rect.set(l + 8f, top, r - 8f, sy)
            c.drawRoundRect(rect, 18f, 18f, fill)
            fill.alpha = 255

            stroke.color = Color.argb(alpha, 255, 255, 255)
            stroke.strokeWidth = 8f
            c.drawRoundRect(rect, 18f, 18f, stroke)

            text.color = Color.WHITE
            text.alpha = if (g.done && g.chosen != side) 90 else 255
            stroke.color = Color.argb(text.alpha, 20, 20, 40)
            drawOutlined(c, op.label, (l + r) / 2f, top + GATE_H / 2f, 100f)
            text.alpha = 255
        }
        // poteaux
        fill.color = Color.rgb(230, 230, 235)
        c.drawRect(ROAD_L - 6f, top - 30f, ROAD_L + 16f, sy + 10f, fill)
        c.drawRect(MID - 11f, top - 30f, MID + 11f, sy + 10f, fill)
        c.drawRect(ROAD_R - 16f, top - 30f, ROAD_R + 6f, sy + 10f, fill)
    }

    /** Dessine une foule en spirale (tournesol) + une bulle avec le nombre. */
    private fun drawCrowd(c: Canvas, x: Float, y: Float, n: Int, body: Int, dark: Int) {
        val shown = min(n, 180)
        val spacing = 17f
        val shaking = state == State.FIGHTING
        for (i in shown - 1 downTo 0) {
            val r = spacing * sqrt(i.toFloat())
            val a = i * 2.39996f
            var cx = x + r * cos(a)
            var cy = y + r * sin(a) * 0.75f
            val bob = sin(time * 14f + i * 0.7f) * 3f
            cy += bob
            if (shaking) {
                cx += Random.nextFloat() * 8f - 4f
                cy += Random.nextFloat() * 8f - 4f
            }
            fill.color = dark
            c.drawCircle(cx, cy + 4f, 14f, fill)
            fill.color = body
            c.drawCircle(cx, cy, 13f, fill)
            fill.color = Color.argb(140, 255, 255, 255)
            c.drawCircle(cx - 4f, cy - 5f, 4f, fill)
        }
        // bulle
        val radius = spacing * sqrt(shown.toFloat()) * 0.75f
        val by = y - radius - 75f
        val label = n.toString()
        text.textSize = 70f
        val w = text.measureText(label) + 60f
        fill.color = dark
        rect.set(x - w / 2f, by - 50f, x + w / 2f, by + 30f)
        c.drawRoundRect(rect, 40f, 40f, fill)
        text.color = Color.WHITE
        c.drawText(label, x, by + 14f, text)
    }

    private fun drawCastle(c: Canvas, e: Enemy, sy: Float) {
        val l = 280f
        val r = 720f
        val bottom = sy
        val top = sy - 300f
        fill.color = Color.rgb(120, 85, 70)
        c.drawRect(l, top, r, bottom, fill)
        // créneaux
        val w = (r - l) / 7f
        for (i in 0 until 7 step 2) {
            c.drawRect(l + i * w, top - 50f, l + (i + 1) * w, top, fill)
        }
        // porte
        fill.color = Color.rgb(60, 35, 30)
        rect.set(MID - 70f, bottom - 150f, MID + 70f, bottom)
        c.drawRoundRect(rect, 60f, 60f, fill)
        // drapeau
        fill.color = Color.rgb(90, 90, 90)
        c.drawRect(MID - 4f, top - 170f, MID + 4f, top - 50f, fill)
        fill.color = cEnemy
        c.drawRect(MID + 4f, top - 170f, MID + 90f, top - 120f, fill)
        // nombre
        text.color = Color.WHITE
        stroke.color = Color.rgb(40, 15, 15)
        drawOutlined(c, e.count.toString(), MID, top + 120f, 110f)
    }

    private fun drawHud(c: Canvas) {
        // barre de progression
        val pad = 60f
        val top = 70f
        fill.color = Color.argb(120, 0, 0, 0)
        rect.set(pad, top, 1000f - pad, top + 34f)
        c.drawRoundRect(rect, 17f, 17f, fill)
        val p = (traveled / levelLength).coerceIn(0f, 1f)
        fill.color = cGold
        rect.set(pad, top, pad + (1000f - 2 * pad) * p, top + 34f)
        c.drawRoundRect(rect, 17f, 17f, fill)

        text.color = Color.WHITE
        stroke.color = Color.argb(200, 0, 0, 0)
        drawOutlined(c, "Niveau $level", MID, top + 110f, 64f)

        if (flash > 0f) {
            fill.color = flashColor
            fill.alpha = (flash * 200).toInt().coerceIn(0, 255)
            c.drawRect(0f, 0f, 1000f, viewH, fill)
            fill.alpha = 255
        }
    }

    private fun drawOverlay(c: Canvas) {
        if (state == State.PLAYING || state == State.FIGHTING) return
        fill.color = Color.argb(150, 10, 15, 30)
        c.drawRect(0f, 0f, 1000f, viewH, fill)
        val cy = viewH * 0.38f
        val blink = (sin(time * 5f) * 0.5f + 0.5f)

        when (state) {
            State.MENU -> {
                text.color = Color.WHITE
                stroke.color = Color.rgb(20, 30, 70)
                drawOutlined(c, "LÉGION", MID, cy - 80f, 150f)
                text.color = cGold
                drawOutlined(c, "DES PORTES", MID, cy + 60f, 110f)
                text.color = Color.WHITE
                text.textSize = 48f
                c.drawText("Glisse pour choisir tes portes", MID, cy + 220f, text)
                c.drawText("Grossis ton armée,", MID, cy + 285f, text)
                c.drawText("puis prends le château !", MID, cy + 345f, text)
                text.textSize = 58f
                c.drawText("Niveau $level  ·  Record $best", MID, cy + 420f, text)
                text.alpha = (120 + 135 * blink).toInt()
                text.textSize = 70f
                c.drawText("Touche pour jouer", MID, cy + 580f, text)
                text.alpha = 255
            }

            State.LOST -> {
                text.color = cBad
                stroke.color = Color.rgb(40, 0, 0)
                drawOutlined(c, "DÉFAITE", MID, cy, 150f)
                text.color = Color.WHITE
                text.textSize = 56f
                c.drawText("Ton armée a été anéantie…", MID, cy + 130f, text)
                if (stateTime > 0.7f) {
                    text.alpha = (120 + 135 * blink).toInt()
                    text.textSize = 70f
                    c.drawText("Touche pour réessayer", MID, cy + 300f, text)
                    text.alpha = 255
                }
            }

            State.WON -> {
                text.color = cGold
                stroke.color = Color.rgb(60, 40, 0)
                drawOutlined(c, "VICTOIRE !", MID, cy, 140f)
                text.color = Color.WHITE
                text.textSize = 56f
                c.drawText("Survivants : $count", MID, cy + 130f, text)
                if (stateTime > 0.7f) {
                    text.alpha = (120 + 135 * blink).toInt()
                    text.textSize = 70f
                    c.drawText("Niveau $level  →", MID, cy + 300f, text)
                    text.alpha = 255
                }
            }

            else -> {}
        }
    }

    /** Texte centré verticalement autour de y, avec contour. */
    private fun drawOutlined(c: Canvas, t: String, x: Float, y: Float, size: Float) {
        text.textSize = size
        stroke.textSize = size
        stroke.textAlign = Paint.Align.CENTER
        stroke.typeface = text.typeface
        stroke.strokeWidth = size * 0.12f
        stroke.strokeJoin = Paint.Join.ROUND
        val baseline = y - (text.ascent() + text.descent()) / 2f
        c.drawText(t, x, baseline, stroke)
        c.drawText(t, x, baseline, text)
    }
}

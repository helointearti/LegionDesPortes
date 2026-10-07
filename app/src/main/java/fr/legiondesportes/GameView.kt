package fr.legiondesportes

import android.content.Context
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
    private var best = prefs.getInt("best", 1)

    private val sprites = Sprites()
    private val renderer = Renderer(sprites)
    private var world = World(level)

    private var screen = Screen.MENU
    private var screenTime = 0f
    private var lastFrame = 0L
    private var time = 0f
    private var paused = false
    private var lastTouchX = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun pause() { paused = true }

    fun resume() {
        paused = false
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) renderer.setSize(w, h)
    }

    private fun setScreen(s: Screen) { screen = s; screenTime = 0f }

    private fun startLevel() {
        world = World(level)
        setScreen(Screen.PLAYING)
    }

    // ------------------------------------------------------------------ entrées

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                when (screen) {
                    Screen.MENU -> setScreen(Screen.PLAYING)
                    Screen.WON, Screen.LOST -> if (screenTime > 0.8f) startLevel()
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (screen == Screen.PLAYING) {
                    world.targetX = (world.targetX + renderer.pxToUnits(ev.x - lastTouchX) * 1.3f).coerceIn(-0.8f, 0.8f)
                }
                lastTouchX = ev.x
            }
        }
        return true
    }

    // ------------------------------------------------------------------ boucle

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.05f)
        lastFrame = now
        val step = if (paused) 0f else dt
        time += step
        screenTime += step

        if (screen == Screen.PLAYING) {
            world.update(step)
            when (world.state) {
                World.State.WON -> {
                    level++
                    best = max(best, level)
                    prefs.edit().putInt("level", level).putInt("best", best).apply()
                    setScreen(Screen.WON)
                }
                World.State.LOST -> setScreen(Screen.LOST)
                else -> {}
            }
        } else if (screen == Screen.WON) {
            world.update(0f)
        }

        renderer.draw(canvas, world, step)
        if (screen == Screen.PLAYING) drawHud(canvas)
        drawOverlay(canvas)

        if (!paused) postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------ interface

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val u = w / 1000f
        // niveau + progression
        val top = 70f * u
        rect.set(40f * u, top, 960f * u, top + 30f * u)
        fill.color = Color.argb(150, 0, 0, 0)
        c.drawRoundRect(rect, 15f * u, 15f * u, fill)
        val prog = (world.traveled / world.levelEnd).coerceIn(0f, 1f)
        rect.set(40f * u, top, 40f * u + 920f * u * prog, top + 30f * u)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(40f * u, 0f, 960f * u, 0f, Color.rgb(255, 210, 80), Color.rgb(255, 120, 40), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 15f * u, 15f * u, fill)
        fill.shader = null
        // icône du boss au bout
        rect.set(905f * u, top - 30f * u, 995f * u, top + 60f * u)
        c.drawBitmap(sprites.boss, null, rect, bmp)
        renderer.outlined(c, "NIVEAU $level", w / 2f, top + 85f * u, 56f * u, Color.WHITE)

        // bonus actifs
        var bx = 50f * u
        val by = top + 140f * u
        if (world.dmgMul > 1.01f) {
            chip(c, bx, by, u, sprites.iconSword, "×" + fmt(world.dmgMul))
            bx += 230f * u
        }
        if (world.rateMul > 1.01f) {
            chip(c, bx, by, u, sprites.iconBolt, "×" + fmt(world.rateMul))
            bx += 230f * u
        }
        if (world.dragonTime > 0f) {
            chip(c, bx, by, u, sprites.dragon, (world.dragonTime + 0.99f).toInt().toString() + "s")
        }

        // barre du boss quand il approche
        val b = world.boss
        if (b != null && b.alive && b.z - world.traveled < 20f) {
            val y = by + 70f * u
            renderer.outlined(c, "SEIGNEUR DÉMON", w / 2f, y, 42f * u, Color.rgb(255, 120, 100))
            rect.set(120f * u, y + 30f * u, 880f * u, y + 62f * u)
            fill.color = Color.argb(180, 30, 0, 0)
            c.drawRoundRect(rect, 16f * u, 16f * u, fill)
            val f = (b.hp / b.maxHp).coerceIn(0f, 1f)
            rect.set(120f * u, y + 30f * u, 120f * u + 760f * u * f, y + 62f * u)
            fill.color = Color.rgb(220, 40, 40)
            c.drawRoundRect(rect, 16f * u, 16f * u, fill)
        }

        // aide au départ
        if (world.time < 4f) {
            val a = ((4f - world.time) * 255).toInt().coerceIn(0, 255)
            renderer.outlined(c, "← Glisse pour changer de voie →", w / 2f, height * 0.9f, 44f * u, Color.WHITE, a)
        }
    }

    private fun fmt(v: Float): String {
        val r = (v * 100).toInt() / 100f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }

    private fun chip(c: Canvas, x: Float, y: Float, u: Float, icon: android.graphics.Bitmap, label: String) {
        rect.set(x, y - 34f * u, x + 210f * u, y + 34f * u)
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
        renderer.outlined(c, label, x + 140f * u, y, 40f * u, Color.rgb(255, 225, 120))
    }

    private fun drawOverlay(c: Canvas) {
        if (screen == Screen.PLAYING) return
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 1000f
        fill.color = Color.argb(if (screen == Screen.MENU) 120 else 150, 10, 8, 25)
        c.drawRect(0f, 0f, w, h, fill)
        val cy = h * 0.36f
        val blink = sin(time * 5f) * 0.5f + 0.5f

        // panneau
        rect.set(70f * u, cy - 260f * u, 930f * u, cy + 420f * u)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, Color.argb(235, 40, 30, 70), Color.argb(235, 15, 10, 30), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 40f * u, 40f * u, fill)
        fill.shader = null
        stroke.color = Color.rgb(255, 200, 70)
        stroke.strokeWidth = 6f * u
        c.drawRoundRect(rect, 40f * u, 40f * u, stroke)

        when (screen) {
            Screen.MENU -> {
                rect.set(330f * u, cy - 420f * u, 670f * u, cy - 220f * u)
                c.drawBitmap(sprites.dragon, null, rect, bmp)
                renderer.outlined(c, "LÉGION", w / 2f, cy - 150f * u, 140f * u, Color.WHITE)
                renderer.outlined(c, "DES PORTES", w / 2f, cy - 30f * u, 96f * u, Color.rgb(255, 200, 70))
                renderer.outlined(c, "Ta légion tire toute seule.", w / 2f, cy + 80f * u, 40f * u, Color.WHITE)
                renderer.outlined(c, "Glisse pour choisir ta voie :", w / 2f, cy + 130f * u, 40f * u, Color.WHITE)
                renderer.outlined(c, "casse les tonneaux pour des bonus,", w / 2f, cy + 180f * u, 40f * u, Color.rgb(170, 220, 255))
                renderer.outlined(c, "mais arrête la horde à temps !", w / 2f, cy + 230f * u, 40f * u, Color.rgb(255, 150, 140))
                renderer.outlined(c, "Niveau $level  ·  Record $best", w / 2f, cy + 300f * u, 44f * u, Color.rgb(255, 225, 120))
                renderer.outlined(c, "TOUCHE POUR JOUER", w / 2f, cy + 380f * u, 56f * u, Color.WHITE, (120 + 135 * blink).toInt())
            }
            Screen.WON -> {
                renderer.outlined(c, "VICTOIRE !", w / 2f, cy - 140f * u, 130f * u, Color.rgb(255, 210, 80))
                renderer.outlined(c, "Le Seigneur Démon est tombé", w / 2f, cy - 20f * u, 44f * u, Color.WHITE)
                rect.set(400f * u, cy + 30f * u, 480f * u, cy + 140f * u)
                c.drawBitmap(sprites.ally, null, rect, bmp)
                renderer.outlined(c, "× ${world.count}", w / 2f + 60f * u, cy + 90f * u, 64f * u, Color.rgb(140, 200, 255))
                renderer.outlined(c, "survivants", w / 2f, cy + 180f * u, 40f * u, Color.WHITE)
                if (screenTime > 0.8f) renderer.outlined(c, "NIVEAU $level  →", w / 2f, cy + 330f * u, 60f * u, Color.WHITE, (120 + 135 * blink).toInt())
            }
            Screen.LOST -> {
                renderer.outlined(c, "DÉFAITE", w / 2f, cy - 140f * u, 140f * u, Color.rgb(255, 90, 90))
                renderer.outlined(c, "La horde a submergé ta légion…", w / 2f, cy - 20f * u, 42f * u, Color.WHITE)
                renderer.outlined(c, "Astuce : ne t'attarde pas trop", w / 2f, cy + 90f * u, 40f * u, Color.rgb(255, 225, 120))
                renderer.outlined(c, "sur les tonneaux !", w / 2f, cy + 140f * u, 40f * u, Color.rgb(255, 225, 120))
                if (screenTime > 0.8f) renderer.outlined(c, "TOUCHE POUR RÉESSAYER", w / 2f, cy + 330f * u, 56f * u, Color.WHITE, (120 + 135 * blink).toInt())
            }
            else -> {}
        }
    }
}

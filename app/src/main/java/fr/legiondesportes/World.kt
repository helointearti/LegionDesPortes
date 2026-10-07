package fr.legiondesportes

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Logique pure du jeu (aucune dépendance Android, testable sur JVM).
 *
 * Repère :
 *  - x : position latérale. La route va de -1 (bord gauche) à +1 (bord droit).
 *        Un muret central (x = 0) sépare la voie gauche (-1) et la voie droite (+1).
 *  - z : distance en mètres le long de la route. Les objets ont un z "monde" fixe ;
 *        leur distance au joueur est z - traveled.
 */

enum class Reward { SOLDIERS, DAMAGE, RATE, DRAGON }

class Enemy(var x: Float, var z: Float, var hp: Float, val spread: Float) {
    var alive = true
    var flash = 0f
    val phase = Random.nextFloat() * 6.28f
}

class Barrel(val lane: Int, val z: Float, var hp: Float, val reward: Reward, val amount: Int, val chest: Boolean) {
    val maxHp = hp
    var alive = true
    var flash = 0f
    val x: Float get() = lane * 0.5f
}

class Gate(val lane: Int, val z: Float, var value: Float, val gain: Float) {
    var used = false
    var flash = 0f
    val x: Float get() = lane * 0.5f
}

class Boss(var z: Float, var hp: Float) {
    val maxHp = hp
    var alive = true
    var flash = 0f
    var attackCd = 0f
}

/** kind : 0 = boule de feu du héros, 1 = trait des soldats, 2 = feu du dragon */
class Proj(var x: Float, var z: Float, val dmg: Float, val kind: Int) {
    var alive = true
}

/** Événements que le rendu transforme en effets visuels. z est relatif au joueur. */
class Ev(val type: Int, val x: Float, val z: Float, val value: Int = 0, val reward: Reward? = null) {
    companion object {
        const val HIT = 0
        const val ENEMY_DIE = 1
        const val BARREL_BREAK = 2
        const val GATE = 3
        const val SOLDIER_LOST = 4
        const val BOSS_HIT = 5
        const val BOSS_DIE = 6
        const val BARREL_HIT = 7
        const val GATE_HIT = 8
    }
}

class World(val level: Int, private val rnd: Random = Random.Default) {

    enum class State { RUNNING, WON, LOST }

    companion object {
        const val SCROLL = 2.4f          // vitesse d'avance (m/s)
        const val ENEMY_SPEED = 1.5f     // vitesse propre des ennemis (m/s)
        const val DIVIDER_END = 2.4f     // le muret central s'arrête à cette distance
        const val SPAWN_GAP = 18f        // distance entre deux rencontres
        const val MAX_SHOWN = 40         // soldats dessinés au maximum
        const val CONTACT = 0.35f
        const val VIEW = 19f             // distance de tir max (= distance visible)
    }

    var state = State.RUNNING
    var time = 0f
    var traveled = 0f
    var targetX = 0.5f
    var playerX = 0.5f
    var count = 5
    var dmgMul = 1f
    var rateMul = 1f
    var dragonTime = 0f
    var levelEnd = 1f

    val enemies = ArrayList<Enemy>()
    val barrels = ArrayList<Barrel>()
    val gates = ArrayList<Gate>()
    var boss: Boss? = null
    val projs = ArrayList<Proj>()
    val events = ArrayList<Ev>()

    private var heroCd = 0f
    private var soldierAcc = 0f
    private var dragonAcc = 0f
    private var dragonLane = 1

    // positions de formation (relatives au héros)
    val slotX = FloatArray(MAX_SHOWN)
    val slotZ = FloatArray(MAX_SHOWN)

    init {
        for (i in 0 until MAX_SHOWN) {
            val row = i / 5
            val col = i % 5
            slotX[i] = (col - 2) * 0.085f + (if (row % 2 == 1) 0.04f else 0f)
            slotZ[i] = -0.45f - row * 0.2f
        }
    }

    val lane: Int get() = if (playerX < 0f) -1 else 1

    // ------------------------------------------------------------------ génération
    //
    // Le niveau est une liste de rencontres. Chacune n'est "matérialisée" qu'en entrant
    // dans le champ de vision, et dimensionnée d'après la puissance de feu ACTUELLE du joueur :
    // la difficulté reste juste quel que soit le nombre de soldats accumulés.

    private class Enc(val z: Float, val type: Int, val side: Int, val reward: Reward)

    private val encs = ArrayList<Enc>()
    private var nextEnc = 0

    init {
        generate()
    }

    /** Pression : fraction du temps d'approche nécessaire pour vaincre une horde. */
    private val pressure: Float get() = min(0.60f + 0.012f * (level - 1), 0.85f)

    private fun randomReward(i: Int): Reward {
        val r = rnd.nextFloat()
        return when {
            i >= 2 && r < 0.16f -> Reward.DRAGON
            r < 0.52f -> Reward.SOLDIERS
            r < 0.76f -> Reward.DAMAGE
            else -> Reward.RATE
        }
    }

    private fun generate() {
        val nEnc = 7 + min(level, 8) / 3
        var z = 24f
        for (i in 0 until nEnc) {
            val side = if (rnd.nextBoolean()) -1 else 1
            val type = when (i) {
                0 -> 1
                1 -> 0
                else -> {
                    val r = rnd.nextFloat()
                    when {
                        r < 0.45f -> 0
                        r < 0.68f -> 1
                        r < 0.85f -> 2
                        else -> 3
                    }
                }
            }
            encs.add(Enc(z, type, side, randomReward(i)))
            z += SPAWN_GAP
        }
        encs.add(Enc(z + 6f, 9, 1, Reward.SOLDIERS))
        levelEnd = z + 6f - 13f
    }

    private fun spawnHorde(laneSide: Int, z: Float, n: Int, hp: Float) {
        // laneSide : -1, +1, ou 0 pour toute la largeur
        for (k in 0 until n) {
            val row = k / 8
            val x = when (laneSide) {
                0 -> -0.9f + rnd.nextFloat() * 1.8f
                else -> laneSide * (0.1f + rnd.nextFloat() * 0.82f)
            }
            val zz = z + row * 0.38f + rnd.nextFloat() * 0.3f
            enemies.add(Enemy(x, zz, hp, rnd.nextFloat() * 0.5f - 0.25f))
        }
    }

    private fun spawn(e: Enc) {
        val p = dps()
        val approach = (VIEW - CONTACT) / (SCROLL + ENEMY_SPEED)
        val idx = nextEnc
        fun horde(side: Int, z: Float, frac: Float) {
            val total = p * approach * frac
            val n = (12f + count * 0.9f * frac + 3f * idx).roundToInt().coerceIn(12, 150)
            spawnHorde(side, z, n, max(1f, total / n))
        }
        fun barrel(side: Int, z: Float) {
            val rw = e.reward
            val amount = if (rw == Reward.SOLDIERS) max(4, (count * 0.3f + 3f).roundToInt()) else 0
            val mul = when (rw) { Reward.DRAGON -> 0.55f; Reward.SOLDIERS -> 0.42f; else -> 0.48f }
            val hp = max(10f, (p * approach * mul * (1f + 0.02f * level)).roundToInt().toFloat())
            barrels.add(Barrel(side, z, hp, rw, amount, rw == Reward.DRAGON || rw == Reward.DAMAGE))
        }
        fun gate(side: Int, z: Float, v: Int) {
            // ~3 s de tir sur une porte la fait monter d'environ 40 % de l'armée actuelle
            val gain = (count * 0.4f + 4f) / (p * 3f)
            gates.add(Gate(side, z, v.toFloat(), gain))
        }
        val c = count
        when (e.type) {
            0 -> { horde(e.side, e.z, pressure); barrel(-e.side, e.z) }
            1 -> {
                if (idx == 0) {
                    gate(e.side, e.z, 3)
                    gate(-e.side, e.z, 8)
                } else {
                    // la mauvaise porte ne peut pas vider entièrement l'armée
                    val bad = -min((c * 0.3f + 3f + level).roundToInt(), max(1, c - 2))
                    gate(e.side, e.z, bad)
                    val good = if (rnd.nextFloat() < 0.6f) (c * 0.15f + 2f).roundToInt()
                    else -min((c * 0.12f + 1f).roundToInt(), max(1, c / 3))
                    gate(-e.side, e.z, good)
                }
            }
            2 -> { horde(-1, e.z, pressure * 0.62f); horde(1, e.z + 1.2f, pressure * 0.62f) }
            3 -> { barrel(e.side, e.z); gate(-e.side, e.z + 1.5f, -min((c * 0.2f + 2f + level).roundToInt(), max(1, c - 2))) }
            9 -> {
                // le boss met ~23 s à arriver
                val frac = min(0.48f + 0.01f * level, 0.68f)
                boss = Boss(e.z, (p * 23f * frac).roundToInt().toFloat())
            }
        }
    }

    // ------------------------------------------------------------------ boucle

    fun dps(): Float = (6f + count * 1.2f) * dmgMul * rateMul

    private fun loseSoldier(x: Float, rel: Float) {
        if (count > 0) {
            count--
            events.add(Ev(Ev.SOLDIER_LOST, x, rel))
        } else {
            state = State.LOST
        }
    }

    fun update(dt: Float) {
        if (state != State.RUNNING) return
        time += dt
        playerX += (targetX - playerX) * min(1f, dt * 14f)

        val b = boss
        val bossNear = b != null && b.alive && b.z - traveled < 13f
        if (!bossNear) traveled += SCROLL * dt
        if (dragonTime > 0f) dragonTime -= dt

        // ennemis
        for (e in enemies) {
            if (!e.alive) continue
            if (e.flash > 0f) e.flash -= dt
            val rel0 = e.z - traveled
            if (rel0 < VIEW + 2f) e.z -= ENEMY_SPEED * dt
            val rel = e.z - traveled
            if (rel < DIVIDER_END) {
                val tx = playerX + e.spread
                e.x += (tx - e.x) * min(1f, dt * 2.2f)
            }
            if (rel <= CONTACT) {
                e.alive = false
                events.add(Ev(Ev.ENEMY_DIE, e.x, rel))
                // un ennemi robuste emporte plusieurs soldats
                repeat(max(1, (e.hp / 3f).roundToInt())) { if (state == State.RUNNING) loseSoldier(e.x, rel) }
                if (state != State.RUNNING) return
            }
        }

        // tonneaux
        for (br in barrels) {
            if (br.flash > 0f) br.flash -= dt
            if (br.alive && br.z - traveled < -1.5f) br.alive = false
        }

        // portes
        for (g in gates) {
            if (g.flash > 0f) g.flash -= dt
            if (!g.used && g.z - traveled <= 0f) {
                g.used = true
                if (g.lane == lane) {
                    val v = g.value.roundToInt()
                    count = max(0, count + v)
                    events.add(Ev(Ev.GATE, g.x, 0f, v))
                }
            }
        }

        // boss
        if (b != null && b.alive) {
            if (b.flash > 0f) b.flash -= dt
            val rel = b.z - traveled
            if (rel < 13f && rel > 1.3f) b.z -= 0.5f * dt
            if (rel <= 1.35f) {
                b.attackCd -= dt
                if (b.attackCd <= 0f) {
                    b.attackCd = 0.5f
                    repeat(3) { if (state == State.RUNNING) loseSoldier(playerX, 0.2f) }
                    if (state != State.RUNNING) return
                }
            }
        }

        while (nextEnc < encs.size && encs[nextEnc].z - traveled < VIEW + 1f) {
            spawn(encs[nextEnc]); nextEnc++
        }

        fire(dt)
        moveProjectiles(dt)

        if (enemies.size > 400) enemies.removeAll { !it.alive }
        projs.removeAll { !it.alive }

        if (b != null && !b.alive) state = State.WON
    }

    private fun fire(dt: Float) {
        heroCd -= dt
        if (heroCd <= 0f) {
            heroCd = 1f / (2f * rateMul)
            projs.add(Proj(playerX, 0.2f, 3f * dmgMul, 0))
        }
        if (count > 0) {
            val shotsPerSec = min(count * 1.2f * rateMul, 18f)
            val total = count * 1.2f * rateMul * dmgMul
            soldierAcc += dt * shotsPerSec
            while (soldierAcc >= 1f) {
                soldierAcc -= 1f
                val slot = rnd.nextInt(min(count, MAX_SHOWN))
                projs.add(Proj(playerX + slotX[slot], slotZ[slot] + 0.2f, total / shotsPerSec, 1))
            }
        }
        if (dragonTime > 0f) {
            val rate = 14f
            val dragonDps = 20f + dps() * 1.3f
            dragonAcc += dt * rate
            while (dragonAcc >= 1f) {
                dragonAcc -= 1f
                dragonLane = -dragonLane
                projs.add(Proj(dragonLane * (0.5f + rnd.nextFloat() * 0.5f - 0.25f), 1.0f, dragonDps / rate, 2))
            }
        }
    }

    private fun moveProjectiles(dt: Float) {
        for (p in projs) {
            if (!p.alive) continue
            val speed = when (p.kind) { 0 -> 19f; 1 -> 24f; else -> 16f }
            p.z += speed * dt
            if (p.z > VIEW) { p.alive = false; continue }
            val pl = if (p.x < 0f) -1 else 1

            var bestZ = Float.MAX_VALUE
            var hitEnemy: Enemy? = null
            var hitBarrel: Barrel? = null
            var hitGate: Gate? = null
            var hitBoss = false

            for (e in enemies) {
                if (!e.alive) continue
                val rel = e.z - traveled
                if (rel > p.z || rel < -0.3f) continue
                val el = if (e.x < 0f) -1 else 1
                if (el != pl) continue
                if (rel < bestZ) { bestZ = rel; hitEnemy = e }
            }
            for (br in barrels) {
                if (!br.alive || br.lane != pl) continue
                val rel = br.z - traveled
                if (rel > p.z || rel < -0.3f) continue
                if (rel < bestZ) { bestZ = rel; hitBarrel = br; hitEnemy = null }
            }
            for (g in gates) {
                if (g.used || g.lane != pl) continue
                val rel = g.z - traveled
                if (rel > p.z || rel < 0f) continue
                if (rel < bestZ) { bestZ = rel; hitGate = g; hitBarrel = null; hitEnemy = null }
            }
            val b = boss
            if (b != null && b.alive) {
                val rel = b.z - traveled
                if (rel <= p.z && rel < bestZ) {
                    bestZ = rel; hitBoss = true; hitGate = null; hitBarrel = null; hitEnemy = null
                }
            }

            when {
                hitEnemy != null -> {
                    p.alive = false
                    val e = hitEnemy
                    e.hp -= p.dmg
                    e.flash = 0.12f
                    events.add(Ev(Ev.HIT, e.x, bestZ, p.kind))
                    if (e.hp <= 0f) {
                        e.alive = false
                        events.add(Ev(Ev.ENEMY_DIE, e.x, bestZ))
                    }
                }
                hitBarrel != null -> {
                    p.alive = false
                    val br = hitBarrel
                    br.hp -= p.dmg
                    br.flash = 0.1f
                    events.add(Ev(Ev.BARREL_HIT, p.x, bestZ, p.kind))
                    if (br.hp <= 0f) {
                        br.alive = false
                        applyReward(br)
                        events.add(Ev(Ev.BARREL_BREAK, br.x, bestZ, br.amount, br.reward))
                    }
                }
                hitGate != null -> {
                    p.alive = false
                    val g = hitGate
                    g.value += p.dmg * g.gain
                    g.flash = 0.1f
                    events.add(Ev(Ev.GATE_HIT, p.x, bestZ, p.kind))
                }
                hitBoss -> {
                    p.alive = false
                    val bb = boss!!
                    bb.hp -= p.dmg
                    bb.flash = 0.1f
                    events.add(Ev(Ev.BOSS_HIT, p.x, bestZ, p.kind))
                    if (bb.hp <= 0f) {
                        bb.alive = false
                        events.add(Ev(Ev.BOSS_DIE, 0f, bestZ))
                    }
                }
            }
        }
    }

    private fun applyReward(br: Barrel) {
        when (br.reward) {
            Reward.SOLDIERS -> count = min(9999, count + br.amount)
            Reward.DAMAGE -> dmgMul *= 1.5f
            Reward.RATE -> rateMul *= 1.35f
            Reward.DRAGON -> dragonTime = 7f
        }
    }

    /** Distance relative la plus proche d'un ennemi vivant dans une voie (pour le bot de test). */
    fun nearestEnemy(l: Int): Float {
        var best = Float.MAX_VALUE
        for (e in enemies) if (e.alive) {
            val el = if (e.x < 0f) -1 else 1
            val rel = e.z - traveled
            if (el == l && rel < best && rel > 0f) best = rel
        }
        return best
    }

    fun hpInLane(l: Int, maxRel: Float): Float {
        var s = 0f
        for (e in enemies) if (e.alive) {
            val el = if (e.x < 0f) -1 else 1
            val rel = e.z - traveled
            if (el == l && rel < maxRel) s += e.hp
        }
        return s
    }
}

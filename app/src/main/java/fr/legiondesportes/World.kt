package fr.legiondesportes

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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

// ====================================================================== héros

enum class HeroType(val title: String, val skill: String, val desc: String) {
    FIRE("Pyromancienne", "Boule de feu", "Explosion de zone devant la légion"),
    STORM("Mage de foudre", "Chaîne d'éclairs", "Foudroie des ennemis dans les deux voies"),
    FROST("Mage de givre", "Nova de glace", "Gèle et blesse toute la horde"),
    RANGER("Archère", "Pluie de flèches", "Arrose le plus gros groupe d'ennemis"),
    PRIEST("Prêtresse", "Bénédiction", "Ramène des soldats et soigne les héros")
}

class Hero(val type: HeroType) {
    var level = 1
        private set
    var maxHp = 6
    var hp = 6f
    var alive = true
    var cd = 1.2f
    var cast = 9f          // temps écoulé depuis le dernier sort (pour l'animation)
    var hurt = 0f

    fun setLevel(l: Int) {
        level = l.coerceIn(1, 5)
        maxHp = 4 + 2 * level
        hp = maxHp.toFloat()
        alive = true
    }

    fun cooldown(): Float = when (type) {
        HeroType.FIRE -> 3.0f - 0.28f * (level - 1)
        HeroType.STORM -> 4.0f - 0.35f * (level - 1)
        HeroType.FROST -> 7.5f - 0.6f * (level - 1)
        HeroType.RANGER -> 5.0f - 0.45f * (level - 1)
        HeroType.PRIEST -> 10f - 0.9f * (level - 1)
    }
}

// ====================================================================== ennemis

enum class EnemyType(val speed: Float, val size: Float, val killFrac: Float, val killMin: Int, val heroDmg: Float) {
    SKELETON(1.5f, 0.19f, 0.006f, 1, 0.5f),
    RUNNER(3.0f, 0.16f, 0.004f, 1, 0.4f),
    BRUTE(0.9f, 0.30f, 0.03f, 3, 1.5f),
    KNIGHT(1.0f, 0.42f, 0.12f, 8, 3f)
}

class Enemy(val type: EnemyType, var x: Float, var z: Float, var hp: Float, val spread: Float, val phase: Float) {
    val maxHp = hp
    var alive = true
    var flash = 0f
    var slow = 0f
}

// ====================================================================== récompenses

enum class RewardKind { SOLDIERS, DAMAGE, RATE, DRAGON, SKILL, RECRUIT, HEAL }

class Reward(val kind: RewardKind, val hero: HeroType? = null, val amount: Int = 0) {
    fun label(): String = when (kind) {
        RewardKind.SOLDIERS -> "+$amount"
        RewardKind.DAMAGE -> "×1.3"
        RewardKind.RATE -> "+20%"
        RewardKind.DRAGON -> "DRAGON"
        RewardKind.SKILL -> "NIV +1"
        RewardKind.RECRUIT -> "RECRUE"
        RewardKind.HEAL -> "SOINS"
    }

    fun longLabel(): String = when (kind) {
        RewardKind.SOLDIERS -> "+$amount SOLDATS"
        RewardKind.DAMAGE -> "DÉGÂTS ×1.3"
        RewardKind.RATE -> "CADENCE +20%"
        RewardKind.DRAGON -> "DRAGON !"
        RewardKind.SKILL -> "${hero!!.skill.uppercase()} +1"
        RewardKind.RECRUIT -> "${hero!!.title.uppercase()} !"
        RewardKind.HEAL -> "HÉROS SOIGNÉS"
    }
}

class Barrel(val lane: Int, val z: Float, var hp: Float, val reward: Reward, val chest: Boolean) {
    val maxHp = hp
    var alive = true
    var flash = 0f
    val x: Float get() = lane * 0.5f
}

enum class GateKind { SOLDIERS, MULT, SKILL }

class Gate(val lane: Int, val z: Float, var value: Float, val gain: Float, val kind: GateKind, val reward: Reward? = null) {
    var used = false
    var flash = 0f
    val x: Float get() = lane * 0.5f
}

// ====================================================================== dangers et boss

/** Attaque annoncée sur une voie. kind : 0 rocher, 1 souffle de feu, 2 onde de choc */
class Hazard(val lane: Int, var delay: Float, val telegraph: Float, val window: Float, val frac: Float, val kind: Int) {
    var hit = false
    var impacted = false
    var done = false
}

enum class BossType(val title: String) {
    DEMON("Seigneur Démon"),
    GOLEM("Golem de pierre"),
    NECRO("Nécromancien"),
    DRAGON("Dragon noir")
}

class Boss(val type: BossType, var z: Float, var hp: Float, val mini: Boolean, val final: Boolean) {
    val maxHp = hp
    var alive = true
    var flash = 0f
    var abilityCd = 3f
    var meleeCd = 0f
    var slow = 0f
    var anim = 9f
    var enraged = false
}

/** kind : 0 trait de héros (tag = type de héros), 1 trait de soldat, 2 feu du dragon, 3 boule de feu */
class Proj(var x: Float, var z: Float, val dmg: Float, val kind: Int, val tag: Int = 0, val tx: Float = 0f, val radius: Float = 0f) {
    var alive = true
}

/** Frappe différée (flèche qui tombe). */
class Strike(val x: Float, val z: Float, var delay: Float, val radius: Float, val dmg: Float)

enum class CardKind { RECRUIT, UPGRADE, SOLDIERS, DAMAGE, RATE, HEAL, DRAGON }

class Card(val kind: CardKind, val hero: HeroType? = null)

/** Événements que le rendu transforme en effets visuels. z est relatif au joueur. */
class Ev(val type: Int, val x: Float, val z: Float, val value: Int = 0, val reward: Reward? = null,
         val pts: FloatArray? = null, val f: Float = 0f) {
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
        const val EXPLODE = 9
        const val LIGHTNING = 10
        const val NOVA = 11
        const val BLESS = 12
        const val HERO_HURT = 13
        const val HERO_DOWN = 14
        const val IMPACT = 15
        const val ARROW = 16
        const val SUMMON = 17
        const val REWARD = 18
        const val BOSS_CAST = 19
    }
}

// ====================================================================== monde

enum class Mode { CAMPAIGN, ENDLESS }

class World(val mode: Mode, val level: Int, private val rnd: Random = Random.Default) {

    enum class State { RUNNING, CHOOSING, WON, LOST }

    companion object {
        const val SCROLL = 2.4f          // vitesse d'avance (m/s)
        const val DIVIDER_END = 2.4f     // le muret central s'arrête à cette distance
        const val MAX_SHOWN = 40         // soldats dessinés au maximum
        const val CONTACT = 0.35f
        const val VIEW = 19f             // distance de tir max (= distance visible)
        const val BOSS_EVERY = 10        // rencontres entre deux boss (mode sans limite)
        const val MAX_COUNT = 1_000_000_000

        val HX = floatArrayOf(0f, -0.17f, 0.17f, -0.31f, 0.31f)
        val HZ = floatArrayOf(0f, -0.1f, -0.1f, -0.2f, -0.2f)

        // types de rencontres
        const val E_HORDE = 0
        const val E_GATES = 1
        const val E_DOUBLE = 2
        const val E_BARREL_GATE = 3
        const val E_RUNNERS = 4
        const val E_BRUTES = 5
        const val E_BARRAGE = 6
        const val E_TREASURE = 7
        const val E_SKILLGATES = 8
        const val E_KNIGHT = 9
        const val E_AMBUSH = 10
        const val E_BOSS = 99
    }

    var basePower = 16f
    var growth = 1.115f
    var pressureMul = 1f
    var rubber = 0.55f

    var state = State.RUNNING
    var time = 0f
    var traveled = 0f
    var targetX = 0.5f
    var playerX = 0.5f
    var count = 5
    var dmgMul = 1f
    var rateMul = 1f
    var dragonTime = 0f
    var kills = 0
    var bossKills = 0
    var levelEnd = 1f
    var bossFight = 0f
    val bossFights = ArrayList<Pair<BossType, Float>>()            // campagne : distance du boss final (pour la barre de progression)

    val heroes = ArrayList<Hero>()
    val enemies = ArrayList<Enemy>()
    val barrels = ArrayList<Barrel>()
    val gates = ArrayList<Gate>()
    val hazards = ArrayList<Hazard>()
    var boss: Boss? = null
    val projs = ArrayList<Proj>()
    val strikes = ArrayList<Strike>()
    val events = ArrayList<Ev>()
    var cards: List<Card> = emptyList()
    var announce: String? = null
    var announceTime = 0f

    private var soldierAcc = 0f
    private var dragonAcc = 0f
    private var dragonLane = 1
    private val heroShotCd = FloatArray(5)

    // positions de formation des soldats (relatives au héros principal)
    val slotX = FloatArray(MAX_SHOWN)
    val slotZ = FloatArray(MAX_SHOWN)

    // biomes : 0 village, 1 pont, 2 forêt, 3 forteresse de lave
    private val biomeZ = ArrayList<Float>()
    private val biomeId = ArrayList<Int>()
    val biomeArches = ArrayList<Float>()

    init {
        for (i in 0 until MAX_SHOWN) {
            val row = i / 5
            val col = i % 5
            slotX[i] = (col - 2) * 0.085f + (if (row % 2 == 1) 0.04f else 0f)
            slotZ[i] = -0.45f - row * 0.2f
        }
        heroes.add(Hero(HeroType.FIRE).also { it.setLevel(1) })
        if (mode == Mode.CAMPAIGN) {
            growth = min(1.13f + 0.005f * (level - 1), 1.18f)
            pressureMul = min(1f + 0.035f * (level - 1), 1.4f)
            rubber = 0.75f
        } else {
            growth = 1.16f
            rubber = 0.85f
        }
        val startBiome = if (mode == Mode.CAMPAIGN) (level - 1) % 4 else 0
        biomeZ.add(-1000f); biomeId.add(startBiome)
    }

    val lane: Int get() = if (playerX < 0f) -1 else 1

    fun heroX(i: Int): Float = playerX + HX[i]
    fun heroZ(i: Int): Float = HZ[i]

    fun biomeAt(z: Float): Int {
        var b = biomeId[0]
        for (i in biomeZ.indices) if (biomeZ[i] <= z) b = biomeId[i]
        return b
    }

    fun hasHero(t: HeroType) = heroes.any { it.type == t }
    fun hero(t: HeroType) = heroes.firstOrNull { it.type == t }
    val aliveHeroes: Int get() = heroes.count { it.alive }

    // ================================================================== puissance

    /** Dégâts par seconde des tirs de base (soldats + héros). Les sorts s'en servent comme unité. */
    fun dps(): Float {
        var h = 0f
        for (hero in heroes) if (hero.alive) h += 6f * (1f + 0.25f * (hero.level - 1))
        return (count * 1.2f + h) * dmgMul * rateMul
    }

    /** Estimation de la puissance totale, sorts compris. */
    fun effDps(): Float {
        var m = 1f
        for (h in heroes) if (h.alive) {
            val l = h.level.toFloat()
            m += when (h.type) {
                HeroType.FIRE -> (0.5f + 0.3f * l) * 3f / h.cooldown()
                HeroType.STORM -> (0.3f + 0.15f * l) * min(2f + 2f * l, 8f) / h.cooldown() * 0.7f
                HeroType.FROST -> 0.3f * l * 6f / h.cooldown() * 0.5f
                HeroType.RANGER -> (0.25f + 0.1f * l) * (5f + 3f * l) * 0.5f / h.cooldown()
                HeroType.PRIEST -> 0f
            }
        }
        val dragon = if (dragonTime > 0f) 20f + dps() * 1.3f else 0f
        return dps() * m + dragon
    }

    /** Difficulté "absolue" qui croît avec la progression. */
    private fun power(i: Int): Float = basePower * growth.pow(i)


    // ================================================================== génération

    private class Enc(val z: Float, val type: Int, val side: Int, val idx: Int,
                      val bossType: BossType? = null, val mini: Boolean = false, val final: Boolean = false)

    private val encs = ArrayDeque<Enc>()
    private var genZ = 22f
    private var normalCount = 0
    private var normalSinceBoss = 0
    private var midPlanned = false
    private var campaignDone = false
    private var lastType = -1
    private val bossOrder = ArrayList<BossType>()
    private var nextBiome = 0

    private val offset: Int get() = 0
    val campaignLen: Int get() = 18 + min(level, 10)

    /** Rencontres franchies (score du mode sans limite). */
    val encountersDone: Int get() = normalCount

    init {
        nextBiome = (biomeId[0] + 1) % 4
        planAhead()
    }

    private fun nextBossType(): BossType {
        if (bossOrder.isEmpty()) {
            bossOrder.addAll(BossType.values())
            bossOrder.shuffle(rnd)
        }
        return bossOrder.removeAt(0)
    }

    private fun planBoss(mini: Boolean, final: Boolean) {
        val bz = genZ + 6f
        val bt = if (mode == Mode.CAMPAIGN) {
            if (final) BossType.values()[(level - 1) % 4] else BossType.values()[(level + 1) % 4]
        } else nextBossType()
        encs.addLast(Enc(bz, E_BOSS, 1, normalCount + offset, bt, mini, final))
        if (final) levelEnd = bz - 10.5f
        if (!final) {
            // la région suivante commence derrière le boss
            biomeZ.add(bz + 5f); biomeId.add(nextBiome)
            biomeArches.add(bz + 5f)
            nextBiome = (nextBiome + 1) % 4
        }
        genZ = bz + 20f
    }

    private fun planNext() {
        if (mode == Mode.CAMPAIGN) {
            if (campaignDone) return
            if (normalCount >= campaignLen) { planBoss(mini = false, final = true); campaignDone = true; return }
            if (normalCount >= campaignLen / 2 && !midPlanned) { planBoss(mini = true, final = false); midPlanned = true; return }
        } else if (normalSinceBoss >= BOSS_EVERY) {
            planBoss(mini = false, final = false)
            normalSinceBoss = 0
            return
        }
        val type = chooseType(normalCount)
        lastType = type
        val side = if (rnd.nextBoolean()) -1 else 1
        encs.addLast(Enc(genZ, type, side, normalCount + offset))
        genZ += when (type) {
            E_BARRAGE -> 26f
            E_TREASURE, E_AMBUSH, E_KNIGHT -> 21f
            E_GATES, E_SKILLGATES -> 15f
            else -> 18f
        }
        normalCount++
        normalSinceBoss++
    }

    private fun planAhead() {
        while (encs.size < 3 && !(mode == Mode.CAMPAIGN && campaignDone)) planNext()
    }

    private fun chooseType(n: Int): Int {
        when (n) {
            0 -> return E_GATES
            1 -> return E_HORDE
            2 -> return E_SKILLGATES
        }
        val i = n + offset
        val w = floatArrayOf(
            3f,                                  // horde + tonneau
            1.3f,                                // portes
            if (i >= 2) 1.2f else 0f,            // double horde
            0.8f,                                // tonneau + porte
            if (i >= 3) 1.0f else 0f,            // coureurs
            if (i >= 4) 1.0f else 0f,            // brutes
            if (i >= 5) 0.8f else 0f,            // catapultes
            if (i >= 3) 0.7f else 0f,            // trésor
            1.2f,                                // porte de compétence
            if (i >= 6) 0.7f else 0f,            // chevalier noir
            if (i >= 8) 0.6f else 0f             // embuscade
        )
        if (lastType in w.indices) w[lastType] = 0f
        var s = 0f
        for (v in w) s += v
        var r = rnd.nextFloat() * s
        for (k in w.indices) {
            r -= w[k]
            if (r <= 0f && w[k] > 0f) return k
        }
        return E_HORDE
    }

    // ---------------------------------------------------------------- matérialisation

    private var curB = 1f
    private var curIdx = 0

    private fun pressure(i: Int): Float = min(0.58f + 0.006f * i, 0.78f) * pressureMul

    private fun approach(speed: Float) = (VIEW - CONTACT) / (SCROLL + speed)

    private fun refreshBudget(i: Int) {
        curIdx = i
        val f = power(i)
        val r = max(1f, effDps() / f)
        curB = f * r.pow(rubber)
    }

    private fun spawnHorde(type: EnemyType, side: Int, z: Float, frac: Float) {
        val total = approach(type.speed) * curB * frac
        val i = curIdx
        val n = when (type) {
            EnemyType.SKELETON -> (10f + 2.2f * i).roundToInt().coerceIn(10, 130)
            EnemyType.RUNNER -> (8f + 1.4f * i).roundToInt().coerceIn(8, 60)
            EnemyType.BRUTE -> (3f + 0.35f * i).roundToInt().coerceIn(3, 14)
            EnemyType.KNIGHT -> 1
        }
        val n2 = max(1, (n * min(1f, frac / 0.5f + 0.2f)).roundToInt())
        val hp = max(1f, total / n2)
        val perRow = when (type) { EnemyType.BRUTE -> 3; EnemyType.KNIGHT -> 1; else -> 8 }
        val rowGap = when (type) { EnemyType.BRUTE -> 0.6f; EnemyType.RUNNER -> 0.3f; else -> 0.38f }
        for (k in 0 until n2) {
            val row = k / perRow
            val x = when (side) {
                0 -> -0.9f + rnd.nextFloat() * 1.8f
                else -> if (type == EnemyType.KNIGHT) side * 0.5f else side * (0.12f + rnd.nextFloat() * 0.8f)
            }
            val zz = z + row * rowGap + rnd.nextFloat() * 0.3f
            enemies.add(Enemy(type, x, zz, hp, rnd.nextFloat() * 0.5f - 0.25f, rnd.nextFloat() * 6.28f))
        }
    }

    private fun skillReward(): Reward {
        val missing = HeroType.values().filter { !hasHero(it) }
        val upgradable = heroes.filter { it.level < 5 }
        val recruitChance = if (heroes.size <= 1) 0.8f else 0.45f
        return when {
            missing.isNotEmpty() && (upgradable.isEmpty() || rnd.nextFloat() < recruitChance) ->
                Reward(RewardKind.RECRUIT, missing[rnd.nextInt(missing.size)])
            upgradable.isNotEmpty() -> Reward(RewardKind.SKILL, upgradable[rnd.nextInt(upgradable.size)].type)
            else -> Reward(RewardKind.DAMAGE)
        }
    }

    private fun soldiersReward() = Reward(RewardKind.SOLDIERS, amount = max(5, (count * 0.35f + 4f).roundToInt()))

    private fun barrelReward(): Reward {
        val r = rnd.nextFloat()
        return when {
            r < 0.55f -> soldiersReward()
            r < 0.77f -> Reward(RewardKind.DAMAGE)
            else -> Reward(RewardKind.RATE)
        }
    }

    private fun chestReward(): Reward {
        val r = rnd.nextFloat()
        val hurt = heroes.any { !it.alive || it.hp < it.maxHp * 0.5f }
        return when {
            hurt && r < 0.15f -> Reward(RewardKind.HEAL)
            r < 0.62f -> skillReward()
            r < 0.80f -> Reward(RewardKind.DRAGON)
            else -> Reward(RewardKind.DAMAGE)
        }
    }

    private fun addBarrel(side: Int, z: Float, chest: Boolean, reward: Reward = if (chest) chestReward() else barrelReward()) {
        val mul = when {
            reward.kind == RewardKind.DRAGON -> 0.55f
            chest -> 0.5f
            reward.kind == RewardKind.SOLDIERS -> 0.4f
            else -> 0.45f
        }
        val hp = max(10f, (approach(0f) * curB * mul).roundToInt().toFloat())
        barrels.add(Barrel(side, z, hp, reward, chest))
    }

    private fun gateGain(): Float = (count * 0.4f + 4f) / (dps() * 3f)

    private fun badValue(i: Int) = -min((count * 0.3f + 3f + i * 0.5f).roundToInt(), max(1, count - 2))

    private fun spawn(e: Enc) {
        val i = e.idx
        refreshBudget(i)
        val p = pressure(i)
        val s = e.side
        val z = e.z
        val n = e.idx - offset
        when (e.type) {
            E_HORDE -> { spawnHorde(EnemyType.SKELETON, s, z, p); addBarrel(-s, z, false) }
            E_GATES -> {
                if (n == 0) {
                    gates.add(Gate(s, z, 3f, gateGain(), GateKind.SOLDIERS))
                    gates.add(Gate(-s, z, 8f, gateGain(), GateKind.SOLDIERS))
                } else if (i >= 3 && rnd.nextFloat() < 0.25f) {
                    gates.add(Gate(s, z, 2f, 0f, GateKind.MULT))
                    gates.add(Gate(-s, z, badValue(i).toFloat(), gateGain(), GateKind.SOLDIERS))
                } else {
                    gates.add(Gate(s, z, badValue(i).toFloat(), gateGain(), GateKind.SOLDIERS))
                    val good = if (rnd.nextFloat() < 0.6f) (count * 0.15f + 2f).roundToInt()
                    else -min((count * 0.12f + 1f).roundToInt(), max(1, count / 3))
                    gates.add(Gate(-s, z, good.toFloat(), gateGain(), GateKind.SOLDIERS))
                }
            }
            E_DOUBLE -> { spawnHorde(EnemyType.SKELETON, -1, z, p * 0.6f); spawnHorde(EnemyType.SKELETON, 1, z + 1.2f, p * 0.6f) }
            E_BARREL_GATE -> {
                addBarrel(s, z, false)
                gates.add(Gate(-s, z + 1.5f, badValue(i).toFloat(), gateGain(), GateKind.SOLDIERS))
            }
            E_RUNNERS -> { spawnHorde(EnemyType.RUNNER, s, z + 4f, p * 0.75f); addBarrel(-s, z, true) }
            E_BRUTES -> { spawnHorde(EnemyType.BRUTE, s, z, p * 0.9f); addBarrel(-s, z, true) }
            E_BARRAGE -> {
                addBarrel(s, z, false)
                spawnHorde(EnemyType.SKELETON, s, z + 4f, p * 0.35f)
                var l = if (rnd.nextBoolean()) -1 else 1
                for (k in 0 until 4) {
                    hazards.add(Hazard(l, 3.0f + k * 1.7f, 1.25f, 0.15f, 0.16f, 0))
                    if (rnd.nextFloat() < 0.65f) l = -l
                }
                announceText("Catapultes ! Esquive les rochers")
            }
            E_TREASURE -> {
                val a = chestReward()
                var b = chestReward()
                var guard = 0
                while (b.kind == a.kind && b.hero == a.hero && guard++ < 5) b = chestReward()
                addBarrel(-1, z, true, a)
                addBarrel(1, z, true, b)
                spawnHorde(EnemyType.SKELETON, -1, z + 5f, p * 0.45f)
                spawnHorde(EnemyType.SKELETON, 1, z + 5.6f, p * 0.45f)
            }
            E_SKILLGATES -> {
                val rw = if (n == 2 && heroes.size < 5) {
                    val missing = HeroType.values().filter { !hasHero(it) }
                    Reward(RewardKind.RECRUIT, missing[rnd.nextInt(missing.size)])
                } else skillReward()
                gates.add(Gate(s, z, 0f, 0f, GateKind.SKILL, rw))
                gates.add(Gate(-s, z, (count * 0.25f + 4f).roundToInt().toFloat(), gateGain(), GateKind.SOLDIERS))
            }
            E_KNIGHT -> {
                spawnHorde(EnemyType.KNIGHT, s, z + 2f, p * 0.6f)
                spawnHorde(EnemyType.SKELETON, s, z, p * 0.3f)
                addBarrel(-s, z, true)
                announceText("Un chevalier noir approche")
            }
            E_AMBUSH -> {
                spawnHorde(EnemyType.SKELETON, -1, z, p * 0.45f)
                spawnHorde(EnemyType.SKELETON, 1, z + 0.6f, p * 0.45f)
                spawnHorde(EnemyType.RUNNER, if (rnd.nextBoolean()) -1 else 1, z + 5f, p * 0.35f)
                announceText("Embuscade !")
            }
            E_BOSS -> {
                val t = if (e.mini) 11f else 19f
                val pb = min(0.62f + 0.006f * i, 0.8f) * pressureMul
                val hp = t * max(curB, effDps() * 0.9f) * pb * (if (e.final) 1.15f else 1f)
                boss = Boss(e.bossType!!, z, hp.roundToInt().toFloat(), e.mini, e.final)
                announceText(e.bossType.title + (if (e.mini) " (gardien)" else ""))
            }
        }
    }

    private fun announceText(t: String) {
        announce = t
        announceTime = 2.6f
    }

    // ================================================================== dégâts subis

    private fun loseSoldiers(n: Int, x: Float, rel: Float) {
        if (n <= 0) return
        val k = min(count, n)
        count -= k
        events.add(Ev(Ev.SOLDIER_LOST, x, rel, k))
    }

    private fun damageHero(d: Float) {
        val alive = heroes.filter { it.alive }
        if (alive.isEmpty()) { state = State.LOST; return }
        val h = alive[rnd.nextInt(alive.size)]
        h.hp -= d
        h.hurt = 0.4f
        val idx = heroes.indexOf(h)
        events.add(Ev(Ev.HERO_HURT, heroX(idx), heroZ(idx), idx))
        if (h.hp <= 0f) {
            h.alive = false
            h.hp = 0f
            events.add(Ev(Ev.HERO_DOWN, heroX(idx), heroZ(idx), idx))
            if (heroes.none { it.alive }) state = State.LOST
        }
    }

    private fun contact(e: Enemy, rel: Float) {
        if (count > 0) {
            loseSoldiers(max(e.type.killMin, ceil(count * e.type.killFrac).toInt()), e.x, rel)
        } else {
            damageHero(e.type.heroDmg)
        }
    }

    private fun hazardHit(frac: Float, heroDmg: Float) {
        if (count > 0) loseSoldiers(max(1, ceil(count * frac).toInt()), playerX, 0f)
        else damageHero(heroDmg)
    }

    // ================================================================== boucle

    fun update(dt: Float) {
        if (state != State.RUNNING) return
        time += dt
        if (announceTime > 0f) announceTime -= dt
        playerX += (targetX - playerX) * min(1f, dt * 14f)
        for (h in heroes) { h.cast += dt; if (h.hurt > 0f) h.hurt -= dt }

        val b = boss
        val bossNear = b != null && b.alive && b.z - traveled < 10.5f
        if (!bossNear) traveled += SCROLL * dt
        if (dragonTime > 0f) dragonTime -= dt

        // nouvelles rencontres
        planAhead()
        while (encs.isNotEmpty() && encs.first().z - traveled < VIEW + 1f) {
            spawn(encs.removeFirst())
            planAhead()
        }

        updateEnemies(dt)
        if (state != State.RUNNING) return

        for (br in barrels) {
            if (br.flash > 0f) br.flash -= dt
            if (br.alive && br.z - traveled < -1.5f) br.alive = false
        }
        for (g in gates) {
            if (g.flash > 0f) g.flash -= dt
            if (!g.used && g.z - traveled <= 0f) {
                g.used = true
                if (g.lane == lane) passGate(g)
            }
        }

        updateHazards(dt)
        if (state != State.RUNNING) return
        updateBoss(dt)
        if (state != State.RUNNING) return

        castSkills(dt)
        fire(dt)
        moveProjectiles(dt)
        updateStrikes(dt)

        if (enemies.size > 300) enemies.removeAll { !it.alive }
        barrels.removeAll { !it.alive && it.z - traveled < -2f }
        gates.removeAll { it.used && it.z - traveled < -2f }
        projs.removeAll { !it.alive }
        hazards.removeAll { it.done }

        val bb = boss
        if (bb != null && !bb.alive) {
            boss = null
            bossKills++
            bossFights.add(bb.type to bossFight)
            bossFight = 0f
            if (bb.final) {
                state = State.WON
            } else {
                cards = makeCards()
                state = State.CHOOSING
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            if (e.flash > 0f) e.flash -= dt
            if (e.slow > 0f) e.slow -= dt
            val sp = e.type.speed * (if (e.slow > 0f) 0.3f else 1f)
            val rel0 = e.z - traveled
            if (rel0 < VIEW + 2f) e.z -= sp * dt
            val rel = e.z - traveled
            if (rel < DIVIDER_END) {
                val tx = playerX + e.spread
                e.x += (tx - e.x) * min(1f, dt * 2.2f)
            }
            if (rel <= CONTACT) {
                e.alive = false
                events.add(Ev(Ev.ENEMY_DIE, e.x, rel, e.type.ordinal))
                contact(e, rel)
                if (state != State.RUNNING) return
            }
        }
    }

    private fun applyReward(r: Reward, x: Float, rel: Float) {
        when (r.kind) {
            RewardKind.SOLDIERS -> count = min(MAX_COUNT, count + r.amount)
            RewardKind.DAMAGE -> dmgMul *= 1.3f
            RewardKind.RATE -> rateMul *= 1.2f
            RewardKind.DRAGON -> dragonTime = max(dragonTime, 0f) + 8f
            RewardKind.SKILL, RewardKind.RECRUIT -> {
                val t = r.hero!!
                val h = hero(t)
                if (h == null) {
                    if (heroes.size < 5) heroes.add(Hero(t).also { it.setLevel(1) })
                } else if (h.level < 5) {
                    h.setLevel(h.level + 1)
                } else dmgMul *= 1.3f
            }
            RewardKind.HEAL -> {
                for (h in heroes) { h.alive = true; h.hp = h.maxHp.toFloat() }
                count = min(MAX_COUNT, count + max(5, count / 5))
            }
        }
        events.add(Ev(Ev.REWARD, x, rel, 0, r))
    }

    private fun passGate(g: Gate) {
        when (g.kind) {
            GateKind.SOLDIERS -> {
                val v = g.value.roundToInt()
                count = (count + v).coerceIn(0, MAX_COUNT)
                events.add(Ev(Ev.GATE, g.x, 0f, v))
            }
            GateKind.MULT -> {
                count = min(MAX_COUNT, count * g.value.roundToInt())
                events.add(Ev(Ev.GATE, g.x, 0f, count, f = g.value))
            }
            GateKind.SKILL -> applyReward(g.reward!!, g.x, 0.3f)
        }
    }

    private fun updateHazards(dt: Float) {
        for (hz in hazards) {
            hz.delay -= dt
            if (hz.delay <= 0f) {
                if (!hz.impacted) {
                    hz.impacted = true
                    events.add(Ev(Ev.IMPACT, hz.lane * 0.5f, 0.2f, hz.kind))
                }
                if (!hz.hit && lane == hz.lane && hz.delay > -hz.window) {
                    hz.hit = true
                    hazardHit(hz.frac, 2f)
                    if (state != State.RUNNING) return
                }
                if (hz.delay <= -hz.window) hz.done = true
            }
        }
    }

    private fun updateBoss(dt: Float) {
        val b = boss ?: return
        if (!b.alive) return
        if (b.flash > 0f) b.flash -= dt
        if (b.slow > 0f) b.slow -= dt
        b.anim += dt
        val sf = if (b.slow > 0f) 0.4f else 1f
        val rel = b.z - traveled
        if (rel > 11.5f) return
        bossFight += dt
        if (!b.enraged && bossFight > 30f) {
            b.enraged = true
            announceText(b.type.title + " enrage !")
        }
        val minRel = when {
            b.enraged -> 1.3f
            b.type == BossType.NECRO -> 7f
            b.type == BossType.DRAGON -> 5f
            else -> 1.3f
        }
        val walk = (if (b.type == BossType.GOLEM) 0.32f else 0.5f) * (if (b.enraged) 1.8f else 1f)
        if (rel < 11f && rel > minRel) b.z -= walk * sf * dt

        b.abilityCd -= dt * sf * (if (b.enraged) 1.8f else 1f)
        if (b.abilityCd <= 0f && rel < 11f) {
            val m = if (b.mini) 1.3f else 1f
            b.anim = 0f
            events.add(Ev(Ev.BOSS_CAST, 0f, rel, b.type.ordinal))
            when (b.type) {
                BossType.DEMON -> { hazards.add(Hazard(lane, 1.3f, 1.3f, 0.15f, 0.22f, 2)); b.abilityCd = 4.0f * m }
                BossType.GOLEM -> {
                    hazards.add(Hazard(lane, 1.2f, 1.2f, 0.15f, 0.2f, 0))
                    if (!b.mini && rnd.nextFloat() < 0.4f) hazards.add(Hazard(-lane, 2.4f, 1.2f, 0.15f, 0.2f, 0))
                    b.abilityCd = 3.4f * m
                }
                BossType.NECRO -> {
                    refreshBudget(curIdx)
                    val side = if (rnd.nextBoolean()) -1 else 1
                    spawnHorde(EnemyType.SKELETON, side, traveled + 15f, pressure(curIdx) * 0.38f)
                    events.add(Ev(Ev.SUMMON, side * 0.5f, 15f))
                    b.abilityCd = 5.2f * m
                }
                BossType.DRAGON -> { hazards.add(Hazard(lane, 1.4f, 1.4f, 0.8f, 0.28f, 1)); b.abilityCd = 4.4f * m }
            }
        }
        if (rel <= 1.4f) {
            b.meleeCd -= dt
            if (b.meleeCd <= 0f) {
                b.meleeCd = 0.5f
                val f = if (b.enraged) 0.08f else 0.04f
                if (count > 0) loseSoldiers(max(2, ceil(count * f).toInt()), playerX, 0.3f) else damageHero(if (b.enraged) 2f else 1f)
            }
        }
    }

    // ================================================================== sorts

    private fun anyTarget(): Boolean {
        for (e in enemies) if (e.alive) { val r = e.z - traveled; if (r > 0f && r < VIEW) return true }
        val b = boss
        if (b != null && b.alive && b.z - traveled < VIEW) return true
        for (br in barrels) if (br.alive) { val r = br.z - traveled; if (r > 0f && r < VIEW) return true }
        return false
    }

    private fun castSkills(dt: Float) {
        val has = anyTarget()
        for ((idx, h) in heroes.withIndex()) {
            if (!h.alive) continue
            h.cd -= dt
            if (h.cd > 0f) continue
            if (h.type != HeroType.PRIEST && !has) continue
            if (castSkill(h, idx)) {
                h.cd = h.cooldown()
                h.cast = 0f
            } else h.cd = 0.3f
        }
    }

    private fun castSkill(h: Hero, idx: Int): Boolean {
        val d = dps()
        val l = h.level.toFloat()
        val hx = heroX(idx)
        when (h.type) {
            HeroType.FIRE -> {
                // vise l'ennemi le plus proche dans la voie, sinon le boss, sinon un tonneau
                var tx = lane * 0.5f
                var found = false
                var best = Float.MAX_VALUE
                for (e in enemies) if (e.alive) {
                    val r = e.z - traveled
                    if (r > 0.5f && r < VIEW && (e.x < 0f) == (lane < 0) && r < best) { best = r; tx = e.x; found = true }
                }
                if (!found) {
                    val b = boss
                    if (b != null && b.alive && b.z - traveled < VIEW) { tx = lane * 0.3f; found = true }
                }
                if (!found) for (br in barrels) if (br.alive && br.lane == lane && br.z - traveled < VIEW) found = true
                if (!found) return false
                projs.add(Proj(hx, 0.35f, d * (0.5f + 0.3f * l), 3, 0, tx, 0.3f + 0.05f * l))
            }
            HeroType.STORM -> {
                val n = 2 + 2 * h.level
                val pts = ArrayList<Float>()
                pts.add(hx); pts.add(0f)
                val dmg = d * (0.3f + 0.15f * l)
                val b = boss
                var hits = 0
                if (b != null && b.alive && b.z - traveled < VIEW) {
                    val r = b.z - traveled
                    hurtBoss(b, dmg * 2f, r, 0f, 4)
                    pts.add(0f); pts.add(r)
                    hits++
                }
                val cand = enemies.filter { it.alive && it.z - traveled in 0.3f..VIEW }.sortedBy { it.z }
                for (e in cand) {
                    if (hits >= n) break
                    val r = e.z - traveled
                    hurtEnemy(e, dmg, r, 4)
                    pts.add(e.x); pts.add(r)
                    hits++
                }
                if (hits == 0) return false
                events.add(Ev(Ev.LIGHTNING, hx, 0f, idx, pts = pts.toFloatArray()))
            }
            HeroType.FROST -> {
                val range = 7f + 1.5f * l
                var any = false
                val dmg = d * 0.3f * l
                for (e in enemies) if (e.alive) {
                    val r = e.z - traveled
                    if (r > 0f && r < range) {
                        any = true
                        e.slow = 2f + 0.5f * l
                        hurtEnemy(e, dmg, r, 5)
                    }
                }
                val b = boss
                if (b != null && b.alive && b.z - traveled < range + 4f) {
                    any = true
                    b.slow = 1.5f + 0.4f * l
                    hurtBoss(b, dmg * 2f, b.z - traveled, 0f, 5)
                }
                if (!any) return false
                events.add(Ev(Ev.NOVA, hx, 0f, idx, f = range))
            }
            HeroType.RANGER -> {
                // vise la voie la plus chargée
                var hl = 0f
                var hr = 0f
                for (e in enemies) if (e.alive) {
                    val r = e.z - traveled
                    if (r > 0.5f && r < VIEW) { if (e.x < 0f) hl += e.hp else hr += e.hp }
                }
                val cx: Float
                val cz: Float
                val b = boss
                if (hl + hr > 0f) {
                    val side = if (hl >= hr) -1 else 1
                    val near = enemies.filter { it.alive && (it.x < 0f) == (side < 0) && it.z - traveled in 0.5f..VIEW }
                        .sortedBy { it.z }.take(10)
                    cx = near.map { it.x }.average().toFloat()
                    cz = near.map { it.z }.average().toFloat()
                } else if (b != null && b.alive && b.z - traveled < VIEW) {
                    cx = 0f; cz = b.z
                } else {
                    val br = barrels.filter { it.alive && it.z - traveled in 0.5f..VIEW }.minByOrNull { it.z } ?: return false
                    cx = br.x; cz = br.z
                }
                val n = 5 + 3 * h.level
                val dmg = d * (0.25f + 0.1f * l)
                for (k in 0 until n) {
                    var sx = (cx + (rnd.nextFloat() - 0.5f) * 0.6f).coerceIn(-0.95f, 0.95f)
                    if (abs(cx) > 0.1f && (sx < 0f) != (cx < 0f)) sx = cx
                    strikes.add(Strike(sx, cz + (rnd.nextFloat() - 0.5f) * 2.2f, 0.35f + rnd.nextFloat() * 0.5f, 0.16f, dmg))
                }
            }
            HeroType.PRIEST -> {
                val add = max(3 + 2 * h.level, (count * (0.015f + 0.007f * l)).roundToInt())
                count = min(MAX_COUNT, count + add)
                for (o in heroes) if (o.alive) o.hp = min(o.maxHp.toFloat(), o.hp + 1f + l * 0.5f)
                if (h.level >= 3) {
                    val dead = heroes.firstOrNull { !it.alive }
                    if (dead != null) { dead.alive = true; dead.hp = dead.maxHp * 0.5f }
                }
                events.add(Ev(Ev.BLESS, hx, 0f, add))
            }
        }
        return true
    }

    private fun hurtEnemy(e: Enemy, dmg: Float, rel: Float, src: Int) {
        e.hp -= dmg
        e.flash = 0.12f
        if (e.hp <= 0f && e.alive) {
            e.alive = false
            kills++
            events.add(Ev(Ev.ENEMY_DIE, e.x, rel, e.type.ordinal, f = src.toFloat()))
        }
    }

    private fun hurtBarrel(br: Barrel, dmg: Float, rel: Float, x: Float, kind: Int) {
        br.hp -= dmg
        br.flash = 0.1f
        events.add(Ev(Ev.BARREL_HIT, x, rel, kind))
        if (br.hp <= 0f && br.alive) {
            br.alive = false
            events.add(Ev(Ev.BARREL_BREAK, br.x, rel, if (br.chest) 1 else 0, br.reward))
            applyReward(br.reward, br.x, rel)
        }
    }

    private fun hurtBoss(b: Boss, dmg: Float, rel: Float, x: Float, kind: Int) {
        b.hp -= dmg
        b.flash = 0.1f
        events.add(Ev(Ev.BOSS_HIT, x, rel, kind))
        if (b.hp <= 0f && b.alive) {
            b.alive = false
            events.add(Ev(Ev.BOSS_DIE, 0f, rel, b.type.ordinal))
        }
    }

    /** Dégâts de zone autour de (x, rel). */
    private fun aoe(x: Float, rel: Float, radius: Float, dmg: Float, src: Int) {
        val rz = radius * 3.2f
        for (e in enemies) if (e.alive) {
            val er = e.z - traveled
            val dx = (e.x - x) / radius
            val dz = (er - rel) / rz
            if (dx * dx + dz * dz < 1f) hurtEnemy(e, dmg, er, src)
        }
        for (br in barrels) if (br.alive) {
            val brr = br.z - traveled
            if (abs(br.x - x) < radius + 0.2f && abs(brr - rel) < rz) hurtBarrel(br, dmg, brr, br.x, src)
        }
        val b = boss
        if (b != null && b.alive) {
            val br = b.z - traveled
            if (abs(br - rel) < 1.6f) hurtBoss(b, dmg, br, x, src)
        }
    }

    // ================================================================== tirs

    private fun fire(dt: Float) {
        for ((i, h) in heroes.withIndex()) {
            if (!h.alive) continue
            heroShotCd[i] -= dt
            if (heroShotCd[i] <= 0f) {
                heroShotCd[i] = 1f / (2f * rateMul)
                projs.add(Proj(heroX(i), 0.2f, 3f * dmgMul * (1f + 0.25f * (h.level - 1)), 0, h.type.ordinal))
            }
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
            val speed = when (p.kind) { 0 -> 19f; 1 -> 24f; 2 -> 16f; else -> 14f }
            p.z += speed * dt
            if (p.kind == 3) p.x += (p.tx - p.x) * min(1f, dt * 3f)
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
                if ((e.x < 0f) != (pl < 0)) continue
                if (rel < bestZ) { bestZ = rel; hitEnemy = e }
            }
            for (br in barrels) {
                if (!br.alive || br.lane != pl) continue
                val rel = br.z - traveled
                if (rel > p.z || rel < -0.3f) continue
                if (rel < bestZ) { bestZ = rel; hitBarrel = br; hitEnemy = null }
            }
            if (p.kind != 3) for (g in gates) {
                if (g.used || g.lane != pl || g.kind != GateKind.SOLDIERS) continue
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
            if (bestZ == Float.MAX_VALUE) continue
            p.alive = false
            if (p.kind == 3) {
                events.add(Ev(Ev.EXPLODE, p.x, bestZ, 0, f = p.radius))
                aoe(p.x, bestZ, p.radius, p.dmg, 3)
                continue
            }
            when {
                hitEnemy != null -> {
                    events.add(Ev(Ev.HIT, hitEnemy.x, bestZ, p.kind, f = p.tag.toFloat()))
                    hurtEnemy(hitEnemy, p.dmg, bestZ, p.kind)
                }
                hitBarrel != null -> hurtBarrel(hitBarrel, p.dmg, bestZ, p.x, p.kind)
                hitGate != null -> {
                    hitGate.value += p.dmg * hitGate.gain
                    hitGate.flash = 0.1f
                    events.add(Ev(Ev.GATE_HIT, p.x, bestZ, p.kind))
                }
                hitBoss -> hurtBoss(b!!, p.dmg, bestZ, p.x, p.kind)
            }
        }
    }

    private fun updateStrikes(dt: Float) {
        val it = strikes.iterator()
        val fired = ArrayList<Strike>()
        while (it.hasNext()) {
            val s = it.next()
            s.delay -= dt
            if (s.delay <= 0f) { fired.add(s); it.remove() }
        }
        for (s in fired) {
            val rel = s.z - traveled
            events.add(Ev(Ev.ARROW, s.x, rel))
            aoe(s.x, rel, s.radius, s.dmg, 6)
        }
    }

    // ================================================================== cartes

    private fun makeCards(): List<Card> {
        val pool = ArrayList<Pair<Card, Float>>()
        if (heroes.size < 5) for (t in HeroType.values()) if (!hasHero(t)) pool.add(Card(CardKind.RECRUIT, t) to 1.4f)
        for (h in heroes) if (h.level < 5) pool.add(Card(CardKind.UPGRADE, h.type) to 1.3f)
        pool.add(Card(CardKind.SOLDIERS) to 1f)
        pool.add(Card(CardKind.DAMAGE) to 1f)
        pool.add(Card(CardKind.RATE) to 0.8f)
        pool.add(Card(CardKind.DRAGON) to 0.6f)
        if (heroes.any { !it.alive || it.hp < it.maxHp }) pool.add(Card(CardKind.HEAL) to 1.2f)
        val out = ArrayList<Card>()
        while (out.size < 3 && pool.isNotEmpty()) {
            var s = 0f
            for (p in pool) s += p.second
            var r = rnd.nextFloat() * s
            var pick = pool.size - 1
            for (k in pool.indices) { r -= pool[k].second; if (r <= 0f) { pick = k; break } }
            out.add(pool.removeAt(pick).first)
        }
        return out
    }

    fun cardTitle(c: Card): String = when (c.kind) {
        CardKind.RECRUIT -> c.hero!!.title
        CardKind.UPGRADE -> c.hero!!.skill
        CardKind.SOLDIERS -> "Renforts"
        CardKind.DAMAGE -> "Lames affûtées"
        CardKind.RATE -> "Tir rapide"
        CardKind.HEAL -> "Soins sacrés"
        CardKind.DRAGON -> "Pacte du dragon"
    }

    fun cardDesc(c: Card): String = when (c.kind) {
        CardKind.RECRUIT -> "Rejoint l'équipe|" + c.hero!!.skill
        CardKind.UPGRADE -> {
            val lv = hero(c.hero!!)?.level ?: 1
            "Niveau $lv → ${lv + 1}|" + c.hero.title
        }
        CardKind.SOLDIERS -> "+${max(10, count / 2)} soldats|(+50 %)"
        CardKind.DAMAGE -> "Dégâts|×1.35"
        CardKind.RATE -> "Cadence de tir|×1.25"
        CardKind.HEAL -> "Ressuscite et|soigne les héros"
        CardKind.DRAGON -> "Le dragon combat|à tes côtés 20 s"
    }

    fun chooseCard(k: Int) {
        if (state != State.CHOOSING) return
        val c = cards.getOrNull(k) ?: return
        when (c.kind) {
            CardKind.RECRUIT -> if (heroes.size < 5 && !hasHero(c.hero!!)) heroes.add(Hero(c.hero).also { it.setLevel(1) })
            CardKind.UPGRADE -> hero(c.hero!!)?.let { it.setLevel(it.level + 1) }
            CardKind.SOLDIERS -> count = min(MAX_COUNT, count + max(10, count / 2))
            CardKind.DAMAGE -> dmgMul *= 1.35f
            CardKind.RATE -> rateMul *= 1.25f
            CardKind.HEAL -> {
                for (h in heroes) { h.alive = true; h.hp = h.maxHp.toFloat() }
                count = min(MAX_COUNT, count + max(5, count / 5))
            }
            CardKind.DRAGON -> dragonTime += 20f
        }
        cards = emptyList()
        state = State.RUNNING
    }

    // ================================================================== aides pour le bot de test

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

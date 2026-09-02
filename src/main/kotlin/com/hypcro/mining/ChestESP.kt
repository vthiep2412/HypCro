package com.hypcro.mining

import com.hypcro.config.ConfigManager
import com.hypcro.util.GardenStateReader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.ARGB
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object ChestESP {

    private const val CHEST_SPAWN_MESSAGE = "You uncovered a treasure chest!"
    private const val MAX_PARTICLE_LIFETIME_MS = 250L

    val openedChests: MutableSet<BlockPos> = CopyOnWriteArraySet()
    val activeLockpickChests: MutableSet<BlockPos> = CopyOnWriteArraySet()
    private val activeParticles = ConcurrentHashMap<Vec3, Long>()

    private var cachedNormalChests: List<BlockPos> = emptyList()
    private var waitingForChest: Int = 0
    private var currentLockCount: Int = 0
    private var neededLockCount: Int = 0
    private var lastScanTimeMs: Long = 0L

    fun reset() {
        openedChests.clear()
        activeLockpickChests.clear()
        activeParticles.clear()
        cachedNormalChests = emptyList()
        waitingForChest = 0
        currentLockCount = 0
        neededLockCount = 0
    }

    fun onChatMessage(rawMessage: String) {
        val stripped = GardenStateReader.stripColor(rawMessage).trim()
        if (stripped.contains(CHEST_SPAWN_MESSAGE)) {
            waitingForChest++
        }
    }

    fun onBlockUpdate(pos: BlockPos, newState: BlockState) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        if (waitingForChest > 0 && newState.`is`(Blocks.CHEST)) {
            if (pos.distToCenterSqr(player.position()) <= 100.0) { // Within 10 blocks
                activeLockpickChests.add(pos)
                waitingForChest = (waitingForChest - 1).coerceAtLeast(0)
                currentLockCount = 0
            }
        } else if (newState.isAir) {
            if (activeLockpickChests.contains(pos)) {
                activeLockpickChests.remove(pos)
                currentLockCount = 0
            }
            openedChests.remove(pos)
        }
    }

    fun onParticle(particleType: Any?, x: Double, y: Double, z: Double) {
        if (particleType == ParticleTypes.CRIT) {
            val particlePos = Vec3(x, y, z)
            val client = Minecraft.getInstance()
            val player = client.player
            val level = client.level
            if (player != null && level != null) {
                // If particle is within 20 blocks of player, scan 1-block neighborhood for chest
                if (particlePos.closerThan(player.position(), 20.0)) {
                    activeParticles[particlePos] = System.currentTimeMillis()
                    val bx = net.minecraft.util.Mth.floor(x)
                    val by = net.minecraft.util.Mth.floor(y)
                    val bz = net.minecraft.util.Mth.floor(z)

                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            for (dz in -1..1) {
                                val checkPos = BlockPos(bx + dx, by + dy, bz + dz)
                                val state = level.getBlockState(checkPos)
                                if (state.`is`(Blocks.CHEST) || state.`is`(Blocks.TRAPPED_CHEST)) {
                                    activeLockpickChests.add(checkPos)
                                    openedChests.remove(checkPos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun onSound(soundLocation: String, pitch: Float) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        // 1. Lock picked step sound (Exp orb pickup, pitch 1.0)
        if (soundLocation == SoundEvents.EXPERIENCE_ORB_PICKUP.location().toString() && pitch >= 0.99f && pitch <= 1.01f) {
            if (activeLockpickChests.isNotEmpty()) {
                currentLockCount++
                activeParticles.clear()
            }
        }
        // 2. Lock pick fail sound (Villager No)
        else if (soundLocation == SoundEvents.VILLAGER_NO.location().toString()) {
            currentLockCount = 0
            activeParticles.clear()
        }
        // 3. Chest unlocked/opened (Chest Open)
        else if (soundLocation == SoundEvents.CHEST_OPEN.location().toString()) {
            neededLockCount = currentLockCount.coerceIn(1, 5)
            currentLockCount = 0
            activeParticles.clear()

            val hit = client.hitResult
            if (hit is BlockHitResult && activeLockpickChests.contains(hit.blockPos)) {
                activeLockpickChests.remove(hit.blockPos)
            }
        }
    }

    fun onPlayerInteractBlock(pos: BlockPos) {
        // If the player opens/clicks a normal chest, record it as opened so it stops being highlighted
        if (!activeLockpickChests.contains(pos)) {
            openedChests.add(pos)
        }
    }

    fun tick(client: Minecraft) {
        val cfg = ConfigManager.config.chestEsp
        if (!cfg.enabled && !cfg.chestEsp && !cfg.lockpickHelper) {
            cachedNormalChests = emptyList()
            activeParticles.clear()
            return
        }

        if (!GardenStateReader.isInCrystalHollows(client)) {
            cachedNormalChests = emptyList()
            activeParticles.clear()
            return
        }

        val level = client.level ?: return
        val player = client.player ?: return

        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < com.hypcro.util.EspHelper.SCAN_INTERVAL_MS) {
            return
        }
        lastScanTimeMs = now

        // Prune expired lockpick particles (> 250ms old)
        activeParticles.entries.removeIf { now - it.value > MAX_PARTICLE_LIFETIME_MS }

        if (cfg.chestEsp) {
            val playerChunkX = player.blockX shr 4
            val playerChunkZ = player.blockZ shr 4

            val results = mutableListOf<BlockPos>()
            val chunkSource = level.chunkSource

            for (cx in (playerChunkX - 3)..(playerChunkX + 2)) {
                for (cz in (playerChunkZ - 3)..(playerChunkZ + 2)) {
                    val chunk = chunkSource.getChunk(cx, cz, false) ?: continue
                    for ((pos, be) in chunk.blockEntities) {
                        if (be is ChestBlockEntity || be is TrappedChestBlockEntity) {
                            if (!openedChests.contains(pos) || activeLockpickChests.contains(pos)) {
                                results.add(pos)
                            }
                        }
                    }
                }
            }
            cachedNormalChests = results
        } else {
            cachedNormalChests = emptyList()
        }
    }

    fun renderWorld() {
        val cfg = ConfigManager.config.chestEsp
        if (!cfg.enabled && !cfg.chestEsp && !cfg.lockpickHelper) return

        val client = Minecraft.getInstance()
        if (!GardenStateReader.isInCrystalHollows(client)) return

        // 1. Render Chest ESP Outlines (Warm Gold default)
        if (cfg.chestEsp && cachedNormalChests.isNotEmpty()) {
            val (r, g, b) = parseRgb(cfg.chestColor)
            val normalStyle = GizmoStyle.strokeAndFill(
                ARGB.color(255, r, g, b),
                2.0f,
                ARGB.color(45, r, g, b)
            )

            for (pos in cachedNormalChests) {
                val box = AABB(
                    pos.x + 0.0625, pos.y.toDouble(), pos.z + 0.0625,
                    pos.x + 0.9375, pos.y + 0.875, pos.z + 0.9375
                )
                Gizmos.cuboid(box, normalStyle).setAlwaysOnTop()
            }
        }

        // 2. Render Always-Visible Red Lockpick Helper Cube (X-ray visible sweet-spot)
        if (cfg.lockpickHelper && activeParticles.isNotEmpty()) {
            val (hr, hg, hb) = parseRgb(cfg.helperColor)
            val helperStyle = GizmoStyle.strokeAndFill(
                ARGB.color(255, hr, hg, hb),
                2.0f,
                ARGB.color(200, hr, hg, hb)
            )

            val client = Minecraft.getInstance()
            val player = client.player
            val playerPos = player?.position()

            // Group particles by proximity (< 0.8 blocks)
            val clusters = mutableListOf<MutableList<Vec3>>()
            for (pPos in activeParticles.keys) {
                if (playerPos != null && !pPos.closerThan(playerPos, 20.0)) continue

                var added = false
                for (cluster in clusters) {
                    if (cluster.first().closerThan(pPos, 0.8)) {
                        cluster.add(pPos)
                        added = true
                        break
                    }
                }
                if (!added) {
                    clusters.add(mutableListOf(pPos))
                }
            }

            for (cluster in clusters) {
                var avgX = 0.0
                var avgY = 0.0
                var avgZ = 0.0
                for (p in cluster) {
                    avgX += p.x
                    avgY += p.y
                    avgZ += p.z
                }
                val spot = Vec3(avgX / cluster.size, avgY / cluster.size, avgZ / cluster.size)
                val halfSize = 0.06
                val targetBox = AABB(
                    spot.x - halfSize, spot.y - halfSize, spot.z - halfSize,
                    spot.x + halfSize, spot.y + halfSize, spot.z + halfSize
                )
                Gizmos.cuboid(targetBox, helperStyle).setAlwaysOnTop()
            }
        }
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#").trim()
        return try {
            val num = clean.toInt(16)
            val r = (num shr 16) and 0xFF
            val g = (num shr 8) and 0xFF
            val b = num and 0xFF
            Triple(r, g, b)
        } catch (_: Exception) {
            Triple(255, 170, 0)
        }
    }
}

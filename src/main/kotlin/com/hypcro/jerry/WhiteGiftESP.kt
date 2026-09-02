package com.hypcro.jerry

import com.hypcro.config.ConfigManager
import com.hypcro.util.GardenStateReader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.nio.charset.StandardCharsets
import java.util.Base64

object WhiteGiftESP {

    // =========================================================================
    // WHITE GIFT TEXTURE HASH / SKIN SIGNATURE
    // (Obtained from Jerry's Workshop ArmorStand inspect)
    // =========================================================================
    const val WHITE_GIFT_TEXTURE_HASH: String = "10f5398510b1a05afc5b201ead8bfc583e57d7202f5193b0b761fcbd0ae2"

    private val discoveredGifts = mutableMapOf<String, Vec3>()
    private var lastScanTimeMs: Long = 0L

    private fun ensureCacheLoaded() {
        val cfg = ConfigManager.config.jerryGifts
        if (discoveredGifts.isEmpty() && cfg.discoveredCoords.isNotEmpty()) {
            for ((key, coords) in cfg.discoveredCoords) {
                if (coords.size >= 3) {
                    discoveredGifts[key] = Vec3(coords[0], coords[1], coords[2])
                }
            }
        }
    }

    fun resetCollected() {
        ConfigManager.config.jerryGifts.collectedCoords.clear()
        ConfigManager.save()
    }

    private fun markGiftCollected(key: String, center: Vec3) {
        val cfg = ConfigManager.config.jerryGifts
        if (!discoveredGifts.containsKey(key)) {
            discoveredGifts[key] = center
            cfg.discoveredCoords[key] = listOf(center.x, center.y, center.z)
        }
        if (!cfg.collectedCoords.contains(key)) {
            cfg.collectedCoords.add(key)
            ConfigManager.save()
        }
    }

    fun recordCollectionAt(targetPos: Vec3) {
        val client = Minecraft.getInstance()
        if (!GardenStateReader.isInJerryWorkshop(client)) return

        val matching = discoveredGifts.entries.find { it.value.distanceTo(targetPos) < 0.5 } ?: return
        markGiftCollected(matching.key, matching.value)
    }

    fun onPlayerInteractBlock(pos: BlockPos) {
        val client = Minecraft.getInstance()
        if (!GardenStateReader.isInJerryWorkshop(client)) return
        val level = client.level ?: return

        val be = level.getBlockEntity(pos)
        if (be is SkullBlockEntity) {
            val texture = extractSkullTexture(be)
            if (matchesWhiteGift(texture)) {
                val key = "${pos.x},${pos.y},${pos.z}"
                val matchingKey = discoveredGifts.entries.find { it.key == key || it.value.distanceTo(Vec3.atCenterOf(pos)) < 0.5 }?.key ?: key
                markGiftCollected(matchingKey, Vec3.atCenterOf(pos))
            }
        }
    }

    fun onPlayerInteractEntity(entity: Entity) {
        val client = Minecraft.getInstance()
        if (!GardenStateReader.isInJerryWorkshop(client)) return

        if (entity is ArmorStand && !entity.isRemoved) {
            val headItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
            if (!headItem.isEmpty) {
                val customData = headItem.get(net.minecraft.core.component.DataComponents.PROFILE)
                val texture = extractProfileTexture(customData?.partialProfile()?.properties)
                if (matchesWhiteGift(texture)) {
                    val pos = entity.blockPosition()
                    val key = "${pos.x},${pos.y},${pos.z}"
                    val matchingKey = discoveredGifts.entries.find { it.key == key || it.value.distanceTo(entity.eyePosition) < 0.5 }?.key ?: key
                    markGiftCollected(matchingKey, entity.eyePosition)
                }
            }
        }
    }

    fun tick(client: Minecraft) {
        val cfg = ConfigManager.config.jerryGifts
        if (!cfg.enabled) return

        if (!GardenStateReader.isInJerryWorkshop(client)) {
            return
        }

        ensureCacheLoaded()

        val level = client.level ?: return
        val player = client.player ?: return

        // 1. Year Change Auto-Reset
        val currentYear = GardenStateReader.readSkyBlockYear(client)
        if (currentYear > 0 && currentYear != cfg.lastSavedYear) {
            if (cfg.lastSavedYear != 0) {
                cfg.discoveredCoords.clear()
                cfg.collectedCoords.clear()
                discoveredGifts.clear()
            }
            cfg.lastSavedYear = currentYear
            ConfigManager.save()
        }

        // 3. Dynamic Chunk Block Entity & ArmorStand Scanner
        // Automatically stops scanning once all 20 distinct gift coordinates are discovered for this year!
        if (discoveredGifts.size >= 20) return

        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < com.hypcro.util.EspHelper.SCAN_INTERVAL_MS) return
        lastScanTimeMs = now

        val playerChunkX = player.blockX shr 4
        val playerChunkZ = player.blockZ shr 4
        val chunkSource = level.chunkSource
        var newDiscovered = false

        for (cx in (playerChunkX - 3)..(playerChunkX + 2)) {
            for (cz in (playerChunkZ - 3)..(playerChunkZ + 2)) {
                val chunk = chunkSource.getChunk(cx, cz, false) ?: continue

                for ((pos, be) in chunk.blockEntities) {
                    if (be is SkullBlockEntity) {
                        val texture = extractSkullTexture(be)
                        if (matchesWhiteGift(texture)) {
                            val key = "${pos.x},${pos.y},${pos.z}"
                            val center = Vec3.atCenterOf(pos)
                            if (!discoveredGifts.containsKey(key)) {
                                discoveredGifts[key] = center
                                cfg.discoveredCoords[key] = listOf(center.x, center.y, center.z)
                                newDiscovered = true
                            }
                        }
                    }
                }
            }
        }

        // Check ArmorStands in render distance
        for (entity in level.entitiesForRendering()) {
            if (entity is ArmorStand && !entity.isRemoved) {
                val headItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                if (!headItem.isEmpty) {
                    val customData = headItem.get(net.minecraft.core.component.DataComponents.PROFILE)
                    val texture = extractProfileTexture(customData?.partialProfile()?.properties)
                    if (matchesWhiteGift(texture)) {
                        val pos = entity.blockPosition()
                        val key = "${pos.x},${pos.y},${pos.z}"
                        val center = entity.eyePosition
                        if (!discoveredGifts.containsKey(key)) {
                            discoveredGifts[key] = center
                            cfg.discoveredCoords[key] = listOf(center.x, center.y, center.z)
                            newDiscovered = true
                        }
                    }
                }
            }
        }

        if (newDiscovered) {
            ConfigManager.save()
        }
    }

    fun renderWorld() {
        val cfg = ConfigManager.config.jerryGifts
        if (!cfg.enabled) return

        val client = Minecraft.getInstance()
        if (!GardenStateReader.isInJerryWorkshop(client)) return

        val player = client.player ?: return
        val partialTicks = com.hypcro.util.EspHelper.getPartialTicks()
        val playerPos = com.hypcro.util.EspHelper.getInterpolatedPosition(player, partialTicks)

        val (r, g, b) = parseRgb(cfg.color)
        val waypointStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, r, g, b),
            2.5f,
            ARGB.color(80, r, g, b)
        )

        val collected = cfg.collectedCoords

        for ((key, center) in discoveredGifts) {
            if (collected.contains(key)) continue

            val dist = playerPos.distanceTo(center)

            // 1. In-World Waypoint Box (0.6x0.6x0.6 around head center)
            val half = 0.3
            val box = AABB(
                center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half
            )
            Gizmos.cuboid(box, waypointStyle).setAlwaysOnTop()

            // 2. Un-Capped Distance Scaling Text Label (No Numbers)
            // Scaling proportionally with distance cancels perspective shrink completely
            val textScale = (0.045f * dist.toFloat()).coerceAtLeast(0.28f)
            val textStyle = TextGizmo.Style.whiteAndCentered().withScale(textScale)

            val textPos = Vec3(center.x, center.y + 0.55, center.z)
            val label = "§f§lWhite Gift §7[${dist.toInt()}m]"
            Gizmos.billboardText(label, textPos, textStyle).setAlwaysOnTop()
        }
    }

    private fun matchesWhiteGift(texture: String?): Boolean {
        if (texture == null || texture.isEmpty()) return false
        if (WHITE_GIFT_TEXTURE_HASH.isEmpty()) return false
        return texture.contains(WHITE_GIFT_TEXTURE_HASH, ignoreCase = true)
    }

    fun extractSkullTexture(skull: SkullBlockEntity): String? {
        val profile = skull.ownerProfile ?: return null
        return extractProfileTexture(profile.partialProfile().properties)
    }

    fun extractProfileTexture(properties: com.mojang.authlib.properties.PropertyMap?): String? {
        if (properties == null) return null
        val textureProps = properties.get("textures")
        for (prop in textureProps) {
            val value = prop.value()
            try {
                val decoded = String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
                return decoded
            } catch (_: Exception) {
                return value
            }
        }
        return null
    }

    fun inspectLookTarget(client: Minecraft): String {
        val hit = client.hitResult
        val level = client.level ?: return "§cNo active world."

        if (hit is BlockHitResult) {
            val pos = hit.blockPos
            val blockState = level.getBlockState(pos)
            val be = level.getBlockEntity(pos)

            if (be is SkullBlockEntity) {
                val texture = extractSkullTexture(be)
                val profile = be.ownerProfile?.partialProfile()
                val hash = extractHashFromDecoded(texture)
                return "§a§l[SKULL INSPECT]§r Block: §e$pos§r\n" +
                        "§bBlockState: §f${blockState.block.name.string}\n" +
                        "§bProfile Name: §f${profile?.name ?: "null"}\n" +
                        "§bProfile UUID: §f${profile?.id ?: "null"}\n" +
                        "§6Texture Hash: §e§n$hash§r\n" +
                        "§7Raw JSON/Base64: §f$texture"
            }

            return "§eTarget Block: §f${blockState.block.name.string} at §b$pos (Not a SkullBlockEntity)"
        }

        if (hit is EntityHitResult) {
            val entity = hit.entity
            if (entity is ArmorStand) {
                val headItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                val customData = headItem.get(net.minecraft.core.component.DataComponents.PROFILE)
                val texture = extractProfileTexture(customData?.partialProfile()?.properties)
                val hash = extractHashFromDecoded(texture)
                return "§a§l[ARMORSTAND INSPECT]§r Entity: §e${entity.type.description.string}§r\n" +
                        "§bHead Item: §f${headItem.hoverName.string}\n" +
                        "§6Texture Hash: §e§n$hash§r\n" +
                        "§7Raw JSON/Base64: §f$texture"
            }
            return "§eTarget Entity: §f${entity.type.description.string} (Not an ArmorStand)"
        }

        return "§cNo block or entity in crosshair."
    }

    private fun extractHashFromDecoded(decoded: String?): String {
        if (decoded == null) return "none"
        val regex = Regex("textures\\.minecraft\\.net/texture/([a-zA-Z0-9]+)")
        val match = regex.find(decoded)
        return match?.groupValues?.get(1) ?: decoded
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
            Triple(255, 255, 255)
        }
    }
}

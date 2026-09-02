package com.hypcro.player

import com.hypcro.config.ConfigManager
import com.hypcro.party.PartyApi
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.util.ARGB
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

data class TrackedPlayer(
    val player: Player,
    val isParty: Boolean,
    val colorHex: String
)

object PlayerESP {

    fun tick(client: Minecraft) {
        // No-op: rendering runs live in renderWorld() for full monitor refresh rate
    }

    fun renderWorld() {
        val cfg = ConfigManager.config.playerEsp
        if (!cfg.enabled || (!cfg.partyEsp && !cfg.otherPlayerEsp)) return

        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val localPlayer = client.player ?: return
        val partialTicks = com.hypcro.util.EspHelper.getPartialTicks()
        val localPos = com.hypcro.util.EspHelper.getInterpolatedPosition(localPlayer, partialTicks)

        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || entity == localPlayer) continue
            if (entity.isRemoved) continue
            if (entity.uuid.version() != 4) continue

            val name = entity.gameProfile.name
            val isParty = PartyApi.isPartyMember(name)

            if (isParty && !cfg.partyEsp) continue
            if (!isParty && !cfg.otherPlayerEsp) continue

            val colorHex = if (isParty) cfg.partyColor else cfg.otherPlayerColor
            val (r, g, b) = parseRgb(colorHex)
            val style = GizmoStyle.strokeAndFill(
                ARGB.color(255, r, g, b),
                2.0f,
                ARGB.color(60, r, g, b)
            )

            // Interpolate position with partial ticks for 100% framerate sync
            val box = com.hypcro.util.EspHelper.getInterpolatedBoundingBox(entity, partialTicks)

            // Render bounding box through walls with live frame position
            Gizmos.cuboid(box, style).setAlwaysOnTop()

            // Optional billboard nametag
            if (cfg.renderNametags) {
                val headPos = com.hypcro.util.EspHelper.getInterpolatedEyePosition(entity, partialTicks).add(0.0, 0.45, 0.0)
                val dist = localPos.distanceTo(headPos)
                val clampedDist = dist.coerceIn(6.0, 60.0)
                val textScale = (0.28f * (clampedDist / 6.0).toFloat()).coerceIn(0.28f, 2.5f)
                val textStyle = TextGizmo.Style.whiteAndCentered().withScale(textScale)

                val tagText = if (isParty) {
                    "§a§l[PARTY] §f" + entity.gameProfile.name
                } else {
                    if (cfg.showDistance) {
                        "§b" + entity.gameProfile.name + " §7(" + dist.toInt() + "m)"
                    } else {
                        "§b" + entity.gameProfile.name
                    }
                }
                Gizmos.billboardText(tagText, headPos, textStyle).setAlwaysOnTop()
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
            Triple(0, 255, 255)
        }
    }
}

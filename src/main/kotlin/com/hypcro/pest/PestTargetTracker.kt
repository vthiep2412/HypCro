package com.hypcro.pest

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.sqrt
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items

data class TrackedPest(
    val entity: Entity,
    val skullMarker: ArmorStand? = entity as? ArmorStand,
    private val _pos: Vec3? = null
) {
    val position: Vec3 get() = entity.position()
}

object PestTargetTracker {

    private val confirmedPestUuids = Collections.synchronizedSet(mutableSetOf<UUID>())

    fun clearSessionMemory() {
        confirmedPestUuids.clear()
    }

    val KNOWN_PEST_NAMES = listOf(
        "FLY", "CRICKET", "LOCUST", "RAT", "MOUSE", "FIELD MOUSE",
        "MOSQUITO", "EARTHWORM", "WORM", "TAPEWORM", "MITE", "MOTH",
        "SLUG", "BEETLE", "DRAGONFLY", "FIREFLY", "MANTIS", "PRAYING MANTIS"
    )

    // Skyblocker PEST_HEADS texture set — checked before nameplate strings so pests are found instantly at any render distance
    private val PEST_HEADS = setOf(
        // Beetle
        "ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTc4OTkzNCwKICAicHJvZmlsZUlkIiA6ICJlMjc5NjliODYyNWY0NDg1YjkyNmM5NTBhMDljMWMwMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLRVZJTktFTE9LRSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83MGExZTgzNmJmMTk2OGIyZWFhNDgzNzIyN2ExOTIwNGYxNzI5NWQ4NzBlZTllNzU0YmQ2YjZkNjBkZGJlZDNjIgogICAgfQogIH0KfQ==",
        // Cricket
        "ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTgxMTI2NCwKICAicHJvZmlsZUlkIiA6ICJjZjc4YzFkZjE3ZTI0Y2Q5YTIxYmU4NWQ0NDk5ZWE4ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXR0c0FybW9yU3RhbmRzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyNGM2OWY5NmNlNTU2MjIxZTE5NWM4ZWYyYmZhZDcxZWJmN2Y5NWY1YWU5MTRhNDg0YThkMGVjMjE2NzI2NzQiCiAgICB9CiAgfQp9",
        // Dragonfly
        "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxODQzNywKICAicHJvZmlsZUlkIiA6ICIwNjY5Y2E1MGYyZWU0NTQxODhlYWQ3YTM3NTkzNDRlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDcjR6eWNsb3duVFYiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0YWZmNGMwYjJkY2UzYTY3MjM0OWNjMGVlOWU2ZjNhOWRlZWJlNGIzNTU2ZTg0NjExZWNhMjUwYTc4MjFiZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
        // Earthworm
        "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ1OTc0NywKICAicHJvZmlsZUlkIiA6ICIyNTBlNzc5MjZkNDM0ZDIyYWM2MTQ4N2EyY2M3YzAwNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMdW5hMTIxMDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQwM2JhNDAyN2EzMzNkOGQyZmQzMmFiNTlkMWNmZGJhYTdkOTA4ZDgwZDIzODFkYjJhNjljYmU2NTQ1MGFkOCIKICAgIH0KICB9Cn0=",
        // Earthworm tail
        "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ3ODAzMCwKICAicHJvZmlsZUlkIiA6ICI0NmY3N2NjNmQ2MjU0NjEzYjc2NmYyZDRmMDM2MzZhNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNzV29sZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZDQwYWE1MDkwNTIzNWI2MjhlNzM3OWViMzFmYTQ1Y2Q0MWI1MDNmMDk3MjFkYjNjNDM3ZmNlZTM5MjA3ZGZjIgogICAgfQogIH0KfQ==",
        // Field Mouse
        "ewogICJ0aW1lc3RhbXAiIDogMTcyNzkwNDc5NzQ1OSwKICAicHJvZmlsZUlkIiA6ICI0MmIwOTMyZDUwMWI0MWQ1YTM4YjEwOTcxYTYwYmYxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJBaXJib2x0MDc4IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2YzNzllMDkyNTI4MTczMTRiZDBiNjk0ZjdkNTNiNDhhZjJjN2ZhODQ5OTEwOTgwMmE0MWJiMjk0ZDJmOTNlM2UiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
        // Firefly
        "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMjEzNiwKICAicHJvZmlsZUlkIiA6ICIzNDY4Y2VjMWFlOTY0YWRmYWQyNjEzMGEwZGQ0NjRkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJzdXJlZWxta18iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlNzllOTBhZGYzNDcxOGYzMTNlYzI0ZDZjNjEzNWI2OWIzNzg4YzYxODQ5ODQ0NmNjYzgzY2E2NDBjMGIxNCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
        // Firefly flash
        "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMzg4OSwKICAicHJvZmlsZUlkIiA6ICIyY2Y2MzExZjUyMTM0NTE2YTEyNTY3NWUwMzk3NmU2MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJmaWdodHN0b2NrIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNlNTI3ODJkN2YyYWFlZThhZjViYTI5MjhmZWM3ODg1ZTk0ODc5MzM0YzIyOTZiYzllN2UyZGJjNTQxOGU1OGYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
        // Fly
        "ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTA2MzI4MSwKICAicHJvZmlsZUlkIiA6ICJjN2FmMWNkNjNiNTE0Y2YzOGY4NWQ2ZDUxNzhjYThlNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtb25zdGVyZ2FtZXIzMTUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ5MGU3Nzc4MjZhNTI0NjEzNjhlMjZkMWIyZTE5YmZhMWJhNTgyZDYwMjQ4M2U1NDVmNDEyNGQwZjczMTg0MiIKICAgIH0KICB9Cn0=",
        // Locust
        "ewogICJ0aW1lc3RhbXAiIDogMTY5NzU1NzA3NzAzNywKICAicHJvZmlsZUlkIiA6ICI0YjJlMGM1ODliZjU0ZTk1OWM1ZmJlMzg5MjQ1MzQzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJfTmVvdHJvbl8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGIyNGE0ODJhMzJkYjFlYTc4ZmI5ODA2MGIwYzJmYTRhMzczY2JkMThhNjhlZGRkZWI3NDE5NDU1YTU5Y2RhOSIKICAgIH0KICB9Cn0=",
        // Lunar Moth
        "ewogICJ0aW1lc3RhbXAiIDogMTc3MjE1OTE4MzkzMSwKICAicHJvZmlsZUlkIiA6ICIxNzRjZmRiNGEzY2I0M2I1YmZjZGU0MjRjM2JiMmM2ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYXJhZWwxOCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iZWU0ZmNjNWFhYzI3YmRiYjBiZTIxMGRmMDhiMDViY2E1YWViYzg5YmYyODIxZjYwOGE1NWZkMmNmMDQzNGJlIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
        // Mite
        "ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQxOTcyNSwKICAicHJvZmlsZUlkIiA6ICJkYjYzNWE3MWI4N2U0MzQ5YThhYTgwOTMwOWFhODA3NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJFbmdlbHMxNzQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmU2YmFmNjQzMWE5ZGFhMmNhNjA0ZDVhM2MyNmU5YTc2MWQ1OTUyZjA4MTcxNzRhNGZlMGI3NjQ2MTZlMjFmZiIKICAgIH0KICB9Cn0=",
        // Mosquito
        "ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTAyOTQ2MSwKICAicHJvZmlsZUlkIiA6ICI3NTE0NDQ4MTkxZTY0NTQ2OGM5NzM5YTZlMzk1N2JlYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGFua3NNb2phbmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTJhOWZlMDViYzY2M2VmY2QxMmU1NmEzY2NjNWVjMDM1YmY1NzdiNzg3MDg1NDhiNmY0ZmZjZjFkMzBlY2NmZSIKICAgIH0KICB9Cn0=",
        // Moth
        "ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQwNTk1NCwKICAicHJvZmlsZUlkIiA6ICJiMTUyZDlhZTE1MTM0OWNmOWM2NmI0Y2RjMTA5NTZjOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNxdW90aCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82NTQ4NWM0YjM0ZTViNTQ3MGJlOTRkZTEwMGU2MWY3ODE2ZjgxYmM1YTExZGZkZjBlY2NmODkwMTcyZGE1ZDBhIgogICAgfQogIH0KfQ==",
        // Praying Mantis
        "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxOTYxMiwKICAicHJvZmlsZUlkIiA6ICI0OWIzODUyNDdhMWY0NTM3YjBmN2MwZTFmMTVjMTc2NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJiY2QyMDMzYzYzZWM0YmY4IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFlMDRiYjYzNjdjYWE0ZTg4ZjVmZDBlZTgwZjA3NDVkMTM3YTYwNjAyMjNkYmJjNDJhMTY0NzFmZGY2NGJiODMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
        // Rat
        "ewogICJ0aW1lc3RhbXAiIDogMTYxODQxOTcwMTc1MywKICAicHJvZmlsZUlkIiA6ICI3MzgyZGRmYmU0ODU0NTVjODI1ZjkwMGY4OGZkMzJmOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJCdUlJZXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYThhYmI0NzFkYjBhYjc4NzAzMDExOTc5ZGM4YjQwNzk4YTk0MWYzYTRkZWMzZWM2MWNiZWVjMmFmOGNmZmU4IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
        // Slug
        "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ0MzA4MiwKICAicHJvZmlsZUlkIiA6ICJkOGNkMTNjZGRmNGU0Y2IzODJmYWZiYWIwOGIyNzQ4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJaYWNoeVphY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E3OWQwZmQ2NzdiNTQ1MzA5NjExMTdlZjg0YWRjMjA2ZTJjYzUwNDVjMTM0NGQ2MWQ3NzZiZjhhYzJmZTFiYSIKICAgIH0KICB9Cn0="
    )

    private val SECONDARY_SEGMENT_HEADS = setOf(
        // Earthworm tail
        "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ3ODAzMCwKICAicHJvZmlsZUlkIiA6ICI0NmY3N2NjNmQ2MjU0NjEzYjc2NmYyZDRmMDM2MzZhNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNzV29sZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZDQwYWE1MDkwNTIzNWI2MjhlNzM3OWViMzFmYTQ1Y2Q0MWI1MDNmMDk3MjFkYjNjNDM3ZmNlZTM5MjA3ZGZjIgogICAgfQogIH0KfQ==",
        // Firefly flash
        "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMzg4OSwKICAicHJvZmlsZUlkIiA6ICIyY2Y2MzExZjUyMTM0NTE2YTEyNTY3NWUwMzk3NmU2MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJmaWdodHN0b2NrIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNlNTI3ODJkN2YyYWFlZThhZjViYTI5MjhmZWM3ODg1ZTk0ODc5MzM0YzIyOTZiYzllN2UyZGJjNTQxOGU1OGYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="
    )

    fun isPestNameMatching(rawName: String?): Boolean {
        if (rawName.isNullOrBlank()) return false
        val clean = PestTabReader.stripColor(rawName).uppercase()
        if (clean.contains("ൠ") || clean.contains("\u0D70")) return true
        return KNOWN_PEST_NAMES.any { clean.contains(it) }
    }

    /**
     * Extracts the base64 skull texture value from an ArmorStand's head item.
     * Mirrors Skyblocker's ItemUtils#getHeadTexture logic.
     */
    fun getArmorStandHeadTexture(entity: ArmorStand): String? {
        val headItem = entity.getItemBySlot(EquipmentSlot.HEAD)
        if (headItem.isEmpty || !headItem.`is`(Items.PLAYER_HEAD)) return null
        val profile = headItem.get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().properties.get("textures").firstOrNull()?.value
    }

    /**
     * Checks whether the ArmorStand is wearing a known Pest head texture.
     * Pests are visible instantly at full render distance.
     */
    fun isPestHeadArmorStand(entity: ArmorStand): Boolean {
        val texture = getArmorStandHeadTexture(entity) ?: return false
        return PEST_HEADS.contains(texture)
    }

    fun isSecondarySegment(entity: ArmorStand): Boolean {
        val texture = getArmorStandHeadTexture(entity) ?: return false
        return SECONDARY_SEGMENT_HEADS.contains(texture)
    }

    fun findPestsInRadius(client: Minecraft, center: Vec3, radius: Double): List<TrackedPest> {
        val level = client.level ?: return emptyList()
        val radiusSq = radius * radius

        val loadedEntities = level.entitiesForRendering()
        val candidateStands = mutableListOf<ArmorStand>()

        for (entity in loadedEntities) {
            if (entity.isRemoved) continue
            if (entity !is ArmorStand) continue
            if (entity.position().distanceToSqr(center) > radiusSq) continue

            if (isPestHeadArmorStand(entity)) {
                candidateStands.add(entity)
            }
        }

        // Sort so primary heads are selected before secondary segments (tails/flashes)
        val sortedStands = candidateStands.sortedBy { if (isSecondarySegment(it)) 1 else 0 }
        val results = mutableListOf<TrackedPest>()
        val clusterDistSq = 1.6 * 1.6

        for (stand in sortedStands) {
            val standPos = stand.position()
            // If already merged with an existing nearby pest stand (e.g. Earthworm body/tail segments), skip
            if (results.none { it.position.distanceToSqr(standPos) < clusterDistSq }) {
                results.add(TrackedPest(stand, stand))
            }
        }

        return results
    }

    fun isInsidePlotBounds(pos: Vec3, plotCenter: Vec3, maxDistance: Double = 46.0): Boolean {
        val dx = kotlin.math.abs(pos.x - plotCenter.x)
        val dz = kotlin.math.abs(pos.z - plotCenter.z)
        return dx <= maxDistance && dz <= maxDistance
    }

    fun findPestsInPlot(client: Minecraft, plotCenter: Vec3, maxDistance: Double = 46.0): List<TrackedPest> {
        val allNearby = findPestsInRadius(client, plotCenter, radius = maxDistance * 1.42)
        return allNearby.filter { isInsidePlotBounds(it.position, plotCenter, maxDistance) }
    }

    fun forgetPest(uuid: UUID) {
        confirmedPestUuids.remove(uuid)
    }

    fun isPestDeadOrRemoved(client: Minecraft, pest: TrackedPest): Boolean {
        if (pest.entity.isRemoved) return true
        val level = client.level ?: return true
        val entityById = level.getEntity(pest.entity.id)
        if (entityById == null || entityById.isRemoved) return true
        return false
    }

    fun getSafeHoverY(client: Minecraft, x: Double, startY: Double, z: Double, minAirClearance: Double = 1.8): Double {
        val level = client.level ?: return startY + minAirClearance
        val bx = kotlin.math.floor(x).toInt()
        val bz = kotlin.math.floor(z).toInt()
        val startBY = kotlin.math.floor(startY + 2.0).toInt().coerceIn(-64, 320)
        val minBY = kotlin.math.max(kotlin.math.floor(startY - 15.0).toInt(), -64)

        val mutablePos = net.minecraft.core.BlockPos.MutableBlockPos()
        for (by in startBY downTo minBY) {
            mutablePos.set(bx, by, bz)
            if (level.hasChunk(bx shr 4, bz shr 4)) {
                val state = level.getBlockState(mutablePos)
                val shape = state.getCollisionShape(level, mutablePos)
                if (!shape.isEmpty) {
                    val maxBlockY = by + shape.max(net.minecraft.core.Direction.Axis.Y)
                    return maxBlockY + minAirClearance
                }
            }
        }
        return startY + minAirClearance
    }

    fun findSafeAttackPosition(
        client: Minecraft,
        playerPos: Vec3,
        targetPos: Vec3,
        otherPests: List<TrackedPest>,
        keepPestActive: Boolean,
        targetEntityId: Int? = null,
        attackRange: Double = 7.0,
        plotCenter: Vec3? = null,
        maxPlotOffset: Double = 46.0
    ): Vec3 {
        fun clampToPlot(v: Vec3): Vec3 {
            if (plotCenter == null) return v
            val minX = plotCenter.x - maxPlotOffset
            val maxX = plotCenter.x + maxPlotOffset
            val minZ = plotCenter.z - maxPlotOffset
            val maxZ = plotCenter.z + maxPlotOffset
            val clampedX = v.x.coerceIn(minX, maxX)
            val clampedZ = v.z.coerceIn(minZ, maxZ)
            return Vec3(clampedX, v.y, clampedZ)
        }

        val toTarget = targetPos.subtract(playerPos)
        val dist = sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)

        val defaultPos = if (dist > 0.1) {
            val rawX = targetPos.x - (toTarget.x / dist) * attackRange
            val rawZ = targetPos.z - (toTarget.z / dist) * attackRange
            val safeY = getSafeHoverY(client, rawX, targetPos.y, rawZ, minAirClearance = 1.8)
            clampToPlot(Vec3(rawX, safeY, rawZ))
        } else {
            val safeY = getSafeHoverY(client, targetPos.x + attackRange, targetPos.y, targetPos.z, minAirClearance = 1.8)
            clampToPlot(Vec3(targetPos.x + attackRange, safeY, targetPos.z))
        }

        if (!keepPestActive || otherPests.isEmpty()) {
            return defaultPos
        }

        // Search 16 radial angles around target to find an approach angle with > 25b clearance from other pests in line of fire
        var bestCandidate = defaultPos
        var maxClearanceDistance = -1.0

        for (i in 0 until 16) {
            val angle = i * (Math.PI / 8.0)
            val candidateX = targetPos.x + attackRange * kotlin.math.cos(angle)
            val candidateZ = targetPos.z + attackRange * kotlin.math.sin(angle)
            if (plotCenter != null && !isInsidePlotBounds(Vec3(candidateX, targetPos.y, candidateZ), plotCenter, maxPlotOffset)) {
                continue
            }
            val safeY = getSafeHoverY(client, candidateX, targetPos.y, candidateZ, minAirClearance = 1.8)
            val candidatePos = Vec3(candidateX, safeY, candidateZ)

            val fireVec = targetPos.subtract(candidatePos).normalize()
            var hasCollateral = false
            var minOtherDist = Double.MAX_VALUE

            for (other in otherPests) {
                if (targetEntityId != null && other.entity.id == targetEntityId) continue
                val toOther = other.position.subtract(candidatePos)
                val otherDist = toOther.length()
                if (otherDist < minOtherDist) minOtherDist = otherDist

                // Check if other pest is within 25 blocks and inside the ~40 degree forward firing cone
                if (otherDist <= 25.0) {
                    val toOtherNorm = toOther.normalize()
                    val dot = fireVec.x * toOtherNorm.x + fireVec.y * toOtherNorm.y + fireVec.z * toOtherNorm.z
                    if (dot > 0.7) { // ~45 deg cone
                        hasCollateral = true
                        break
                    }
                }
            }

            if (!hasCollateral) {
                return candidatePos
            }

            if (minOtherDist > maxClearanceDistance) {
                maxClearanceDistance = minOtherDist
                bestCandidate = candidatePos
            }
        }

        return bestCandidate
    }
}

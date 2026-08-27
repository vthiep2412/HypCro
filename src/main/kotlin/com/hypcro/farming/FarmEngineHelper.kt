package com.hypcro.farming

import com.hypcro.config.CropType
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object FarmEngineHelper {

    fun detectFrontCrop(client: Minecraft): CropType? {
        val player = client.player ?: return null
        val level = client.level ?: return null

        val yawRad = Math.toRadians(player.yRot.toDouble())
        val forwardDir = Vec3(-sin(yawRad), 0.0, cos(yawRad)).normalize()
        val footPos = player.position()

        val mutablePos = BlockPos.MutableBlockPos()
        // Scan 1 to 3 blocks forward in horizontal yaw direction
        for (i in 1..3) {
            val checkX = footPos.x + forwardDir.x * i
            val checkY = footPos.y
            val checkZ = footPos.z + forwardDir.z * i

            val blockX = kotlin.math.floor(checkX).toInt()
            val blockY = kotlin.math.floor(checkY).toInt()
            val blockZ = kotlin.math.floor(checkZ).toInt()

            for (dy in -1..1) {
                mutablePos.set(blockX, blockY + dy, blockZ)
                val block = level.getBlockState(mutablePos).block

                when (block) {
                    is CropBlock -> {
                        if (block == Blocks.WHEAT) return CropType.WHEAT
                        if (block == Blocks.CARROTS) return CropType.CARROT
                        if (block == Blocks.POTATOES) return CropType.POTATO
                    }
                    is NetherWartBlock -> return CropType.NETHER_WART
                    is MushroomBlock -> return CropType.MUSHROOM
                }
            }
        }
        return null
    }

    fun isPlayerFeetInWater(client: Minecraft): Boolean {
        val player = client.player ?: return false
        val level = client.level ?: return false

        if (player.isInWater || player.isUnderWater) return true
        if (player.isEyeInFluid(FluidTags.WATER)) return true

        // Check entity bounding box lower half (feet/legs) for water fluid or water block
        val bb = player.boundingBox
        val minX = kotlin.math.floor(bb.minX).toInt()
        val maxX = kotlin.math.floor(bb.maxX).toInt()
        val minY = kotlin.math.floor(bb.minY).toInt()
        val maxY = kotlin.math.floor(bb.minY + 0.6).toInt()
        val minZ = kotlin.math.floor(bb.minZ).toInt()
        val maxZ = kotlin.math.floor(bb.maxZ).toInt()

        val mutablePos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    mutablePos.set(x, y, z)
                    val fluid = level.getFluidState(mutablePos)
                    if (fluid.`is`(FluidTags.WATER) && !fluid.isEmpty) {
                        return true
                    }
                    val blockState = level.getBlockState(mutablePos)
                    if (blockState.block == Blocks.WATER || blockState.block == Blocks.BUBBLE_COLUMN) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

package com.hypcro.pathfinding

import com.hypcro.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

object PathfindingVisualizer {

    enum class SegmentType {
        YELLOW_SEARCHING,
        GREEN_REACHABLE,
        RED_UNREACHABLE,
        CYAN_CHOSEN
    }

    data class DebugSegment(
        val from: Vec3,
        val to: Vec3,
        val type: SegmentType
    )

    var isVerbose: Boolean
        get() = try {
            ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual
        } catch (_: Exception) {
            false
        }
        set(value) {
            try {
                ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual = value
                ConfigManager.save()
            } catch (_: Exception) {}
        }

    @Volatile
    var currentPath: List<Vec3> = Collections.emptyList()
        private set

    @Volatile
    var activeAlgorithmName: String = ""
        private set

    @Volatile
    var lastCalculationTimeMs: Long = 0L
        private set

    private const val MAX_DEBUG_SEGMENTS = 5000
    private val debugSegmentsLock = Any()
    private val debugSegments: MutableList<DebugSegment> = mutableListOf()

    fun clearDebug() {
        synchronized(debugSegmentsLock) {
            debugSegments.clear()
        }
    }

    fun addDebugBranch(from: Vec3, to: Vec3, type: SegmentType) {
        if (!isVerbose) return
        synchronized(debugSegmentsLock) {
            if (debugSegments.size < MAX_DEBUG_SEGMENTS) {
                debugSegments.add(DebugSegment(from, to, type))
            }
        }
    }

    fun setPath(algorithm: String, path: List<Vec3>, calcTimeMs: Long) {
        activeAlgorithmName = algorithm
        currentPath = path.toList()
        lastCalculationTimeMs = calcTimeMs
    }

    fun clear() {
        currentPath = Collections.emptyList()
        activeAlgorithmName = ""
        lastCalculationTimeMs = 0L
        clearDebug()
    }

    fun renderWorld() {
        if (!ConfigManager.config.generalConfig.visuals.pathfindingVisualizer) return

        val mc = Minecraft.getInstance()
        if (mc.player == null) return

        // 1. Render Verbose Exploration Branches if enabled
        val segmentsToRender = if (isVerbose) {
            synchronized(debugSegmentsLock) {
                if (debugSegments.isEmpty()) emptyList() else ArrayList(debugSegments)
            }
        } else {
            emptyList()
        }

        if (segmentsToRender.isNotEmpty()) {
            val redColor = ARGB.color(180, 239, 68, 68)      // Red: Unreachable / collision
            val greenColor = ARGB.color(180, 34, 197, 94)    // Green: Reachable candidate not chosen
            val yellowColor = ARGB.color(220, 234, 179, 8)   // Yellow: Currently expanding / searching
            val cyanDebugColor = ARGB.color(240, 6, 182, 212) // Cyan debug line

            for (seg in segmentsToRender) {
                val color = when (seg.type) {
                    SegmentType.RED_UNREACHABLE -> redColor
                    SegmentType.GREEN_REACHABLE -> greenColor
                    SegmentType.YELLOW_SEARCHING -> yellowColor
                    SegmentType.CYAN_CHOSEN -> cyanDebugColor
                }
                Gizmos.line(seg.from, seg.to, color, 1.0f)
            }
        }

        val path = currentPath
        if (path.isEmpty()) return

        val startStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, 251, 146, 60),  // Orange stroke (start node)
            2.0f,
            ARGB.color(120, 251, 146, 60)   // Translucent orange fill
        )

        val nodeStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, 56, 189, 248),  // Cyan stroke
            1.5f,
            ARGB.color(100, 56, 189, 248)   // Translucent cyan fill
        )

        val goalStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, 16, 185, 129),  // Emerald stroke
            2.0f,
            ARGB.color(140, 16, 185, 129)   // Translucent emerald fill
        )

        val chosenLineColor = ARGB.color(255, 6, 182, 212) // Bright cyan line for chosen path

        for (i in path.indices) {
            val node = path[i]
            val isStart = (i == 0)
            val isGoal = (i == path.size - 1)

            when {
                isStart || isGoal -> {
                    val style = if (isStart) startStyle else goalStyle
                    val bodyBox = AABB(
                        node.x - 0.3, node.y,       node.z - 0.3,
                        node.x + 0.3, node.y + 1.8, node.z + 0.3
                    )
                    Gizmos.cuboid(bodyBox, style)
                }
                else -> {
                    val box = AABB(
                        node.x - 0.15, node.y - 0.15, node.z - 0.15,
                        node.x + 0.15, node.y + 0.15, node.z + 0.15
                    )
                    Gizmos.cuboid(box, nodeStyle)
                }
            }

            if (i < path.size - 1) {
                val nextNode = path[i + 1]
                Gizmos.line(node, nextNode, chosenLineColor, 3.0f)
            }
        }
    }
}

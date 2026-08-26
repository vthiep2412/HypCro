package com.hypcro.pathfinding

import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

interface IPathfinder {
    fun computePath(level: Level, start: Vec3, destination: Vec3): List<Vec3>
}

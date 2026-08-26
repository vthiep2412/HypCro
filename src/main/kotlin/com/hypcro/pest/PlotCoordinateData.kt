package com.hypcro.pest

import net.minecraft.world.phys.Vec3

data class PlotInfo(
    val id: Int,
    val tpPos: Vec3,
    val centerPos: Vec3
)

object PlotCoordinateData {
    val PLOTS: Map<Int, PlotInfo> = mapOf(
        0 to PlotInfo(0, Vec3(0.0, 71.0, 0.0), Vec3(0.0, 71.0, 0.0)),
        1 to PlotInfo(1, Vec3(0.0, 71.0, -48.0), Vec3(0.0, 71.0, -96.0)),
        2 to PlotInfo(2, Vec3(-48.0, 71.0, 0.0), Vec3(-96.0, 71.0, 0.0)),
        3 to PlotInfo(3, Vec3(48.0, 71.0, 0.0), Vec3(96.0, 71.0, 0.0)),
        4 to PlotInfo(4, Vec3(0.0, 71.0, 48.0), Vec3(0.0, 71.0, 96.0)),
        5 to PlotInfo(5, Vec3(-48.0, 71.0, -96.0), Vec3(-96.0, 71.0, -96.0)),
        6 to PlotInfo(6, Vec3(48.0, 71.0, -96.0), Vec3(96.0, 71.0, -96.0)),
        7 to PlotInfo(7, Vec3(-48.0, 71.0, 96.0), Vec3(-96.0, 71.0, 96.0)),
        8 to PlotInfo(8, Vec3(48.0, 71.0, 96.0), Vec3(96.0, 71.0, 96.0)),
        9 to PlotInfo(9, Vec3(0.0, 71.0, -144.0), Vec3(0.0, 71.0, -192.0)),
        10 to PlotInfo(10, Vec3(-144.0, 71.0, 0.0), Vec3(-192.0, 71.0, 0.0)),
        11 to PlotInfo(11, Vec3(144.0, 71.0, 0.0), Vec3(192.0, 71.0, 0.0)),
        12 to PlotInfo(12, Vec3(0.0, 71.0, 144.0), Vec3(0.0, 71.0, 192.0)),
        13 to PlotInfo(13, Vec3(-96.0, 71.0, -144.0), Vec3(-96.0, 71.0, -192.0)),
        14 to PlotInfo(14, Vec3(96.0, 71.0, -144.0), Vec3(96.0, 71.0, -192.0)),
        15 to PlotInfo(15, Vec3(-144.0, 71.0, -96.0), Vec3(-192.0, 71.0, -96.0)),
        16 to PlotInfo(16, Vec3(-144.0, 71.0, 96.0), Vec3(-192.0, 71.0, 96.0)),
        17 to PlotInfo(17, Vec3(144.0, 71.0, -96.0), Vec3(192.0, 71.0, -96.0)),
        18 to PlotInfo(18, Vec3(144.0, 71.0, 96.0), Vec3(192.0, 71.0, 96.0)),
        19 to PlotInfo(19, Vec3(-96.0, 71.0, 144.0), Vec3(-96.0, 71.0, 192.0)),
        20 to PlotInfo(20, Vec3(96.0, 71.0, 144.0), Vec3(96.0, 71.0, 192.0)),
        21 to PlotInfo(21, Vec3(-144.0, 71.0, -192.0), Vec3(-192.0, 71.0, -192.0)),
        22 to PlotInfo(22, Vec3(144.0, 71.0, -192.0), Vec3(192.0, 71.0, -192.0)),
        23 to PlotInfo(23, Vec3(-144.0, 71.0, 192.0), Vec3(-192.0, 71.0, 192.0)),
        24 to PlotInfo(24, Vec3(144.0, 71.0, 192.0), Vec3(192.0, 71.0, 192.0))
    )

    fun getPlot(id: Int): PlotInfo? = PLOTS[id]
}

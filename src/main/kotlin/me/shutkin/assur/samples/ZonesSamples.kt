package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram
import me.shutkin.assur.filters.buildZones

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], 0.0, 128.0, 0.0) { raster ->
    val zones = buildZones(raster)
    val average = zones.zonesLums.average()
    buildHistogram(0.0, 128.0, 128, zones.zonesLums.size) { Math.abs(average - zones.zonesLums[it]) }.histogram
  }
  saveSamples(samples, "zones")
}
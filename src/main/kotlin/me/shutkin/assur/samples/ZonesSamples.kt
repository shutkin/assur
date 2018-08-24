package me.shutkin.assur.samples

import me.shutkin.assur.HDRRaster
import me.shutkin.assur.buildHistogram
import me.shutkin.assur.filters.ZonesData

fun main(args: Array<String>) {
  val references = collectReferences(args[0], 0.0, ::evalArraysDiffM) { raster ->
    val zones = buildZones(raster)
    val average = zones.zonesLums.average()
    buildHistogram(0.0, 128.0, 128, zones.zonesLums.size) { Math.abs(average - zones.zonesLums[it]) }.histogram
  }
  saveReferences(references, "zones")
}

fun buildZones(source: HDRRaster): ZonesData {
  val zoneSize = Math.max(source.width, source.height) / 15
  val horizZones = (source.width + zoneSize - 1) / zoneSize
  val vertZones = (source.height + zoneSize - 1) / zoneSize
  return ZonesData(zoneSize, horizZones, vertZones, DoubleArray(horizZones * vertZones) {
    val zoneX = it % horizZones
    val zoneY = it / horizZones
    calculateZoneLum(source, zoneX, zoneY, zoneSize)
  })
}

private fun calculateZoneLum(raster: HDRRaster, zoneX: Int, zoneY: Int, zoneSize: Int): Double {
  val zoneWidth = if ((zoneX + 1) * zoneSize < raster.width) zoneSize else raster.width - zoneX * zoneSize
  val zoneHeight = if ((zoneY + 1) * zoneSize < raster.height) zoneSize else raster.height - zoneY * zoneSize
  var sum = 0.0
  for (y in zoneY * zoneSize until zoneY * zoneSize + zoneHeight) {
    for (x in zoneX * zoneSize until zoneX * zoneSize + zoneWidth) {
      sum += raster.data[y * raster.width + x].luminance
    }
  }
  return sum / (zoneWidth * zoneHeight)
}

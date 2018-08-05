package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiffM
import java.io.FileInputStream

private val zonesSamples = deserializeSamples(FileInputStream("zones.samples"), 128)

fun zonalFilter(source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  log("ZonalFilter start")

  var error: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val samples = zonesSamples.filterIndexed { index, _ ->
      index in diapason.getStartIndex(zonesSamples.size)..(diapason.getEndIndex(zonesSamples.size) - 1) }.toTypedArray()
    val adjuster = SplineAdjuster(samples, 0.0, 128.0)
    adjuster.adjustPoints = 3
    adjuster.steps = 3
    val reduced = reduceSizeFilter(source, 384, false)
    val reducedZones = buildZones(reduced)
    val averageReducedLum = reducedZones.zonesLums.average()
    val bestSpline = adjuster.findSpline({ spline ->
      val result = HDRRaster(reduced.width, reduced.height) {
        val x = it % reduced.width
        val y = it / reduced.width
        val zonalLum = zone(x, y, reducedZones)
        val diff = zonalLum - averageReducedLum
        val correctedDiff = spline.interpolate(Math.abs(diff)) * (if (diff < 0) -1 else 1)
        val factor = (averageReducedLum + correctedDiff + 1.0) / (zonalLum + 1.0)
        source.data[it].multiply(factor)
      }
      val resultZones = buildZones(result)
      val resultAverage = resultZones.zonesLums.average()
      buildHistogram(0.0, 128.0, 128, resultZones.zonesLums.size) { Math.abs(resultAverage - resultZones.zonesLums[it]) }.histogram
    }, ::evalArraysDiffM)
    error = adjuster.smallestError
    median = adjuster.bestMedian
    bestSpline

  } else predefinedSpline

  val zones = buildZones(source)
  log("zone size: ${zones.zoneSize}, horizontal: ${zones.horizZones}, vertical: ${zones.vertZones}")
  log("zones: ${zones.zonesLums.joinToString()}")
  val averageLum = zones.zonesLums.average()
  log("Average lum $averageLum")
  return FilterResult(HDRRaster(source.width, source.height) {
    val x = it % source.width
    val y = it / source.width
    val zonalLum = zone(x, y, zones)
    val diff = zonalLum - averageLum
    val correctedDiff = spline.interpolate(Math.abs(diff)) * (if (diff < 0) -1 else 1)
    val factor = (averageLum + correctedDiff + 1.0) / (zonalLum + 1.0)
    source.data[it].multiply(factor)
  }, spline, error, median)
}

data class ZonesData(val zoneSize: Int, val horizZones: Int, val vertZones: Int, val zonesLums: DoubleArray)

fun buildZones(source: HDRRaster): ZonesData {
  val zoneSize = Math.max(source.width, source.height) / 16
  val horizZones = (source.width + zoneSize - 1) / zoneSize
  val vertZones = (source.height + zoneSize - 1) / zoneSize
  return ZonesData(zoneSize, horizZones, vertZones, DoubleArray(horizZones * vertZones) {
    val zoneX = it % horizZones
    val zoneY = it / horizZones
    calculateZoneLum(source, zoneX, zoneY, zoneSize)
  })
}

private fun zone(x: Int, y: Int, zones: ZonesData): Double {
  val zoneX = x / zones.zoneSize
  val zoneY = y / zones.zoneSize
  var sum = 0.0
  var sumWeight = 0.0
  for (neighborZoneY in (zoneY - 5 .. zoneY + 5)) {
    if (neighborZoneY < 0 || neighborZoneY > zones.vertZones - 1)
      continue
    val dy = (if (y == neighborZoneY * zones.zoneSize) 1.0 else (y - neighborZoneY * zones.zoneSize).toDouble()) / zones.zoneSize
    for (neighborZoneX in (zoneX - 5 .. zoneX + 5)) {
      if (neighborZoneX < 0 || neighborZoneX > zones.horizZones - 1)
        continue
      val dx = (if (x == neighborZoneX * zones.zoneSize) 1.0 else (x - neighborZoneX * zones.zoneSize).toDouble()) / zones.zoneSize
      val d = dx * dx + dy * dy
      val sigma = 0.84089642 * 5.0 / 3.0
      val weight = Math.exp(-d / (2.0 * sigma * sigma))
      sum += zones.zonesLums[neighborZoneY * zones.horizZones + neighborZoneX] * weight
      sumWeight += weight
    }
  }
  return sum / sumWeight
}

private fun calculateZoneLum(raster: HDRRaster, zoneX: Int, zoneY: Int, zoneSize: Int): Double {
  val zoneWidth = if ((zoneX + 1) * zoneSize < raster.width) zoneSize else raster.width - zoneX * zoneSize
  val zoneHeight = if ((zoneY + 1) * zoneSize < raster.height) zoneSize else raster.height - zoneY * zoneSize
  return (0 until zoneWidth * zoneHeight).map {
    val x = zoneX * zoneSize + (it % zoneWidth)
    val y = zoneY * zoneSize + (it / zoneWidth)
    raster.data[y * raster.width + x].luminance
  }.average()
}

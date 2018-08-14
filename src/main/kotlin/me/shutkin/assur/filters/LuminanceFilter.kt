package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.assurLog
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiff

private val luminanceSamples = deserializeSamples(object {}.javaClass.getResourceAsStream("/luminance.samples"), 256)

fun luminanceFilter(source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  assurLog("LuminanceFilter start, diapason $diapason")
  var error: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val samples = luminanceSamples.filterIndexed { index, _ ->
      index in diapason.getStartIndex(luminanceSamples.size)..(diapason.getEndIndex(luminanceSamples.size) - 1) }.toTypedArray()
    val reduced = reduceSizeFilter(source, 384, false)
    val adjuster = SplineAdjuster(samples, 0.0, 255.0)
    val bestSpline = adjuster.findSpline({ spline ->
      buildHistogram(0.0, 255.0, 256, reduced.data.size) {
        val l = reduced.data[it].luminance
        val testRGB = reduced.data[it].multiply(spline.interpolate(l) / (l + 1.0))
        testRGB.luminance
      }.histogram
    }, ::evalArraysDiff)
    error = adjuster.smallestError
    median = adjuster.bestMedian
    bestSpline
  } else predefinedSpline

  return FilterResult(HDRRaster(source.width, source.height) {
    val l = source.data[it].luminance
    source.data[it].multiply(spline.interpolate(l) / (l + 0.1))
  }, spline, error, median)
}

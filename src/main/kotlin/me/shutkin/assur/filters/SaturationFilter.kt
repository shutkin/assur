package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiff

private val saturationSamples = deserializeSamples(object {}.javaClass.getResourceAsStream("/saturation.samples"), 128)

fun saturationFilter(context: AssurContext, source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  context.log("SaturationFilter start, diapason $diapason")
  var error: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val samples = saturationSamples.filterIndexed { index, _ ->
      index in diapason.getStartIndex(saturationSamples.size)..(diapason.getEndIndex(saturationSamples.size) - 1) }.toTypedArray()
    val reduced = reduceSizeFilter(context, source, 384, false)
    val adjuster = SplineAdjuster(samples, 0.0, 1.0)
    val bestSpline = adjuster.findSpline(context, ::evalArraysDiff) { spline ->
      buildHistogram(0.0, 1.0, 128, reduced.data.size) {
        val s = reduced.data[it].saturation
        val factor = spline.interpolate(s) / (s + 0.001)
        val rgb = reduced.data[it].adjustSaturation(factor)
        rgb.saturation
      }.histogram
    }
    error = adjuster.correctnessImprovement
    median = adjuster.bestMedian
    bestSpline
  } else predefinedSpline

  return FilterResult(HDRRaster(source.width, source.height) {
    val s = source.data[it].saturation
    val factor = spline.interpolate(s) / (s + 0.001)
    source.data[it].adjustSaturation(factor)
  }, spline, error, median)
}

package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import java.io.FileInputStream

private val window: Window = StraightWindow(32)
private val smallWindow: Window = StraightWindow(16)

private val detailsSamples = deserializeSamples(FileInputStream("details.samples"), 1024)

fun detailsFilter(source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  log("DetailsFilter start, diapason $diapason")
  var error: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val samples = detailsSamples.filterIndexed { index, _ ->
      index in diapason.getStartIndex(detailsSamples.size)..(diapason.getEndIndex(detailsSamples.size) - 1) }.toTypedArray()
    val reduced = reduceSizeFilter(source, 256, false)
    val reducedWindow = StraightWindow(6)
    val reducedBlur = reducedWindow.apply(reduced)
    val adjuster = SplineAdjuster(samples, 0.0, 64.0)
    adjuster.adjustPoints = 3
    val bestSpline = adjuster.findSpline { testSpline ->
      val testRaster = HDRRaster(reduced.width, reduced.height, {
        val l = reduced.data[it].luminance
        val details = l - reducedBlur[it]
        val targetDetails = testSpline.interpolate(Math.abs(details))
        val targetLuminance = reducedBlur[it] + if (details > 0) targetDetails else -targetDetails
        reduced.data[it].multiply(targetLuminance / (l + 0.1))
      })
      val testBlur = reducedWindow.apply(testRaster)
      buildHistogram(0.0, 64.0, 1024, reduced.data.size, { Math.abs(testRaster.data[it].luminance - testBlur[it]) }).histogram
    }
    error = adjuster.smallestError
    median = adjuster.bestMedian
    bestSpline
  } else predefinedSpline

  val blur = (if (source.width > 1100 || source.height > 1100) window else smallWindow).apply(source)
  return FilterResult(HDRRaster(source.width, source.height, {
    val l = source.data[it].luminance
    val details = l - blur[it]
    val targetDetails = spline.interpolate(Math.abs(details))
    val targetLuminance = blur[it] + if (details > 0) targetDetails else -targetDetails
    source.data[it].multiply(targetLuminance / (l + 0.1))
  }), spline, error, median)
}

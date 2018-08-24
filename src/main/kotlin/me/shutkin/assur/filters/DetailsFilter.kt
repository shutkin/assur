package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.samples.deserializeReferences
import me.shutkin.assur.samples.evalArraysDiffM
import me.shutkin.assur.samples.getReferences

private val window: Window = OptimizedWindow(64, (1.0 / 6.0E-5).toInt())
private val smallWindow: Window = StraightWindow(24)

val detailsReferences = deserializeReferences(object {}.javaClass.getResourceAsStream("/details.ref"), 1024)

fun detailsFilter(context: AssurContext, source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null,
                  referenceIndex: Int = -1): FilterResult {
  context.log("DetailsFilter start, diapason $diapason")
  var selectedRefIndex: Int? = null
  var correctness: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val references = getReferences(detailsReferences.refs, diapason, referenceIndex)
    val reduced = reduceSizeFilter(context, source, 256, false)
    val reducedWindow = StraightWindow(6)
    val reducedBlur = reducedWindow.apply(reduced)
    val adjuster = SplineAdjuster(references, 0.0, 64.0)
    adjuster.adjustPoints = 3
    adjuster.levels = 4
    val bestSpline = adjuster.findSpline(context, ::evalArraysDiffM) { testSpline ->
      val testRaster = HDRRaster(reduced.width, reduced.height) {
        val originalLum = reduced.data[it].luminance
        val diff = originalLum - reducedBlur[it]
        val correctedDiff = testSpline.interpolate(Math.abs(diff)) * (if (diff < 0) -1 else 1)
        val factor = (reducedBlur[it] + correctedDiff + 1.0) / (originalLum + 1)
        reduced.data[it].multiply(factor)
      }
      val testBlur = reducedWindow.apply(testRaster)
      buildHistogram(0.0, 64.0, 1024, reduced.data.size) { Math.abs(testRaster.data[it].luminance - testBlur[it]) }.histogram
    }
    selectedRefIndex = adjuster.selectedRefIndex
    correctness = adjuster.bestRelativeCorrectness
    median = adjuster.bestMedian
    bestSpline
  } else predefinedSpline

  val blur = (if (source.width > 1100 || source.height > 1100) window else smallWindow).apply(source)
  return FilterResult(HDRRaster(source.width, source.height) {
    val originalLum = source.data[it].luminance
    val diff = originalLum - blur[it]
    val correctedDiff = spline.interpolate(Math.abs(diff)) * (if (diff < 0) -1 else 1)
    val factor = 1.0 * (blur[it] + correctedDiff + 1.0) / (originalLum + 1)
    source.data[it].multiply(factor)
  }, spline, selectedRefIndex, correctness, median)
}

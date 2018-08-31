package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.samples.deserializeReferences
import me.shutkin.assur.samples.evalArraysDiffM
import me.shutkin.assur.samples.getReferences

val detailsReferences = deserializeReferences(object {}.javaClass.getResourceAsStream("/details.ref"), 1024)

fun detailsFilter(context: AssurContext, source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  context.log("DetailsFilter start, " + if (predefinedSpline == null) "diapason $diapason" else "spline $predefinedSpline")
  var selectedRefIndex: Int? = null
  var correctness: Double? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val references = getReferences(detailsReferences.refs, diapason)
    val reduced = reduceSizeFilter(context, source, 320, false)
    val reducedWindow = getWindow(reduced)
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
    selectedRefIndex = adjuster.selectedRef!!.id
    median = adjuster.bestMedian
    correctness = adjuster.bestCorrectness
    bestSpline
  } else predefinedSpline

  val blur = getWindow(source).apply(source)
  return FilterResult(HDRRaster(source.width, source.height) {
    val originalLum = source.data[it].luminance
    val diff = originalLum - blur[it]
    val correctedDiff = spline.interpolate(Math.abs(diff)) * (if (diff < 0) -1 else 1)
    val factor = 1.0 * (blur[it] + correctedDiff + 1.0) / (originalLum + 1)
    source.data[it].multiply(factor)
  }, spline, selectedRefIndex, if (selectedRefIndex != null) detailsReferences.refs[selectedRefIndex].popularity else null,
          median, correctness)
}

private fun getWindow(image: HDRRaster): Window {
  val r = Math.max(image.width, image.height).toDouble() / 15.0
  return OptimizedWindow((r * 0.5).toInt(), (r * r).toInt())
}
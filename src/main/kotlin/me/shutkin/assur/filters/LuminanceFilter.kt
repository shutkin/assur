package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.samples.deserializeReferences
import me.shutkin.assur.samples.evalArraysDiff
import me.shutkin.assur.samples.getReferences

val luminanceReferences = deserializeReferences(object {}.javaClass.getResourceAsStream("/luminance.ref"), 256)

fun luminanceFilter(context: AssurContext, source: HDRRaster, diapason: Diapason, predefinedSpline: CubicSpline? = null,
                    referenceIndex: Int = -1): FilterResult {
  context.log("LuminanceFilter start, diapason $diapason")
  var selectedRefIndex: Int? = null
  var median: Double? = null
  val spline = if (predefinedSpline == null) {
    val references = getReferences(luminanceReferences.refs, diapason, referenceIndex)
    val reduced = reduceSizeFilter(context, source, 384, false)
    val adjuster = SplineAdjuster(references, 0.0, 255.0)
    adjuster.levels = 4
    val bestSpline = adjuster.findSpline(context, ::evalArraysDiff) { spline ->
      buildHistogram(0.0, 255.0, 256, reduced.data.size) {
        val l = reduced.data[it].luminance
        val testRGB = reduced.data[it].multiply(spline.interpolate(l) / (l + 1.0))
        testRGB.luminance
      }.histogram
    }
    selectedRefIndex = adjuster.selectedRefIndex
    median = adjuster.bestMedian
    bestSpline
  } else predefinedSpline

  return FilterResult(HDRRaster(source.width, source.height) {
    val l = source.data[it].luminance
    source.data[it].multiply(spline.interpolate(l) / (l + 0.1))
  }, spline, selectedRefIndex, if (selectedRefIndex != null) luminanceReferences.refs[selectedRefIndex].popularity else null, median)
}

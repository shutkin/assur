package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.samples.deserializeReferences
import me.shutkin.assur.samples.evalArraysDiff
import me.shutkin.assur.samples.getReferences

val saturationReferences = deserializeReferences(object {}.javaClass.getResourceAsStream("/saturation.ref"), 128)

fun saturationFilter(context: AssurContext, source: HDRRaster, diapason: Diapason = Diapason.ALL, predefinedSpline: CubicSpline? = null): FilterResult {
  context.log("SaturationFilter start, " + if (predefinedSpline == null) "diapason $diapason" else "spline $predefinedSpline")
  var selectedRefIndex: Int? = null
  var median: Double? = null
  var correctness: Double? = null
  val spline = if (predefinedSpline == null) {
    val references = getReferences(saturationReferences.refs, diapason)
    val reduced = reduceSizeFilter(context, source, 384, false)
    val adjuster = SplineAdjuster(references, 0.0, 1.0)
    val bestSpline = adjuster.findSpline(context, ::evalArraysDiff) { spline ->
      buildHistogram(0.0, 1.0, 128, reduced.data.size) {
        val s = reduced.data[it].saturation
        val factor = spline.interpolate(s) / (s + 0.001)
        val rgb = reduced.data[it].adjustSaturation(factor)
        rgb.saturation
      }.histogram
    }
    selectedRefIndex = adjuster.selectedRef!!.id
    median = adjuster.bestMedian
    correctness = adjuster.bestCorrectness
    bestSpline
  } else predefinedSpline

  return FilterResult(HDRRaster(source.width, source.height) {
    val s = source.data[it].saturation
    val factor = spline.interpolate(s) / (s + 0.001)
    source.data[it].adjustSaturation(factor)
  }, spline, selectedRefIndex, if (selectedRefIndex != null) saturationReferences.refs[selectedRefIndex].popularity else null,
          median, correctness)
}

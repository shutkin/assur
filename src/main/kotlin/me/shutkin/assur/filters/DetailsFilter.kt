package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import java.io.FileInputStream

private val window: Window = StraightWindow(32)

fun detailsFilter(source: HDRRaster): HDRRaster {
  log("DetailsFilter start")
  val samples = deserializeSamples(FileInputStream("details.samples"), 1024)
  val selectedSamples = samples.filter {
    getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) > 1.1 * 0
  }.toTypedArray()
  log("selected ${selectedSamples.size} samples")

  val reduced = reduceSizeFilter(source, 256, false)
  val reducedWindow = StraightWindow(8)
  val reducedBlur = reducedWindow.apply(reduced)
  val adjuster = SplineAdjuster(selectedSamples, 0.0, 64.0)
  adjuster.adjustPoints = 4
  adjuster.filenamePrefix = "details"
  val spline = adjuster.findSpline { testSpline ->
    val testRaster = HDRRaster(reduced.width, reduced.height, {
      val l = reduced.data[it].luminance()
      val details = l - reducedBlur[it]
      val targetDetails = testSpline.interpolate(Math.abs(details))
      val targetLuminance = reducedBlur[it] + if (details > 0) targetDetails else -targetDetails
      reduced.data[it].multiply((targetLuminance + 1.0) / (l + 1))
    })
    val testBlur = reducedWindow.apply(testRaster)
    buildHistogram(0.0, 64.0, 1024, reduced.data.size, { Math.abs(testRaster.data[it].luminance() - testBlur[it]) }).histogram
  }
  log(spline.toString())

  val blur = window.apply(source)
  return HDRRaster(source.width, source.height, {
    val l = source.data[it].luminance()
    val details = l - blur[it]
    val targetDetails = spline.interpolate(Math.abs(details))
    val targetLuminance = blur[it] + if (details > 0) targetDetails else -targetDetails
    source.data[it].multiply((targetLuminance + 1.0) / (l + 1))
  })
}

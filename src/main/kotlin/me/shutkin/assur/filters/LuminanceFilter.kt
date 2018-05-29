package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import java.io.FileInputStream

fun luminanceFilter(source: HDRRaster): HDRRaster {
  log("LuminanceFilter start")
  val samples = deserializeSamples(FileInputStream("histogram.samples"), 256)
  val selectedSamples = samples.filter {
    getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) > 1.1 * 0
  }.toTypedArray()
  log("selected ${selectedSamples.size} samples")

  val reduced = reduceSizeFilter(source, 384, false)
  val adjuster = SplineAdjuster(selectedSamples, 0.0, 255.0)
  adjuster.filenamePrefix = "luminance"
  val spline = adjuster.findSpline { spline ->
    buildHistogram(0.0, 255.0, 256, reduced.data.size, {
      val l = reduced.data[it].luminance()
      val testRGB = reduced.data[it].multiply((spline.interpolate(l) + 0.0) / (l + 0.1))
      testRGB.luminance()
    }).histogram
  }
  log(spline.toString())
  return HDRRaster(source.width, source.height, {
    val l = source.data[it].luminance()
    source.data[it].multiply((spline.interpolate(l) + 0.0) / (l + 0.1))
  })
}

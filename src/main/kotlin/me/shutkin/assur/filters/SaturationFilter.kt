package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import java.io.FileInputStream

fun saturationFilter(source: HDRRaster): HDRRaster {
  log("SaturationFilter start")
  val samples = deserializeSamples(FileInputStream("saturation.samples"), 128)
  val reduced = reduceSizeFilter(source, 384, false)
  val adjuster = SplineAdjuster(samples, 0.0, 1.0)
  adjuster.filenamePrefix = "saturation"
  val spline = adjuster.findSpline { spline ->
    buildHistogram(0.0, 1.0, 128, reduced.data.size, {
      val hsl = reduced.data[it].hsl()
      val rgb = hslConvertToRGB(hsl[0], spline.interpolate(hsl[1]), hsl[2])
      val testRGB = RGB(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
      testRGB.hsl()[1]
    }).histogram
  }
  log(spline.toString())
  return HDRRaster(source.width, source.height, {
    val hsl = source.data[it].hsl()
    val rgb = hslConvertToRGB(hsl[0], spline.interpolate(hsl[1]), hsl[2])
    RGB(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
  })
}

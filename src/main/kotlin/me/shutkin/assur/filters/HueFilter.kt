package me.shutkin.assur.filters

import me.shutkin.assur.HDRRaster
import me.shutkin.assur.RGB
import me.shutkin.assur.buildHistogram
import me.shutkin.assur.hslConvertToRGB
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiff
import java.io.FileInputStream

fun hueFilter(source: HDRRaster): HDRRaster {
  log("HueFilter start")
  val samples = deserializeSamples(FileInputStream("hue.samples"), 180)
  val size = samples[0].size
  var minDiff = Double.MAX_VALUE
  var bestShift = 0.0
  val hueHistogramData = buildHistogram(0.0, 360.0, size, source.data.size, { source.data[it].hsl[0] })
  (-size / 36 until size / 36).forEach { shift ->
    val shiftedHueHistogram = DoubleArray(size, {
      val i = it + shift
      hueHistogramData.histogram[if (i < 0) i + size else if (i >= size) i - size else i]
    })
    val diff = samples.map { evalArraysDiff(shiftedHueHistogram, it) }.min()!!
    if (minDiff > diff) {
      minDiff = diff
      bestShift = shift.toDouble() * 360.0 / size
    }
  }
  log("Shift $bestShift")

  return HDRRaster(source.width, source.height, {
    val hsl = source.data[it].hsl
    val targetH = hsl[0] + bestShift
    val rgb = hslConvertToRGB(if (targetH < 0) targetH + 360.0 else if (targetH > 360.0) targetH - 360 else targetH, hsl[1], hsl[2])
    RGB(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
  })
}

package me.shutkin.assur.filters

import me.shutkin.assur.*

fun cutoffFilter(context: AssurContext, source: HDRRaster): HDRRaster {
  context.log("CutoffFilter start")
  val allChannels = DoubleArray(source.data.size * 3) { i ->
    val sourcePixel = source.data[i / 3]
    when (i % 3) {
      0 -> sourcePixel.r.toDouble()
      1 -> sourcePixel.g.toDouble()
      else -> sourcePixel.b.toDouble()
    }
  }
  val histogramData = buildHistogram(allChannels.min()!!, allChannels.max()!!, 256, allChannels.size, { allChannels[it] })
  val threshold = 1.0 / 8192.0
  val min = getHistogramLowValue(histogramData, threshold).toFloat()
  val max = getHistogramHighValue(histogramData, threshold).toFloat()
  context.log("Range: $min ... $max")
  return HDRRaster(source.width, source.height) { i ->
    val sourcePixel = source.data[i]
    RGB(
            if (sourcePixel.r < min) min else if (sourcePixel.r > max) max else sourcePixel.r,
            if (sourcePixel.g < min) min else if (sourcePixel.g > max) max else sourcePixel.g,
            if (sourcePixel.b < min) min else if (sourcePixel.b > max) max else sourcePixel.b
    )
  }
}
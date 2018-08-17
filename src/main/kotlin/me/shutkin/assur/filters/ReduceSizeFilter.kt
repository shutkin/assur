package me.shutkin.assur.filters

import me.shutkin.assur.AssurContext
import me.shutkin.assur.HDRRaster
import me.shutkin.assur.RGB

fun reduceSizeFilter(context: AssurContext, source: HDRRaster, maxSize: Int, verbose: Boolean): HDRRaster {
  if (source.width <= maxSize && source.height <= maxSize)
    return source

  val scale = maxSize.toDouble() / (if (source.width > source.height) source.width else source.height)
  val pixelSize = 1.0 / scale

  if (verbose) {
    context.log("ReduceSizeFilter start")
    context.log("Scale $scale")
  }
  val width = (scale * source.width).toInt()
  val height = (scale * source.height).toInt()
  if (verbose)
    context.log("Reduce to $width x $height")
  return HDRRaster(width, height) { getSourceAverage(source, pixelSize, (it % width).toDouble() / scale, (it / width).toDouble() / scale) }
}

private fun getSourceAverage(source: HDRRaster, pixelSize: Double, x: Double, y: Double): RGB {
  val (startX, startY) = intArrayOf(Math.floor(x).toInt(), Math.floor(y).toInt())
  val (endX, endY) = intArrayOf(Math.ceil(x + pixelSize).toInt(), Math.ceil(y + pixelSize).toInt())
  val (startXf, startYf) = doubleArrayOf(1.0 - (x - startX), 1.0 - (y - startY))
  val (endXf, endYf) = doubleArrayOf(1.0 - (endX.toDouble() - x - pixelSize), 1.0 - (endY.toDouble() - y - pixelSize))
  var (sumR, sumG, sumB) = doubleArrayOf(0.0, 0.0, 0.0)
  var sum = 0.0
  for (sY in startY..endY) {
    for (sX in startX..endX) {
      if (sX >= 0 && sX < source.width && sY >= 0 && sY < source.height) {
        var f = 1.0
        if (sX == startX)
          f *= startXf
        else if (sX == endX)
          f *= endXf
        if (sY == startY)
          f *= startYf
        else if (sY == endY)
          f *= endYf
        val pixel = source.data[sY * source.width + sX]
        sumR += f * pixel.r
        sumG += f * pixel.g
        sumB += f * pixel.b
        sum += f
      }
    }
  }
  return RGB((sumR / sum).toFloat(), (sumG / sum).toFloat(), (sumB / sum).toFloat())
}

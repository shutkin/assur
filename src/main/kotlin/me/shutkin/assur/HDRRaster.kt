package me.shutkin.assur

import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

class HDRRaster(val width: Int, val height: Int, func: (Int) -> RGB) {
  val data = Array(width * height, {
    val rgb = func(it)
    RGB(
            if (rgb.r < 0) 0f else if (rgb.r > 255) 255f else rgb.r,
            if (rgb.g < 0) 0f else if (rgb.g > 255) 255f else rgb.g,
            if (rgb.b < 0) 0f else if (rgb.b > 255) 255f else rgb.b
    )
  })
}

fun readHDRRaster(stream: InputStream): HDRRaster {
  val image = ImageIO.read(stream)
  stream.close()
  val rgbArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  return HDRRaster(image.width, image.height, { i ->
    val c = rgbArray[i]
    RGB((c and 0xff0000 shr 16).toFloat(), (c and 0xff00 shr 8).toFloat(), (c and 0xff).toFloat())
  })
}

fun saveHDRRaster(raster: HDRRaster, stream: OutputStream) {
  val minValue = raster.data.map { rgb -> rgb.min.toDouble() }.min()!!
  val maxValue = raster.data.map { rgb -> rgb.max.toDouble() }.max()!!
  val factor = 255.0 / if (maxValue > minValue + 1) maxValue - minValue else 1.0
  val rgbArray = IntArray(raster.data.size, {
    val intR = rgbChannelToInt(raster.data[it].r, minValue, factor)
    val intG = rgbChannelToInt(raster.data[it].g, minValue, factor)
    val intB = rgbChannelToInt(raster.data[it].b, minValue, factor)
    intB or (intG shl 8) or (intR shl 16) or (255 shl 24)
  })
  val image = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_INT_RGB)
  image.setRGB(0, 0, raster.width, raster.height, rgbArray, 0, raster.width)
  ImageIO.write(image, "png", stream)
  stream.flush()
  stream.close()
}

data class RGB(val r: Float, val g: Float, val b: Float) {
  val min: Float get() = if (r < g && r < b) r else if (g < r && g < b) g else b
  val max: Float get() = if (r > g && r > b) r else if (g > r && g > b) g else b
  val luminance: Double get() = rgbGetLuminance(r.toDouble(), g.toDouble(), b.toDouble())

  fun multiply(factor: Double) = RGB((factor * r).toFloat(), (factor * g).toFloat(), (factor * b).toFloat())

  val saturation: Double
    get() {
      val average = (r + g + b) / 3.0
      val s = (Math.abs(average - r) + Math.abs(average - g) + Math.abs(average - b)) / (3.0 * 128.0)
      return if (s > 1) 1.0 else s
    }

  fun adjustSaturation(factor: Double): RGB {
    val average = (r + g + b) / 3.0
    return RGB((average + (r - average) * factor).toFloat(),
            (average + (g - average) * factor).toFloat(),
            (average + (b - average) * factor).toFloat())
  }
}

fun rgbGetLuminance(r: Double, g: Double, b: Double) = Math.sqrt(0.299 * r * r + 0.587 * g * g + 0.114 * b * b)

fun rgbChannelToInt(c: Float, min: Double, factor: Double): Int {
  val result = Math.round(factor * (c - min)).toInt()
  return when {
    result < 0 -> 0
    result > 255 -> 255
    else -> result
  }
}
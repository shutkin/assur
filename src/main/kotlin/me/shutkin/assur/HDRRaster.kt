package me.shutkin.assur

import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

class HDRRaster(val width: Int, val height: Int, val data: Array<RGB>) {
  constructor(width: Int, height: Int, func: (Int) -> RGB): this(width, height, Array(width * height, func))
}

fun readHDRRaster(stream: InputStream): HDRRaster {
  val image = ImageIO.read(stream)
  stream.close()
  val rgbArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  return HDRRaster(image.width, image.height) { i ->
    val c = rgbArray[i]
    RGB((c and 0xff0000 shr 16).toFloat(), (c and 0xff00 shr 8).toFloat(), (c and 0xff).toFloat())
  }
}

fun saveHDRRaster(raster: HDRRaster, stream: OutputStream, imageFormat: String = "png") {
  val minValue = raster.data.map { rgb -> rgb.min.toDouble() }.min()!!
  val maxValue = raster.data.map { rgb -> rgb.max.toDouble() }.max()!!
  val factor = 255.0 / if (maxValue > minValue + 1) maxValue - minValue else 1.0
  val rgbArray = IntArray(raster.data.size) {
    val intR = rgbChannelToInt(raster.data[it].r, minValue, factor)
    val intG = rgbChannelToInt(raster.data[it].g, minValue, factor)
    val intB = rgbChannelToInt(raster.data[it].b, minValue, factor)
    intB or (intG shl 8) or (intR shl 16) or (255 shl 24)
  }
  val image = BufferedImage(raster.width, raster.height, BufferedImage.TYPE_INT_RGB)
  image.setRGB(0, 0, raster.width, raster.height, rgbArray, 0, raster.width)
  ImageIO.write(image, imageFormat, stream)
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

fun rgbToHsl(rNotNormalized: Double, gNotNormalized: Double, bNotNormalized: Double): DoubleArray {
  val r = /*if (rNotNormalized < 0) 0.0 else if (rNotNormalized > 255) 1.0 else*/ rNotNormalized / 255.0
  val g = /*if (gNotNormalized < 0) 0.0 else if (gNotNormalized > 255) 1.0 else*/ gNotNormalized / 255.0
  val b = /*if (bNotNormalized < 0) 0.0 else if (bNotNormalized > 255) 1.0 else*/ bNotNormalized / 255.0

  val min = Math.min(r, Math.min(g, b))
  val max = Math.max(r, Math.max(g, b))

  val h = when {
    max - min < 0.5 / 255.0 -> 0.0
    r > g && r > b -> (60.0 * (g - b) / (max - min) + 360.0) % 360
    g > r && g > b -> 60.0 * (b - r) / (max - min) + 120.0
    else -> 60.0 * (r - g) / (max - min) + 240.0
  }

  val l = (max + min) / 2.0

  val s = when {
    max - min < 0.5 / 255.0 -> 0.0
    l <= 0.5 -> (max - min) / (max + min)
    else -> (max - min) / (2.0 - max - min)
  }

  return doubleArrayOf(h, s * 100.0, l * 100.0)
}

fun hslToRgb(hNotNormalized: Double, sNotNormalized: Double, lNotNormalized: Double): RGB {
  val h = (if (hNotNormalized > 360) hNotNormalized - 360.0 else if (hNotNormalized < 0) hNotNormalized + 360.0 else hNotNormalized) / 360.0
  val s = if (sNotNormalized < 0) 0.0 else if (sNotNormalized > 100) 1.0 else sNotNormalized / 100.0
  val l = if (lNotNormalized < 0) 0.0 else if (lNotNormalized > 100) 1.0 else lNotNormalized / 100.0

  val q = if (l < 0.5) l * (1 + s) else l + s - s * l
  val p = 2 * l - q
  return RGB(hueToRgb(p, q, h + 1.0 / 3.0).toFloat(), hueToRgb(p, q, h).toFloat(), hueToRgb(p, q, h - 1.0 / 3.0).toFloat())
}

private fun hueToRgb(p: Double, q: Double, hNotNormalized: Double): Double {
  val h = if (hNotNormalized < 0) hNotNormalized + 1.0 else if (hNotNormalized > 1.0) hNotNormalized - 1.0 else hNotNormalized

  if (6 * h < 1) {
    return p + (q - p) * 6f * h
  }

  if (2 * h < 1) {
    return q
  }

  return if (3 * h < 2) {
    p + (q - p) * 6f * (2.0f / 3.0f - h)
  } else p
}
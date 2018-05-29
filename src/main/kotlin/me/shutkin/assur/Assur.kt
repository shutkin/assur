package me.shutkin.assur

import me.shutkin.assur.filters.*
import me.shutkin.assur.logger.log
import java.awt.image.BufferedImage
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Source image file must be given as argument")
    return
  }

  val fileToProcess = args[0]
  val original = readHDRRaster(FileInputStream(fileToProcess))

  val startTime = System.currentTimeMillis()
  var raster = reduceSizeFilter(original, 1920, true)
  //raster = hueFilter(raster)
  raster = saturationFilter(raster)
  //raster = fftFilter(raster)
  raster = detailsFilter(raster)
  raster = luminanceFilter(raster)
  raster = cutoffFilter(raster)
  val duration = System.currentTimeMillis() - startTime
  log("duration $duration")

  val outputStream = FileOutputStream(fileToProcess.substring(0, fileToProcess.lastIndexOf('.')) + "_result.png")
  saveHDRRaster(raster, outputStream)
}

data class RGB(val r: Float, val g: Float, val b: Float) {
  fun min() = if (r < g && r < b) r else if (g < r && g < b) g else b
  fun max() = if (r > g && r > b) r else if (g > r && g > b) g else b
  fun luminance() = rgbGetLuminance(r.toDouble(), g.toDouble(), b.toDouble())
  fun hsl() = rgbConvertToHSL(r.toDouble(), g.toDouble(), b.toDouble())
  fun multiply(factor: Double) = RGB((factor * r).toFloat(), (factor * g).toFloat(), (factor * b).toFloat())
}

fun rgbGetLuminance(r: Double, g: Double, b: Double) = Math.sqrt(0.299 * r * r + 0.587 * g * g + 0.114 * b * b)

fun rgbConvertToHSL(r: Double, g: Double, b: Double): DoubleArray {
  val rgb = doubleArrayOf(r / 255.0 + 0.001, g / 255.0, b / 255.0 - 0.001)
  val cMax = rgb.max()!!
  val cMin = rgb.min()!!
  val delta = cMax - cMin

  val h = when {
    delta < 2.0 * Double.MIN_VALUE -> 0.0
    rgb[0] > rgb[1] && rgb[0] > rgb[2] -> 60.0 * (rgb[1] - rgb[2]) / delta
    rgb[1] > rgb[0] && rgb[1] > rgb[2] -> 60.0 * ((rgb[2] - rgb[0]) / delta + 2)
    else -> 60.0 * ((rgb[0] - rgb[1]) / delta + 4)
  }
  val l = (cMax + cMin) / 2.0
  val s = when {
    delta < 2.0 * Double.MIN_VALUE -> 0.0
    else -> delta / (1.0 - Math.abs(2.0 * l - 1.0))
  }

  return doubleArrayOf(
          when { h < 0 -> h + 360.0; h.toInt() >= 360 -> h - 360.0; else -> h
          },
          when { s < 0 -> 0.0; s > 1.0 -> 1.0; else -> s
          },
          when { l < 0 -> 0.0; l > 1.0 -> 1.0; else -> l
          }
  )
}

fun hslConvertToRGB(h: Double, s: Double, l: Double): DoubleArray {
  val c = (1.0 - Math.abs(2.0 * l - 1.0)) * s
  val x = c * (1.0 - Math.abs((h / 60) % 2 - 1.0))
  val m = l - c / 2.0
  val rgb = when (h.roundToInt()) {
    in (0 until 60) -> doubleArrayOf(c, x, 0.0)
    in (60 until 120) -> doubleArrayOf(x, c, 0.0)
    in (120 until 180) -> doubleArrayOf(0.0, c, x)
    in (180 until 240) -> doubleArrayOf(0.0, x, c)
    in (240 until 300) -> doubleArrayOf(x, 0.0, c)
    else -> doubleArrayOf(c, 0.0, x)
  }
  return doubleArrayOf(255.0 * (rgb[0] + m), 255.0 * (rgb[1] + m), 255.0 * (rgb[2] + m))
}

fun rgbChannelToInt(c: Float, min: Double, factor: Double): Int {
  val result = Math.round(factor * (c - min)).toInt()
  return when {
    result < 0 -> 0
    result > 255 -> 255
    else -> result
  }
}

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
  val minValue = raster.data.map { rgb -> rgb.min().toDouble() }.min()!!
  val maxValue = raster.data.map { rgb -> rgb.max().toDouble() }.max()!!
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

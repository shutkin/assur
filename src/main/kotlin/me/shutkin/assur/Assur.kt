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
  val imageName = fileToProcess.substring(0, fileToProcess.lastIndexOf('.'))
  val original = readHDRRaster(FileInputStream(fileToProcess))

  val startTime = System.currentTimeMillis()
  if (args.size >= 4) {
    val diapasons = Array(3, { Diapason.valueOf(args[it + 1].toUpperCase()) })
    log("selected diapasons ${diapasons.joinToString()}")

    var raster = reduceSizeFilter(original, 1920, true)
    raster = detailsFilter(raster, diapasons[0]).raster
    raster = saturationFilter(raster, diapasons[1]).raster
    raster = luminanceFilter(raster, diapasons[2]).raster
    raster = cutoffFilter(raster)
    log("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, FileOutputStream(imageName + "_result.png"))
  } else {
    val variants = generateVariants(original)
    log("duration ${System.currentTimeMillis() - startTime}")
    variants.forEachIndexed { index, variant -> saveHDRRaster(variant.raster, FileOutputStream("${index}_${imageName}_${variant.diapasons.joinToString("_")}.png")) }
  }
}

private data class VariantData(val raster: HDRRaster,
                               val detailsError: Double?, val saturationError: Double?, val lumError: Double?,
                               val detailsMedian: Double?, val saturationMedian: Double?, val lumMedian: Double?,
                               val diapasons: List<Diapason>) {
  fun cutOff() = VariantData(cutoffFilter(raster), detailsError, saturationError, lumError, detailsMedian, saturationMedian, lumMedian, diapasons)
  val error: Double get() = (detailsError ?: 0.0) + (saturationError ?: 0.0) + (lumError ?: 0.0)
  fun isCloseTo(v1: VariantData): Boolean {
    if (detailsMedian != null && v1.detailsMedian != null && Math.abs(detailsMedian - v1.detailsMedian) > 0.04)
      return false
    if (saturationMedian != null && v1.saturationMedian != null && Math.abs(saturationMedian - v1.saturationMedian) > 0.03)
      return false
    if (lumMedian != null && v1.lumMedian != null && Math.abs(lumMedian - v1.lumMedian) > 0.12)
      return false
    return true
  }
}

private fun generateVariants(source: HDRRaster): Array<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val errors = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  val variants = filterVariants(generateSaturationVariants(generateDetailsVariants(source)).flatMap { variant ->
    List(3, {
      val diapason = getDiapason(it)
      val result = luminanceFilter(variant.raster, diapason, splines[diapason])
      if (result.error != null)
        errors[diapason] = result.error
      if (result.median != null)
        medians[diapason] = result.median
      splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsError, variant.saturationError, errors[diapason],
              variant.detailsMedian, variant.saturationMedian, medians[diapason], variant.diapasons + diapason)
    })
  }).toTypedArray()
  variants.indices.forEach { log("errors: ${variants[it].detailsError} ${variants[it].saturationError} ${variants[it].lumError}"); variants[it] = variants[it].cutOff() }
  variants.sortBy { it.error }
  return variants
}

private fun generateDetailsVariants(source: HDRRaster): List<VariantData> {
  val reduced = reduceSizeFilter(source, 800, true)
  return filterVariants(List(3, {
    val diapason = getDiapason(it)
    val result = detailsFilter(reduced, diapason)
    VariantData(result.raster, result.error, null, null, result.median, null, null, listOf(diapason))
  }))
}

private fun generateSaturationVariants(detailed: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val errors = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(detailed.flatMap { variant ->
    log("detailed index ${detailed.indexOf(variant)}")
    List(3, {
      val diapason = getDiapason(it)
      val result = saturationFilter(variant.raster, diapason, splines[diapason])
      if (result.error != null)
        errors[diapason] = result.error
      if (result.median != null)
        medians[diapason] = result.median
      splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsError, errors[diapason], null,
              variant.detailsMedian, medians[diapason], null, variant.diapasons + diapason)
    })
  })
}

private fun filterVariants(variants: List<VariantData>): List<VariantData> {
  val result = variants.map { v0 ->
    val closeVariants = variants.filter { v1 -> v0.isCloseTo(v1) }.sortedBy { it.error }
    val bestVariant = closeVariants[0]
    if (closeVariants.size > 1)
      log("filtered ${closeVariants.joinToString { it.diapasons.joinToString("_") }} -> ${bestVariant.diapasons.joinToString("_")}")
    bestVariant
  }.distinct()
  log("${variants.size} -> ${result.size}")
  return result
}

private fun getDiapason(index: Int) = when (index) { 0 -> Diapason.LOW; 1 -> Diapason.MID; else -> Diapason.HIGH }

enum class Diapason {
  ALL, LOW, MID, HIGH;

  fun getStartIndex(size: Int) = when (this) { ALL, LOW -> 0; MID -> (size * 0.33).toInt(); HIGH -> (size * 0.66).toInt() }
  fun getEndIndex(size: Int) = when (this) { LOW -> (size * 0.33).toInt(); MID -> (size * 0.66).toInt(); ALL, HIGH -> size }
}

data class RGB(val r: Float, val g: Float, val b: Float) {
  val min: Float get() = if (r < g && r < b) r else if (g < r && g < b) g else b
  val max: Float get() = if (r > g && r > b) r else if (g > r && g > b) g else b
  val luminance: Double get() = rgbGetLuminance(r.toDouble(), g.toDouble(), b.toDouble())
  val hsl: DoubleArray get() = rgbConvertToHSL(r.toDouble(), g.toDouble(), b.toDouble())

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

data class FilterResult(val raster: HDRRaster, val spline: CubicSpline, val error: Double? = null, val median: Double? = null)
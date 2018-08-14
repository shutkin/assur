package me.shutkin.assur

import me.shutkin.assur.filters.*
import me.shutkin.assur.logger.assurLog
import java.io.FileInputStream
import java.io.FileOutputStream

class Assur {
}

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Source image file must be given as argument")
    return
  }

  val fileToProcess = args[0]
  val imageName = fileToProcess.substring(0, fileToProcess.lastIndexOf('.'))
  val original = readHDRRaster(FileInputStream(fileToProcess))

  val startTime = System.currentTimeMillis()
  if (args.size >= 5) {
    val diapasons = Array(4) { Diapason.valueOf(args[it + 1].toUpperCase()) }
    assurLog("selected diapasons ${diapasons.joinToString()}")

    var raster = reduceSizeFilter(original, 1920, true)
    raster = detailsFilter(raster, diapasons[0]).raster
    raster = zonalFilter(raster, diapasons[1]).raster
    raster = saturationFilter(raster, diapasons[2]).raster
    raster = luminanceFilter(raster, diapasons[3]).raster
    raster = cutoffFilter(raster)
    assurLog("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, FileOutputStream(imageName + "_result.png"))
  } else {
    val variants = generateVariants(original)
    assurLog("duration ${System.currentTimeMillis() - startTime}")
    variants.forEachIndexed { index, variant -> saveHDRRaster(variant.raster, FileOutputStream("${index}_${imageName}_${variant.diapasons.joinToString("_")}.png")) }
  }
}

private data class VariantData(val raster: HDRRaster,
                               val detailsError: Double?, val zonalError: Double?, val saturationError: Double?, val lumError: Double?,
                               val detailsMedian: Double?, val zonalMedian: Double?, val saturationMedian: Double?, val lumMedian: Double?,
                               val diapasons: List<Diapason>) {
  fun cutOff() = VariantData(cutoffFilter(raster), detailsError, zonalError, saturationError, lumError, detailsMedian,
          zonalMedian, saturationMedian, lumMedian, diapasons)
  val error: Double get() = (detailsError ?: 0.0) + (zonalError ?: 0.0) + (saturationError ?: 0.0) + (lumError ?: 0.0)
  fun isCloseTo(v1: VariantData): Boolean {
    if (detailsMedian != null && v1.detailsMedian != null && Math.abs(detailsMedian - v1.detailsMedian) > 0.001)
      return false
    if (zonalMedian != null && v1.zonalMedian != null && Math.abs(zonalMedian - v1.zonalMedian) > 0.035)
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
  val variants = filterVariants(generateSaturationVariants(generateZonalVariants(generateDetailsVariants(source))).flatMap { variant ->
    List(3) {
      val diapason = getDiapason(it)
      val result = luminanceFilter(variant.raster, diapason, splines[diapason])
      if (result.error != null)
        errors[diapason] = result.error
      if (result.median != null)
        medians[diapason] = result.median
      splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsError, variant.zonalError, variant.saturationError, errors[diapason],
              variant.detailsMedian, variant.zonalMedian, variant.saturationMedian, medians[diapason], variant.diapasons + diapason)
    }
  }).toTypedArray()
  variants.indices.forEach { assurLog("errors: ${variants[it].detailsError} ${variants[it].saturationError} ${variants[it].lumError}"); variants[it] = variants[it].cutOff() }
  return variants
}

private fun generateDetailsVariants(source: HDRRaster): List<VariantData> {
  val reduced = reduceSizeFilter(source, 800, true)
  return filterVariants(List(3) {
    val diapason = getDiapason(it)
    val result = detailsFilter(reduced, diapason)
    VariantData(result.raster, result.error, null, null, null, result.median, null, null, null, listOf(diapason))
  })
}

private fun generateZonalVariants(detailed: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val errors = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(detailed.flatMap { variant ->
    assurLog("zonal index ${detailed.indexOf(variant)}")
    List(3) {
      val diapason = getDiapason(it)
      val result = zonalFilter(variant.raster, diapason, splines[diapason])
      if (result.error != null)
        errors[diapason] = result.error
      if (result.median != null)
        medians[diapason] = result.median
      splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsError, errors[diapason], null, null,
              variant.detailsMedian, medians[diapason], null, null, variant.diapasons + diapason)
    }
  })
}

private fun generateSaturationVariants(zoned: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val errors = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(zoned.flatMap { variant ->
    assurLog("detailed index ${zoned.indexOf(variant)}")
    List(3) {
      val diapason = getDiapason(it)
      val result = saturationFilter(variant.raster, diapason, splines[diapason])
      if (result.error != null)
        errors[diapason] = result.error
      if (result.median != null)
        medians[diapason] = result.median
      splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsError, variant.zonalError, errors[diapason], null,
              variant.detailsMedian, variant.zonalMedian, medians[diapason], null, variant.diapasons + diapason)
    }
  })
}

private fun filterVariants(variants: List<VariantData>): List<VariantData> {
  val result = variants.map { v0 ->
    val closeVariants = variants.filter { v1 -> v0.isCloseTo(v1) }.sortedBy { it.error }
    val bestVariant = closeVariants[0]
    if (closeVariants.size > 1)
      assurLog("filtered ${closeVariants.joinToString { it.diapasons.joinToString("_") }} -> ${bestVariant.diapasons.joinToString("_")}")
    bestVariant
  }.distinct().sortedBy { it.error }
  val resultLimit = if (result.size <= 16) result else result.subList(0, 16)
  assurLog("${variants.size} -> ${resultLimit.size}")
  return resultLimit
}

private fun getDiapason(index: Int) = when (index) { 0 -> Diapason.LOW; 1 -> Diapason.MID; else -> Diapason.HIGH }

enum class Diapason {
  ALL, LOW, MID, HIGH;

  fun getStartIndex(size: Int) = when (this) { ALL, LOW -> 0; MID -> (size * 0.33).toInt(); HIGH -> (size * 0.66).toInt() }
  fun getEndIndex(size: Int) = when (this) { LOW -> (size * 0.33).toInt(); MID -> (size * 0.66).toInt(); ALL, HIGH -> size }
}

data class FilterResult(val raster: HDRRaster, val spline: CubicSpline, val error: Double? = null, val median: Double? = null)
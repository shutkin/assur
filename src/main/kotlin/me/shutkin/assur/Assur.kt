package me.shutkin.assur

import me.shutkin.assur.filters.*
import me.shutkin.assur.logger.assurLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class Assur(private val context: AssurContext) {
  fun variants(imageStream: InputStream): List<AssurVariantMetadata> {
    val startTime = System.currentTimeMillis()

    val source = readHDRRaster(imageStream)
    val variants = generateVariants(context, source)

    context.log("duration ${System.currentTimeMillis() - startTime}")
    return variants.mapIndexed { index, variant ->
      val filename = "${index}_${variant.diapasons.joinToString("_")}.${context.imageFormat}"
      saveHDRRaster(variant.raster, File(context.path, filename).outputStream(), context.imageFormat)
      AssurVariantMetadata(filename, variant.diapasons.map { it.name })
    }
  }

  fun process(imageStream: InputStream, params: List<String>) {
    val startTime = System.currentTimeMillis()

    val diapasons = params.map { Diapason.valueOf(it.toUpperCase()) }
    context.log("selected diapasons ${diapasons.joinToString()}")
    var raster = readHDRRaster(imageStream)
    raster = reduceSizeFilter(context, raster, 1920, true)
    raster = detailsFilter(context, raster, diapasons[0]).raster
    raster = zonalFilter(context, raster, diapasons[1]).raster
    raster = saturationFilter(context, raster, diapasons[2]).raster
    raster = luminanceFilter(context, raster, diapasons[3]).raster
    raster = cutoffFilter(context, raster)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, File(context.path, "result.${context.imageFormat}").outputStream(), context.imageFormat)
  }
}

data class AssurContext(val log: (String) -> Unit, val path: String, val detailsFilterEnabled: Boolean = true, val imageFormat: String)
data class AssurVariantMetadata(val filename: String, val params: List<String>)

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Source image file must be given as argument")
    return
  }

  val context = AssurContext(log = ::assurLog, path = ".", imageFormat = "png", detailsFilterEnabled = true)

  val fileToProcess = args[0]
  val imageName = fileToProcess.substring(0, fileToProcess.lastIndexOf('.'))
  val original = readHDRRaster(FileInputStream(fileToProcess))

  val startTime = System.currentTimeMillis()
  if (args.size >= 5) {
    val diapasons = Array(4) { Diapason.valueOf(args[it + 1].toUpperCase()) }
    context.log("selected diapasons ${diapasons.joinToString()}")

    var raster = reduceSizeFilter(context, original, 1920, true)
    raster = detailsFilter(context, raster, diapasons[0]).raster
    raster = zonalFilter(context, raster, diapasons[1]).raster
    raster = saturationFilter(context, raster, diapasons[2]).raster
    raster = luminanceFilter(context, raster, diapasons[3]).raster
    raster = cutoffFilter(context, raster)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, FileOutputStream(imageName + "_result.png"))
  } else {
    val variants = generateVariants(context, original)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    variants.forEachIndexed { index, variant -> saveHDRRaster(variant.raster, FileOutputStream("${index}_${imageName}_${variant.diapasons.joinToString("_")}.png")) }
  }
}

private data class VariantFilterInfo(val filter: FilterType, val popularity: Double, val median: Double, val diapason: Diapason, val refIndex: Int)

private data class VariantData(val raster: HDRRaster, val filtersInfo: Map<FilterType, VariantFilterInfo>) {
  fun cutOff(context: AssurContext) = VariantData(cutoffFilter(context, raster), filtersInfo)
  val popularity: Double get() = filtersInfo.values.map { it.popularity }.sum()

  fun isCloseTo(v1: VariantData): Boolean {
    FilterType.values().forEach {
      val diff = Math.abs((filtersInfo[it]?.median ?: 0.0) - (v1.filtersInfo[it]?.median ?: 0.0))
      when (it) {
        FilterType.DETAILS -> if (diff > detailsReferences.getMedianQuantum())
          return false
        FilterType.ZONAL -> if (diff > zonesReferences.getMedianQuantum())
          return false
        FilterType.SATURATION -> if (diff > saturationReferences.getMedianQuantum())
          return false
        FilterType.LUMINANCE -> if (diff > luminanceReferences.getMedianQuantum())
          return false
      }
    }
    return true
  }
}

private fun generateVariants(context: AssurContext, source: HDRRaster): List<VariantData> {
  context.log("median quantum: details ${detailsReferences.getMedianQuantum()}, zones ${zonesReferences.getMedianQuantum()}, " +
          "saturation ${saturationReferences.getMedianQuantum()}, luminance ${luminanceReferences.getMedianQuantum()}")
  val reduced = reduceSizeFilter(context, source, 720, true)
  var variants = List(1) { VariantData(reduced, null, null, null, null,
          null, null, null, null, emptyList()) }
  if (context.detailsFilterEnabled)
    variants = generateDetailsVariants(context, variants)
  variants = generateZonalVariants(context, variants)
  variants = generateSaturationVariants(context, variants)
  variants = generateLuminanceVariants(context, variants)
  variants = variants.map { it.cutOff(context) }
  variants.forEach { context.log(it.toString()) }
  return variants
}

private fun generateDetailsVariants(context: AssurContext, variants: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val popularity = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(context, variants.flatMap { variant ->
    context.log("zonal index ${variants.indexOf(variant)}")
    List(3) {
      val diapason = getDiapason(it)
      val result = detailsFilter(context, variant.raster, diapason, splines[diapason])
      if (result.popularity != null)
        popularity[diapason] = result.popularity
      if (result.median != null)
        medians[diapason] = result.median
      //splines[diapason] = result.spline
      VariantData(result.raster, popularity[diapason], null, null, null,
              medians[diapason], null, null, null, variant.diapasons + diapason)
    }
  })
}

private fun generateZonalVariants(context: AssurContext, variants: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val popularity = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(context, variants.flatMap { variant ->
    List(3) {
      val diapason = getDiapason(it)
      val result = zonalFilter(context, variant.raster, diapason, splines[diapason])
      if (result.popularity != null)
        popularity[diapason] = result.popularity
      if (result.median != null)
        medians[diapason] = result.median
      //splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsPopularity, popularity[diapason], null, null,
              variant.detailsMedian, medians[diapason], null, null, variant.diapasons + diapason)
    }
  })
}

private fun generateSaturationVariants(context: AssurContext, zoned: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val popularity = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(context, zoned.flatMap { variant ->
    List(3) {
      val diapason = getDiapason(it)
      val result = saturationFilter(context, variant.raster, diapason, splines[diapason])
      if (result.popularity != null)
        popularity[diapason] = result.popularity
      if (result.median != null)
        medians[diapason] = result.median
      //splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsPopularity, variant.zonalPopularity, popularity[diapason], null,
              variant.detailsMedian, variant.zonalMedian, medians[diapason], null, variant.diapasons + diapason)
    }
  })
}

private fun generateLuminanceVariants(context: AssurContext, variants: List<VariantData>): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val popularity = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  return filterVariants(context, variants.flatMap { variant ->
    context.log("index ${variants.indexOf(variant)}")
    List(3) {
      val diapason = getDiapason(it)
      val result = luminanceFilter(context, variant.raster, diapason, splines[diapason])
      if (result.popularity != null)
        popularity[diapason] = result.popularity
      if (result.median != null)
        medians[diapason] = result.median
      //splines[diapason] = result.spline
      VariantData(result.raster, variant.detailsPopularity, variant.zonalPopularity, variant.saturationPopularity, popularity[diapason],
              variant.detailsMedian, variant.zonalMedian, variant.saturationMedian, medians[diapason], variant.diapasons + diapason)
    }
  })
}

private fun filterVariants(context: AssurContext, variants: List<VariantData>): List<VariantData> {
  val result = variants.map { v0 ->
    val closeVariants = variants.filter { v1 -> v0.isCloseTo(v1) }.sortedByDescending { it.popularity }
    val bestVariant = closeVariants[0]
    if (closeVariants.size > 1)
      context.log("filtered ${closeVariants.joinToString { it.diapasons.joinToString("_") }} -> ${bestVariant.diapasons.joinToString("_")}")
    bestVariant
  }.distinct().sortedByDescending { it.popularity }
  val resultLimit = if (result.size <= 16) result else result.subList(0, 16)
  context.log("${variants.size} -> ${resultLimit.size}")
  return resultLimit
}

private fun getDiapason(index: Int) = when (index) { 0 -> Diapason.LOW; 1 -> Diapason.MID; else -> Diapason.HIGH }

enum class Diapason {
  ALL, LOW, MID, HIGH;

  fun getStartIndex(size: Int) = when (this) { ALL, LOW -> 0; MID -> (size * 0.33).toInt(); HIGH -> (size * 0.66).toInt() }
  fun getEndIndex(size: Int) = when (this) { LOW -> (size * 0.33).toInt(); MID -> (size * 0.66).toInt(); ALL, HIGH -> size }
}

enum class FilterType {
  DETAILS, ZONAL, SATURATION, LUMINANCE
}

data class FilterResult(val raster: HDRRaster, val spline: CubicSpline, val selectedReference: Int? = null,
                        val popularity: Double? = null, val median: Double? = null)
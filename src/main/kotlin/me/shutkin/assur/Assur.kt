package me.shutkin.assur

import me.shutkin.assur.filters.*
import me.shutkin.assur.logger.assurLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

val appProperties = loadProperties()

private fun loadProperties(): Properties {
  val resourceStream = object {}.javaClass.getResourceAsStream("/app.properties")
  val properties = Properties()
  properties.load(resourceStream)
  return properties
}

class Assur(private val context: AssurContext) {
  fun variants(imageStream: InputStream): List<AssurVariantMetadata> {
    context.log("app version ${appProperties["app.version"]}")

    val startTime = System.currentTimeMillis()
    val source = readHDRRaster(imageStream)
    val variants = generateVariants(context, source)
    context.log("duration ${System.currentTimeMillis() - startTime}")

    return variants.mapIndexed { index, variant ->
      val filename = "$index.${context.imageFormat}"
      saveHDRRaster(variant.raster, File(context.path, filename).outputStream(), context.imageFormat)
      AssurVariantMetadata(filename, variant.filtersInfo.entries.associate{ Pair(it.key.name, it.value.refIndex.toString()) },
              variant.filtersInfo.entries.associate { Pair(it.key.name, it.value.spline) })
    }
  }

  fun process(imageStream: InputStream, splines: Map<String, CubicSpline>) {
    context.log("app version ${appProperties["app.version"]}")

    val startTime = System.currentTimeMillis()
    var raster = readHDRRaster(imageStream)
    raster = reduceSizeFilter(context, raster, 1920, true)
    raster = zonalFilter(context, raster, predefinedSpline = splines[FilterType.ZONAL.name]).raster
    raster = detailsFilter(context, raster, predefinedSpline = splines[FilterType.DETAILS.name]).raster
    raster = saturationFilter(context, raster, predefinedSpline = splines[FilterType.SATURATION.name]).raster
    raster = luminanceFilter(context, raster, predefinedSpline = splines[FilterType.LUMINANCE.name]).raster
    raster = cutoffFilter(context, raster)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, File(context.path, "result.${context.imageFormat}").outputStream(), context.imageFormat)
  }
}

data class AssurContext(val log: (String) -> Unit, val path: String, val detailsFilterEnabled: Boolean = true, val imageFormat: String)
data class AssurVariantMetadata(val filename: String, val params: Map<String, String>, val splines: Map<String, CubicSpline>)

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Source image file must be given as argument")
    return
  }

  val context = AssurContext(log = ::assurLog, path = ".", imageFormat = "png", detailsFilterEnabled = true)
  context.log("v${appProperties["app.version"]}")

  val fileToProcess = args[0]
  context.log("process file $fileToProcess")

  val imageName = fileToProcess.substring(0, fileToProcess.lastIndexOf('.'))
  val original = readHDRRaster(FileInputStream(fileToProcess))

  val startTime = System.currentTimeMillis()
  if (args.size >= 5) {
    val diapasons = Array(4) { Diapason.valueOf(args[it + 1].toUpperCase()) }
    context.log("selected diapasons ${diapasons.joinToString()}")

    var raster = reduceSizeFilter(context, original, 1920, true)
    raster = zonalFilter(context, raster, diapasons[0]).raster
    raster = detailsFilter(context, raster, diapasons[1]).raster
    raster = saturationFilter(context, raster, diapasons[2]).raster
    raster = luminanceFilter(context, raster, diapasons[3]).raster
    raster = cutoffFilter(context, raster)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    saveHDRRaster(raster, FileOutputStream(imageName + "_result.png"))
  } else {
    val variants = generateVariants(context, original)
    context.log("duration ${System.currentTimeMillis() - startTime}")
    variants.forEachIndexed { index, variant ->
      saveHDRRaster(variant.raster, FileOutputStream("${index}_${imageName}_${variant.printDiapasons()}.png")) }
  }
}

private data class VariantFilterInfo(val filter: FilterType, val popularity: Double, val median: Double, val correctness: Double,
                                     val diapason: Diapason, val refIndex: Int, val spline: CubicSpline)

private data class VariantData(val raster: HDRRaster, val filtersInfo: Map<FilterType, VariantFilterInfo>) {
  fun cutOff(context: AssurContext) = VariantData(cutoffFilter(context, raster), filtersInfo)
  val popularity: Double get() = filtersInfo.values.map { it.popularity }.sum()
  val correctness: Double get() = filtersInfo.values.map { it.correctness }.sum()
  val rank: Double get() = popularity * correctness

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

  fun calcDiff(v1: VariantData) =
    FilterType.values().filter {
      val filter0 = filtersInfo[it]
      val filter1 = v1.filtersInfo[it]
      filter0 != null && filter1 != null && filter0.diapason != filter1.diapason
    }.count()

  fun printDiapasons() = filtersInfo.values.joinToString(separator = ".") { "${it.filter}_${it.diapason}" }

  fun printRefIndices() = filtersInfo.values.joinToString(separator = "_") { "${it.filter}.${it.refIndex}" }
}

private fun generateVariants(context: AssurContext, source: HDRRaster): List<VariantData> {
  context.log("median quantum: details ${detailsReferences.getMedianQuantum()}, zones ${zonesReferences.getMedianQuantum()}, " +
          "saturation ${saturationReferences.getMedianQuantum()}, luminance ${luminanceReferences.getMedianQuantum()}")
  val reduced = reduceSizeFilter(context, source, 720, true)
  var variants = List(1) { VariantData(reduced, emptyMap()) }
  if (context.detailsFilterEnabled)
    variants = generateFilterVariants(context, variants, FilterType.ZONAL)
  variants = generateFilterVariants(context, variants, FilterType.DETAILS)
  variants = generateFilterVariants(context, variants, FilterType.SATURATION)
  variants = generateFilterVariants(context, variants, FilterType.LUMINANCE)
  variants = variants.map { it.cutOff(context) }
  variants.forEach { context.log(it.toString()) }
  return variants
}

private fun generateFilterVariants(context: AssurContext, variants: List<VariantData>, filter: FilterType): List<VariantData> {
  val splines = HashMap<Diapason, CubicSpline>()
  val popularity = HashMap<Diapason, Double>()
  val medians = HashMap<Diapason, Double>()
  val correctness = HashMap<Diapason, Double>()
  val refIndices = HashMap<Diapason, Int>()
  return filterVariants(context, variants.flatMap { variant ->
    context.log(variant.printDiapasons())
    List(3) {
      val diapason = getDiapason(it)
      val result = when (filter) {
        FilterType.DETAILS -> detailsFilter(context, variant.raster, diapason, splines[diapason])
        FilterType.ZONAL -> zonalFilter(context, variant.raster, diapason, splines[diapason])
        FilterType.SATURATION -> saturationFilter(context, variant.raster, diapason, splines[diapason])
        FilterType.LUMINANCE -> luminanceFilter(context, variant.raster, diapason, splines[diapason])
      }
      splines[diapason] = result.spline
      if (result.popularity != null)
        popularity[diapason] = result.popularity
      if (result.median != null)
        medians[diapason] = result.median
      if (result.correctness != null)
        correctness[diapason] = result.correctness
      if (result.selectedReference != null)
        refIndices[diapason] = result.selectedReference
      val filtersInfo = variant.filtersInfo.toMutableMap()
      filtersInfo[filter] = VariantFilterInfo(filter, popularity[diapason]!!, medians[diapason]!!, correctness[diapason]!!,
              diapason, refIndices[diapason]!!, result.spline)
      VariantData(result.raster, filtersInfo)
    }
  })
}

private fun filterVariants(context: AssurContext, variants: List<VariantData>): List<VariantData> {
  val notCloseVariants = variants.map { v0 ->
    val closeVariants = variants.filter { v1 -> v0.isCloseTo(v1) }.sortedByDescending { it.rank }
    val bestVariant = closeVariants[0]
    bestVariant
  }.distinct()

  val sortedVariants = ArrayList<VariantData>()
  val unsortedVariants = LinkedList<VariantData>()
  unsortedVariants.addAll(notCloseVariants)
  while (unsortedVariants.isNotEmpty()) {
    val diffRanks = unsortedVariants.map { unsorted ->
      sortedVariants.map { sorted ->
        unsorted.calcDiff(sorted)
      }.sum()
    }
    val maxDiff = diffRanks.max()
    val maxDiffVariants = unsortedVariants.filterIndexed { index, _ ->
      diffRanks[index] == maxDiff
    }.sortedByDescending { it.rank }
    val bestVariant = maxDiffVariants[0]
    sortedVariants.add(bestVariant)
    unsortedVariants.remove(bestVariant)
  }

  val resultLimit = if (sortedVariants.size <= 12) sortedVariants else sortedVariants.subList(0, 12)
  context.log("${variants.size} -> ${resultLimit.size}")
  resultLimit.forEach { context.log("${it.printDiapasons()} ${it.popularity} ${it.correctness}") }
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
                        val popularity: Double? = null, val median: Double? = null, val correctness: Double? = null)
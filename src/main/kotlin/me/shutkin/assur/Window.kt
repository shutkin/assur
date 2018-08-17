package me.shutkin.assur

import java.util.HashMap
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.set

interface Window {
  val radius: Int
  fun apply(source: HDRRaster): DoubleArray
}

private data class Offset(val x: Int, val y: Int)
private data class WeightData(val weight: Double, val offsets: IntArray) {
  override fun equals(other: Any?) = false
  override fun hashCode() = 0
}

class OptimizedWindow(override val radius: Int, precision: Int) : Window {
  private val windowWidth = 2 * radius + 1
  private val windowData: Array<WeightData>

  init {
    val notNormalizedWindow = DoubleArray(windowWidth * windowWidth) {
      val y: Int = it / windowWidth - radius
      val x: Int = it % windowWidth - radius
      if (x == 0 && y == 0) 0.0 else 1.0 / (x.toDouble() * x + y.toDouble() * y)
    }
    val sum: Double = notNormalizedWindow.sum()

    val weightsMap = HashMap<Long, ArrayList<Offset>>()
    notNormalizedWindow.forEachIndexed { index, v ->
      val fixedValue: Long = (precision.toDouble() * v / sum).toLong()
      if (fixedValue > 0) {
        if (!weightsMap.containsKey(fixedValue))
          weightsMap[fixedValue] = ArrayList()
        weightsMap[fixedValue]!!.add(Offset(index % windowWidth - radius, index / windowWidth - radius))
      }
    }

    val fixedValuesArray = weightsMap.keys.toList()
    windowData = Array(weightsMap.size) { index ->
      val fixedValue = fixedValuesArray[index]
      val offsetsList = weightsMap[fixedValue]!!
      WeightData(fixedValue.toDouble() / precision, IntArray(offsetsList.size * 2) {
        if (it % 2 == 0) offsetsList[it / 2].x else offsetsList[it / 2].y })
    }
  }

  override fun apply(source: HDRRaster): DoubleArray {
    val sums = DoubleArray(source.data.size)
    val weights = DoubleArray(source.data.size)
    source.data.forEachIndexed { index, pixel ->
      val x = index % source.width
      val y = index / source.width
      val luminance = pixel.luminance
      windowData.forEach {
        val w = it.weight
        val v = luminance * w
        for (i in 0 until it.offsets.size / 2) {
          val wx = x + it.offsets[i * 2]; val wy = y + it.offsets[i * 2 + 1]
          if (wx >= 0 && wx < source.width && wy >= 0 && wy < source.height) {
            val wi = wy * source.width + wx
            sums[wi] += v
            weights[wi] += w
          }
        }
      }
    }
    return DoubleArray(sums.size) { sums[it] / weights[it] }
  }
}

class StraightWindow(override val radius: Int) : Window {
  private val window: DoubleArray
  private val windowWidth = 2 * radius + 1

  init {
    val notNormalizedWindow = DoubleArray(windowWidth * windowWidth) {
      val y: Int = it / windowWidth - radius
      val x: Int = it % windowWidth - radius
      if (x == 0 && y == 0) 0.0 else 1.0 / (x.toDouble() * x + y.toDouble() * y)
    }
    val sum: Double = notNormalizedWindow.sum()
    window = DoubleArray(windowWidth * windowWidth) { i -> notNormalizedWindow[i] / sum }
  }

  override fun apply(source: HDRRaster): DoubleArray {
    return DoubleArray(source.data.size) {
      val centerX = it % source.width
      val centerY = it / source.width
      var (sumR, sumG, sumB) = doubleArrayOf(0.0, 0.0, 0.0)
      var weightsSum = 0.0
      for (y in centerY - radius..centerY + radius) {
        if (y < 0 || y >= source.height)
          continue
        val rowStartIndexRaster = y * source.width
        val rowStartIndexWindow = (y - centerY + radius) * windowWidth
        for (x in centerX - radius..centerX + radius) {
          if (x < 0 || x >= source.width)
            continue
          val pixel = source.data[rowStartIndexRaster + x]
          val weight = window[rowStartIndexWindow + x - centerX + radius]
          sumR += weight * pixel.r
          sumG += weight * pixel.g
          sumB += weight * pixel.b
          weightsSum += weight
        }
      }
      rgbGetLuminance(sumR / weightsSum, sumG / weightsSum, sumB / weightsSum)
    }
  }
}
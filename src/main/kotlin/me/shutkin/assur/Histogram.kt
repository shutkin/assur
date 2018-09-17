package me.shutkin.assur

import me.shutkin.assur.filters.MAX_SUB_ARRAYS
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class HistogramData(val minValue: Double, val maxValue: Double, val histogram: DoubleArray)

fun buildHistogram(minValue: Double, maxValue: Double, precision: Int, dataSize: Int, dataSource: (Int) -> Double): HistogramData {
  val counters = Array(precision) { AtomicInteger(0) }
  val blockSize = Math.ceil(dataSize.toDouble() / 16.0).toInt()
  val blocksNum = Math.ceil(dataSize.toDouble() / blockSize).toInt()
  (0 until blocksNum).map { blockIndex ->
    val blockStart = blockIndex * blockSize
    val blockEndUnbound = (blockIndex + 1) * blockSize
    val blockEnd = if (blockEndUnbound > dataSize) dataSize else blockEndUnbound
    (blockStart until blockEnd).forEach {
      val index = ((dataSource(it) - minValue) * (precision - 1) / (maxValue - minValue)).toInt()
      if (index in 0 until precision)
        counters[index].incrementAndGet()
    }
  }
  return HistogramData(minValue, maxValue, DoubleArray(precision) {
    counters[it].get().toDouble() / dataSize
  })
}

fun getHistogramLowValue(data: HistogramData, threshold: Double) =
        convertIndexToValue(data, data.histogram.indexOfFirst { v -> v > threshold })

fun getHistogramHighValue(data: HistogramData, threshold: Double) =
        convertIndexToValue(data, data.histogram.indexOfLast { v -> v > threshold })

fun getHistogramMedianValue(data: HistogramData, threshold: Double): Double {
  var sum = 0.0
  for (i in data.histogram.indices) {
    sum += data.histogram[i]
    if (sum > threshold)
      return convertIndexToValue(data, i)
  }
  return 0.5 * (data.minValue + data.maxValue)
}

private fun convertIndexToValue(data: HistogramData, index: Int) =
        if (index < 0) 0.0 else data.minValue + (data.maxValue - data.minValue) * index / (data.histogram.size - 1.0)

fun saveHistogram(histogram: DoubleArray, filename: String) =
        saveHDRRaster(HDRRaster(histogram.size, 1024) {
          val x = it % histogram.size
          val y = it / histogram.size
          val v = histogram[x] * 1024.0 * 16.0
          if (1024 - y < v) RGB(255f, 255f, 255f) else RGB(0f, 0f, 0f)
        }, FileOutputStream(filename))


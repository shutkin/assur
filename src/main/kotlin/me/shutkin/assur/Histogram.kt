package me.shutkin.assur

import java.io.FileOutputStream

class HistogramData(val minValue: Double, val maxValue: Double, val histogram: DoubleArray)

fun buildHistogram(minValue: Double, maxValue: Double, precision: Int, dataSize: Int, dataSource: (Int) -> Double): HistogramData {
  val data = HistogramData(minValue, maxValue, DoubleArray(precision))
  val step = 1.0 / dataSize
  (0 until dataSize).forEach {
    val index = ((dataSource(it) - data.minValue) * (data.histogram.size - 1) / (data.maxValue - data.minValue)).toInt()
    if (index >= 0 && index < data.histogram.size)
      data.histogram[index] += step
  }
  return data
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


package me.shutkin.assur

import me.shutkin.assur.logger.assurLog
import me.shutkin.assur.samples.evalArraysDiff

class SplineAdjuster (private val samples: Array<DoubleArray>, private val minValue: Double, private val maxValue: Double) {
  var filenamePrefix: String? = null
  var adjustPoints = 4
  var steps = 4
  var smallestError = Double.MAX_VALUE
  var bestMedian = 0.0

  fun findSpline(testFunction: (CubicSpline) -> DoubleArray, errorFunction: (DoubleArray, DoubleArray) -> Double = ::evalArraysDiff): CubicSpline {
    smallestError = Double.MAX_VALUE
    var selectedSampleIndex = 0
    var bestHistogram: DoubleArray? = null
    val range = (maxValue - minValue) / (2.0 * adjustPoints)
    val keyPoints = DoubleArray(adjustPoints + 2) { when(it) { 0 -> minValue; adjustPoints + 1 -> maxValue; else -> minValue + range * (1 + (it - 1) * 2) } }
    val bestPoints = DoubleArray(adjustPoints) { keyPoints[it + 1] }
    val nextBestPoints = DoubleArray(adjustPoints) { keyPoints[it + 1] }
    (1 .. 3).forEach { level ->
      assurLog("Spline points adjustment level $level")
      for (variant in 0 until Math.pow(steps.toDouble(), bestPoints.size.toDouble()).toInt()) {
        val points = getVariant(bestPoints, 0.45 * range / level, steps, variant)
        val splinePoints = keyPoints.sliceArray(0 until 1) + points + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size)
        val spline = CubicSpline(keyPoints, splinePoints)
        val testHistogram = testFunction(spline)
        val errors = samples.map { errorFunction(testHistogram, it) }
        val minError = errors.min() ?: Double.MAX_VALUE
        if (minError < smallestError) {
          points.indices.forEach { nextBestPoints[it] = points[it] }
          smallestError = minError
          selectedSampleIndex = errors.indexOf(minError)
          bestHistogram = testHistogram
        }
      }
      assurLog("smallest error $smallestError")
      bestPoints.indices.forEach { bestPoints[it] = nextBestPoints[it] }
    }
    bestMedian = getHistogramMedianValue(HistogramData(0.0, 1.0, bestHistogram!!), 0.5)
    assurLog("best sample $selectedSampleIndex, error $smallestError, median $bestMedian")
    if (bestHistogram != null && filenamePrefix != null) {
      saveHistogram(bestHistogram!!, filenamePrefix + "_histogram.png")
      saveHistogram(samples[selectedSampleIndex], filenamePrefix + "_histogram_sample.png")
    }
    val spline = CubicSpline(keyPoints, keyPoints.sliceArray(0 until 1) + bestPoints + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size))
    assurLog(spline.toString())
    return spline
  }
}

private fun getVariant(points: DoubleArray, range: Double, count: Int, variant: Int): DoubleArray {
  var t = variant
  return DoubleArray(points.size) {
    val pos = t % count
    t /= count
    points[it] - range + pos * 2.0 * range / (count - 1)
  }
}

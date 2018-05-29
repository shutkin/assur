package me.shutkin.assur

import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.evalArraysDiff

class SplineAdjuster (private val samples: Array<DoubleArray>, private val minValue: Double, private val maxValue: Double) {
  var filenamePrefix: String? = null
  var adjustPoints = 5
  var steps = 3
  var median = -1.0

  fun findSpline(testFunction: (CubicSpline) -> DoubleArray): CubicSpline {
    var smallestError = Double.MAX_VALUE
    var selectedSampleIndex = 0
    var bestHistogram: DoubleArray? = null
    val range = (maxValue - minValue) / (2.0 * adjustPoints)
    val keyPoints = DoubleArray(adjustPoints + 2, { when(it) { 0 -> minValue; adjustPoints + 1 -> maxValue; else -> minValue + range * (1 + (it - 1) * 2) } })
    val bestPoints = DoubleArray(adjustPoints, { keyPoints[it + 1] })
    val nextBestPoints = DoubleArray(adjustPoints, { keyPoints[it + 1] })
    (1 .. 4).forEach { level ->
      for (variant in 0 until Math.pow(steps.toDouble(), bestPoints.size.toDouble()).toInt()) {
        val points = getVariant(bestPoints, 0.45 * range / level, steps, variant)
        val splinePoints = keyPoints.sliceArray(0 until 1) + points + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size)
        //if ((0 until splinePoints.size - 1).any { splinePoints[it] > splinePoints[it + 1] })
        //  continue

        val spline = CubicSpline(keyPoints, splinePoints)
        val testHistogram = testFunction(spline)
        val errors = samples.map { evalArraysDiff(testHistogram, it) }
        val minError = errors.min()!!
        if (minError < smallestError) {
          points.indices.forEach { nextBestPoints[it] = points[it] }
          smallestError = minError
          selectedSampleIndex = errors.indexOf(minError)
          bestHistogram = testHistogram
        }
      }
      bestPoints.indices.forEach { bestPoints[it] = nextBestPoints[it] }
    }
    log("best sample $selectedSampleIndex")
    if (bestHistogram != null && filenamePrefix != null) {
      median = getHistogramMedianValue(HistogramData(0.0, 1.0, samples[selectedSampleIndex]), 0.5)
      log("median $median")
      saveHistogram(bestHistogram!!, filenamePrefix + "_histogram.png")
      saveHistogram(samples[selectedSampleIndex], filenamePrefix + "_histogram_sample.png")
    }

    return CubicSpline(keyPoints, keyPoints.sliceArray(0 until 1) + bestPoints + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size))
  }
}

private fun getVariant(points: DoubleArray, range: Double, count: Int, variant: Int): DoubleArray {
  var t = variant
  return DoubleArray(points.size, {
    val pos = t % count
    t /= count
    points[it] - range + pos * 2.0 * range / (count - 1)
  })
}

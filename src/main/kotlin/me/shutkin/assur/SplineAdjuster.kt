package me.shutkin.assur

import me.shutkin.assur.samples.Reference

class SplineAdjuster (private val references: List<Reference>, private val minValue: Double, private val maxValue: Double) {
  var filenamePrefix: String? = null
  var adjustPoints = 4
  var steps = 4
  var levels = 3
  var bestMedian = 0.0
  var bestCorrectness = 0.0
  var selectedRef: Reference? = null

  fun findSpline(context: AssurContext, diffFunction: (DoubleArray, DoubleArray) -> Double, testFunction: (CubicSpline) -> DoubleArray): CubicSpline {
    var bestData: DoubleArray? = null
    val range = (maxValue - minValue) / (2.0 * adjustPoints)
    val keyPoints = DoubleArray(adjustPoints + 2) {
      when(it) {
        0 -> minValue
        adjustPoints + 1 -> maxValue
        else -> minValue + range * (1 + (it - 1) * 2) }
    }
    val bestPoints = DoubleArray(adjustPoints) { keyPoints[it + 1] }
    val nextBestPoints = DoubleArray(adjustPoints) { keyPoints[it + 1] }
    for (level in 1 .. levels) {
      context.log("Spline points adjustment level $level")
      var bestLevelCorrectness = 0.0
      for (variant in 0 until Math.pow(steps.toDouble(), bestPoints.size.toDouble()).toInt()) {
        val points = getVariant(bestPoints, 0.45 * range / level, steps, variant)
        val splinePoints = keyPoints.sliceArray(0 until 1) + points + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size)
        val spline = CubicSpline(keyPoints, splinePoints)
        val testData = testFunction(spline)
        val correctness = references.map { it.averageError / diffFunction(testData, it.data) }
        val maxCorrectness = correctness.max() ?: 0.0
        if (maxCorrectness > bestLevelCorrectness) {
          points.indices.forEach { nextBestPoints[it] = points[it] }
          bestLevelCorrectness = maxCorrectness
          selectedRef = references[correctness.indexOf(maxCorrectness)]
          bestData = testData
        }
      }
      if (bestLevelCorrectness < bestCorrectness)
        break
      context.log("best level relative correctness $bestLevelCorrectness")
      bestPoints.indices.forEach { bestPoints[it] = nextBestPoints[it] }
      bestCorrectness = bestLevelCorrectness
    }
    bestMedian = getHistogramMedianValue(HistogramData(0.0, 1.0, bestData!!), 0.5)

    context.log("best sample ${selectedRef!!.id}, popularity ${selectedRef!!.popularity}, median $bestMedian")
    if (filenamePrefix != null) {
      saveHistogram(bestData, filenamePrefix + "_histogram.png")
      saveHistogram(selectedRef!!.data, filenamePrefix + "_histogram_ref.png")
    }
    val spline = CubicSpline(keyPoints, keyPoints.sliceArray(0 until 1) + bestPoints + keyPoints.sliceArray(keyPoints.size - 1 until keyPoints.size))
    context.log(spline.toString())
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

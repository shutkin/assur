package me.shutkin.assur.samples

import me.shutkin.assur.HistogramData
import me.shutkin.assur.getHistogramMedianValue
import me.shutkin.assur.logger.assurLog

fun grouping(samples: List<DoubleArray>, groupsNumber: Int, diffFunction: (DoubleArray, DoubleArray) -> Double = ::evalArraysDiff): List<Reference> {
  val averageDiff = findAverageDiff(samples, diffFunction)
  var diffFactor = 3.0
  while (diffFactor > 0) {
    val groups = MutableList<MutableSet<Int>>(1) { HashSet() }
    samples.forEachIndexed { index, sample ->
      val maxDiffsByGroup = groups.map { group -> group.map { diffFunction(sample, samples[it]) }.max() ?: 0.0 }
      val minGroupDiff = maxDiffsByGroup.min() ?: 0.0
      if (minGroupDiff < averageDiff * diffFactor) {
        groups[maxDiffsByGroup.indexOf(minGroupDiff)].add(index)
      } else {
        val newGroup = HashSet<Int>()
        newGroup.add(index)
        groups.add(newGroup)
      }
    }
    val minSamplesInGroup = samples.size / 80
    val filtered = groups.filter { it.size >= minSamplesInGroup }
    assurLog("Build ${filtered.size} samples group with diff factor $diffFactor")
    if (filtered.size >= groupsNumber) {
      val referencesData = filtered.map { group ->
        DoubleArray(samples[0].size) { group.map { sampleIndex -> samples[sampleIndex][it] }.sum() / group.size }
      }.sortedBy { getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) }
      return referencesData.mapIndexed { index, data -> Reference(index, evalAverageDiff(samples, data, diffFunction), data) }
    }
    diffFactor -= 0.05
  }
  throw Exception("Can't collect $groupsNumber groups")
}

private fun evalAverageDiff(samples: List<DoubleArray>, referenceData: DoubleArray, diffFunction: (DoubleArray, DoubleArray) -> Double) =
  samples.map { diffFunction(it, referenceData) }.average()

private fun findAverageDiff(samples: List<DoubleArray>, diffFunction: (DoubleArray, DoubleArray) -> Double): Double {
  var sumDiffs = 0.0
  var count = 0
  samples.forEachIndexed { index, sample0 ->
    (index + 1 until samples.size).forEach { sumDiffs += diffFunction(sample0, samples[it]); count++ }
  }
  return sumDiffs / count
}

fun evalArraysDiff(test: DoubleArray, sample: DoubleArray) =
  test.indices.map {
    val d1 = test[it] - sample[it]
    val d2 = if (it > 0) {
      (test[it] - test[it - 1]) - (sample[it] - sample[it - 1])
    } else 0.0
    d1 * d1 + d2 * d2 * d2 * d2
  }.sum()

fun evalArraysDiffM(test: DoubleArray, sample: DoubleArray): Double {
  val testHistogramData = HistogramData(0.0, 1.0, test)
  val sampleHistogramData = HistogramData(0.0, 1.0, sample)
  return (1 until 8).map {
    val threshold = 1.0 * it / 8.0
    val d = getHistogramMedianValue(testHistogramData, threshold) - getHistogramMedianValue(sampleHistogramData, threshold)
    d * d
  }.sum() / 8
}

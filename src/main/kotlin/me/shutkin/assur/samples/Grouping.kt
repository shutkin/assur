package me.shutkin.assur.samples

import me.shutkin.assur.HistogramData
import me.shutkin.assur.getHistogramMedianValue

fun grouping(samples: List<DoubleArray>, groupsNumber: Int): List<DoubleArray> {
  val averageDiff = findAverageDiff(samples)
  var diffFactor = 5.0
  while (diffFactor > 0) {
    val groups = MutableList<MutableSet<Int>>(1, { HashSet() })
    samples.forEachIndexed { index, sample ->
      val maxDiffsByGroup = groups.map { group -> group.map { evalArraysDiff(sample, samples[it]) }.max() ?: 0.0 }
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
    if (filtered.size >= groupsNumber)
      return filtered.map { group ->
        DoubleArray(samples[0].size, { group.map { sampleIndex -> samples[sampleIndex][it] }.sum() / group.size })
      }.sortedBy { getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) }
    diffFactor -= 0.2
  }
  throw Exception("Can't collect $groupsNumber groups")
}

private fun findAverageDiff(samples: List<DoubleArray>): Double {
  var sumDiffs = 0.0
  var count = 0
  samples.forEachIndexed { index, sample0 ->
    (index + 1 until samples.size).forEach { sumDiffs += evalArraysDiff(sample0, samples[it]); count++ }
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

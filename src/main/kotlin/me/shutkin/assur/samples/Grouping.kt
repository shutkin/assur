package me.shutkin.assur.samples

import me.shutkin.assur.HistogramData
import me.shutkin.assur.getHistogramMedianValue
import me.shutkin.assur.logger.log

fun grouping(samples: List<DoubleArray>, minSamplesInGroup: Int, differenceFactor: Double): List<DoubleArray> {
  val averageDiff = findAverageDiff(samples) * differenceFactor
  log("maximum difference $averageDiff")
  val groups = MutableList<MutableSet<Int>>(1, { HashSet<Int>() })
  samples.forEachIndexed { index, sample ->
    val maxDiffsByGroup = groups.map { group -> group.map { evalArraysDiff(sample, samples[it]) }.max() ?: 0.0 }
    val minGroupDiff = maxDiffsByGroup.min() ?: 0.0
    if (minGroupDiff < averageDiff) {
      groups[maxDiffsByGroup.indexOf(minGroupDiff)].add(index)
    } else {
      val newGroup = HashSet<Int>()
      newGroup.add(index)
      groups.add(newGroup)
    }
  }
  val filtered = groups.filter { it.size >= minSamplesInGroup }
  filtered.forEach { group -> log(group.joinToString { it.toString() }) }
  return filtered.map { group ->
    DoubleArray(samples[0].size, { group.map { sampleIndex -> samples[sampleIndex][it] }.sum() / group.size })
  }.sortedBy { getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) }
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
  test.indices.map { (test[it] - sample[it]) * (test[it] - sample[it]) }.sum()

fun evalArraysDiffM(test: DoubleArray, sample: DoubleArray): Double {
  val testHistogramData = HistogramData(0.0, 1.0, test)
  val sampleHistogramData = HistogramData(0.0, 1.0, sample)
  return (1 until 6).map {
    val threshold = 1.0 * it / 6.0
    val d = getHistogramMedianValue(testHistogramData, threshold) - getHistogramMedianValue(sampleHistogramData, threshold)
    d * d
  }.sum() / 6
}

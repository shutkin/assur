package me.shutkin.assur.filters

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking

const val MAX_SUB_ARRAYS = 256

data class SubArray<T>(val start: Int, val size: Int, val data: Array<T>)

inline fun <reified T> parallelArrayGeneration(size: Int, crossinline generator: (Int) -> T): Array<T> {
  val subArraySize = Math.ceil(size.toDouble() / MAX_SUB_ARRAYS).toInt()
  val actualSubArrays = Math.ceil(size.toDouble() / subArraySize).toInt()
  val deffered = (0 until actualSubArrays).map {
    async {
      val subArrayStart = it * subArraySize
      val subArrayEndUnbound = (it + 1) * subArraySize
      val subArrayEnd = if (subArrayEndUnbound < size) subArrayEndUnbound else size
      val subArray = Array(subArrayEnd - subArrayStart) { index -> generator(index + subArrayStart) }
      SubArray(subArrayStart, subArrayEnd - subArrayStart, subArray)
    }
  }
  val subArrays = runBlocking {
     deffered.map { def -> def.await() }
  }.sortedBy { it.start }
  return Array(size) {
    val subArray = subArrays[it / subArraySize]
    subArray.data[it - subArray.start]
  }
}
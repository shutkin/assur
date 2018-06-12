package me.shutkin.assur.samples

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import java.io.*

fun collectSamples(path: String, processRaster: (HDRRaster) -> DoubleArray): List<DoubleArray> {
  val allSamples = File(path).listFiles().filter { it.isFile }.mapIndexed { index, file ->
    log("process ${file.name} #$index")
    try {
      processRaster(readHDRRaster(FileInputStream(file)))
    } catch (e: Exception) {
      log(e.message ?: "some exception occurs")
      DoubleArray(0)
    }
  }.filter { it.isNotEmpty() }
  return grouping(allSamples, 15)
}

fun saveSamples(samples: List<DoubleArray>, prefix: String) {
  samples.forEachIndexed { index, sample ->
    saveHistogram(sample, "${prefix}_sample_$index.png")
    log(sample.joinToString { it.toString() })
  }
  serializeSamples(FileOutputStream("$prefix.samples"), samples)
}

private fun serializeSamples(stream: OutputStream, samples: List<DoubleArray>) {
  val objectStream = ObjectOutputStream(stream)
  objectStream.writeInt(samples.size)
  samples.forEach { sample -> sample.forEach { objectStream.writeDouble(it) } }
  objectStream.flush()
  objectStream.close()
}

fun deserializeSamples(stream: InputStream, sampleSize: Int): Array<DoubleArray> {
  val objectStream = ObjectInputStream(stream)
  val size = objectStream.readInt()
  val result = Array(size, { DoubleArray(sampleSize) })
  for (sampleIndex in result.indices)
    for (index in result[sampleIndex].indices)
      try {
        result[sampleIndex][index] = objectStream.readDouble()
      } catch (e: Exception) {
        log("Exception at sample $sampleIndex index $index")
        stream.close()
        return result
      }
  stream.close()
  return result
}

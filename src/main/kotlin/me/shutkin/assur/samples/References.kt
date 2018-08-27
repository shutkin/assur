package me.shutkin.assur.samples

import me.shutkin.assur.*
import me.shutkin.assur.logger.assurLog
import java.io.*

data class Reference(val id: Int, val averageError: Double, val popularity: Double, val data: DoubleArray) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Reference

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id
  }
}

data class References(val medianMin: Double, val medianMax: Double, val refs: List<Reference>) {
  fun getMedianQuantum() = (medianMax - medianMin) / 22.0
}

fun collectReferences(path: String, medianFilter: Double = 0.5,
                   diffFunction: (DoubleArray, DoubleArray) -> Double = ::evalArraysDiff,
                   processRaster: (HDRRaster) -> DoubleArray): List<Reference> {
  val allSamples = File(path).listFiles().filter { it.isFile }.mapIndexed { index, file ->
    assurLog("process ${file.name} #$index")
    try {
      processRaster(readHDRRaster(FileInputStream(file)))
    } catch (e: Exception) {
      assurLog(e.message ?: "some exception occurs")
      DoubleArray(0)
    }
  }.filter { it.isNotEmpty() }
  assurLog("Total samples: ${allSamples.size}")
  val averageMedian = allSamples.map { getHistogramMedianValue(HistogramData(0.0, 1.0, it), medianFilter) }.average()
  assurLog("Average median: $averageMedian")
  val filteredSamples = allSamples.filter{ getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) > averageMedian }
  assurLog("Filtered samples: ${filteredSamples.size}")
  return grouping(filteredSamples, 21, diffFunction)
}

fun saveReferences(references: List<Reference>, prefix: String) {
  references.forEach { reference ->
    saveHistogram(reference.data, "${prefix}_ref_${reference.id}.png")
    assurLog(reference.toString())
  }
  serializeReferences(FileOutputStream("$prefix.ref"), references)
}

private fun serializeReferences(stream: OutputStream, references: List<Reference>) {
  val objectStream = ObjectOutputStream(stream)
  objectStream.writeInt(references.size)
  references.forEach { ref ->
    objectStream.writeDouble(ref.averageError)
    objectStream.writeDouble(ref.popularity)
    ref.data.forEach { objectStream.writeDouble(it) }
  }
  objectStream.flush()
  objectStream.close()
}

fun deserializeReferences(stream: InputStream, sampleSize: Int): References {
  val objectStream = ObjectInputStream(stream)
  val size = objectStream.readInt()
  val dataArrays = Array(size) { DoubleArray(sampleSize) }
  val averageErrors = DoubleArray(size)
  val popularities = DoubleArray(size)
  for (referenceIndex in dataArrays.indices)
    try {
      averageErrors[referenceIndex] = objectStream.readDouble()
      popularities[referenceIndex] = objectStream.readDouble()
      for (index in dataArrays[referenceIndex].indices)
        dataArrays[referenceIndex][index] = objectStream.readDouble()
    } catch (e: Exception) {
      assurLog("Exception at reference $referenceIndex")
      stream.close()
      break
    }
  stream.close()
  val medians = dataArrays.map { getHistogramMedianValue(HistogramData(0.0, 1.0, it), 0.5) }
  return References(medians.min() ?: 0.0, medians.max() ?: 1.0,
          List(size) { Reference(it, averageErrors[it], popularities[it], dataArrays[it]) })
}

fun getReferences(allReferences: List<Reference>, diapason: Diapason, referenceIndex: Int) =
        if (referenceIndex >= 0)
          List(1) { allReferences[referenceIndex] }
        else
          allReferences.slice(diapason.getStartIndex(allReferences.size) until diapason.getEndIndex(allReferences.size))
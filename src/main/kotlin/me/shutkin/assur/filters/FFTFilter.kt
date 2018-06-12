package me.shutkin.assur.filters

import me.shutkin.assur.*
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiff
import java.io.FileInputStream

val fftSamples = deserializeSamples(FileInputStream("spectrum.samples"), 256)

fun fftFilter(source: HDRRaster): HDRRaster {
  log("FFTFilter start")
  val samples = fftSamples

  val spectrum = buildSpectrum(source, true)
  val diffs = samples.map { evalArraysDiff(spectrum, it) }
  val sampleIndex = diffs.indexOf(diffs.min())
  log("selected sample $sampleIndex")
  val sampleSpectrum = samples[sampleIndex]
  val harmonicFactors = DoubleArray(spectrum.size, { sampleSpectrum[it] / (spectrum[it] + 0.01) })

  val transformedLuminances = DoubleArray(source.width * source.height)
  val weights = DoubleArray(source.width * source.height)
  val columns = source.width / FFT_WINDOW_SIZE * FFT_OVERLAP
  val rows = source.height / FFT_WINDOW_SIZE * FFT_OVERLAP
  for (winColumn in 0 until columns) {
    for (winRow in 0 until rows) {
      val winX = winColumn * (source.width - 1 - FFT_WINDOW_SIZE) / (columns - 1)
      val winY = winRow * (source.height - FFT_WINDOW_SIZE) / (rows - 1)
      val transformed = transformWindow(winX, winY, source)
      val adjustedTransformed = adjustTransformed(transformed, harmonicFactors)
      val inverse = inverseTransform(adjustedTransformed)
      inverse.forEachIndexed({ index, value ->
        val x = index % FFT_WINDOW_SIZE
        val y = index / FFT_WINDOW_SIZE
        val globalIndex = (winY + y) * source.width + winX + x
        val wx = (if (x < FFT_WINDOW_SIZE / 2) 1.0 + x else FFT_WINDOW_SIZE.toDouble() - x) / FFT_WINDOW_SIZE
        val wy = (if (y < FFT_WINDOW_SIZE / 2) 1.0 + y else FFT_WINDOW_SIZE.toDouble() - y) / FFT_WINDOW_SIZE
        weights[globalIndex] += wx * wy
        transformedLuminances[globalIndex] += wx * wy * value.module()
      })
    }
  }
  return HDRRaster(source.width, source.height, {
    val transformed = if (weights[it] > 0.00001) transformedLuminances[it] / weights[it] else 0.0
    source.data[it].multiply(transformed / (source.data[it].luminance + 0.1))
  })
}

private fun adjustTransformed(transformed: Array<Complex>, harmonicFactors: DoubleArray) = Array(FFT_WINDOW_SIZE * FFT_WINDOW_SIZE, {
  val f = getFreq(it)
  if (f < harmonicFactors.size) transformed[it] * harmonicFactors[f.toInt()] else Complex(0.0, 0.0)
})

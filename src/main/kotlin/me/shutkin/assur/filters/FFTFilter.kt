package me.shutkin.assur.filters

import me.shutkin.assur.Complex
import me.shutkin.assur.FFT
import me.shutkin.assur.HDRRaster
import me.shutkin.assur.logger.log
import me.shutkin.assur.samples.deserializeSamples
import me.shutkin.assur.samples.evalArraysDiff
import java.io.FileInputStream

const val FFT_WINDOW_SIZE = 512
private const val FFT_OVERLAP = 2

fun fftFilter(source: HDRRaster): HDRRaster {
  log("FFTFilter start")
  val samples = deserializeSamples(FileInputStream("spectrum.samples"), 256)

  val spectrum = buildSpectrum(source, true)
  val diffs = samples.map { evalArraysDiff(spectrum, it) }
  val sampleIndex = diffs.indexOf(diffs.min())
  log("selected sample $sampleIndex")
  val sampleSpectrum = samples[sampleIndex]
  val harmonicFactors = DoubleArray(spectrum.size, { (sampleSpectrum[it] + 0.01) / (spectrum[it] + 0.01) })

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
    val tl = if (weights[it] > 0.00001) transformedLuminances[it] / weights[it] else 0.0
    val f = (1.0 + tl) / (1.0 + source.data[it].luminance())
    source.data[it].multiply(f)
  })
}

fun buildSpectrum(raster: HDRRaster, normalize: Boolean): DoubleArray {
  val spectrum = DoubleArray(FFT_WINDOW_SIZE / 2)
  val columnsNoOverlap = Math.max(raster.width / FFT_WINDOW_SIZE, 2)
  val rowsNoOverlap = Math.max(raster.height / FFT_WINDOW_SIZE, 2)
  for (winColumn in 0 until columnsNoOverlap) {
    for (winRow in 0 until rowsNoOverlap) {
      val winX = winColumn * (raster.width - 1 - FFT_WINDOW_SIZE) / (columnsNoOverlap - 1)
      val winY = winRow * (raster.height - FFT_WINDOW_SIZE) / (rowsNoOverlap - 1)
      val transformed = transformWindow(winX, winY, raster)
      val windowSpectrum = getSpectrum(transformed)
      windowSpectrum.forEachIndexed({ index, value -> spectrum[index] += value / (columnsNoOverlap * rowsNoOverlap) })
    }
  }
  if (normalize) {
    val spectrumMin = spectrum.min() ?: 0.0
    val spectrumMax = spectrum.max() ?: 1.0
    spectrum.forEachIndexed({ index, harmonic -> spectrum[index] = 0.25 * (harmonic - spectrumMin) / (spectrumMax - spectrumMin) })
  }
  return spectrum
}

private fun transformWindow(winX: Int, winY: Int, raster: HDRRaster): Array<Complex> {
  val fft = FFT()
  val transformedLines = Array(FFT_WINDOW_SIZE, { lineIndex ->
    val lineBegin = (lineIndex + winY) * raster.width
    val lineComplex = Array(FFT_WINDOW_SIZE, { Complex(raster.data[lineBegin + winX + it].luminance(), 0.0) })
    fft.fft(lineComplex)
  })
  val transformedRows = Array(FFT_WINDOW_SIZE, { rowIndex ->
    val rowComplex = Array(FFT_WINDOW_SIZE, { transformedLines[it][rowIndex] })
    fft.fft(rowComplex)
  })
  return Array(FFT_WINDOW_SIZE * FFT_WINDOW_SIZE, { transformedRows[it % FFT_WINDOW_SIZE][it / FFT_WINDOW_SIZE] })
}

private fun inverseTransform(transformed: Array<Complex>): Array<Complex> {
  val fft = FFT()
  val iRows = Array(FFT_WINDOW_SIZE, { rowIndex ->
    fft.ifft(Array(FFT_WINDOW_SIZE, { transformed[rowIndex + it * FFT_WINDOW_SIZE] }))
  })
  val iLines = Array(FFT_WINDOW_SIZE, { lineIndex ->
    val line = Array(FFT_WINDOW_SIZE, { iRows[it][lineIndex] })
    fft.ifft(line)
  })
  return Array(FFT_WINDOW_SIZE * FFT_WINDOW_SIZE, { iLines[it / FFT_WINDOW_SIZE][it % FFT_WINDOW_SIZE] })
}

private fun adjustTransformed(transformed: Array<Complex>, harmonicFactors: DoubleArray) = Array(FFT_WINDOW_SIZE * FFT_WINDOW_SIZE, {
  val f = getFreq(it)
  if (f < harmonicFactors.size) transformed[it] * harmonicFactors[f.toInt()] else Complex(0.0, 0.0)
})

private fun getSpectrum(transformed: Array<Complex>): DoubleArray {
  val spectrum = DoubleArray(FFT_WINDOW_SIZE / 2)
  transformed.forEachIndexed({ index, value ->
    val f = getFreq(index)
    if (f < spectrum.size)
      spectrum[f.toInt()] += value.module()
  })
  return spectrum
}

private fun getFreq(windowIndex: Int): Double {
  val x = windowIndex % FFT_WINDOW_SIZE
  val y = windowIndex / FFT_WINDOW_SIZE
  val cx = if (x < FFT_WINDOW_SIZE / 2) x else FFT_WINDOW_SIZE - 1 - x
  val cy = if (y < FFT_WINDOW_SIZE / 2) y else FFT_WINDOW_SIZE - 1 - y
  return Math.sqrt(cx.toDouble() * cx + cy.toDouble() * cy)
}

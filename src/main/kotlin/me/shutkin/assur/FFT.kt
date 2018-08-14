package me.shutkin.assur

const val FFT_WINDOW_SIZE = 512
const val FFT_OVERLAP = 2

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

fun transformWindow(winX: Int, winY: Int, raster: HDRRaster): Array<Complex> {
  val fft = FFT()
  val transformedLines = Array(FFT_WINDOW_SIZE, { lineIndex ->
    val lineBegin = (lineIndex + winY) * raster.width
    val lineComplex = Array(FFT_WINDOW_SIZE, { Complex(raster.data[lineBegin + winX + it].luminance, 0.0) })
    fft.fft(lineComplex)
  })
  val transformedRows = Array(FFT_WINDOW_SIZE, { rowIndex ->
    val rowComplex = Array(FFT_WINDOW_SIZE, { transformedLines[it][rowIndex] })
    fft.fft(rowComplex)
  })
  return Array(FFT_WINDOW_SIZE * FFT_WINDOW_SIZE, { transformedRows[it % FFT_WINDOW_SIZE][it / FFT_WINDOW_SIZE] })
}

fun getSpectrum(transformed: Array<Complex>): DoubleArray {
  val spectrum = DoubleArray(FFT_WINDOW_SIZE / 2)
  transformed.forEachIndexed({ index, value ->
    val f = getFreq(index)
    if (f < spectrum.size)
      spectrum[f.toInt()] += value.module()
  })
  return spectrum
}

fun inverseTransform(transformed: Array<Complex>): Array<Complex> {
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

fun getFreq(windowIndex: Int): Double {
  val x = windowIndex % FFT_WINDOW_SIZE
  val y = windowIndex / FFT_WINDOW_SIZE
  val cx = if (x < FFT_WINDOW_SIZE / 2) x else FFT_WINDOW_SIZE - 1 - x
  val cy = if (y < FFT_WINDOW_SIZE / 2) y else FFT_WINDOW_SIZE - 1 - y
  return Math.sqrt(cx.toDouble() * cx + cy.toDouble() * cy)
}


/******************************************************************************
 *  Compilation:  javac FFT.java
 *  Execution:    java FFT n
 *  Dependencies: Complex.java
 *
 *  Compute the FFT and inverse FFT of a length n complex sequence
 *  using the radix 2 Cooley-Tukey algorithm.

 *  Bare bones implementation that runs in O(n assurLog n) time. Our goal
 *  is to optimize the clarity of the code, rather than performance.
 *
 *  Limitations
 *  -----------
 *   -  assumes n is a power of 2
 *
 *   -  not the most memory efficient algorithm (because it uses
 *      an object type for representing complex numbers and because
 *      it re-allocates memory for the subarray, instead of doing
 *      in-place or reusing a single temporary array)
 *
 *  For an in-place radix 2 Cooley-Tukey FFT, see
 *  https://introcs.cs.princeton.edu/java/97data/InplaceFFT.java.html
 *
 ******************************************************************************/

data class Complex(val re: Double, val im: Double) {
  // return a new Complex object whose value is (this + b)
  operator fun plus(b: Complex) = Complex(re + b.re, im + b.im)

  // return a new Complex object whose value is (this - b)
  operator fun minus(b: Complex) = Complex(re - b.re, im - b.im)

  // return a new Complex object whose value is (this * b)
  operator fun times(b: Complex) = Complex(re * b.re - im * b.im, re * b.im + im * b.re)

  // return a new object whose value is (this * alpha)
  operator fun times(alpha: Double) = Complex(re * alpha, im * alpha)

  // return a new Complex object whose value is the conjugate of this
  fun conjugate() = Complex(re, -im)

  fun module() = Math.sqrt(re * re + im * im)
}

class FFT {
  // compute the FFT of x[], assuming its length is a power of 2
  fun fft(x: Array<Complex>): Array<Complex> {
    val n = x.size

    // base case
    if (n == 1) return arrayOf(x[0])

    // radix 2 Cooley-Tukey FFT
    if (n % 2 != 0) {
      throw IllegalArgumentException("n is not a power of 2")
    }

    // fft of even terms
    val even = Array(n / 2, {x[2 * it]})
    val q = fft(even)

    // fft of odd terms
    for (k in 0 until n / 2) {
      even[k] = x[2 * k + 1]
    }
    val r = fft(even)

    // combine
    val y = Array(n, {Complex(0.0, 0.0)})
    for (k in 0 until n / 2) {
      val kth = -2.0 * k.toDouble() * Math.PI / n
      val wk = Complex(Math.cos(kth), Math.sin(kth))
      y[k] = q[k] + (wk * r[k])
      y[k + n / 2] = q[k] - (wk * r[k])
    }
    return y
  }


  // compute the inverse FFT of x[], assuming its length is a power of 2
  fun ifft(x: Array<Complex>): Array<Complex> {
    val n = x.size
    var y = Array(n, {x[it].conjugate()})

    // compute forward FFT
    y = fft(y)

    // take conjugate again
    for (i in 0 until n) {
      y[i] = y[i].conjugate()
    }

    // divide by n
    for (i in 0 until n) {
      y[i] = y[i] * (1.0 / n)
    }

    return y
  }

  // compute the circular convolution of x and y
  private fun cconvolve(x: Array<Complex>, y: Array<Complex>): Array<Complex> {
    // should probably pad x and y with 0s so that they have same length
    // and are powers of 2
    if (x.size != y.size) {
      throw IllegalArgumentException("Dimensions don't agree")
    }

    val n = x.size

    // compute FFT of each sequence
    val a = fft(x)
    val b = fft(y)

    // point-wise multiply
    val c = Array(n, {a[it] * b[it]})

    // compute inverse FFT
    return ifft(c)
  }


  // compute the linear convolution of x and y
  fun convolve(x: Array<Complex>, y: Array<Complex>): Array<Complex> {
    val ZERO = Complex(0.0, 0.0)
    val a = Array(2 * x.size, {if (it < x.size) x[it] else ZERO})
    val b = Array(2 * y.size, {if (it < y.size) y[it] else ZERO})
    return cconvolve(a, b)
  }
}

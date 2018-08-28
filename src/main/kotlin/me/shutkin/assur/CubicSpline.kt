package me.shutkin.assur

import java.util.*

private class SplineTuple(var x: Double, y: Double) {
  var a = y
  var b = 0.0
  var c = 0.0
  var d = 0.0
}

class CubicSpline(private val x: DoubleArray, private val y: DoubleArray) {
  private val splines: Array<SplineTuple> = Array(x.size) { i -> SplineTuple(x[i], y[i]) }

  init {
    splines[splines.size - 1].c = 0.0
    splines[0].c = splines[splines.size - 1].c

    val alpha = DoubleArray(splines.size - 1)
    val beta = DoubleArray(splines.size - 1)
    beta[0] = 0.0
    alpha[0] = beta[0]
    for (i in 1 until splines.size - 1) {
      val hi = x[i] - x[i - 1]
      val hi1 = x[i + 1] - x[i]
      val c = 2.0 * (hi + hi1)
      val f = 6.0 * ((y[i + 1] - y[i]) / hi1 - (y[i] - y[i - 1]) / hi)
      val z = hi * alpha[i - 1] + c
      alpha[i] = -hi1 / z
      beta[i] = (f - hi * beta[i - 1]) / z
    }

    for (i in splines.size - 2 downTo 1)
      splines[i].c = alpha[i] * splines[i + 1].c + beta[i]

    for (i in splines.size - 1 downTo 1) {
      val hi = x[i] - x[i - 1]
      splines[i].d = (splines[i].c - splines[i - 1].c) / hi
      splines[i].b = hi * (2.0 * splines[i].c + splines[i - 1].c) / 6.0 + (y[i] - y[i - 1]) / hi
    }
  }

  fun interpolate(x: Double): Double {
    if (x < splines.first().x || x > splines.last().x)
      return x

    var i = 0
    var j = splines.size - 1
    while (i + 1 < j) {
      val k = i + (j - i) / 2
      if (x <= splines[k].x)
        j = k
      else
        i = k
    }
    val s = splines[j]
    val dx = x - s.x
    return s.a + (s.b + (s.c / 2.0 + s.d * dx / 6.0) * dx) * dx
  }

  override fun toString(): String {
    return "CubicSpline: ${Arrays.toString(x)} -> ${Arrays.toString(y)}"
  }
}
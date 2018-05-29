package me.shutkin.assur.samples

import me.shutkin.assur.StraightWindow
import me.shutkin.assur.Window
import me.shutkin.assur.buildHistogram
import me.shutkin.assur.filters.reduceSizeFilter

fun main(args: Array<String>) {
  val window: Window = StraightWindow(32)

  val samples = collectSamples(args[0], {
    val raster = reduceSizeFilter(it, 1024, false)
    val blur = window.apply(raster)
    buildHistogram(0.0, 64.0, 1024, raster.data.size, { Math.abs(raster.data[it].luminance() - blur[it]) }).histogram
  }, 2.0)
  saveSamples(samples, "hue")
}

package me.shutkin.assur.samples

import me.shutkin.assur.AssurContext
import me.shutkin.assur.StraightWindow
import me.shutkin.assur.Window
import me.shutkin.assur.buildHistogram
import me.shutkin.assur.filters.reduceSizeFilter
import me.shutkin.assur.logger.assurLog

fun main(args: Array<String>) {
  val context = AssurContext(log = ::assurLog, path = ".", imageFormat = "png")
  val window: Window = StraightWindow(32)

  val references = collectReferences(args[0], 0.125) { sample ->
    val raster = reduceSizeFilter(context, sample, 1024, false)
    val blur = window.apply(raster)
    buildHistogram(0.0, 64.0, 1024, raster.data.size) { Math.abs(raster.data[it].luminance - blur[it]) }.histogram
  }
  saveReferences(references, "details")
}

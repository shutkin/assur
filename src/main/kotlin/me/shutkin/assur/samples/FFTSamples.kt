package me.shutkin.assur.samples

import me.shutkin.assur.filters.buildSpectrum
import me.shutkin.assur.filters.reduceSizeFilter

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], { raster -> buildSpectrum(reduceSizeFilter(raster, 1024, false), true) }, 2.0)
  saveSamples(samples, "spectrum")
}

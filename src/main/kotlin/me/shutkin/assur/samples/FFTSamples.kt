package me.shutkin.assur.samples

import me.shutkin.assur.buildSpectrum
import me.shutkin.assur.filters.reduceSizeFilter

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], { raster -> buildSpectrum(reduceSizeFilter(raster, 1024, false), true) })
  saveSamples(samples, "spectrum")
}

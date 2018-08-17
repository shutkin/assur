package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], 0.0, 1.0, 0.25) { raster ->
    buildHistogram(0.0, 1.0, 128, raster.data.size) { raster.data[it].saturation }.histogram
  }
  saveSamples(samples, "saturation")
}

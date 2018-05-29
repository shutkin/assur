package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], { raster ->
    buildHistogram(0.0, 1.0, 128, raster.data.size, { raster.data[it].hsl()[1] }).histogram
  }, 2.0)
  saveSamples(samples, "saturation")
}

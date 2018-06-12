package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], { raster ->
    buildHistogram(0.0, 255.0, 256, raster.data.size, { raster.data[it].luminance }).histogram
  })
  saveSamples(samples, "luminance")
}

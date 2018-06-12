package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram

fun main(args: Array<String>) {
  val samples = collectSamples(args[0], { raster ->
    buildHistogram(0.0, 360.0, 180, raster.data.size, { raster.data[it].hsl[0] }).histogram
  })
  saveSamples(samples, "hue")
}

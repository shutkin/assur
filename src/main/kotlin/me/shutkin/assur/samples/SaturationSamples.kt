package me.shutkin.assur.samples

import me.shutkin.assur.buildHistogram

fun main(args: Array<String>) {
  val references = collectReferences(args[0], 0.0) { raster ->
    buildHistogram(0.0, 1.0, 128, raster.data.size) { raster.data[it].saturation }.histogram
  }
  saveReferences(references, "saturation")
}

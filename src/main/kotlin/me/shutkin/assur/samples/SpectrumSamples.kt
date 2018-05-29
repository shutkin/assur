package me.shutkin.assur.samples

import me.shutkin.assur.HDRRaster
import me.shutkin.assur.RGB
import me.shutkin.assur.filters.FFT_WINDOW_SIZE
import me.shutkin.assur.filters.buildSpectrum
import me.shutkin.assur.filters.reduceSizeFilter
import me.shutkin.assur.logger.log
import me.shutkin.assur.readHDRRaster
import me.shutkin.assur.saveHDRRaster
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun main(args: Array<String>) {
  val allSpectres = File(args[0]).listFiles().filter { it.isFile }.map {
    log("process ${it.name}")
    val raster = readHDRRaster(FileInputStream(it))
    buildSpectrum(reduceSizeFilter(raster, 1400, false), true)
  }
  val groups = grouping(allSpectres, 5, 2.0)
  groups.forEachIndexed{ groupIndex, groupSpectrum ->
    val sampleSpectrumGraph = HDRRaster(FFT_WINDOW_SIZE, FFT_WINDOW_SIZE, {
      val x = (it % FFT_WINDOW_SIZE) / 2
      val y = it / FFT_WINDOW_SIZE
      val spectrum = groupSpectrum[x] * FFT_WINDOW_SIZE
      if (FFT_WINDOW_SIZE - y < spectrum) RGB(255f, 255f, 255f) else RGB(0f, 0f, 0f)
    })
    saveHDRRaster(sampleSpectrumGraph, FileOutputStream("sample_spectrum$groupIndex.png"))
    log("doubleArrayOf(${groupSpectrum.joinToString { it.toString() }}),")
  }
}

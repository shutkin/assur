package me.shutkin.assur.logger

import java.text.SimpleDateFormat
import java.util.*

class AssurLogger {
  companion object {
    var loggerFunction: (String) -> Unit = { println(it) }
  }
}

private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")

fun assurLog(message: String) {
  AssurLogger.loggerFunction("${dateFormat.format(Date())}: $message")
}
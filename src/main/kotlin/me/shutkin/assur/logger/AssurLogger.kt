package me.shutkin.assur.logger

import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")

fun log(message: String) {
  println("${dateFormat.format(Date())}: $message")
}
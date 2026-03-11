package com.rakshak.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val fullFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatFull(timestamp: Long): String = fullFormat.format(Date(timestamp))
    fun formatShort(timestamp: Long): String = shortFormat.format(Date(timestamp))
}

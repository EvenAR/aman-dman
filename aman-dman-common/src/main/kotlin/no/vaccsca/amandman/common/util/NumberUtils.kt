package no.vaccsca.amandman.common.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime

object NumberUtils {
    @OptIn(FormatStringsInDatetimeFormats::class)
    fun Instant.format(pattern: String): String {
        val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(pattern) }
        return dateTimeFormat.format(this.toLocalDateTime(TimeZone.UTC))
    }

    fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

    fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}
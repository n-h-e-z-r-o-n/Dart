package com.zadhron.adaa.ui.shared

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun paymentMoney(minor: Long): String = "KES " + NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(BigDecimal.valueOf(minor, 2))

fun parsePaymentAmount(text: String): Long? = if (!text.trim().matches(Regex("^[0-9]{1,9}(\\.[0-9]{1,2})?$"))) null
    else runCatching { BigDecimal(text.trim()).movePointRight(2).longValueExact().takeIf { it > 0 } }.getOrNull()

fun normalizePaymentPhone(text: String): String? {
    val digits = text.filterNot { it.isWhitespace() || it == '-' }.removePrefix("+")
    val normalized = if (digits.matches(Regex("0[17][0-9]{8}"))) "254" + digits.drop(1) else digits
    return normalized.takeIf { it.matches(Regex("254[17][0-9]{8}")) }
}

fun paymentDate(text: String): String = runCatching {
    OffsetDateTime.parse(text).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
}.getOrDefault(text.take(10))

fun paymentStatus(value: String): String = when (value) {
    "requested" -> "Awaiting payment"; "funded" -> "Held in escrow"; "completed" -> "Completed"
    "external_paid" -> "Paid outside Adaa"; "queued" -> "Waiting to process"; "pending" -> "Processing"
    "review" -> "Needs review"; "succeeded" -> "Confirmed"; "disputed" -> "Under review"
    else -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

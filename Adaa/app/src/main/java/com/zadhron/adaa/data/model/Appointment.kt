package com.zadhron.adaa.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AppointmentStatus(val label: String) {
    SCHEDULED("Scheduled"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    companion object {
        fun fromString(value: String): AppointmentStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SCHEDULED
        }
    }
}

data class Appointment(
    val id: Long = 0,
    val clientId: Long,
    val serviceId: Long,
    val clientName: String,
    val clientPhone: String = "",
    val serviceTitle: String,
    val serviceCategory: ServiceCategory = ServiceCategory.NAILS,
    val startEpochMillis: Long,
    val durationMinutes: Int,
    val price: Double,
    val status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    val notes: String = ""
) {
    val endEpochMillis: Long
        get() = startEpochMillis + (durationMinutes * 60 * 1000L)

    val localDateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpochMillis), ZoneId.systemDefault())

    val formattedTime: String
        get() = localDateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))

    val formattedEndTime: String
        get() {
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpochMillis), ZoneId.systemDefault())
            return end.format(DateTimeFormatter.ofPattern("hh:mm a"))
        }

    val formattedDate: String
        get() = localDateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
}

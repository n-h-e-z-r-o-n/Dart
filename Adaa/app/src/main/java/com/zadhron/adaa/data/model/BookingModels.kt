package com.zadhron.adaa.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CatalogService(val id: String, val name: String, val description: String, val priceMinor: Long,
    val durationMinutes: Int, val active: Boolean = true)
data class Storefront(val phone: String = "", val email: String = "", val website: String = "", val hours: String = "",
    val timezone: String = "Africa/Nairobi", val photos: List<String> = emptyList(), val acceptingRequests: Boolean = true,
    val canManage: Boolean = false, val services: List<CatalogService> = emptyList())
data class BookingRequest(val id: String, val businessName: String, val customerName: String, val serviceName: String,
    val priceMinor: Long?, val durationMinutes: Int?, val kind: String, val preferredAt: String, val timezone: String,
    val message: String, val state: String, val response: String, val createdAt: String, val canManage: Boolean, val isCustomer: Boolean)
data class BookingEvent(val state: String, val message: String, val createdAt: String)

fun bookingTime(instant: String, timezone: String): String = runCatching {
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm").withZone(ZoneId.of(timezone)).format(Instant.parse(instant)) + " · $timezone"
}.getOrDefault(instant)
fun bookingStatus(state: String) = when (state) {
    "pending" -> "Awaiting business confirmation"; "confirmed" -> "Confirmed by business"
    "completed" -> "Completed"; "declined" -> "Declined"; "cancelled" -> "Cancelled"; "answered" -> "Inquiry answered"
    "no_show" -> "Customer did not attend"
    else -> state
}

interface BookingGateway {
    suspend fun storefront(businessId: String, userId: String?): Storefront
    suspend fun saveStorefront(businessId: String, userId: String, value: Storefront)
    suspend fun saveService(businessId: String, userId: String, value: CatalogService)
    suspend fun submit(userId: String, requestId: String, businessId: String, service: CatalogService?, preferredAt: String?, message: String): String
    suspend fun requests(userId: String, businessId: String?, before: BookingRequest? = null, state: String? = null): List<BookingRequest>
    suspend fun detail(userId: String, id: String): Pair<BookingRequest, List<BookingEvent>>
    suspend fun act(userId: String, id: String, action: String, response: String)
}

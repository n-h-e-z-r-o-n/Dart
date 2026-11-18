package com.zadhron.adaa.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zadhron.adaa.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.*
import java.util.UUID

data class BookingState(val storefront: Storefront? = null, val requests: List<BookingRequest> = emptyList(),
    val hasMore: Boolean = false, val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val page: String = "home", val service: CatalogService? = null, val selected: BookingRequest? = null,
    val events: List<BookingEvent> = emptyList(), val sent: Boolean = false, val sessionExpired: Boolean = false, val filter: String? = null)

/** Separate from navigation, session and payments. A booking request never moves funds. */
class BookingViewModel(private val gateway: BookingGateway, private val businessId: String?,
    initialUserId: String?, private val manage: Boolean, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private var userId = initialUserId
    private val mutable = MutableStateFlow(BookingState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    fun bindUser(value: String?) {
        if (value == userId) return
        val discardDraft = userId != null
        generation++; work?.cancel(); userId = value
        if (discardDraft) listOf("date", "time", "message", "nonce", "requestedAt", "serviceId", "inquiry").forEach { saved.remove<Any>(it) }
        change { it.copy(requests = emptyList(), selected = null, events = emptyList(), busy = false, error = null,
            page = if (discardDraft || it.page == "detail") "home" else it.page, service = if (discardDraft) null else it.service) }
        refresh()
    }
    var date: String
        get() = saved["date"] ?: ""
        set(value) { saved["date"] = value; resetIntent() }
    var time: String
        get() = saved["time"] ?: ""
        set(value) { saved["time"] = value; resetIntent() }
    var message: String
        get() = saved["message"] ?: ""
        set(value) { saved["message"] = value; resetIntent() }
    private fun resetIntent() { saved.remove<String>("nonce"); saved.remove<String>("requestedAt") }
    private fun change(block: (BookingState) -> BookingState) { mutable.value = block(mutable.value) }
    private fun run(block: suspend () -> Unit) {
        if (state.value.busy) return
        change { it.copy(busy = true, error = null, notice = null, sessionExpired = false) }
        val epoch = generation
        work = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == generation) change { it.copy(error = if (e is java.io.IOException) "Connection interrupted. Try again; your request will not be duplicated." else e.message ?: "Please try again.", sessionExpired = e is SessionExpiredException) } }
            finally { if (epoch == generation) change { it.copy(busy = false) } }
        }
    }
    init { refresh() }
    fun refresh() = run {
        if (businessId != null) {
            val profile = gateway.storefront(businessId, userId)
            change { it.copy(storefront = profile) }
            val pendingId: String? = saved["serviceId"]
            if (!manage && pendingId != null) {
                val service = profile.services.firstOrNull { it.id == pendingId && it.active }
                if (service != null && state.value.page == "home") change { it.copy(service = service, page = "request") }
            }
        }
        if (userId != null && (manage || businessId == null)) loadRequests()
        val selected = state.value.selected
        if (userId != null && state.value.page == "detail" && selected != null) {
            val detail = gateway.detail(requireNotNull(userId), selected.id)
            change { it.copy(selected = detail.first, events = detail.second) }
        }
        restoreInquiry()
    }
    private suspend fun loadRequests() {
        val rows = gateway.requests(requireNotNull(userId), if (manage) businessId else null, state = state.value.filter)
        change { it.copy(requests = rows, hasMore = rows.size == 30) }
    }
    fun loadMore() = run {
        val rows = gateway.requests(requireNotNull(userId), if (manage) businessId else null, state.value.requests.lastOrNull(), state.value.filter)
        change { it.copy(requests = (it.requests + rows).distinctBy { r -> r.id }, hasMore = rows.size == 30) }
    }
    fun filter(value: String?) {
        if (state.value.busy) return
        change { it.copy(filter = value, requests = emptyList(), hasMore = false) }
        run { loadRequests() }
    }
    fun page(value: String) { if (!state.value.busy) change { it.copy(page = value, error = null, notice = null) } }
    fun choose(service: CatalogService?) {
        if (state.value.busy) return
        saved["serviceId"] = service?.id
        saved["inquiry"] = service == null
        resetIntent()
        change { it.copy(service = service, page = "request", error = null, sent = false) }
    }
    fun restoreInquiry() { if (saved.get<Boolean>("inquiry") == true && !manage && state.value.page == "home") change { it.copy(service = null, page = "request") } }
    fun review() {
        try {
            require(message.trim().length in 1..2000) { "Add a message, up to 2,000 characters." }
            if (state.value.service != null) saved["requestedAt"] = requestedInstant()
            page("review")
        } catch (e: Exception) { change { it.copy(error = e.message ?: "Check your preferred date and time.") } }
    }
    fun requestedInstant(): String {
        val zone = ZoneId.of(state.value.storefront?.timezone ?: "Africa/Nairobi")
        val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1) { "This time is ambiguous or unavailable because the clocks change. Choose another time." }
        val instant = local.atOffset(offsets.single()).toInstant()
        require(instant > Instant.now() && instant < Instant.now().plus(Duration.ofDays(365))) { "Choose a future date within the next year." }
        return instant.toString()
    }
    fun submit() {
      if (state.value.page != "review") return
      run {
        val user = requireNotNull(userId) { "Sign in to send your request." }
        val nonce = saved.get<String>("nonce") ?: UUID.randomUUID().toString().also { saved["nonce"] = it }
        gateway.submit(user, nonce, requireNotNull(businessId), state.value.service,
            if (state.value.service == null) null else saved.get<String>("requestedAt") ?: requestedInstant(), message.trim())
        saved.remove<String>("serviceId"); saved.remove<Boolean>("inquiry")
        change { it.copy(page = "sent", sent = true) }
      }
    }
    fun select(request: BookingRequest) = run {
        change { it.copy(selected = request, page = "detail", events = emptyList()) }
        val detail = gateway.detail(requireNotNull(userId), request.id)
        change { it.copy(selected = detail.first, events = detail.second) }
    }
    fun act(action: String, response: String) = run {
        gateway.act(requireNotNull(userId), requireNotNull(state.value.selected).id, action, response.trim())
        change { it.copy(page = "home", selected = null, notice = "Request updated.") }
        loadRequests()
    }
    fun saveProfile(value: Storefront) = run {
        gateway.saveStorefront(requireNotNull(businessId), requireNotNull(userId), value)
        change { it.copy(page = "home", notice = "Storefront details saved.") }
        val profile = gateway.storefront(businessId, userId)
        change { it.copy(storefront = profile) }
    }
    fun saveService(value: CatalogService) = run {
        gateway.saveService(requireNotNull(businessId), requireNotNull(userId), value)
        change { it.copy(page = "home", notice = "Service saved.") }
        val profile = gateway.storefront(businessId, userId)
        change { it.copy(storefront = profile) }
    }
}

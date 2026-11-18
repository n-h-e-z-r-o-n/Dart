package com.zadhron.adaa

import androidx.lifecycle.SavedStateHandle
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.viewmodel.BookingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BookingJourneyTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeBookings
    private val service = CatalogService("service", "Treatment", "", 350000, 60)
    @Before fun setup() { Dispatchers.setMain(dispatcher); gateway = FakeBookings() }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()
    private fun model(user: String? = "customer", saved: SavedStateHandle = SavedStateHandle(), business: String? = "business") =
        BookingViewModel(gateway, business, user, false, saved).also { settle() }
    private fun draft(vm: BookingViewModel) {
        vm.choose(service); vm.date = LocalDate.now().plusDays(2).toString(); vm.time = "10:00"; vm.message = "Please confirm this time."; vm.review()
    }
    @Test fun ambiguousNetworkFailureRetriesTheOriginalRequestAndPrice() {
        val vm = model(); draft(vm)
        gateway.submitError = IOException(); vm.submit(); settle()
        assertEquals("review", vm.state.value.page)
        val nonce = gateway.nonces.single()
        gateway.submitError = null; vm.submit(); settle()
        assertEquals(nonce, gateway.nonces.last())
        assertEquals(350000L, gateway.prices.last())
        assertEquals("sent", vm.state.value.page)
    }
    @Test fun changingTheDraftStartsANewIntent() {
        val vm = model(); draft(vm); gateway.submitError = IOException(); vm.submit(); settle()
        vm.page("request"); vm.message = "Changed details"; vm.review(); vm.submit(); settle()
        assertNotEquals(gateway.nonces.first(), gateway.nonces.last())
    }
    @Test fun guestDraftSurvivesSignInWithoutSendingAutomatically() {
        val vm = model(null); draft(vm)
        vm.bindUser("customer"); settle()
        assertEquals("Please confirm this time.", vm.message)
        assertEquals(service, vm.state.value.service)
        assertTrue(gateway.nonces.isEmpty())
        vm.submit(); settle(); assertEquals("sent", vm.state.value.page)
    }
    @Test fun inquiryDoesNotRequireDateOrPrice() {
        val vm = model(); vm.choose(null); vm.message = "Do you offer home visits?"; vm.review()
        assertEquals("review", vm.state.value.page)
        vm.submit(); settle(); assertNull(gateway.prices.single()); assertNull(gateway.times.single())
    }
    @Test fun missingAndPastTimesCannotBeReviewed() {
        val vm = model(); vm.choose(service); vm.message = "Please confirm"
        vm.review(); assertEquals("request", vm.state.value.page)
        vm.date = "2020-01-01"; vm.time = "10:00"; vm.review()
        assertEquals("request", vm.state.value.page); assertTrue(gateway.nonces.isEmpty())
    }
    @Test fun inquiryRetryReferenceSurvivesProcessRestoration() {
        val saved = SavedStateHandle()
        val vm = model(saved = saved); vm.choose(null); vm.message = "Question"; vm.review()
        gateway.submitError = IOException(); vm.submit(); settle()
        val nonce = gateway.nonces.single()
        val restored = model(saved = saved); restored.review(); restored.submit(); settle()
        assertEquals(nonce, gateway.nonces.last())
    }
    @Test fun identityChangeDropsPrivateRequestDetails() {
        val vm = model(business = null); vm.select(gateway.row); settle()
        assertNotNull(vm.state.value.selected)
        gateway.rows = emptyList(); vm.bindUser("another-customer"); settle()
        assertNull(vm.state.value.selected); assertTrue(vm.state.value.events.isEmpty()); assertTrue(vm.state.value.requests.isEmpty())
    }
    @Test fun failedReadAfterActionCannotRepeatMutation() {
        val vm = model(business = null); vm.select(gateway.row); settle()
        gateway.readError = IOException(); vm.act("cancelled", "Plans changed"); settle()
        assertEquals("home", vm.state.value.page); assertNull(vm.state.value.selected)
        gateway.readError = null; vm.refresh(); settle()
        assertEquals(1, gateway.actions)
    }
    private class FakeBookings : BookingGateway {
        val row = BookingRequest("r", "Studio", "", "Treatment", 350000, 60, "appointment", "2030-01-01T07:00:00Z", "Africa/Nairobi", "Hello", "pending", "", "2026-11-01T00:00:00Z", false, true)
        var rows = listOf(row)
        var readError: Exception? = null
        var submitError: Exception? = null
        var actions = 0
        val nonces = mutableListOf<String>(); val prices = mutableListOf<Long?>(); val times = mutableListOf<String?>()
        override suspend fun storefront(businessId: String, userId: String?) = Storefront(services = listOf(CatalogService("service", "Treatment", "", 350000, 60)))
        override suspend fun saveStorefront(businessId: String, userId: String, value: Storefront) {}
        override suspend fun saveService(businessId: String, userId: String, value: CatalogService) {}
        override suspend fun submit(userId: String, requestId: String, businessId: String, service: CatalogService?, preferredAt: String?, message: String): String {
            nonces += requestId; prices += service?.priceMinor; times += preferredAt; submitError?.let { throw it }; return "new"
        }
        override suspend fun requests(userId: String, businessId: String?, before: BookingRequest?, state: String?): List<BookingRequest> { readError?.let { throw it }; return rows }
        override suspend fun detail(userId: String, id: String) = row to listOf(BookingEvent("pending", "Submitted", row.createdAt))
        override suspend fun act(userId: String, id: String, action: String, response: String) { actions++ }
    }
}

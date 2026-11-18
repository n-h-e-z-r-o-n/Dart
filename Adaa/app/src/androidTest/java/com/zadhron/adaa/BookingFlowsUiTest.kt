package com.zadhron.adaa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.shared.BookingFeature
import com.zadhron.adaa.ui.theme.AdaaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import java.time.LocalDate

/** Deterministic transport; real Compose interaction on a phone. No live records/funds. */
@RunWith(AndroidJUnit4::class)
class BookingFlowsUiTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    private val business = BusinessDetails("business", "The Garden Studio", 1, "Wellness", "physical", "published", location = "Westlands, Nairobi", description = "A calm space for your everyday rituals.")
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null), "booking-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun show(gateway: FakeBookings, manage: Boolean = false, user: String? = "customer", inbox: Boolean = false) {
        compose.setContent { AdaaTheme { BookingFeature(user, if (inbox) null else business, manage, {}, {}, {}, {}, gateway) } }
    }
    @Test fun customerCanReviewAndSendAnInquiryWithoutBeingCharged() {
        val gateway = FakeBookings(); show(gateway)
        capture("01-storefront")
        compose.onNodeWithText("Send an inquiry").performScrollTo().performClick()
        compose.onNodeWithText("Your question").performScrollTo().performTextInput("Do you offer home visits?")
        compose.onNodeWithText("Review request").performScrollTo().performClick()
        compose.onNodeWithText("Check your request.").assertExists()
        capture("02-inquiry-review")
        compose.onNodeWithText("Send request").performScrollTo().performClick()
        compose.onNodeWithText("Your request is sent.").assertExists()
        capture("03-request-sent")
        assertEquals(1, gateway.submitted); assertNull(gateway.submittedService)
    }
    @Test fun pausedStorefrontDisablesRequestsButKeepsBusinessInformation() {
        val gateway = FakeBookings(); gateway.profile = gateway.profile.copy(acceptingRequests = false); show(gateway)
        compose.onNodeWithText("Send an inquiry").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Requests are paused").assertExists()
        assertEquals(0, gateway.submitted)
    }
    @Test fun customerRequestsPreferredTimeThroughNativePickersThenReviewsBeforeSending() {
        val gateway = FakeBookings(); show(gateway)
        compose.onNodeWithText("Request appointment").performScrollTo().performClick()
        compose.onNodeWithText("Choose a date").performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Choose a future preferred date"
            override fun perform(controller: UiController, view: View) {
                val day = LocalDate.now().plusDays(2); (view as DatePicker).updateDate(day.year, day.monthValue - 1, day.dayOfMonth)
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("Choose a time").performScrollTo().performClick()
        onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "Choose a preferred time"
            override fun perform(controller: UiController, view: View) { (view as TimePicker).hour = 10; view.minute = 30 }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("Details for the business").performScrollTo().performTextInput("Please confirm your team can accommodate me.")
        compose.onNodeWithText("Review request").performScrollTo().performClick()
        assertEquals(0, gateway.submitted)
        compose.onNodeWithText("Check your request.").assertExists()
        capture("06-appointment-review")
        compose.onNodeWithText("Send request").performScrollTo().performClick()
        compose.onNodeWithText("Your request is sent.").assertExists()
        assertEquals("service", gateway.submittedService?.id)
    }
    @Test fun businessMustConfirmBeforeAcceptingAndCanSeeHistory() {
        val gateway = FakeBookings(); show(gateway, manage = true, user = "owner")
        compose.onNodeWithText("View details & history").performScrollTo().performClick()
        capture("04-business-request")
        compose.onNodeWithText("Accept preferred time").performScrollTo().performClick()
        compose.onNodeWithText("Confirm this appointment?").assertExists()
        assertEquals(0, gateway.actions)
        compose.onNode(hasText("Confirm") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle(); assertEquals(1, gateway.actions)
        compose.onNodeWithText("Bookings & inquiries.").assertExists()
    }
    @Test fun staffCannotManageServicesOrAcceptAppointments() {
        val gateway = FakeBookings(); gateway.profile = gateway.profile.copy(canManage = false); gateway.row = gateway.row.copy(canManage = false)
        show(gateway, manage = true, user = "staff")
        compose.onNodeWithText("Manage services").assertDoesNotExist()
        compose.onNodeWithText("View details & history").performScrollTo().performClick()
        compose.onNodeWithText("Accept preferred time").assertDoesNotExist()
        compose.onNodeWithText("Cancel appointment").assertDoesNotExist()
    }
    @Test fun ownerCanEditServicesIndependentlyOfPaymentRequests() {
        val gateway = FakeBookings(); show(gateway, manage = true, user = "owner")
        compose.onNodeWithText("Manage services").performScrollTo().performClick()
        compose.onNodeWithText("Edit service").performScrollTo().performClick()
        capture("05-service-editor")
        compose.onNodeWithText("Service name").performScrollTo().performTextClearance()
        compose.onNodeWithText("Service name").performTextInput("Updated treatment")
        compose.onNodeWithText("Save service").performScrollTo().performClick()
        compose.waitForIdle(); assertEquals("Updated treatment", gateway.savedService?.name)
    }
    private class FakeBookings : BookingGateway {
        var profile = Storefront(hours = "Monday – Saturday · 09:00–18:00", canManage = true, services = listOf(CatalogService("service", "Signature treatment", "A thoughtful hour.", 350000, 60)))
        var row = BookingRequest("request", "The Garden Studio", "Alex", "Signature treatment", 350000, 60, "appointment", "2030-01-01T07:00:00Z", "Africa/Nairobi", "Please confirm this time.", "pending", "", "2026-11-04T00:00:00Z", true, false)
        var submitted = 0; var actions = 0; var submittedService: CatalogService? = null; var savedService: CatalogService? = null
        override suspend fun storefront(businessId: String, userId: String?) = profile
        override suspend fun saveStorefront(businessId: String, userId: String, value: Storefront) { profile = value }
        override suspend fun saveService(businessId: String, userId: String, value: CatalogService) { savedService = value }
        override suspend fun submit(userId: String, requestId: String, businessId: String, service: CatalogService?, preferredAt: String?, message: String): String { submitted++; submittedService = service; return "request" }
        override suspend fun requests(userId: String, businessId: String?, before: BookingRequest?, state: String?) = if (state == null || state == row.state) listOf(row) else emptyList()
        override suspend fun detail(userId: String, id: String) = row to listOf(BookingEvent("pending", "Request submitted", row.createdAt))
        override suspend fun act(userId: String, id: String, action: String, response: String) { actions++; row = row.copy(state = action) }
    }
}

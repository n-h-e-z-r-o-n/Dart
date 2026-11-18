package com.zadhron.adaa.ui.shared

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.personal.catalogPrice
import com.zadhron.adaa.ui.viewmodel.BookingState
import com.zadhron.adaa.ui.viewmodel.BookingViewModel
import java.time.*

@Composable
fun BookingRequestScreen(state: BookingState, model: BookingViewModel, signedIn: Boolean, onSignIn: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(model.date) }
    var time by rememberSaveable { mutableStateOf(model.time) }
    var message by rememberSaveable { mutableStateOf(model.message) }
    val service = state.service
    JourneyPage(if (service == null) "Ask the business." else "Your preferred visit.",
        if (service == null) "Send your question. The business will reply here." else "Request a time that suits you. The business will check its team and capacity before confirming.",
        "STEP 1 OF 2 · ${if (service == null) "INQUIRY" else "APPOINTMENT REQUEST"}", onBack) {
        if (service != null) {
            InfoPanel(service.name, "${catalogPrice(service.priceMinor)} · ${service.durationMinutes} minutes", Icons.Outlined.Spa)
            val timezone = state.storefront?.timezone ?: "Africa/Nairobi"
            Text("Preferred date and time · $timezone", style = MaterialTheme.typography.titleMedium)
            SecondaryAction(if (date.isBlank()) "Choose a date" else date, {
                val initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now(ZoneId.of(timezone)).plusDays(1))
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString(); model.date = date }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }, !state.busy, Icons.Outlined.CalendarMonth)
            SecondaryAction(if (time.isBlank()) "Choose a time" else time, {
                val initial = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(10, 0))
                TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString(); model.time = time }, initial.hour, initial.minute, true).show()
            }, !state.busy, Icons.Outlined.Schedule)
        }
        OutlinedTextField(message, { if (it.length <= 2000) { message = it; model.message = it } }, Modifier.fillMaxWidth(), enabled = !state.busy,
            label = { Text(if (service == null) "Your question" else "Details for the business") }, minLines = 3,
            supportingText = { Text("${message.length}/2000 · Include relevant preferences. Avoid sensitive information.") }, shape = RoundedCornerShape(18.dp))
        if (!signedIn) InfoPanel("Sign in before sending", "Your service, preferred time and message will be kept while you sign in.", Icons.Outlined.PersonOutline)
        PrimaryAction(if (signedIn) "Review request" else "Sign in to continue", if (signedIn) model::review else onSignIn, !state.busy)
    }
}

@Composable
fun BookingReviewScreen(state: BookingState, model: BookingViewModel) {
    JourneyPage("Check your request.", "You are sending a request, not a confirmed appointment.", "STEP 2 OF 2 · REVIEW", { model.page("request") }) {
        InfoPanel(state.service?.name ?: "General inquiry", state.service?.let { "${catalogPrice(it.priceMinor)} · ${it.durationMinutes} minutes" } ?: "Ask about services, prices or visiting.", Icons.Outlined.EventNote)
        if (state.service != null) Text(runCatching { bookingTime(model.requestedInstant(), state.storefront?.timezone ?: "Africa/Nairobi") }.getOrDefault("Check the date and time."), style = MaterialTheme.typography.titleMedium)
        Text(model.message, style = MaterialTheme.typography.bodyLarge)
        InfoPanel("What happens next", "The business will accept or decline your appointment, or reply to your inquiry. Track updates under My bookings & inquiries. Sending this request does not charge your wallet.", Icons.Outlined.Info)
        PrimaryAction("Send request", model::submit, !state.busy, busy = state.busy)
        SecondaryAction("Edit details", { model.page("request") }, !state.busy)
    }
}

@Composable
fun BookingInbox(requests: List<BookingRequest>, busy: Boolean, hasMore: Boolean, business: Boolean, onBack: () -> Unit,
    onSelect: (BookingRequest) -> Unit, onRefresh: () -> Unit, onMore: () -> Unit, filter: String? = null,
    onFilter: (String?) -> Unit = {}, header: (@Composable () -> Unit)? = null) {
    JourneyPage(if (business) "Bookings & inquiries." else "Your plans, in one place.",
        if (business) "Review requested times against your team and capacity. Customers are waiting for your confirmation."
        else "Check confirmations, business replies and the history of your requests.", if (business) "BUSINESS WORKSPACE" else "MY BOOKINGS & INQUIRIES", onBack) {
        header?.invoke()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "All", "pending" to "Pending", "confirmed" to "Confirmed", "completed" to "Completed", "closed" to "Closed").forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { onFilter(value) }, enabled = !busy, label = { Text(label) })
            }
        }
        SecondaryAction("Refresh requests", onRefresh, !busy, Icons.Outlined.Refresh)
        if (requests.isEmpty()) InfoPanel(if (filter == null) "Nothing here yet" else "No matching requests", if (filter != null) "Choose another status or refresh for new updates." else if (business) "Customer appointment requests and inquiries will appear here. Add services and publish your listing to get started."
            else "Explore a business and request an appointment or send an inquiry. Updates will appear here.", Icons.Outlined.EventNote)
        requests.forEach { row ->
            OutlinedCard(onClick = { onSelect(row) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (business) row.customerName.ifBlank { "Customer request" } else row.businessName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(row.serviceName, style = MaterialTheme.typography.titleLarge)
                    if (row.preferredAt.isNotBlank()) Text(bookingTime(row.preferredAt, row.timezone), style = MaterialTheme.typography.bodyMedium)
                    Text(bookingStatus(row.state), style = MaterialTheme.typography.labelLarge)
                    row.priceMinor?.let { Text(catalogPrice(it), style = MaterialTheme.typography.bodyMedium) }
                    Text("View details & history", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (hasMore) SecondaryAction("Load older requests", onMore, !busy)
    }
}

@Composable
fun BookingDetailScreen(state: BookingState, model: BookingViewModel, onPayments: () -> Unit) {
    val row = state.selected ?: return
    var response by rememberSaveable(row.id) { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    JourneyPage(row.serviceName, row.businessName, bookingStatus(row.state), { model.page("home") }) {
        if (row.preferredAt.isNotBlank()) DetailRow("Preferred time", bookingTime(row.preferredAt, row.timezone), Icons.Outlined.Schedule)
        row.priceMinor?.let { DetailRow("Quoted service price", catalogPrice(it), Icons.Outlined.Payments) }
        row.durationMinutes?.let { Text("Estimated duration: $it minutes") }
        Text(row.message, style = MaterialTheme.typography.bodyLarge)
        if (row.response.isNotBlank()) InfoPanel("Latest update", row.response, Icons.Outlined.ChatBubbleOutline)
        if (row.state in listOf("pending", "confirmed") && (row.canManage || row.isCustomer)) {
            OutlinedTextField(response, { if (it.length <= 2000) response = it }, Modifier.fillMaxWidth(), label = { Text("Reply / reason") },
                supportingText = { Text("Required for a decline, cancellation or inquiry reply.") }, minLines = 2, enabled = !state.busy)
            if (row.canManage && row.state == "pending") {
                if (row.kind == "appointment") PrimaryAction("Accept preferred time", { confirm = "confirmed" }, !state.busy)
                else PrimaryAction("Send reply & close inquiry", { confirm = "answered" }, !state.busy && response.isNotBlank())
                SecondaryAction("Decline request", { confirm = "declined" }, !state.busy && response.isNotBlank())
            }
            if (row.canManage && row.state == "confirmed" && runCatching { Instant.parse(row.preferredAt) <= Instant.now() }.getOrDefault(false)) {
                PrimaryAction("Mark appointment completed", { confirm = "completed" }, !state.busy)
                SecondaryAction("Record a no-show", { confirm = "no_show" }, !state.busy && response.isNotBlank())
            }
            SecondaryAction("Cancel ${if (row.kind == "inquiry") "inquiry" else "appointment"}", { confirm = "cancelled" }, !state.busy && response.isNotBlank())
            if (row.isCustomer && row.kind == "appointment") Text("Need a different time? Cancel with a reason, then send a new request from the storefront. Your existing time stays in place until you cancel it.", style = MaterialTheme.typography.bodyMedium)
        }
        if (row.kind == "appointment") {
            InfoPanel("Booking and payment are separate", "A confirmation does not collect payment. Review any service payment request separately. Cancelling an appointment does not automatically refund a payment.", Icons.Outlined.AccountBalanceWallet)
            SecondaryAction("Open service payments", onPayments, !state.busy)
        }
        Text("Request history", style = MaterialTheme.typography.titleLarge)
        state.events.forEach { event ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bookingStatus(event.state), style = MaterialTheme.typography.titleSmall)
                if (event.message.isNotBlank()) Text(event.message, style = MaterialTheme.typography.bodyMedium)
                Text(bookingTime(event.createdAt, row.timezone), style = MaterialTheme.typography.labelSmall)
                HorizontalDivider()
            }
        }
        SecondaryAction("Refresh status & history", { model.select(row) }, !state.busy)
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(when(action) {
        "confirmed" -> "Confirm this appointment?"; "completed" -> "Mark as completed?"; "answered" -> "Send this reply?"; "declined" -> "Decline this request?"; "no_show" -> "Record that the customer did not attend?"; else -> "Cancel this request?"
    }) }, text = { Text(if (action == "confirmed") "Confirm that your team can serve this customer at their requested time. Other customers can request the same time."
        else if (action == "completed") "Only mark the appointment complete after the service was provided. This does not release wallet escrow."
        else response) }, confirmButton = { TextButton(onClick = { confirm = null; model.act(action, response) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Go back") } }) }
}

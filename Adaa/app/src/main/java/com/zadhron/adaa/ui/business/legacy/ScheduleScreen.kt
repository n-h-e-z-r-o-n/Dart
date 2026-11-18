package com.zadhron.adaa.ui.business.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.Appointment
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.ui.components.BookingDialog
import com.zadhron.adaa.ui.theme.PrimaryRose
import com.zadhron.adaa.ui.theme.StatusCancelled
import com.zadhron.adaa.ui.theme.StatusCancelledBg
import com.zadhron.adaa.ui.theme.StatusCompleted
import com.zadhron.adaa.ui.theme.StatusCompletedBg
import com.zadhron.adaa.ui.theme.StatusScheduled
import com.zadhron.adaa.ui.theme.StatusScheduledBg
import com.zadhron.adaa.ui.viewmodel.AdaaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleScreen(viewModel: AdaaViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val appointments by viewModel.appointmentsForSelectedDate.collectAsState()
    val rawClients by viewModel.rawClients.collectAsState()
    val rawServices by viewModel.rawServices.collectAsState()
    val isBookingVisible by viewModel.isBookingDialogVisible.collectAsState()
    ScheduleScreen(selectedDate, appointments, rawClients, rawServices, isBookingVisible,
        { viewModel.isBookingDialogVisible.value = it }, viewModel::selectDate,
        viewModel::updateAppointmentStatus, viewModel::deleteAppointment, viewModel::bookAppointment)
}

@Composable
fun ScheduleScreen(
    selectedDate: LocalDate, appointments: List<Appointment>,
    rawClients: List<com.zadhron.adaa.data.model.Client> = emptyList(),
    rawServices: List<com.zadhron.adaa.data.model.Service> = emptyList(), isBookingVisible: Boolean = false,
    onDialogVisible: (Boolean) -> Unit = {}, onSelectDate: (LocalDate) -> Unit = {},
    onStatus: (Long, AppointmentStatus) -> Unit = { _, _ -> }, onDelete: (Long) -> Unit = {},
    onBook: (com.zadhron.adaa.data.model.Client, com.zadhron.adaa.data.model.Service, Long, String) -> Unit = { _, _, _, _ -> }
) {

    // Generate date strip: 3 days prior to 14 days ahead
    val dates = remember {
        val today = LocalDate.now()
        (-3L..14L).map { today.plusDays(it) }
    }

    val totalExpectedForDay = appointments
        .filter { it.status != AppointmentStatus.CANCELLED }
        .sumOf { it.price }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onDialogVisible(true) },
                containerColor = PrimaryRose,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Book Appointment")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header section
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Adaa Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryRose,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${appointments.size} Appointments") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$$totalExpectedForDay Projected") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Horizontal Date Strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(dates) { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == LocalDate.now()

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryRose else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 6.dp else 1.dp,
                        modifier = Modifier
                            .width(62.dp)
                            .height(84.dp)
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.border(1.5.dp, PrimaryRose, RoundedCornerShape(16.dp))
                                } else Modifier
                            )
                            .clickable { onSelectDate(date) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("EEE")).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appointments List
            if (appointments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryRose.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = PrimaryRose,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Appointments Scheduled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the + button below to book an appointment for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(appointments, key = { it.id }) { appt ->
                        AppointmentCard(
                            appointment = appt,
                            onMarkCompleted = {
                                onStatus(appt.id, AppointmentStatus.COMPLETED)
                            },
                            onCancel = {
                                onStatus(appt.id, AppointmentStatus.CANCELLED)
                            },
                            onDelete = {
                                onDelete(appt.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (isBookingVisible) {
        BookingDialog(
            initialDate = selectedDate,
            clients = rawClients,
            services = rawServices,
            onDismiss = { onDialogVisible(false) },
            onConfirm = { client, service, startEpoch, notes ->
                onBook(client, service, startEpoch, notes)
            }
        )
    }
}

@Composable
fun AppointmentCard(
    appointment: Appointment,
    onMarkCompleted: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (appointment.status) {
        AppointmentStatus.SCHEDULED -> StatusScheduled
        AppointmentStatus.COMPLETED -> StatusCompleted
        AppointmentStatus.CANCELLED -> StatusCancelled
    }
    val statusBg = when (appointment.status) {
        AppointmentStatus.SCHEDULED -> StatusScheduledBg
        AppointmentStatus.COMPLETED -> StatusCompletedBg
        AppointmentStatus.CANCELLED -> StatusCancelledBg
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Time, Category & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${appointment.formattedTime} - ${appointment.formattedEndTime}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "(${appointment.durationMinutes}m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg
                ) {
                    Text(
                        text = appointment.status.label,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle: Client info & Service details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = appointment.serviceTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryRose,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (appointment.clientPhone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = appointment.clientPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "$${appointment.price}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (appointment.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Note: ${appointment.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appointment.status == AppointmentStatus.SCHEDULED) {
                    IconButton(onClick = onMarkCompleted) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Mark Completed",
                            tint = StatusCompleted
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel Appointment",
                            tint = StatusCancelled
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Schedule Screen · With Bookings", showBackground = true, showSystemUi = true)
@Composable
fun ScheduleScreenWithBookingsPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ScheduleScreen(
            selectedDate = LocalDate.now(),
            appointments = com.zadhron.adaa.ui.preview.PreviewData.sampleAppointments,
            rawClients = com.zadhron.adaa.ui.preview.PreviewData.sampleClients,
            rawServices = com.zadhron.adaa.ui.preview.PreviewData.sampleServices
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Schedule Screen · Empty Day", showBackground = true, showSystemUi = true)
@Composable
fun ScheduleScreenEmptyDayPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        ScheduleScreen(
            selectedDate = LocalDate.now(),
            appointments = emptyList(),
            rawClients = com.zadhron.adaa.ui.preview.PreviewData.sampleClients,
            rawServices = com.zadhron.adaa.ui.preview.PreviewData.sampleServices
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Appointment Card", showBackground = true)
@Composable
fun AppointmentCardItemPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AppointmentCard(
                appointment = com.zadhron.adaa.ui.preview.PreviewData.sampleAppointments[0],
                onMarkCompleted = {},
                onCancel = {},
                onDelete = {}
            )
        }
    }
}
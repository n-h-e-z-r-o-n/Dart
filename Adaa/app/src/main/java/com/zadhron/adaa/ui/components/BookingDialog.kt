package com.zadhron.adaa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.ui.theme.PrimaryRose
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BookingDialog(
    initialDate: LocalDate,
    clients: List<Client>,
    services: List<Service>,
    onDismiss: () -> Unit,
    onConfirm: (client: Client, service: Service, startEpochMillis: Long, notes: String) -> Unit
) {
    var selectedClient by remember { mutableStateOf(clients.firstOrNull()) }
    var selectedService by remember { mutableStateOf(services.firstOrNull()) }
    var clientDropdownExpanded by remember { mutableStateOf(false) }
    var serviceDropdownExpanded by remember { mutableStateOf(false) }

    var selectedHour by remember { mutableIntStateOf(10) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var timeDropdownExpanded by remember { mutableStateOf(false) }

    var notes by remember { mutableStateOf("") }

    val times = listOf(
        Pair(9, 0), Pair(9, 30),
        Pair(10, 0), Pair(10, 30),
        Pair(11, 0), Pair(11, 30),
        Pair(12, 0), Pair(12, 30),
        Pair(13, 0), Pair(13, 30),
        Pair(14, 0), Pair(14, 30),
        Pair(15, 0), Pair(15, 30),
        Pair(16, 0), Pair(16, 30),
        Pair(17, 0), Pair(17, 30),
        Pair(18, 0)
    )

    fun formatHourMinute(hour: Int, minute: Int): String {
        val t = LocalTime.of(hour, minute)
        return t.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Book Appointment", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = PrimaryRose)
                        Text(
                            text = initialDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Client Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "Select Client",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Client *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clientDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = clientDropdownExpanded,
                        onDismissRequest = { clientDropdownExpanded = false }
                    ) {
                        clients.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${c.phone})") },
                                onClick = {
                                    selectedClient = c
                                    clientDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Service Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedService?.let { "${it.title} â€¢ $${it.price} (${it.durationMinutes}m)" } ?: "Select Service",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Service *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { serviceDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = serviceDropdownExpanded,
                        onDismissRequest = { serviceDropdownExpanded = false }
                    ) {
                        services.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.title} â€” $${s.price} (${s.durationMinutes}m)") },
                                onClick = {
                                    selectedService = s
                                    serviceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Time Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = formatHourMinute(selectedHour, selectedMinute),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Start Time *") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timeDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = timeDropdownExpanded,
                        onDismissRequest = { timeDropdownExpanded = false }
                    ) {
                        times.forEach { (h, m) ->
                            DropdownMenuItem(
                                text = { Text(formatHourMinute(h, m)) },
                                onClick = {
                                    selectedHour = h
                                    selectedMinute = m
                                    timeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Special Requests / Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val canConfirm = selectedClient != null && selectedService != null
            Button(
                onClick = {
                    if (canConfirm) {
                        val zone = ZoneId.systemDefault()
                        val dateTime = initialDate.atTime(selectedHour, selectedMinute)
                        val epoch = dateTime.atZone(zone).toInstant().toEpochMilli()
                        onConfirm(selectedClient!!, selectedService!!, epoch, notes.trim())
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRose)
            ) {
                Text("Confirm Booking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Booking Appointment Dialog", showBackground = true)
@Composable
fun BookingDialogPreview() {
    com.zadhron.adaa.ui.theme.AdaaTheme {
        BookingDialog(
            initialDate = java.time.LocalDate.now(),
            clients = com.zadhron.adaa.ui.preview.PreviewData.sampleClients,
            services = com.zadhron.adaa.ui.preview.PreviewData.sampleServices,
            onDismiss = {},
            onConfirm = { _, _, _, _ -> }
        )
    }
}
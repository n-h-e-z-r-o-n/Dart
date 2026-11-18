package com.zadhron.adaa.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.personal.ServiceCard
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.theme.AdaaTheme
import java.math.BigDecimal
import java.util.UUID

@Composable
fun CatalogManagementScreen(profile: Storefront, busy: Boolean, onBack: () -> Unit, onEdit: (CatalogService?) -> Unit) {
    JourneyPage("Your service menu.", "Customers see active services and request a preferred time. You decide how many appointments your team can accept.", "BUSINESS · SERVICES", onBack) {
        PrimaryAction("Add a service", { onEdit(null) }, !busy)
        if (profile.services.isEmpty()) Text("No services yet. Add your first service, price and estimated duration.")
        profile.services.forEach { ServiceCard(it, busy, { onEdit(it) }, "Edit service") }
    }
}

@Composable
fun ServiceEditorScreen(service: CatalogService?, busy: Boolean, onBack: () -> Unit, onSave: (CatalogService) -> Unit) {
    val id = rememberSaveable(service?.id) { service?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(service?.name.orEmpty()) }
    var description by rememberSaveable(id) { mutableStateOf(service?.description.orEmpty()) }
    var price by rememberSaveable(id) { mutableStateOf(service?.let { BigDecimal.valueOf(it.priceMinor, 2).toPlainString() }.orEmpty()) }
    var duration by rememberSaveable(id) { mutableStateOf(service?.durationMinutes?.toString() ?: "60") }
    var active by rememberSaveable(id) { mutableStateOf(service?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    JourneyPage(if (service == null) "A new service." else "Refine your service.", "Prices are in KES. Existing booking requests keep their original quoted price.", "BUSINESS · SERVICE DETAILS", onBack) {
        OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), label = { Text("Service name") }, enabled = !busy)
        OutlinedTextField(description, { if (it.length <= 2000) description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, enabled = !busy)
        OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Price (KES)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
        OutlinedTextField(duration, { duration = it }, Modifier.fillMaxWidth(), label = { Text("Estimated duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
        Row { Checkbox(active, { active = it }, enabled = !busy); Text("Visible to customers") }
        Text("Hide a service to stop new requests. Its existing requests and history remain available.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryAction("Save service", {
            runCatching {
                require(name.trim().isNotEmpty()) { "Enter a service name." }
                require(price.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,2})?"))) { "Enter a KES amount with up to two decimal places." }
                val amount = BigDecimal(price).movePointRight(2).longValueExact()
                require(amount in 0..100000000) { "Enter a price between KES 0 and KES 1,000,000, with up to two decimal places." }
                val minutes = duration.toIntOrNull()
                require(minutes != null && minutes in 5..1440) { "Enter a duration from 5 to 1,440 minutes." }
                CatalogService(id, name.trim(), description.trim(), amount, minutes, active)
            }.onSuccess(onSave).onFailure { error = it.message ?: "Check the price and duration." }
        }, !busy)
    }
}

@Composable
fun StorefrontEditorScreen(profile: Storefront, busy: Boolean, onBack: () -> Unit, onSave: (Storefront) -> Unit) {
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var website by rememberSaveable { mutableStateOf(profile.website) }
    var hours by rememberSaveable { mutableStateOf(profile.hours) }
    var timezone by rememberSaveable { mutableStateOf(profile.timezone) }
    var photos by rememberSaveable { mutableStateOf(profile.photos.joinToString("\n")) }
    var accepting by rememberSaveable { mutableStateOf(profile.acceptingRequests) }
    var error by remember { mutableStateOf<String?>(null) }
    JourneyPage("An inviting storefront.", "These contact details and photos are public. Keep your opening hours accurate, including breaks and closed days.", "BUSINESS · PUBLIC DETAILS", onBack) {
        OutlinedTextField(phone, { if (it.length <= 40) phone = it }, Modifier.fillMaxWidth(), label = { Text("Public phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !busy)
        OutlinedTextField(email, { if (it.length <= 254) email = it }, Modifier.fillMaxWidth(), label = { Text("Public email address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy)
        OutlinedTextField(website, { website = it }, Modifier.fillMaxWidth(), label = { Text("Website (https://…)") }, enabled = !busy)
        OutlinedTextField(hours, { if (it.length <= 1500) hours = it }, Modifier.fillMaxWidth(), label = { Text("Opening hours & visiting notes") }, minLines = 3, enabled = !busy)
        OutlinedTextField(timezone, { timezone = it }, Modifier.fillMaxWidth(), label = { Text("Business time zone") }, supportingText = { Text("IANA name, for example Africa/Nairobi. Customer requests use this zone.") }, enabled = !busy)
        OutlinedTextField(photos, { photos = it }, Modifier.fillMaxWidth(), label = { Text("Photo links · one HTTPS URL per line") }, minLines = 3,
            supportingText = { Text("Up to 8 public image links. First photo is the cover. Use images you have permission to publish; up to 4 MB each.") }, enabled = !busy)
        Row { Switch(accepting, { accepting = it }, enabled = !busy); Text("Accept new requests & inquiries") }
        Text("Pausing requests keeps your listing and existing requests visible. It does not cancel any appointment.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryAction("Save public details", {
            runCatching {
                java.time.ZoneId.of(timezone.trim())
                val links = photos.lines().map(String::trim).filter(String::isNotBlank)
                require(links.size <= 8) { "Use up to 8 photo links." }
                (links + listOf(website.trim()).filter(String::isNotBlank)).forEach {
                    val uri = java.net.URI(it)
                    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Website and photo links must be valid HTTPS URLs." }
                }
                profile.copy(phone = phone.trim(), email = email.trim(), website = website.trim(), hours = hours.trim(), timezone = timezone.trim(), photos = links, acceptingRequests = accepting)
            }.onSuccess(onSave).onFailure { error = it.message ?: "Check your public details." }
        }, !busy)
    }
}

@Preview(name = "Business service editor", showBackground = true, showSystemUi = true)
@Composable
fun ServiceEditorPreview() { AdaaTheme { ServiceEditorScreen(CatalogService("s", "Signature treatment", "A thoughtful hour.", 350000, 60), false, {}, {}) } }

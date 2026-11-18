package com.zadhron.adaa.ui.personal

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.theme.AdaaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.math.BigDecimal

fun catalogPrice(minor: Long) = "KES ${BigDecimal.valueOf(minor, 2).toPlainString()}"

@Composable
fun StorefrontScreen(business: BusinessDetails, profile: Storefront, busy: Boolean, onBack: () -> Unit,
    onService: (CatalogService) -> Unit, onInquiry: () -> Unit, onBookings: () -> Unit) {
    val context = LocalContext.current
    var contactError by remember { mutableStateOf<String?>(null) }
    fun open(uri: Uri) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: android.content.ActivityNotFoundException) { contactError = "No app is available to open this contact option." }
        catch (_: SecurityException) { contactError = "This contact option could not be opened." }
    }
    JourneyPage(business.name, business.category, "DISCOVER · CONNECT · VISIT", onBack) {
        if (profile.photos.isNotEmpty()) StorefrontPhoto(profile.photos.first(), "${business.name} cover photo", Modifier.fillMaxWidth().height(220.dp))
        else Surface(shape = RoundedCornerShape(28.dp), color = BrandPlum) { StorefrontArtwork(Modifier.fillMaxWidth().height(190.dp)) }
        if (business.verification == "verified") DetailRow("Verified business", "Verified by Adaa", Icons.Outlined.Verified)
        if (business.description.isNotBlank()) {
            Text("Meet ${business.name}", style = MaterialTheme.typography.titleLarge)
            Text(business.description, style = MaterialTheme.typography.bodyLarge)
        }
        if (business.location.isNotBlank()) {
            DetailRow("Location / service area", business.location, Icons.Outlined.LocationOn)
            if (business.operatingModel == "physical") SecondaryAction("Get directions", {
                open(Uri.parse("geo:0,0?q=${Uri.encode(business.location)}"))
            }, icon = Icons.Outlined.NearMe)
        }
        if (profile.hours.isNotBlank()) InfoPanel("Opening hours", profile.hours + "\nTimes shown in ${profile.timezone}.", Icons.Outlined.Schedule)
        else Text("Opening hours have not been added. Ask the business before visiting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Services & prices", style = MaterialTheme.typography.headlineSmall)
        Text("Choose a service and request a preferred time. Your appointment is confirmed only when the business accepts it.", style = MaterialTheme.typography.bodyMedium)
        val services = profile.services.filter { it.active }
        if (services.isEmpty()) InfoPanel("Let's start a conversation", "This business hasn't listed its services yet. Send an inquiry to ask about availability or pricing.", Icons.Outlined.ChatBubbleOutline)
        services.forEach { service ->
            ServiceCard(service, busy || !profile.acceptingRequests, { onService(service) })
        }
        if (!profile.acceptingRequests) InfoPanel("Requests are paused", "This business is not accepting new appointment requests or inquiries right now. You can still use its published contact details.", Icons.Outlined.PauseCircleOutline)
        PrimaryAction("Send an inquiry", onInquiry, enabled = !busy && profile.acceptingRequests)
        SecondaryAction("My bookings & inquiries", onBookings, !busy, Icons.Outlined.EventNote)
        if (profile.phone.isNotBlank() || profile.email.isNotBlank() || profile.website.isNotBlank()) {
            Text("Get in touch", style = MaterialTheme.typography.titleLarge)
            if (profile.phone.isNotBlank()) SecondaryAction("Call ${profile.phone}", { open(Uri.fromParts("tel", profile.phone, null)) }, icon = Icons.Outlined.Call)
            if (profile.email.isNotBlank()) SecondaryAction("Email the business", { open(Uri.fromParts("mailto", profile.email, null)) }, icon = Icons.Outlined.Email)
            if (profile.website.isNotBlank()) SecondaryAction("Visit website", { open(Uri.parse(profile.website)) }, icon = Icons.Outlined.Language)
        }
        contactError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (profile.photos.size > 1) {
            Text("A closer look", style = MaterialTheme.typography.titleLarge)
            profile.photos.drop(1).forEachIndexed { index, url -> StorefrontPhoto(url, "${business.name} gallery photo ${index + 1}", Modifier.fillMaxWidth().height(210.dp)) }
        }
    }
}

@Composable
fun ServiceCard(service: CatalogService, disabled: Boolean, onClick: () -> Unit, action: String = "Request appointment") {
    OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(service.name, style = MaterialTheme.typography.titleLarge)
            Text("${catalogPrice(service.priceMinor)} · ${service.durationMinutes} min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (service.description.isNotBlank()) Text(service.description, style = MaterialTheme.typography.bodyMedium)
            if (!service.active) Text("Hidden from customers", style = MaterialTheme.typography.labelLarge)
            SecondaryAction(action, onClick, !disabled)
        }
    }
}

/** Bounded HTTPS download and sampled decode. Failed images keep an accessible fallback. */
@Composable
fun StorefrontPhoto(url: String, description: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val target = URL(url)
                require(target.protocol == "https" && target.userInfo == null)
                val c = target.openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = false; c.connectTimeout = 8000; c.readTimeout = 8000
                    require(c.responseCode == 200 && c.contentType?.startsWith("image/") == true)
                    val bytes = c.inputStream.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val size = stream.read(buffer); if (size < 0) break; require(out.size() + size <= 4 * 1024 * 1024); out.write(buffer, 0, size) }
                        out.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply { inSampleSize = 1; while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }
    Surface(modifier.clip(RoundedCornerShape(24.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
        bitmap?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Box(Modifier.padding(24.dp)) { Text("Business photo", style = MaterialTheme.typography.labelLarge) }
    }
}

@Preview(name = "Customer storefront", showBackground = true, showSystemUi = true)
@Composable
fun StorefrontPreview() { AdaaTheme { StorefrontScreen(
    BusinessDetails("preview", "The Garden Studio", 1, "Beauty & wellness", "physical", "published", location = "Westlands, Nairobi", description = "A calm space for your everyday rituals."),
    Storefront(hours = "Monday – Saturday · 09:00–18:00", services = listOf(CatalogService("s", "Signature treatment", "A thoughtful hour, just for you.", 350000, 60))), false, {}, {}, {}, {}) } }

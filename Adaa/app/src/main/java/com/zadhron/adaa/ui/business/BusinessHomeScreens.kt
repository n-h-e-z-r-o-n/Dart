package com.zadhron.adaa.ui.business

import com.zadhron.adaa.ui.shared.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.BusinessDetails
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun BusinessesScreen(state: FoundationState, actions: JourneyActions) {
    JourneyPage(if (state.businesses.isEmpty()) "A home for\nyour business." else "Your businesses.",
        if (state.businesses.isEmpty()) "You're signed in. Create a business draft to get started, or join a team through its owner."
        else "Choose a workspace. Your access is specific to each business.", "BUSINESS MODE") {
        state.businesses.forEach { business ->
            Card(onClick = { actions.openBusiness(business.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storefront, null, Modifier.size(25.dp))
                        Spacer(Modifier.weight(1f)); StatusPill(business.status)
                    }
                    Text(business.name, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${business.role.replaceFirstChar { it.uppercase() }} access", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Open business", Modifier.size(20.dp))
                    }
                }
            }
        }
        if (state.businesses.isEmpty()) {
            Surface(shape = RoundedCornerShape(26.dp), color = BrandPlum) { StorefrontArtwork(Modifier.fillMaxWidth().height(190.dp)) }
            InfoPanel("A few details. A new beginning.", "Add your business name, choose a category, and tell us where you work. Publishing comes later.", Icons.Outlined.AutoAwesome)
        }
        PrimaryAction(if (state.draft.name.isNotBlank()) "Continue business setup" else if (state.businesses.isEmpty()) "Set up my business" else "Add another business",
            actions::startSetup, enabled = !state.busy)
        TextButton(onClick = actions::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh my businesses") }
    }
}

@Composable
fun BusinessHomeScreen(state: FoundationState, actions: JourneyActions) {
    val business = state.business
    if (business == null) {
        JourneyPage("Your business home.", if (state.busy) "Loading your workspace…" else "We couldn't open this workspace. Retry or choose another business.", "BUSINESS MODE") {
            if (!state.busy) PrimaryAction("Try again", actions::refresh)
            SecondaryAction("Choose a business", actions::chooseBusiness, !state.busy)
        }
        return
    }
    var publishDialog by remember { mutableStateOf(false) }
    JourneyPage("Welcome to\n${business.name}.", "Your business overview and next steps.", "BUSINESS DASHBOARD") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StatusPill(business.status)
            TextButton(onClick = actions::chooseBusiness, enabled = !state.busy) { Text("Switch business"); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
        }
        if (business.role == "owner") SecondaryAction("Open business wallet", actions::businessWallet, !state.busy, Icons.Outlined.AccountBalanceWallet)
        SecondaryAction("Services & payments", actions::businessPayments, !state.busy, Icons.Outlined.Receipt)
        SecondaryAction("Storefront, bookings & inquiries", actions::businessBookings, !state.busy, Icons.Outlined.EventNote)
        if (business.wallet != null) BusinessDashboardMetrics(business)
        Surface(shape = RoundedCornerShape(26.dp), color = BrandPlum) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(if (business.status == "published") Icons.Outlined.CheckCircle else Icons.Outlined.Storefront,
                    null, Modifier.size(30.dp), tint = BrandChampagne)
                Text(when (business.status) {
                    "published" -> "You're open for discovery."
                    "suspended" -> "Your listing is suspended."
                    else -> if (business.canPublish) "Your next step: go live." else "Your team is getting ready."
                }, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFFF6EE))
                Text(when (business.status) {
                    "published" -> "Your business profile is visible to customers on Adaa. Keep your details fresh."
                    "suspended" -> "Customers can't discover this listing. Contact the platform team for help."
                    else -> if (business.canPublish) "Your business is saved privately. Check the details, then publish when you're ready."
                    else "This listing is private. An owner or manager can publish it when it's ready."
                }, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFDDCDD4))
                PrimaryAction(if (business.status == "published") "View my listing" else "Review my listing",
                    actions::previewBusiness, enabled = !state.busy, inverse = true)
            }
        }
        if (business.role == "owner") SecondaryAction("Team permissions", actions::openTeam, !state.busy, Icons.Outlined.Groups)
        if (business.wallet == null) BusinessDashboardMetrics(business)
        Text("Your business at a glance", style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DetailRow("Category", business.category, Icons.Outlined.Category)
                DetailRow("How you operate", operatingModelLabel(business.operatingModel), Icons.Outlined.Storefront)
                DetailRow("Your access", business.role.replaceFirstChar { it.uppercase() }, Icons.Outlined.VerifiedUser)
                DetailRow("Team", "${business.membersCount} ${if (business.membersCount == 1) "member" else "members"}", Icons.Outlined.Groups)
            }
        }
        if (business.role in listOf("owner", "manager") && business.status != "suspended")
            SecondaryAction("Edit business details", actions::editBusiness, !state.busy, Icons.Outlined.Edit)
        if (business.canPublish) SecondaryAction("Publish business", { publishDialog = true }, !state.busy, Icons.Outlined.Publish)
        if (business.status != "published" && !business.canPublish && business.status != "suspended")
            InfoPanel("Your team is getting ready", "An owner or manager can publish this business when it's ready.", Icons.Outlined.Lock)
        TextButton(onClick = actions::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh business home") }
    }
    if (publishDialog) PublishConfirmation(business.name, { publishDialog = false }, { publishDialog = false; actions.publishBusiness() })
}

@Composable
fun BusinessProfileScreen(state: FoundationState, actions: JourneyActions, ownListing: Boolean) {
    var publishDialog by remember { mutableStateOf(false) }
    val business = if (ownListing) state.business else state.publicBusiness
    if (business == null) {
        JourneyPage("Business profile.", if (state.busy) "Loading the details…" else "This listing is currently unavailable.", onBack = actions::back) {
            if (!state.busy) PrimaryAction("Try again", actions::refresh)
        }
        return
    }
    JourneyPage(business.name, business.category, if (ownListing && business.status != "published") "PRIVATE LISTING PREVIEW" else "BUSINESS PROFILE", actions::back) {
        Surface(shape = RoundedCornerShape(26.dp), color = BrandPlum) { StorefrontArtwork(Modifier.fillMaxWidth().height(190.dp)) }
        if (ownListing) StatusPill(business.status)
        if (business.verification == "verified") InfoPanel("Verified business", "This business has been verified by the platform.", Icons.Outlined.Verified)
        if (business.description.isNotBlank()) {
            Text("A little about us", style = MaterialTheme.typography.titleLarge)
            Text(business.description, style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow("How we work", operatingModelLabel(business.operatingModel), Icons.Outlined.Storefront)
        if (business.location.isNotBlank()) DetailRow(if (business.operatingModel == "physical") "Find us" else "Where we work", business.location, Icons.Outlined.LocationOn)
        if (ownListing && business.status != "published") InfoPanel("This preview is private", "Only your business team can see it until you publish.", Icons.Outlined.VisibilityOff)
        if (ownListing && business.canPublish) PrimaryAction("Publish business", { publishDialog = true }, enabled = !state.busy)
    }
    if (publishDialog) PublishConfirmation(business.name, { publishDialog = false }, { publishDialog = false; actions.publishBusiness() })
}

@Composable
private fun PublishConfirmation(name: String, onDismiss: () -> Unit, onPublish: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ready to go live?") },
        text = { Text("$name will appear in public discovery. Customers will see the business details and location or service area you've provided.") },
        confirmButton = { TextButton(onClick = onPublish) { Text("Publish business") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep as draft") } })
}

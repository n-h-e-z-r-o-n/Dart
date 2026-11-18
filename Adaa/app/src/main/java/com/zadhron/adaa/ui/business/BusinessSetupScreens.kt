package com.zadhron.adaa.ui.business

import com.zadhron.adaa.ui.shared.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.model.JourneyScreen
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
private fun StepProgress(step: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { index ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (index < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Text("Step $step of 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BusinessBasicsScreen(state: FoundationState, actions: JourneyActions) {
    var categoryOpen by remember { mutableStateOf(false) }
    val draft = state.draft
    JourneyPage(if (state.editingBusinessId == null) "Let's meet\nyour business." else "Make it yours.",
        "Start with the essentials. You can update these details later.", "BUSINESS SETUP", actions::back) {
        StepProgress(1)
        FormField(draft.name, { actions.updateDraft(draft.copy(name = it)) }, "Business name",
            helper = "The name customers will recognise.", error = state.fieldErrors["name"], enabled = !state.busy, tag = "business_name")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Primary category", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { categoryOpen = true }, enabled = !state.busy && state.categories.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(state.categories.firstOrNull { it.id == draft.categoryId }?.name ?: "Choose your category", Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(categoryOpen, { categoryOpen = false }, Modifier.heightIn(max = 340.dp)) {
                    state.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = {
                            actions.updateDraft(draft.copy(categoryId = category.id)); categoryOpen = false
                        })
                    }
                }
            }
            Text(state.fieldErrors["category"] ?: "Choose the closest match so people can find you.",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.fieldErrors.containsKey("category")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.categories.isEmpty() && !state.busy) TextButton(onClick = actions::refresh) { Text("Retry loading categories") }
        }
        FormField(draft.description, { actions.updateDraft(draft.copy(description = it)) }, "About your business (optional)",
            helper = "A short introduction, in your own words.", singleLine = false,
            error = state.fieldErrors["description"], enabled = !state.busy)
        PrimaryAction("Continue to location", actions::nextSetup, enabled = !state.busy)
    }
}

@Composable
fun BusinessLocationScreen(state: FoundationState, actions: JourneyActions) {
    val draft = state.draft
    JourneyPage("Where do you\ndo your best work?", "Choose how customers connect with your business.",
        "BUSINESS SETUP", actions::back) {
        StepProgress(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("physical", "A physical location", "Customers visit your shop, studio or office."),
                Triple("service_area", "A service area", "You travel to customers in your area."),
                Triple("online", "Online", "You work with customers remotely.")
            ).forEach { (value, title, description) ->
                val selected = draft.operatingModel == value
                Card(onClick = { actions.updateDraft(draft.copy(operatingModel = value)) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(when (value) { "physical" -> Icons.Outlined.Storefront; "service_area" -> Icons.Outlined.NearMe; else -> Icons.Outlined.Language }, null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, if (selected) "Selected" else "Not selected", Modifier.size(20.dp))
                    }
                }
            }
        }
        if (draft.operatingModel != "online") FormField(draft.location, { actions.updateDraft(draft.copy(location = it)) },
            if (draft.operatingModel == "physical") "Business address" else "Area you serve",
            helper = if (draft.operatingModel == "physical") "Customers will see this address when you publish. Use your public business address."
            else "Add your city or neighbourhoods. You don't need to share a home address.",
            singleLine = false, error = state.fieldErrors["location"], enabled = !state.busy, tag = "business_location")
        else InfoPanel("No street address needed", "Your online business can be discovered without a physical address.", Icons.Outlined.Language)
        PrimaryAction("Review my business", actions::nextSetup, enabled = !state.busy)
    }
}

@Composable
fun BusinessReviewScreen(state: FoundationState, actions: JourneyActions) {
    val draft = state.draft
    val editing = state.editingBusinessId != null
    JourneyPage("Looking good.", "Take a moment to check your details before ${if (editing) "saving" else "creating your draft"}.",
        "BUSINESS SETUP", actions::back) {
        StepProgress(3)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(draft.name, style = MaterialTheme.typography.headlineSmall)
                DetailRow("Category", state.categories.firstOrNull { it.id == draft.categoryId }?.name.orEmpty(), Icons.Outlined.Category)
                DetailRow("How you operate", operatingModelLabel(draft.operatingModel), Icons.Outlined.Storefront)
                if (draft.operatingModel != "online") DetailRow(if (draft.operatingModel == "physical") "Business address" else "Service area", draft.location, Icons.Outlined.LocationOn)
                if (draft.description.isNotBlank()) DetailRow("About", draft.description)
            }
        }
        if (!editing) InfoPanel("Your draft stays private", "Only your business team can see it. You'll choose when to publish from your business home.", Icons.Outlined.Lock)
        else InfoPanel("${if (state.business?.status == "published") "These changes will be public" else "Your listing stays private"}",
            if (state.business?.status == "published") "Saving will update your published business profile immediately." else "Saving these details won't publish your business.", Icons.Outlined.Info)
        PrimaryAction(if (editing) "Save business details" else "Create my business draft", actions::submitBusiness, busy = state.busy)
        TextButton(onClick = actions::back, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Go back and make a change") }
    }
}

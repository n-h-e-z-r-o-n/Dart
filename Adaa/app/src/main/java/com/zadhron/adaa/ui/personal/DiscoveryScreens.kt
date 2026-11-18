package com.zadhron.adaa.ui.personal

import com.zadhron.adaa.ui.shared.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zadhron.adaa.data.remote.BusinessSummary
import com.zadhron.adaa.ui.viewmodel.FoundationState

@Composable
fun ExploreScreen(state: FoundationState, actions: JourneyActions) {
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandWordmark(); Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Explore, null, Modifier.size(16.dp))
                        Text("Explore freely", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Find your kind\nof local.", style = MaterialTheme.typography.displaySmall)
                Text("Discover businesses, services and people worth knowing.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                placeholder = { Text("Business name or category") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { IconButton(onClick = { focus.clearFocus(); actions.search(query) }, enabled = !state.busy) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Search businesses")
                } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); actions.search(query) }))
        }
        if (state.categories.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.query.isEmpty(), onClick = { actions.search("") }, enabled = !state.busy, label = { Text("All businesses") }) }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(selected = state.query == category.name, onClick = { actions.search(category.name) }, enabled = !state.busy,
                        label = { Text(category.name) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.query.isBlank()) "Open for discovery" else "Search results", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = actions::refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Refresh businesses") }
            }
        }
        if (state.discovered.isEmpty() && !state.busy) item {
            Box(Modifier.padding(horizontal = 28.dp)) {
                EmptyPanel(if (state.error == null) "A little quiet here." else "Let's try that again.",
                    if (state.error != null) "We couldn't load businesses. You can retry, or use Account to sign in."
                    else if (state.query.isBlank()) "Published businesses will appear here. Have a business of your own? Give it a home on Adaa."
                    else "Try another name or category, or see all businesses.",
                    action = if (state.error != null) "Try again" else if (state.query.isNotBlank()) "See all businesses" else "Set up a business",
                    onAction = if (state.error != null) actions::refresh else if (state.query.isNotBlank()) ({ actions.search("") }) else actions::businessMode)
            }
        }
        items(state.discovered, key = { it.id }) { business ->
            DiscoveryCard(business, { actions.openPublicBusiness(business.id) }, !state.busy, Modifier.padding(horizontal = 24.dp))
        }
        if (state.hasMore) item {
            Box(Modifier.padding(horizontal = 24.dp)) { SecondaryAction("Discover more", actions::loadMore, !state.busy) }
        }
        item {
            Card(onClick = actions::businessMode, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BrandPlum)) {
                Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storefront, null, Modifier.size(32.dp), tint = BrandChampagne)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Your business belongs here.", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFF6EE))
                        Text("Start a draft. Go live when you're ready.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDDCDD4))
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = BrandChampagne)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(business: BusinessSummary, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Card(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(60.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text(business.name.trim().take(1).uppercase(), style = MaterialTheme.typography.headlineLarge) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(business.name, style = MaterialTheme.typography.titleMedium)
                Text(business.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(operatingModelLabel(business.operatingModel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, "View business", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


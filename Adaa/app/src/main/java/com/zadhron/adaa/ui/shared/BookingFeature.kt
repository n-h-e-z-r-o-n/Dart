package com.zadhron.adaa.ui.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.remote.BookingRepository
import com.zadhron.adaa.ui.business.*
import com.zadhron.adaa.ui.personal.StorefrontScreen
import com.zadhron.adaa.ui.viewmodel.BookingViewModel
import kotlinx.coroutines.delay

@Composable
fun BookingFeature(userId: String?, business: BusinessDetails?, manage: Boolean, onExit: () -> Unit,
    onSignIn: () -> Unit, onBookings: () -> Unit, onPayments: () -> Unit, gateway: BookingGateway? = null) {
    val context = LocalContext.current.applicationContext
    // Public request drafts survive sign-in. Private inbox/workspace state is keyed by identity.
    val key = if (business != null && !manage) "storefront:${business.id}" else "bookings:$userId:${business?.id}:$manage"
    val model: BookingViewModel = viewModel(key = key, factory = viewModelFactory { initializer {
        BookingViewModel(gateway ?: BookingRepository(context), business?.id, userId, manage, createSavedStateHandle())
    } })
    val state by model.state.collectAsState()
    LaunchedEffect(userId) { model.bindUser(userId); if (model.state.value.sessionExpired) model.refresh() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(30_000)
                if (model.state.value.page in listOf("home", "detail")) model.refresh()
            }
        }
    }
    var editService by remember { mutableStateOf<CatalogService?>(null) }
    fun back() { if (state.page == "review") model.page("request") else if (state.page != "home") model.page("home") else onExit() }
    BackHandler(enabled = state.page != "home" || state.busy) { if (!state.busy) back() }
    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (state.error ?: state.notice)?.let {
            Surface(color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Text(it, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        if (state.sessionExpired) TextButton(onClick = onSignIn) { Text("Sign in again") }
        Box(Modifier.weight(1f)) {
            when (state.page) {
                "request" -> BookingRequestScreen(state, model, userId != null, onSignIn, ::back)
                "review" -> BookingReviewScreen(state, model)
                "sent" -> JourneyPage("Your request is sent.", "Awaiting business confirmation. You can check its status and replies in My bookings & inquiries.", "REQUEST RECEIVED", ::back) {
                    PrimaryAction("View my requests", onBookings)
                    SecondaryAction("Back to business", { model.page("home") })
                }
                "detail" -> BookingDetailScreen(state, model, onPayments)
                "catalog" -> state.storefront?.let { CatalogManagementScreen(it, state.busy, ::back) { service -> editService = service; model.page("service") } }
                "service" -> ServiceEditorScreen(editService, state.busy, { model.page("catalog") }, model::saveService)
                "settings" -> state.storefront?.let { StorefrontEditorScreen(it, state.busy, ::back, model::saveProfile) }
                else -> {
                    if (business != null && !manage) {
                        val profile = state.storefront
                        if (profile == null) JourneyPage(business.name, if (state.busy) "Loading this storefront…" else "We couldn't load this storefront. Please try again.", onBack = onExit) {
                            if (!state.busy) PrimaryAction("Try again", model::refresh)
                        } else StorefrontScreen(business, profile, state.busy, onExit, model::choose, { model.choose(null) }, onBookings)
                    } else BookingInbox(state.requests, state.busy, state.hasMore, manage, onExit, model::select, model::refresh, model::loadMore,
                        filter = state.filter, onFilter = model::filter,
                        header = if (manage && state.storefront?.canManage == true) ({
                            PrimaryAction("Manage services", { model.page("catalog") }, !state.busy)
                            SecondaryAction("Public details & request settings", { model.page("settings") }, !state.busy)
                        }) else null)
                }
            }
        }
    }
}

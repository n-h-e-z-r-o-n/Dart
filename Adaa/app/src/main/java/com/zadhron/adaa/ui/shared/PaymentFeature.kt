package com.zadhron.adaa.ui.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zadhron.adaa.data.model.WalletScope
import com.zadhron.adaa.data.remote.PaymentRepository
import com.zadhron.adaa.ui.personal.PersonalWalletScreen
import com.zadhron.adaa.ui.business.BusinessWalletScreen
import com.zadhron.adaa.ui.viewmodel.*
import kotlinx.coroutines.delay

@Composable
fun PaymentFeature(scope: WalletScope, services: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val model: PaymentViewModel = viewModel(key = "payments:${scope.userId}:${scope.businessId}:$services", factory = viewModelFactory {
        initializer { PaymentViewModel(PaymentRepository(context), scope, createSavedStateHandle(), services) }
    })
    LaunchedEffect(model) { model.enter() }
    DisposableEffect(model) { onDispose { model.clearOnExit() } }
    val state by model.state.collectAsState()
    val back = { model.back(onExit) }
    BackHandler { back() }
    // Poll only while the pending operation is actually visible in the foreground composition.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.page, state.operationId, lifecycle) {
        if (state.page == PaymentPage.OPERATION) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (model.state.value.selectedOperation?.status !in listOf("succeeded", "failed", "review")) { delay(10_000); model.refresh() }
        }
    }
    PaymentContent(state, model, back)
}

@Composable
fun PaymentContent(state: PaymentState, actions: PaymentActions = object : PaymentActions {}, onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val message = state.error ?: state.notice
        if (message != null) Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
            Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        when (state.page) {
            PaymentPage.HOME -> if (state.scope.isBusiness) BusinessWalletScreen(state, actions, onBack) else PersonalWalletScreen(state, actions, onBack)
            PaymentPage.FORM -> WalletOperationForm(state, actions, onBack)
            PaymentPage.REVIEW -> WalletOperationReview(state, actions, onBack)
            PaymentPage.OPERATION -> WalletOperationScreen(state, actions, onBack)
            PaymentPage.ACTIVITY -> WalletActivityScreen(state, actions, onBack)
            PaymentPage.SERVICES -> ServicePaymentsScreen(state, actions, onBack)
            PaymentPage.CREATE_SERVICE -> CreateServicePaymentScreen(state, actions, onBack)
            PaymentPage.CHARGE -> ServicePaymentScreen(state, actions, onBack)
            PaymentPage.MESSAGE -> PaymentMessageScreen(state, actions, onBack)
        }
    }
}

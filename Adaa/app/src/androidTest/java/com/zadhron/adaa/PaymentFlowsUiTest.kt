package com.zadhron.adaa

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.shared.PaymentContent
import com.zadhron.adaa.ui.theme.AdaaTheme
import com.zadhron.adaa.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PaymentFlowsUiTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyTestActivity>()
    private fun run(scope: WalletScope, services: Boolean = false, gateway: TestPayments = TestPayments(), check: (PaymentViewModel, TestPayments) -> Unit) {
        val model = PaymentViewModel(gateway, scope, startWithServices = services)
        val store = ViewModelStore().apply { put("payment", model) }
        try {
            compose.setContent { val state by model.state.collectAsState(); AdaaTheme { PaymentContent(state, model) { model.back {} } } }
            compose.waitUntil(5000) { model.state.value.ready && !model.state.value.busy }
            compose.waitUntil(5000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            check(model, gateway)
        } finally { store.clear() }
    }
    @Test fun personalDepositRequiresReviewAndConfirmation() = run(WalletScope("customer")) { model, gateway ->
        compose.onNodeWithText("Deposit with M-Pesa").performScrollTo().performClick()
        compose.onNodeWithTag("wallet_amount").performScrollTo().performTextInput("500")
        compose.onNodeWithTag("wallet_phone").performScrollTo().performTextInput("0712345678")
        compose.onNodeWithText("Review deposit").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.page == PaymentPage.REVIEW && !model.state.value.busy }
        assertEquals(0, gateway.requests)
        compose.onNodeWithText("Send M-Pesa deposit prompt").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.page == PaymentPage.OPERATION && !model.state.value.busy }
        assertEquals(1, gateway.requests)
        assertEquals("254712345678", gateway.phone)
        compose.onNodeWithText("Processing").assertExists()
    }
    @Test fun customerAuthorizesServiceEscrowBeforeAnyDebit() = run(WalletScope("customer"), services = true, gateway = TestPayments("requested")) { model, gateway ->
        compose.onNodeWithText("Agreed service").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge != null && !model.state.value.busy }
        compose.onNodeWithText("Pay from my wallet").performScrollTo().performClick()
        assertEquals(0, gateway.actions)
        compose.onNodeWithText("Pay into escrow?").assertExists()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5000) { model.state.value.charge?.status == "funded" && !model.state.value.busy }
        assertEquals(1, gateway.actions)
    }
    @Test fun delegatedEmployeeCanConfirmServiceWithoutWalletAccess() = run(WalletScope("employee", "business", "staff"), services = true) { model, gateway ->
        compose.onNodeWithText("Agreed service").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge != null && !model.state.value.busy }
        compose.onNodeWithText("Confirm service completed").performScrollTo().performClick()
        assertEquals(0, gateway.actions)
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5000) { model.state.value.charge?.status == "completed" && !model.state.value.busy }
        assertEquals(0, gateway.snapshotCalls)
        compose.onNodeWithText("Withdraw to M-Pesa").assertDoesNotExist()
    }
    @Test fun customerCanOpenADisputeWithAnAuditMessage() = run(WalletScope("customer"), services = true) { model, _ ->
        compose.onNodeWithText("Agreed service").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge != null && !model.state.value.busy }
        compose.onNodeWithText("Raise a payment dispute").performScrollTo().performClick()
        compose.onNodeWithTag("payment_message").performScrollTo().performTextInput("The service was not provided as agreed.")
        compose.onNodeWithText("Submit details").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge?.status == "disputed" && !model.state.value.busy }
        compose.onNodeWithText("This payment is under review").performScrollTo().assertIsDisplayed()
    }
    @Test fun ownerRefundAlsoRequiresConfirmation() = run(WalletScope("owner", "business", "owner"), services = true, gateway = TestPayments("completed")) { model, gateway ->
        compose.onNodeWithText("Agreed service").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge != null && !model.state.value.busy }
        compose.onNodeWithText("Refund service payment").performScrollTo().performClick()
        assertEquals(0, gateway.actions)
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5000) { model.state.value.charge?.status == "refunded" && !model.state.value.busy }
        assertEquals(1, gateway.actions)
    }
    @Test fun reportingAnExternalPaymentDoesNotAlsoOfferWalletPayment() = run(WalletScope("customer"), services = true, gateway = TestPayments("requested")) { model, gateway ->
        compose.onNodeWithText("Agreed service").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge != null && !model.state.value.busy }
        compose.onNodeWithText("I paid directly outside Adaa").performScrollTo().performClick()
        compose.onNodeWithTag("payment_message").performScrollTo().performTextInput("EXT123")
        compose.onNodeWithText("Report payment").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.charge?.externalReported == true && !model.state.value.busy }
        compose.onNodeWithText("Pay from my wallet").assertDoesNotExist()
        assertEquals(1, gateway.actions)
    }
    private class TestPayments(var status: String = "funded") : PaymentGateway {
        var requests = 0; var actions = 0; var phone = ""; var snapshotCalls = 0; var externalReported = false
        override suspend fun prepare(userId: String) {}
        override suspend fun policy() = WalletPolicy(2,true,true,100,5000000,10000000,72,0)
        override suspend fun snapshot(scope: WalletScope): WalletSummary { snapshotCalls++; return WalletSummary("KES",100000,0,0,0,0,0,true,emptyList()) }
        override suspend fun operations(scope: WalletScope, before: GatewayOperation?) = emptyList<GatewayOperation>()
        override suspend fun operation(id: String) = GatewayOperation(id,"deposit",50000,"5678","pending","","2026-11-04T10:00:00Z","")
        override suspend fun activity(scope: WalletScope, before: WalletMovement?) = emptyList<WalletMovement>()
        override suspend fun services(scope: WalletScope, before: ServicePayment?) = listOf(detail("charge"))
        override suspend fun detail(id: String) = ServicePayment(id,"business","Test Studio","customer","Customer","Agreed service",50000,status,"2026-11-04T10:00:00Z",true,true,externalReported)
        override suspend fun quote(scope: WalletScope, direction: String, amountMinor: Long) = PaymentQuote(2,amountMinor,0,amountMinor,1000000,1000000,true)
        override suspend fun requestOperation(scope: WalletScope, requestId: String, direction: String, amountMinor: Long, phone: String, version: Long): String { requests++; this.phone = phone; return "job" }
        override suspend fun createService(scope: WalletScope, requestId: String, customerId: String, amountMinor: Long, description: String) = "charge"
        override suspend fun serviceAction(action: String, id: String, requestId: String, message: String) {
            actions++; if (action == "external") externalReported = true
            status = when (action) { "pay" -> "funded"; "complete" -> "completed"; "dispute" -> "disputed"; "refund" -> "refunded"; else -> status }
        }
    }
}

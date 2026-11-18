package com.zadhron.adaa

import androidx.lifecycle.SavedStateHandle
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.shared.*
import com.zadhron.adaa.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentJourneyTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakePayments
    private val personal = WalletScope("customer")
    @Before fun setup() { Dispatchers.setMain(dispatcher); gateway = FakePayments() }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()
    private fun model(scope: WalletScope = personal, services: Boolean = false) = PaymentViewModel(gateway, scope, SavedStateHandle(), services).also { settle() }

    @Test fun parsesMoneyWithoutFloatingPointRoundingOrScientificNotation() {
        assertEquals(101L, parsePaymentAmount("1.01"))
        assertEquals(99999999999L, parsePaymentAmount("999999999.99"))
        for (bad in listOf("0", "-1", "1e3", "1.001", "NaN", "1,000")) assertNull(parsePaymentAmount(bad))
        assertEquals("254712345678", normalizePaymentPhone("+254 712 345 678"))
        assertEquals("254112345678", normalizePaymentPhone("0112345678"))
        assertNull(normalizePaymentPhone("07123456789"))
    }
    @Test fun anUncertainDepositRetriesTheSameRequestReference() {
        val vm = model(); vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle()
        gateway.requestError = IOException(); vm.confirmOperation(); settle()
        assertEquals(PaymentPage.REVIEW, vm.state.value.page)
        val first = gateway.requests.single()
        gateway.requestError = null; vm.confirmOperation(); settle()
        assertEquals(first, gateway.requests.last())
        assertEquals(PaymentPage.OPERATION, vm.state.value.page)
    }
    @Test fun aDeliberatelyNewDepositGetsANewReference() {
        val vm = model(); vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle(); vm.confirmOperation(); settle()
        vm.back {}; settle(); vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle(); vm.confirmOperation(); settle()
        assertEquals(2, gateway.requests.size)
        assertNotEquals(gateway.requests[0], gateway.requests[1])
    }
    @Test fun successfulMutationAndFailedReadCannotResubmitTheOperation() {
        val vm = model(); vm.beginOperation("withdrawal"); vm.reviewOperation("500", "0712345678"); settle()
        gateway.operationError = IOException(); vm.confirmOperation(); settle()
        assertEquals(PaymentPage.OPERATION, vm.state.value.page)
        assertEquals("job", vm.state.value.operationId)
        vm.confirmOperation(); settle()
        assertEquals(1, gateway.requests.size)
        gateway.operationError = null; vm.refresh(); settle()
        assertEquals("job", vm.state.value.selectedOperation?.id)
    }
    @Test fun newRequestNeverDisplaysThePreviousOperationsReceipt() {
        val vm = model(); vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle(); vm.confirmOperation(); settle()
        assertNotNull(vm.state.value.selectedOperation)
        vm.back {}; settle(); vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle()
        gateway.operationError = IOException(); vm.confirmOperation(); settle()
        assertNull(vm.state.value.selectedOperation)
        assertEquals(PaymentPage.OPERATION, vm.state.value.page)
    }
    @Test fun quoteChecksBalanceAndDoesNotSendAWithdrawal() {
        val vm = model(); gateway.quoteValue = gateway.quoteValue.copy(availableMinor = 0)
        vm.beginOperation("withdrawal"); vm.reviewOperation("500", "0712345678"); settle()
        assertEquals(PaymentPage.FORM, vm.state.value.page)
        assertTrue(gateway.requests.isEmpty())
    }
    @Test fun deactivatedGatewayCannotReachConfirmation() {
        val vm = model(); gateway.quoteValue = gateway.quoteValue.copy(active = false)
        vm.beginOperation("deposit"); vm.reviewOperation("500", "0712345678"); settle()
        assertEquals(PaymentPage.FORM, vm.state.value.page)
        assertTrue(gateway.requests.isEmpty())
    }
    @Test fun staffServicesDoNotLoadOwnerWalletBalances() {
        val vm = model(WalletScope("employee", "business", "staff"), services = true)
        assertEquals(0, gateway.snapshotCalls)
        vm.beginOperation("withdrawal")
        assertEquals(PaymentPage.SERVICES, vm.state.value.page)
    }
    @Test fun createdChargeCanRefreshAfterLosingItsFirstDetailResponse() {
        val vm = model(WalletScope("owner", "business", "owner"), services = true)
        vm.createServicePage(); gateway.detailError = IOException()
        vm.createService("11111111-1111-1111-1111-111111111111", "500", "Agreed service"); settle()
        assertEquals("charge", vm.state.value.chargeId)
        assertNull(vm.state.value.charge)
        gateway.detailError = null; vm.refresh(); settle()
        assertEquals("charge", vm.state.value.charge?.id)
        assertEquals(1, gateway.creates)
    }
    @Test fun paymentRetryReusesItsChargeActionReference() {
        val vm = model(services = true); vm.showCharge("charge"); settle()
        gateway.actionError = IOException(); vm.serviceAction("pay"); settle()
        gateway.actionError = null; vm.serviceAction("pay"); settle()
        assertEquals(gateway.actionRequests[0], gateway.actionRequests[1])
    }
    @Test fun sessionMismatchClearsFinancialState() {
        gateway.prepareError = SessionExpiredException()
        val vm = model()
        assertNull(vm.state.value.wallet)
        assertFalse(vm.state.value.ready)
        assertTrue(vm.state.value.error!!.contains("sign in"))
    }
    private class FakePayments : PaymentGateway {
        var prepareError: Exception? = null; var requestError: Exception? = null; var operationError: Exception? = null
        var detailError: Exception? = null; var actionError: Exception? = null
        var quoteValue = PaymentQuote(2, 50000, 0, 50000, 100000, 100000, true)
        var snapshotCalls = 0; var creates = 0
        val requests = mutableListOf<String>(); val actionRequests = mutableListOf<String>()
        override suspend fun prepare(userId: String) { prepareError?.let { throw it } }
        override suspend fun policy() = WalletPolicy(2, true, true, 100, 50000, 100000, 72, 0)
        override suspend fun snapshot(scope: WalletScope): WalletSummary { snapshotCalls++; return WalletSummary("KES",100000,0,0,0,0,0,true,emptyList()) }
        override suspend fun operations(scope: WalletScope, before: GatewayOperation?) = emptyList<GatewayOperation>()
        override suspend fun operation(id: String): GatewayOperation { operationError?.let { throw it }; return GatewayOperation(id,"deposit",50000,"5678","pending","","2026-11-04T10:00:00Z","") }
        override suspend fun activity(scope: WalletScope, before: WalletMovement?) = emptyList<WalletMovement>()
        override suspend fun services(scope: WalletScope, before: ServicePayment?) = emptyList<ServicePayment>()
        override suspend fun detail(id: String): ServicePayment { detailError?.let { throw it }; return ServicePayment(id,"business","Test Studio","customer","Customer","Service",50000,"requested","2026-11-04T10:00:00Z",false,false,false) }
        override suspend fun quote(scope: WalletScope, direction: String, amountMinor: Long) = quoteValue
        override suspend fun requestOperation(scope: WalletScope, requestId: String, direction: String, amountMinor: Long, phone: String, version: Long): String { requests += requestId; requestError?.let { throw it }; return "job" }
        override suspend fun createService(scope: WalletScope, requestId: String, customerId: String, amountMinor: Long, description: String): String { creates++; return "charge" }
        override suspend fun serviceAction(action: String, id: String, requestId: String, message: String) { actionRequests += requestId; actionError?.let { throw it } }
    }
}

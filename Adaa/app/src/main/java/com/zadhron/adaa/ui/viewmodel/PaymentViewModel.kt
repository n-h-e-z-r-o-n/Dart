package com.zadhron.adaa.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.ui.shared.PaymentActions
import com.zadhron.adaa.ui.shared.normalizePaymentPhone
import com.zadhron.adaa.ui.shared.parsePaymentAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.security.MessageDigest
import java.util.UUID

enum class PaymentPage { HOME, FORM, REVIEW, OPERATION, ACTIVITY, SERVICES, CREATE_SERVICE, CHARGE, MESSAGE }
data class PaymentState(
    val scope: WalletScope, val page: PaymentPage = PaymentPage.HOME, val busy: Boolean = false,
    val ready: Boolean = false, val policy: WalletPolicy? = null, val wallet: WalletSummary? = null,
    val operations: List<GatewayOperation> = emptyList(), val movements: List<WalletMovement> = emptyList(),
    val services: List<ServicePayment> = emptyList(), val charge: ServicePayment? = null,
    val operationId: String? = null, val selectedOperation: GatewayOperation? = null, val chargeId: String? = null, val direction: String = "deposit", val amount: String = "", val phone: String = "",
    val quote: PaymentQuote? = null, val messageAction: String = "dispute", val error: String? = null,
    val notice: String? = null, val moreOperations: Boolean = false, val moreActivity: Boolean = false, val moreServices: Boolean = false
)

/** Financial state is separate from account/listing navigation. Request IDs survive uncertain retries. */
class PaymentViewModel(private val gateway: PaymentGateway, private val scope: WalletScope,
    private val saved: SavedStateHandle = SavedStateHandle(), private val startWithServices: Boolean = false) : ViewModel(), PaymentActions {
    private var running: Job? = null
    private var generation = 0
    private val _state = MutableStateFlow(PaymentState(scope, page = if (startWithServices) PaymentPage.SERVICES else PaymentPage.HOME))
    val state = _state.asStateFlow()
    private fun change(update: (PaymentState) -> PaymentState) { _state.value = update(_state.value) }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        val runGeneration = generation
        change { it.copy(busy = true, error = null, notice = null) }
        running = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: SessionExpiredException) { if (generation == runGeneration) change { PaymentState(scope, page = if (startWithServices) PaymentPage.SERVICES else PaymentPage.HOME, error = "Your session has ended. Go back and sign in again.") } }
            catch (_: java.io.IOException) { if (generation == runGeneration) change { it.copy(error = "The connection was interrupted. Refresh status before making another payment. Retrying this request uses the same reference.") } }
            catch (e: Exception) { if (generation == runGeneration) change { it.copy(error = e.message ?: "We couldn't complete that request. Please refresh and try again.") } }
            finally { if (generation == runGeneration) change { it.copy(busy = false) } }
        }
    }
    init { refresh() }
    fun enter() {
        if (!_state.value.busy) { change { it.copy(ready = false) }; refresh() }
    }
    fun clearOnExit() {
        generation++
        running?.cancel()
        _state.value = PaymentState(scope, page = if (startWithServices) PaymentPage.SERVICES else PaymentPage.HOME)
    }
    private suspend fun prepare() {
        if (!_state.value.ready) { gateway.prepare(scope.userId); change { it.copy(ready = true) } }
    }
    private suspend fun home() {
        val policy = gateway.policy()
        val wallet = gateway.snapshot(scope)
        val operations = gateway.operations(scope)
        change { it.copy(policy = policy, wallet = wallet, operations = operations, moreOperations = operations.size == 30) }
    }
    private suspend fun listServices() {
        val services = gateway.services(scope)
        change { it.copy(services = services, moreServices = services.size == 30) }
    }
    override fun refresh() = action {
        prepare()
        when (_state.value.page) {
            PaymentPage.OPERATION -> { val operation = gateway.operation(_state.value.operationId ?: return@action); change { it.copy(selectedOperation = operation) } }
            PaymentPage.SERVICES -> listServices()
            PaymentPage.CHARGE, PaymentPage.MESSAGE -> _state.value.chargeId?.let { id -> val detail = gateway.detail(id); change { it.copy(charge = detail) } }
            PaymentPage.ACTIVITY -> { val rows = gateway.activity(scope); change { it.copy(movements = rows, moreActivity = rows.size == 30) } }
            else -> home()
        }
    }
    override fun showServices() {
        if (_state.value.busy) return
        change { it.copy(page = PaymentPage.SERVICES, charge = null, error = null, notice = null) }; refresh()
    }
    override fun showActivity() {
        if (_state.value.busy) return
        change { it.copy(page = PaymentPage.ACTIVITY, error = null) }; refresh()
    }
    override fun more(kind: String) = action {
        when (kind) {
            "operations" -> { val rows = gateway.operations(scope, _state.value.operations.lastOrNull()); change { it.copy(operations = (it.operations + rows).distinctBy { r -> r.id }, moreOperations = rows.size == 30) } }
            "activity" -> { val rows = gateway.activity(scope, _state.value.movements.lastOrNull()); change { it.copy(movements = (it.movements + rows).distinctBy { r -> r.id }, moreActivity = rows.size == 30) } }
            "services" -> { val rows = gateway.services(scope, _state.value.services.lastOrNull()); change { it.copy(services = (it.services + rows).distinctBy { r -> r.id }, moreServices = rows.size == 30) } }
        }
    }
    override fun beginOperation(direction: String) {
        if (_state.value.busy || !scope.canWithdraw || direction !in listOf("deposit", "withdrawal") || scope.isBusiness && direction == "deposit") return
        saved["gateway-form"] = UUID.randomUUID().toString()
        change { it.copy(page = PaymentPage.FORM, direction = direction, quote = null, amount = "", phone = "", selectedOperation = null, error = null, notice = null) }
    }
    override fun reviewOperation(amount: String, phone: String) {
        val minor = parsePaymentAmount(amount)
        val normalized = normalizePaymentPhone(phone)
        if (minor == null || minor % 100 != 0L || normalized == null) {
            change { it.copy(error = "Enter a positive whole-shilling amount and a valid Kenyan M-Pesa phone number.") }; return
        }
        action {
            val quote = gateway.quote(scope, _state.value.direction, minor)
            check(quote.active) { "Wallet deposits and withdrawals are not active yet." }
            check(minor <= quote.remainingDailyMinor) { "This exceeds your remaining daily wallet limit." }
            if (_state.value.direction == "withdrawal") check(quote.totalDebitMinor <= quote.availableMinor) { "Your available balance is too low for this withdrawal." }
            change { it.copy(quote = quote, amount = amount, phone = normalized, page = PaymentPage.REVIEW) }
        }
    }
    private fun intent(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((scope.userId + ":" + scope.businessId + ":" + key).toByteArray()).joinToString("") { "%02x".format(it) }
        val name = "payment-intent-$digest"
        return saved.get<String>(name) ?: UUID.randomUUID().toString().also { saved[name] = it }
    }
    override fun confirmOperation() = action {
        val current = _state.value
        val quote = current.quote ?: return@action
        val key = intent("gateway:${saved.get<String>("gateway-form")}:${current.direction}:${quote.amountMinor}:${current.phone}:${quote.policyVersion}")
        val id = gateway.requestOperation(scope, key, current.direction, quote.amountMinor, current.phone, quote.policyVersion)
        // Switch screens before refreshing; a failed read cannot create a second operation.
        change { it.copy(page = PaymentPage.OPERATION, operationId = id, selectedOperation = null, quote = null) }
        val operation = gateway.operation(id); change { it.copy(selectedOperation = operation) }
    }
    override fun showOperation(id: String) { if (!_state.value.busy) change { it.copy(page = PaymentPage.OPERATION, operationId = id, selectedOperation = it.operations.firstOrNull { row -> row.id == id }, error = null) }; refresh() }
    override fun showCharge(id: String) = action {
        change { it.copy(page = PaymentPage.CHARGE, charge = null, chargeId = id) }
        val detail = gateway.detail(id); change { it.copy(charge = detail) }
    }
    override fun createServicePage() { if (!_state.value.busy && scope.canManage) { saved["service-form"] = UUID.randomUUID().toString(); change { it.copy(page = PaymentPage.CREATE_SERVICE, error = null) } } }
    override fun createService(customer: String, amount: String, description: String) {
        val id = runCatching { UUID.fromString(customer.trim()).toString() }.getOrNull()
        val minor = parsePaymentAmount(amount)
        if (!scope.canManage || id == null || minor == null || description.trim().length !in 1..500) {
            change { it.copy(error = "Enter the customer's payment code, a positive amount and a description of up to 500 characters.") }; return
        }
        action {
            val created = gateway.createService(scope, intent("service:${saved.get<String>("service-form")}:$id:$minor:${description.trim()}"), id, minor, description.trim())
            change { it.copy(page = PaymentPage.CHARGE, charge = null, chargeId = created) }
            val detail = gateway.detail(created); change { it.copy(charge = detail, notice = "Payment request created. No money has been charged.") }
        }
    }
    override fun messagePage(action: String) { if (!_state.value.busy) change { it.copy(page = PaymentPage.MESSAGE, messageAction = action, error = null) } }
    override fun serviceAction(action: String, message: String) {
        val charge = _state.value.charge ?: return
        if (action in listOf("dispute", "respond", "withdraw_report") && message.trim().length !in 10..2000 || action == "external" && message.trim().length !in 3..80) {
            change { it.copy(error = if (action == "external") "Enter a payment reference between 3 and 80 characters." else "Add details between 10 and 2,000 characters.") }; return
        }
        this.action {
            gateway.serviceAction(action, charge.id, intent("service-action:$action:${charge.id}"), message.trim())
            change { it.copy(page = PaymentPage.CHARGE, charge = null, notice = "Your request was saved. Refresh if the latest status isn't visible yet.") }
            val detail = gateway.detail(charge.id); change { it.copy(charge = detail) }
        }
    }
    fun back(onExit: () -> Unit) {
        if (_state.value.busy) return
        when (_state.value.page) {
            PaymentPage.HOME -> onExit()
            PaymentPage.SERVICES -> if (startWithServices) onExit() else { change { it.copy(page = PaymentPage.HOME, error = null) }; refresh() }
            PaymentPage.REVIEW -> change { it.copy(page = PaymentPage.FORM, error = null) }
            PaymentPage.CHARGE, PaymentPage.CREATE_SERVICE -> showServices()
            PaymentPage.MESSAGE -> change { it.copy(page = PaymentPage.CHARGE, error = null) }
            else -> { change { it.copy(page = PaymentPage.HOME, error = null) }; refresh() }
        }
    }
}

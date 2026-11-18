package com.zadhron.adaa.data.model

data class WalletScope(val userId: String, val businessId: String? = null, val businessRole: String = "") {
    val isBusiness get() = businessId != null
    val canManage get() = isBusiness && businessRole in listOf("owner", "manager")
    val canWithdraw get() = !isBusiness || businessRole == "owner"
}
data class WalletPolicy(val version: Long, val configured: Boolean, val active: Boolean,
    val minMinor: Long?, val maxMinor: Long?, val dailyMinor: Long?, val disputeHours: Int?, val feeMinor: Long)
data class PaymentQuote(val policyVersion: Long, val amountMinor: Long, val feeMinor: Long,
    val totalDebitMinor: Long, val remainingDailyMinor: Long, val availableMinor: Long, val active: Boolean)
data class GatewayOperation(val id: String, val direction: String, val amountMinor: Long, val phoneSuffix: String,
    val status: String, val receipt: String, val createdAt: String, val settledAt: String)
data class WalletMovement(val id: String, val kind: String, val createdAt: String, val entries: List<WalletTransaction>)
data class PaymentEvent(val action: String, val message: String, val createdAt: String, val actorName: String)
data class PaymentDispute(val id: String, val reason: String, val status: String, val resolution: String)
data class ServicePayment(val id: String, val businessId: String, val businessName: String, val customerId: String,
    val customerName: String, val description: String, val amountMinor: Long, val status: String, val createdAt: String,
    val canRelease: Boolean, val canRefund: Boolean, val externalReported: Boolean,
    val completedAt: String = "", val dispute: PaymentDispute? = null, val events: List<PaymentEvent> = emptyList())

interface PaymentGateway {
    suspend fun prepare(userId: String)
    suspend fun policy(): WalletPolicy
    suspend fun snapshot(scope: WalletScope): WalletSummary
    suspend fun operations(scope: WalletScope, before: GatewayOperation? = null): List<GatewayOperation>
    suspend fun operation(id: String): GatewayOperation
    suspend fun activity(scope: WalletScope, before: WalletMovement? = null): List<WalletMovement>
    suspend fun services(scope: WalletScope, before: ServicePayment? = null): List<ServicePayment>
    suspend fun detail(id: String): ServicePayment
    suspend fun quote(scope: WalletScope, direction: String, amountMinor: Long): PaymentQuote
    suspend fun requestOperation(scope: WalletScope, requestId: String, direction: String, amountMinor: Long, phone: String, version: Long): String
    suspend fun createService(scope: WalletScope, requestId: String, customerId: String, amountMinor: Long, description: String): String
    suspend fun serviceAction(action: String, id: String, requestId: String, message: String = "")
}

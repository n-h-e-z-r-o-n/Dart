package com.zadhron.adaa.data.remote

import android.content.Context
import com.zadhron.adaa.data.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Transport/decoding only. Every authorization and money rule is enforced by RPC. */
class PaymentRepository(context: Context) : PaymentGateway {
    private var boundUserId: String? = null
    private val client = SupabaseRepository.shared(context.applicationContext)
    override suspend fun prepare(userId: String) {
        val identity = client.restoreSession()
        if (identity?.id != userId || identity.passwordRecovery) throw SessionExpiredException()
        boundUserId = userId
    }
    private suspend fun rpc(name: String, body: JSONObject = JSONObject()) = client.paymentRpc(name, body, boundUserId ?: throw SessionExpiredException())
    private fun scope(value: WalletScope) = JSONObject().put("p_business_id", value.businessId ?: JSONObject.NULL)
    private fun page(value: WalletScope, at: String?, id: String?) = scope(value).apply {
        if (at != null && id != null) { put("p_before", at); put("p_before_id", id) }
    }
    private fun <T> rows(text: String, decode: (JSONObject) -> T): List<T> = JSONArray(text).let { rows ->
        (0 until rows.length()).map { decode(rows.getJSONObject(it)) }
    }
    override suspend fun policy(): WalletPolicy = JSONObject(rpc("get_wallet_policy")).let {
        WalletPolicy(it.getLong("version"), it.getBoolean("configured"), it.getBoolean("payments_active"),
            it.nullLong("min_external_minor"), it.nullLong("max_external_minor"), it.nullLong("daily_external_minor"),
            it.nullLong("dispute_window_hours")?.toInt(), it.getLong("platform_fee_minor"))
    }
    override suspend fun snapshot(scope: WalletScope): WalletSummary = JSONObject(rpc("get_wallet_dashboard", scope(scope))).let { row ->
        check(row.getString("currency") == "KES") { "This wallet currency isn't supported yet." }
        WalletSummary("KES", row.getLong("available_minor"), row.getLong("escrow_minor"), row.getLong("withdrawal_pending_minor"),
            row.getLong("completed_services"), row.getLong("held_services"), row.getLong("completed_service_value_minor"), row.getBoolean("payments_active"), emptyList(), row.optLong("held_for_services_minor"), row.optBoolean("withdrawals_blocked"))
    }
    override suspend fun operations(scope: WalletScope, before: GatewayOperation?) = rows(rpc("get_wallet_operations", page(scope, before?.createdAt, before?.id))) { row ->
        GatewayOperation(row.getString("id"), row.getString("direction"), row.getLong("amount_minor"), row.getString("phone_suffix"),
            row.getString("state"), row.text("receipt"), row.getString("created_at"), row.text("settled_at"))
    }
    override suspend fun operation(id: String): GatewayOperation = JSONObject(rpc("get_wallet_operation", JSONObject().put("p_operation_id", id))).let { row ->
        GatewayOperation(row.getString("id"), row.getString("direction"), row.getLong("amount_minor"), row.getString("phone_suffix"),
            row.getString("state"), row.text("receipt"), row.getString("created_at"), row.text("settled_at"))
    }
    override suspend fun activity(scope: WalletScope, before: WalletMovement?) = rows(rpc("get_wallet_activity", page(scope, before?.createdAt, before?.id))) { row ->
        val entries = row.getJSONArray("entries")
        WalletMovement(row.getString("id"), row.getString("kind"), row.getString("created_at"),
            (0 until entries.length()).map { i -> entries.getJSONObject(i).let { e ->
                WalletTransaction(row.getString("kind"), e.getString("bucket"), e.getLong("amount_minor"), row.getString("created_at")) } })
    }
    override suspend fun services(scope: WalletScope, before: ServicePayment?) = rows(rpc("get_payment_requests", page(scope, before?.createdAt, before?.id)), ::service)
    override suspend fun detail(id: String) = service(JSONObject(rpc("get_payment_request", JSONObject().put("p_charge_id", id))))
    override suspend fun quote(scope: WalletScope, direction: String, amountMinor: Long) = JSONObject(rpc("quote_wallet_operation",
        scope(scope).put("p_direction", direction).put("p_amount_minor", amountMinor))).let {
        PaymentQuote(it.getLong("policy_version"), it.getLong("amount_minor"), it.getLong("fee_minor"), it.getLong("total_debit_minor"),
            it.getLong("remaining_daily_minor"), it.getLong("available_minor"), it.getBoolean("payments_active"))
    }
    override suspend fun requestOperation(scope: WalletScope, requestId: String, direction: String, amountMinor: Long, phone: String, version: Long): String =
        org.json.JSONTokener(rpc("request_wallet_gateway_operation", scope(scope).put("p_request_id", requestId).put("p_direction", direction)
            .put("p_amount_minor", amountMinor).put("p_phone", phone).put("p_policy_version", version))).nextValue() as String
    override suspend fun createService(scope: WalletScope, requestId: String, customerId: String, amountMinor: Long, description: String): String =
        org.json.JSONTokener(rpc("create_service_charge", scope(scope).put("p_request_id", requestId).put("p_customer_id", customerId)
            .put("p_amount_minor", amountMinor).put("p_description", description))).nextValue() as String
    override suspend fun serviceAction(action: String, id: String, requestId: String, message: String) {
        val body = JSONObject().put("p_charge_id", id)
        when (action) {
            "pay", "complete", "refund" -> body.put("p_request_id", requestId)
            "dispute", "withdraw_report" -> body.put("p_reason", message)
            "respond" -> body.put("p_message", message)
            "external" -> body.put("p_reference", message)
        }
        val name = when (action) {
            "pay" -> "pay_service_from_wallet"; "complete" -> "complete_service_and_release"
            "refund" -> "refund_payment_request"; "cancel" -> "cancel_payment_request"
            "dispute" -> "open_payment_dispute"; "respond" -> "respond_payment_dispute"
            "external" -> "report_external_payment"; "confirm_external" -> "record_external_service_payment"
            "withdraw_report" -> "withdraw_external_payment_report"; else -> error("Unknown payment action")
        }
        rpc(name, body)
    }
    private fun JSONObject.nullLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key)
    private fun service(row: JSONObject): ServicePayment {
        val dispute = row.optJSONObject("dispute")?.let { PaymentDispute(it.getString("id"), it.getString("reason"), it.getString("state"), it.text("resolution")) }
        val events = row.optJSONArray("events") ?: JSONArray()
        return ServicePayment(row.getString("id"), row.getString("business_id"), row.getString("business_name"), row.getString("customer_id"),
            row.text("customer_name"), row.getString("description"), row.getLong("amount_minor"), row.getString("state"), row.getString("created_at"),
            row.getBoolean("can_release"), row.getBoolean("can_refund"), row.getBoolean("external_reported"), row.text("completed_at"), dispute,
            (0 until events.length()).map { i -> events.getJSONObject(i).let { PaymentEvent(it.getString("action"), it.getString("message"), it.getString("created_at"), it.getString("actor_name")) } })
    }
}

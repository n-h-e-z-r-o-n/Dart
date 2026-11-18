package com.zadhron.adaa.data.remote

import android.content.Context
import com.zadhron.adaa.data.model.*
import org.json.JSONArray
import org.json.JSONObject

class BookingRepository(context: Context) : BookingGateway {
    private val client = SupabaseRepository.shared(context.applicationContext)
    private suspend fun rpc(name: String, body: JSONObject, user: String?): String {
        if (user == null) return client.publicRpc(name, body)
        val identity = client.restoreSession()
        if (identity?.id != user || identity.passwordRecovery) throw SessionExpiredException()
        return client.paymentRpc(name, body, user)
    }
    private fun business(id: String?) = JSONObject().put("p_business_id", id ?: JSONObject.NULL)
    private fun <T> rows(text: String, decode: (JSONObject) -> T) = JSONArray(text).let { a -> (0 until a.length()).map { decode(a.getJSONObject(it)) } }
    override suspend fun storefront(businessId: String, userId: String?) = JSONObject(rpc("get_storefront", business(businessId), userId)).let { r ->
        val photos = r.getJSONArray("photos")
        Storefront(r.getString("phone"), r.getString("email"), r.getString("website"), r.getString("hours"), r.getString("timezone"),
            (0 until photos.length()).map { photos.getString(it) }, r.getBoolean("accepting_requests"), r.getBoolean("can_manage"),
            rows(r.getJSONArray("services").toString()) { CatalogService(it.getString("id"), it.getString("name"), it.getString("description"), it.getLong("price_minor"), it.getInt("duration_minutes"), it.getBoolean("active")) })
    }
    override suspend fun saveStorefront(businessId: String, userId: String, value: Storefront) {
        rpc("save_storefront", business(businessId).put("p_phone", value.phone).put("p_email", value.email).put("p_website", value.website)
            .put("p_hours", value.hours).put("p_timezone", value.timezone).put("p_photos", JSONArray(value.photos))
            .put("p_accepting_requests", value.acceptingRequests), userId)
    }
    override suspend fun saveService(businessId: String, userId: String, value: CatalogService) {
        rpc("save_catalog_service", business(businessId).put("p_id", value.id).put("p_name", value.name).put("p_description", value.description)
            .put("p_price_minor", value.priceMinor).put("p_duration_minutes", value.durationMinutes).put("p_active", value.active), userId)
    }
    override suspend fun submit(userId: String, requestId: String, businessId: String, service: CatalogService?, preferredAt: String?, message: String): String =
        org.json.JSONTokener(rpc("submit_booking_request", business(businessId).put("p_request_id", requestId)
            .put("p_service_id", service?.id ?: JSONObject.NULL).put("p_preferred_at", preferredAt ?: JSONObject.NULL)
            .put("p_message", message).put("p_expected_price_minor", service?.priceMinor ?: JSONObject.NULL), userId)).nextValue() as String
    override suspend fun requests(userId: String, businessId: String?, before: BookingRequest?, state: String?) = rows(rpc("get_booking_requests", business(businessId).put("p_state", state ?: JSONObject.NULL).apply {
        before?.let { put("p_before", it.createdAt); put("p_before_id", it.id) }
    }, userId), ::booking)
    private fun booking(r: JSONObject): BookingRequest =
        BookingRequest(r.getString("id"), r.getString("business_name"), r.getString("customer_name"), r.getString("service_name"),
            if (r.isNull("price_minor")) null else r.getLong("price_minor"), if (r.isNull("duration_minutes")) null else r.getInt("duration_minutes"),
            r.getString("kind"), if (r.isNull("preferred_at")) "" else r.getString("preferred_at"), r.getString("timezone"), r.getString("message"),
            r.getString("state"), r.getString("response"), r.getString("created_at"), r.getBoolean("can_manage"), r.getBoolean("is_customer"))
    override suspend fun detail(userId: String, id: String): Pair<BookingRequest, List<BookingEvent>> {
        val row = JSONObject(rpc("get_booking_detail", JSONObject().put("p_id", id), userId))
        return booking(row) to rows(row.getJSONArray("events").toString()) {
            BookingEvent(it.getString("state"), it.getString("message"), it.getString("created_at"))
        }
    }
    override suspend fun act(userId: String, id: String, action: String, response: String) {
        rpc("act_on_booking", JSONObject().put("p_id", id).put("p_action", action).put("p_response", response), userId)
    }
}

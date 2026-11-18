package com.zadhron.adaa.data.remote

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.zadhron.adaa.BuildConfig
import com.zadhron.adaa.data.model.*
import com.zadhron.adaa.data.session.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

data class BusinessSummary(val id: String, val name: String, val operatingModel: String,
    val status: String = "published", val role: String = "", val category: String = "")
data class BusinessCategory(val id: Long, val name: String)

/** HTTP transport only. Business permissions, validation and metrics live in Backend. */
class SupabaseRepository(context: Context) : JourneyGateway {
    override val configured get() = BuildConfig.SUPABASE_URL.isNotBlank()
    private val store = SecureSessionStore(context.applicationContext)
    @Volatile private var session: JSONObject? = null
    private val refreshLock = Mutex()
    private class ApiException(val status: Int, val code: String, message: String) : Exception(message)

    private suspend fun request(path: String, body: JSONObject? = null, authenticated: Boolean = false,
        method: String = if (body == null) "GET" else "POST", retry: Boolean = true, expectedUser: String? = null): String = withContext(Dispatchers.IO) {
        check(configured) { "Adaa is temporarily unavailable. Please try again later." }
        if (authenticated && session == null) throw SessionExpiredException()
        val requestSession = session
        val requestUser = requestSession?.optJSONObject("user")?.optString("id")
        val requestToken = requestSession?.optString("access_token")
        if (authenticated && expectedUser != null && requestUser != expectedUser) throw SessionExpiredException()
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer ${requestToken}")
            body?.let { value ->
                connection.doOutput = true
                connection.outputStream.use { it.write(value.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                if (status == 401 && authenticated) {
                    if (session?.optJSONObject("user")?.optString("id") != requestUser) throw SessionExpiredException()
                    if (retry) {
                        refreshSession(requestToken)
                        return@withContext request(path, body, true, method, false, requestUser)
                    }
                    if (session === requestSession) clearSession()
                    throw SessionExpiredException()
                }
                val json = runCatching { JSONObject(response) }.getOrDefault(JSONObject())
                val code = json.optString("error_code", json.optString("code"))
                val raw = json.optString("msg", json.optString("message"))
                val message = when {
                    status == 429 -> "Too many attempts. Please wait a little and try again."
                    code == "invalid_credentials" -> "That email and password don't match. Please try again."
                    code == "email_not_confirmed" -> "Please confirm your email before signing in."
                    code == "weak_password" -> "Choose a longer password with upper- and lower-case letters, a number and a symbol to meet the account's security requirements."
                    code == "user_already_exists" -> "Please sign in with your existing account, or reset your password."
                    code == "over_email_send_rate_limit" -> "Please wait a minute before requesting another email."
                    code == "validation_failed" -> "Check your email address and try again."
                    code == "captcha_failed" -> "Account access needs additional verification. Please contact support."
                    raw == "FORBIDDEN" || status == 403 -> "You don't have permission to do that. Try refreshing your businesses."
                    raw == "BUSINESS_SUSPENDED" -> "This listing is suspended. Contact the platform team before publishing."
                    raw == "LISTING_UNAVAILABLE" -> "This business is no longer available for new requests. Existing requests remain in your bookings."
                    raw == "REQUESTS_PAUSED" -> "The business has paused new requests. Contact it directly or try again later."
                    raw == "SERVICE_UNAVAILABLE" -> "This service is no longer offered. Return to the storefront and choose another service."
                    raw == "SERVICE_PRICE_CHANGED" -> "The service price changed. Return to the storefront, refresh and review the updated price before sending."
                    raw == "INVALID_BOOKING_TIME" -> "Choose a future date and time within the next year."
                    raw == "BOOKING_RATE_LIMIT" -> "You've sent several requests recently. Please wait before sending another."
                    raw == "BOOKING_STATE_CHANGED" -> "This request has changed or the action is no longer available. Return to the inbox and refresh."
                    raw == "BOOKING_REASON_REQUIRED" -> "Add a reason before cancelling or declining this request."
                    raw == "CATALOG_LIMIT" -> "This business has reached the 200-service catalogue limit. Edit an existing service instead."
                    raw == "WALLET_POLICY_NOT_CONFIGURED" -> "Wallet limits and fees have not been configured yet."
                    raw == "WALLET_AMOUNT_OUTSIDE_LIMITS" -> "Enter a whole-shilling amount within the displayed limits."
                    raw == "WALLET_POLICY_CHANGED" -> "The payment rules changed. Go back and review a fresh quote."
                    raw == "WALLET_DAILY_LIMIT" -> "This request exceeds your remaining daily wallet limit."
                    raw == "ACTIVE_WALLET_OPERATION" -> "You already have a pending request. Check its status in your wallet."
                    raw == "WITHDRAWAL_BLOCKED_BY_DISPUTE" -> "Withdrawals are on hold while a payment dispute is reviewed."
                    raw == "DISPUTE_WINDOW_CLOSED" -> "This payment is outside the configured dispute window. Contact support."
                    raw == "EXTERNAL_PAYMENT_AWAITING_CONFIRMATION" -> "Your direct payment report needs to be checked before this can be paid through the wallet."
                    raw == "INVALID_SERVICE_STATE" || raw == "ALREADY_FUNDED" || raw == "ALREADY_COMPLETED" || raw == "ALREADY_REFUNDED" -> "This payment has changed. Refresh to see its current status."
                    code == "23503" -> "That customer payment code or business member could not be found. Check it and try again."
                    raw == "INSUFFICIENT_FUNDS" -> "Your available wallet balance is too low for this payment."
                    raw == "WALLET_PAYMENTS_NOT_ACTIVE" -> "Wallet deposits and withdrawals are not active yet."
                    raw == "INVALID_CATEGORY" -> "That category is no longer available. Choose another category."
                    raw == "REQUEST_ALREADY_USED" -> "This business was already created. Open My businesses to continue."
                    code.startsWith("PGRST") || code == "42883" || code == "42P01" -> "This service is not available yet. Please try again later."
                    status >= 500 -> "Adaa is having trouble connecting. Please try again shortly."
                    path.startsWith("/auth/") -> "We couldn't complete that request. Check your details or request a new email link."
                    else -> "We couldn't save that change. Please check your information and try again."
                }
                throw ApiException(status, code, message)
            }
            response
        } finally { connection.disconnect() }
    }

    internal suspend fun paymentRpc(name: String, body: JSONObject, expectedUser: String): String = request("/rest/v1/rpc/$name", body, authenticated = true, expectedUser = expectedUser)
    internal suspend fun publicRpc(name: String, body: JSONObject): String = request("/rest/v1/rpc/$name", body)

    private fun persistSession(value: JSONObject?) {
        session = value
        val root = store.read()
        if (value == null) root.remove("session") else root.put("session", value)
        store.write(root)
    }
    private fun clearSession() = persistSession(null)
    private fun identity(value: JSONObject): SessionIdentity {
        val user = value.getJSONObject("user")
        return SessionIdentity(user.getString("id"), user.optString("email"), value.optBoolean("recovery"),
            when (user.optJSONObject("user_metadata")?.optString("account_type")) {
                "personal" -> AppMode.CUSTOMER
                "business" -> AppMode.BUSINESS
                else -> null
            })
    }
    private fun acceptSession(response: JSONObject, recovery: Boolean = false): SessionIdentity {
        check(response.optString("access_token").isNotBlank() && response.optString("refresh_token").isNotBlank()) {
            "The sign-in link has expired. Please request a new one."
        }
        response.put("recovery", recovery)
        if (!response.has("expires_at")) response.put("expires_at", System.currentTimeMillis() / 1000 + response.optLong("expires_in", 3600))
        val user = identity(response)
        persistSession(response)
        return user
    }

    private suspend fun refreshSession(expectedToken: String? = session?.optString("access_token")) = refreshLock.withLock {
        val current = session ?: throw SessionExpiredException()
        if (current.optString("access_token") != expectedToken) return@withLock
        try {
            val response = JSONObject(request("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", current.getString("refresh_token")), retry = false))
            if (session !== current) throw SessionExpiredException()
            acceptSession(response, current.optBoolean("recovery"))
        } catch (error: ApiException) {
            if (error.status in listOf(400, 401, 403) && error.code !in listOf("request_timeout", "unexpected_failure")) {
                if (session === current) clearSession()
                throw SessionExpiredException()
            }
            throw error
        }
    }

    override suspend fun restoreSession(): SessionIdentity? = withContext(Dispatchers.IO) {
        session = store.read().optJSONObject("session") ?: return@withContext null
        if (session!!.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 60) refreshSession()
        val user = JSONObject(request("/auth/v1/user", authenticated = true))
        val current = session ?: throw SessionExpiredException()
        current.put("user", user)
        persistSession(current)
        identity(current)
    }

    private fun newChallenge(kind: String, email: String): String {
        val verifier = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val root = store.read().put("pending", JSONObject().put("verifier", verifier)
            .put("kind", kind).put("email", email).put("created", System.currentTimeMillis()))
        store.write(root)
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    private fun clearPending() { val root = store.read(); root.remove("pending"); store.write(root) }
    private val redirectQuery get() = "?redirect_to=" + Uri.encode(AUTH_REDIRECT)

    override suspend fun register(email: String, password: String, accountType: AppMode): AuthenticationResult =
        authenticateRequest(email, password, true, accountType)

    override suspend fun authenticate(email: String, password: String, register: Boolean): AuthenticationResult =
        authenticateRequest(email, password, register, AppMode.CUSTOMER)

    private suspend fun authenticateRequest(email: String, password: String, register: Boolean, accountType: AppMode): AuthenticationResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password)
        if (register) body.put("data", JSONObject().put("account_type", if (accountType == AppMode.BUSINESS) "business" else "personal"))
        if (register) body.put("code_challenge", newChallenge("signup", email)).put("code_challenge_method", "s256")
        val response = JSONObject(request(if (register) "/auth/v1/signup$redirectQuery" else "/auth/v1/token?grant_type=password", body))
        if (response.optString("access_token").isBlank()) return@withContext AuthenticationResult(needsConfirmation = true)
        val user = acceptSession(response)
        clearPending()
        AuthenticationResult(user)
    }
    override suspend fun resendConfirmation(email: String) {
        request("/auth/v1/resend$redirectQuery", JSONObject().put("email", email).put("type", "signup"))
    }
    override suspend fun requestPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val challenge = newChallenge("recovery", email)
        request("/auth/v1/recover$redirectQuery", JSONObject().put("email", email)
            .put("code_challenge", challenge).put("code_challenge_method", "s256"))
        Unit
    }
    override suspend fun exchangeAuthLink(link: String): SessionIdentity = withContext(Dispatchers.IO) {
        val uri = Uri.parse(link)
        require(uri.scheme == "com.zadhron.adaa" && uri.host == "auth" && uri.path == "/callback") { "This link cannot be opened in Adaa." }
        val pending = store.read().optJSONObject("pending")
            ?: throw IllegalStateException("Please open this link on the device where you requested it, or request a new email.")
        check(System.currentTimeMillis() - pending.getLong("created") < 24 * 60 * 60 * 1000) { "This link is too old. Request a new email." }
        val code = uri.getQueryParameter("code") ?: throw IllegalArgumentException("This link has expired. Please request another email.")
        val response = JSONObject(request("/auth/v1/token?grant_type=pkce",
            JSONObject().put("auth_code", code).put("code_verifier", pending.getString("verifier"))))
        val user = acceptSession(response, pending.optString("kind") == "recovery")
        clearPending()
        user
    }
    override suspend fun updatePassword(password: String) = withContext(Dispatchers.IO) {
        request("/auth/v1/user", JSONObject().put("password", password), authenticated = true, method = "PUT")
        session?.let { persistSession(it.put("recovery", false)) }
        Unit
    }
    override suspend fun signOut() = withContext(Dispatchers.IO) {
        try { if (session != null) request("/auth/v1/logout?scope=local", JSONObject(), authenticated = true) }
        finally { clearSession(); clearPending() }
    }

    override suspend fun categories(): List<BusinessCategory> {
        val rows = JSONArray(request("/rest/v1/business_categories?select=id,name&order=name"))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> BusinessCategory(row.getLong("id"), row.getString("name")) } }
    }
    override suspend fun businesses(query: String, mine: Boolean, offset: Int): List<BusinessSummary> {
        val path = if (mine) "get_my_businesses" else "search_businesses"
        val parameters = if (mine) JSONObject() else JSONObject().put("p_query", query).put("p_limit", 20).put("p_offset", offset)
        val rows = JSONArray(request("/rest/v1/rpc/$path", parameters, authenticated = mine))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row ->
            BusinessSummary(row.getString("id"), row.getString("name"), row.getString("operating_model"),
                row.optString("publication_status", "published"), row.optString("role"), row.optString("category_name"))
        } }
    }
    private fun draftJson(draft: BusinessDraft) = JSONObject().put("p_name", draft.name.trim())
        .put("p_category_id", draft.categoryId).put("p_operating_model", draft.operatingModel)
        .put("p_location_or_area", draft.location.trim()).put("p_description", draft.description.trim())

    override suspend fun createBusiness(draft: BusinessDraft): String {
        val parameters = draftJson(draft).put("p_request_id", draft.requestId)
            .put("p_timezone", java.time.ZoneId.systemDefault().id)
        return org.json.JSONTokener(request("/rest/v1/rpc/create_business_once", parameters, authenticated = true)).nextValue() as String
    }
    override suspend fun updateBusiness(id: String, draft: BusinessDraft) {
        request("/rest/v1/rpc/update_business", draftJson(draft).put("p_business_id", id), authenticated = true)
    }
    private fun details(row: JSONObject) = BusinessDetails(row.getString("id"), row.getString("name"),
        row.getLong("category_id"), row.getString("category"), row.getString("operating_model"),
        row.optString("publication_status", "published"), row.optString("verification_status", "unverified"),
        row.optString("role"), row.optString("location_or_area"), row.optString("description"),
        row.optInt("members_count"), row.optInt("locations_count"), row.optBoolean("can_publish"))
    override suspend fun dashboard(id: String): BusinessDetails {
        val business = details(JSONObject(request("/rest/v1/rpc/get_business_dashboard", JSONObject().put("p_business_id", id), authenticated = true)))
        if (business.role != "owner") return business
        return try {
            val row = JSONObject(request("/rest/v1/rpc/get_wallet_dashboard", JSONObject().put("p_business_id", id), authenticated = true))
            check(row.getString("currency") == "KES") { "This wallet currency isn't supported yet." }
            val entries = row.getJSONArray("recent_transactions")
            business.copy(wallet = WalletSummary(row.getString("currency"), row.getLong("available_minor"), row.getLong("escrow_minor"),
                row.getLong("withdrawal_pending_minor"), row.getLong("completed_services"), row.getLong("held_services"),
                row.getLong("completed_service_value_minor"), row.getBoolean("payments_active"),
                (0 until entries.length()).map { i -> entries.getJSONObject(i).let { e ->
                    WalletTransaction(e.getString("kind"), e.getString("bucket"), e.getLong("amount_minor"), e.getString("created_at")) } }))
        } catch (error: ApiException) {
            business.copy(walletIssue = if (error.code == "PGRST202" || error.code == "42883")
                "Your wallet is not activated yet." else "Wallet information is unavailable. Refresh to try again.")
        } catch (_: java.io.IOException) { business.copy(walletIssue = "Wallet information couldn't be refreshed. Check your connection.") }
    }
    override suspend fun businessTeam(id: String): List<BusinessTeamMember> {
        val rows = JSONArray(request("/rest/v1/rpc/get_business_wallet_team", JSONObject().put("p_business_id", id), authenticated = true))
        return (0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            BusinessTeamMember(row.getString("user_id"), row.getString("display_name"), row.getString("role"), row.getBoolean("can_release_escrow")) } }
    }
    override suspend fun setEscrowPermission(id: String, userId: String, allowed: Boolean) {
        request("/rest/v1/rpc/set_escrow_release_permission", JSONObject().put("p_business_id", id).put("p_user_id", userId).put("p_allowed", allowed), authenticated = true)
    }
    override suspend fun publicProfile(id: String): BusinessDetails {
        val value = request("/rest/v1/rpc/get_business_public_profile", JSONObject().put("p_business_id", id))
        check(value.isNotBlank() && value != "null") { "This listing isn't available. It may have been unpublished." }
        return details(JSONObject(value))
    }
    override suspend fun publishBusiness(id: String) {
        request("/rest/v1/rpc/publish_business", JSONObject().put("p_business_id", id), authenticated = true)
    }
    companion object {
        const val AUTH_REDIRECT = "com.zadhron.adaa://auth/callback"
        @Volatile private var sharedInstance: SupabaseRepository? = null
        fun shared(context: Context): SupabaseRepository = sharedInstance ?: synchronized(this) {
            sharedInstance ?: SupabaseRepository(context.applicationContext).also { sharedInstance = it }
        }
    }
}

package com.zadhron.adaa.data.model

import com.zadhron.adaa.data.remote.BusinessCategory
import com.zadhron.adaa.data.remote.BusinessSummary
import java.util.UUID

enum class AppMode { CUSTOMER, BUSINESS }
enum class JourneyScreen {
    STARTUP, WELCOME, EXPLORE, SIGN_IN, ACCOUNT_TYPE, SIGN_UP, CONFIRM_EMAIL,
    FORGOT_PASSWORD, RESET_SENT, RESET_PASSWORD, BUSINESSES,
    BUSINESS_BASICS, BUSINESS_LOCATION, BUSINESS_REVIEW, BUSINESS_HOME,
    BUSINESS_DETAILS, BUSINESS_TEAM, PUBLIC_PROFILE, ACCOUNT, PERSONAL_WALLET, BUSINESS_WALLET, PERSONAL_PAYMENTS, BUSINESS_PAYMENTS,
    PERSONAL_BOOKINGS, BUSINESS_BOOKINGS
}

data class SessionIdentity(val id: String, val email: String, val passwordRecovery: Boolean = false, val accountType: AppMode? = null)
data class AuthenticationResult(val identity: SessionIdentity? = null, val needsConfirmation: Boolean = false)
data class BusinessDraft(
    val requestId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val categoryId: Long? = null,
    val operatingModel: String = "physical",
    val location: String = "",
    val description: String = ""
)
data class BusinessDetails(
    val id: String,
    val name: String,
    val categoryId: Long,
    val category: String,
    val operatingModel: String,
    val status: String,
    val verification: String = "unverified",
    val role: String = "",
    val location: String = "",
    val description: String = "",
    val membersCount: Int = 0,
    val locationsCount: Int = 0,
    val canPublish: Boolean = false,
    val wallet: WalletSummary? = null,
    val walletIssue: String? = null
)

data class WalletTransaction(val kind: String, val bucket: String, val amountMinor: Long, val createdAt: String)
data class WalletSummary(val currency: String, val availableMinor: Long, val escrowMinor: Long,
    val withdrawalPendingMinor: Long, val completedServices: Long, val heldServices: Long,
    val completedServiceValueMinor: Long, val paymentsActive: Boolean, val recentTransactions: List<WalletTransaction>, val heldForServicesMinor: Long = 0, val withdrawalsBlocked: Boolean = false)
data class BusinessTeamMember(val userId: String, val displayName: String, val role: String, val canReleaseEscrow: Boolean)

/** Implementations perform transport; the database remains authoritative. */
interface JourneyGateway {
    val configured: Boolean
    suspend fun restoreSession(): SessionIdentity?
    suspend fun authenticate(email: String, password: String, register: Boolean): AuthenticationResult
    suspend fun register(email: String, password: String, accountType: AppMode): AuthenticationResult = authenticate(email, password, true)
    suspend fun resendConfirmation(email: String)
    suspend fun requestPasswordReset(email: String)
    suspend fun exchangeAuthLink(link: String): SessionIdentity
    suspend fun updatePassword(password: String)
    suspend fun signOut()
    suspend fun categories(): List<BusinessCategory>
    suspend fun businesses(query: String = "", mine: Boolean = false, offset: Int = 0): List<BusinessSummary>
    suspend fun createBusiness(draft: BusinessDraft): String
    suspend fun updateBusiness(id: String, draft: BusinessDraft)
    suspend fun dashboard(id: String): BusinessDetails
    suspend fun publicProfile(id: String): BusinessDetails
    suspend fun businessTeam(id: String): List<BusinessTeamMember> = error("Team permissions are not available yet.")
    suspend fun setEscrowPermission(id: String, userId: String, allowed: Boolean) { error("Team permissions are not available yet.") }
    suspend fun publishBusiness(id: String)
}

interface JourneyPreferences {
    var welcomed: Boolean
    var mode: AppMode
    fun selectedBusiness(userId: String): String?
    fun selectBusiness(userId: String, businessId: String)
}

class SessionExpiredException : Exception("Your session has ended. Please sign in again.")

/** A returning account can only resume a business in its current membership list. */
fun businessToResume(businesses: List<BusinessSummary>, rememberedId: String?): String? =
    businesses.firstOrNull { it.id == rememberedId }?.id ?: businesses.singleOrNull()?.id
